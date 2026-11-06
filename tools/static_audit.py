#!/usr/bin/env python3
"""
Static consistency audit for FamilySafeTube. Needs only Python 3 (no Android SDK).

It cannot replace a real build, but it catches the class of mistakes that a syntax check misses:
 - a function that is called but was deleted,
 - a string/resource that is used but not defined (or defined in English but not Arabic),
 - an activity in the manifest with no class,
 - a version-catalog alias used in a Gradle file that does not exist.

Usage:  python3 tools/static_audit.py        (exit code 1 if anything fails)
"""
import glob, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app/src/main")
failures = []

def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()

def check(name, problems):
    status = "ok  " if not problems else "FAIL"
    print(f"[{status}] {name}")
    for p in problems:
        print(f"         - {p}")
        failures.append((name, p))

kt_files = glob.glob(os.path.join(MAIN, "java/**/*.kt"), recursive=True)
code = {f: read(f) for f in kt_files}
all_code = "\n".join(code.values())
no_comments = {f: re.sub(r"//.*", "", re.sub(r"/\*.*?\*/", "", t, flags=re.S)) for f, t in code.items()}

# 1. Manifest -> classes
manifest = read(os.path.join(MAIN, "AndroidManifest.xml"))
problems = []
for name in re.findall(r'android:name="\.([A-Za-z0-9_.]+)"', manifest):
    if not re.search(r"\b(class|object)\s+%s\b" % re.escape(name.split(".")[-1]), all_code):
        problems.append(f"manifest references .{name} but no such class exists")
check("manifest components exist as classes", problems)

# 2. Strings: used vs defined, en/ar parity, format-argument parity
def strings(path):
    return dict(re.findall(r'<string name="([^"]+)">(.*?)</string>', read(path), flags=re.S))
en = strings(os.path.join(MAIN, "res/values/strings.xml"))
ar = strings(os.path.join(MAIN, "res/values-ar/strings.xml"))
used = set(re.findall(r"(?<![A-Za-z.])R\.string\.([A-Za-z0-9_]+)", all_code))
used |= set(re.findall(r"@string/([A-Za-z0-9_]+)", manifest))
problems  = [f"R.string.{n} used but not defined in English" for n in sorted(used - set(en))]
problems += [f"R.string.{n} used but not defined in Arabic" for n in sorted(used - set(ar))]
problems += [f"'{n}' defined in only one language" for n in sorted(set(en) ^ set(ar))]
args = lambda s: sorted(re.findall(r"%\d\$[sd]", s))
problems += [f"'{n}' has different format arguments in en/ar" for n in en if n in ar and args(en[n]) != args(ar[n])]
problems += [f"'{n}' uses a non-positional format (%s/%d)" for n, v in en.items() if re.search(r"%(?!\d\$)[sd]", v)]
check("strings: defined, translated, formats match", problems)
check("strings: none unused", [f"'{n}' is defined but never used" for n in sorted(set(en) - used)])

# 3. Other resources referenced from code/xml exist
res_files = {}
for kind in ("drawable", "mipmap", "layout", "xml", "raw", "values"):
    for f in glob.glob(os.path.join(MAIN, f"res/{kind}*/*")):
        res_files.setdefault(kind.split("-")[0] if kind != "values" else "values", set()).add(os.path.splitext(os.path.basename(f))[0])
defined = {"color": set(), "style": set()}
for f in glob.glob(os.path.join(MAIN, "res/values*/*.xml")):
    t = read(f)
    defined["color"] |= set(re.findall(r'<color name="([^"]+)"', t))
    defined["style"] |= set(re.findall(r'<style name="([^"]+)"', t))
problems = []
for kind in ("drawable", "mipmap", "layout", "xml", "raw"):
    for ref in set(re.findall(r"(?<![A-Za-z.])R\.%s\.([A-Za-z0-9_]+)" % kind, all_code)):
        if ref not in res_files.get(kind, set()):
            problems.append(f"R.{kind}.{ref} used but missing")
xml_text = manifest + "".join(read(f) for f in glob.glob(os.path.join(MAIN, "res/**/*.xml"), recursive=True))
for kind in ("drawable", "mipmap", "layout", "xml", "raw"):
    for ref in set(re.findall(r"@%s/([A-Za-z0-9_]+)" % kind, xml_text)):
        if ref not in res_files.get(kind, set()):
            problems.append(f"@{kind}/{ref} referenced in XML but missing")
for ref in set(re.findall(r"@color/([A-Za-z0-9_]+)", xml_text)) | set(re.findall(r"(?<![A-Za-z.])R\.color\.([A-Za-z0-9_]+)", all_code)):
    if ref not in defined["color"]:
        problems.append(f"color {ref} referenced but not defined")
for ref in set(re.findall(r"@style/([A-Za-z0-9_.]+)", manifest)):
    if ref not in defined["style"] and not ref.startswith(("Theme.AppCompat", "Theme.Material")):
        problems.append(f"style {ref} referenced but not defined")
check("resources referenced exist", problems)

# 4. Calls to our own objects resolve to a definition
defs = set(re.findall(r"\bfun\s+(?:[A-Za-z0-9_<>?.]+\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(", all_code))
objs = sorted(set(re.findall(r"\bobject\s+([A-Z][A-Za-z0-9]*)", all_code)))
problems = []
for f, t in no_comments.items():
    for m in re.finditer(r"\b(%s)\.([a-z][A-Za-z0-9_]*)\s*\(" % "|".join(objs), t):
        if m.group(2) not in defs:
            problems.append(f"{os.path.basename(f)}: {m.group(1)}.{m.group(2)}() is not defined anywhere")
check("calls on our objects resolve", sorted(set(problems)))

# 4a. Bundled animal sounds: catalog keys <-> BuiltInSounds <-> res/raw files all line up
cat_src = read(os.path.join(MAIN, "java/com/example/familysafetube/QuizCatalog.kt"))
bi_src = read(os.path.join(MAIN, "java/com/example/familysafetube/BuiltInSounds.kt"))
keys = set(re.findall(r'"([a-z]+)"', re.search(r"SOUND_KEYS\s*=\s*setOf\((.*?)\)", cat_src, re.S).group(1)))
mapped = dict(re.findall(r'"([a-z]+)"\s*->\s*R\.raw\.([a-z0-9_]+)', bi_src))
problems = []
for k in sorted(keys):
    if k not in mapped:
        problems.append(f"sound key '{k}' is in QuizCatalog.SOUND_KEYS but not mapped in BuiltInSounds")
    elif not os.path.exists(os.path.join(MAIN, f"res/raw/{mapped[k]}.ogg")):
        problems.append(f"BuiltInSounds maps '{k}' to R.raw.{mapped[k]} but res/raw/{mapped[k]}.ogg is missing")
for k in sorted(set(mapped) - keys):
    problems.append(f"BuiltInSounds maps '{k}' which is not in QuizCatalog.SOUND_KEYS")
for f in glob.glob(os.path.join(MAIN, "res/raw/*")):
    if os.path.getsize(f) < 1000:
        problems.append(f"{os.path.basename(f)} is suspiciously small")
for entry_sound in re.findall(r'CatalogEntry\(.*?\)|add\("[^"]+",\s*"[^"]+",\s*"([a-z]+)"\)', cat_src):
    if entry_sound and entry_sound not in keys:
        problems.append(f"catalog entry uses unknown sound key '{entry_sound}'")
check("built-in sounds: catalog, code and files agree", problems)

# 4b. Every class that implements one of OUR interfaces overrides all of its methods
def supertypes_of(text, header_end):
    """Text of the supertype list (after ':'), skipping the primary-constructor parentheses."""
    i = header_end
    while i < len(text) and text[i].isspace(): i += 1
    if i < len(text) and text[i] == "<":                      # generics
        depth = 0
        while i < len(text):
            depth += {"<": 1, ">": -1}.get(text[i], 0); i += 1
            if depth == 0: break
    while i < len(text) and text[i].isspace(): i += 1
    if i < len(text) and text[i] == "(":                      # primary constructor
        depth = 0
        while i < len(text):
            depth += {"(": 1, ")": -1}.get(text[i], 0); i += 1
            if depth == 0: break
    head = text[i:text.find("{", i) if text.find("{", i) != -1 else len(text)]
    head = head.split("\n\n")[0]
    return head.split(":", 1)[1] if ":" in head else ""

problems = []
for f, t in no_comments.items():
    for im in re.finditer(r"\binterface\s+([A-Z][A-Za-z0-9]*)\s*\{", t):
        name, start = im.group(1), im.end()
        depth, i = 1, start
        while i < len(t) and depth:
            depth += {"{": 1, "}": -1}.get(t[i], 0)
            i += 1
        methods = re.findall(r"^\s*fun\s+([A-Za-z0-9_]+)\s*\(", t[start:i - 1], flags=re.M)
        for g, gt in no_comments.items():
            for cm in re.finditer(r"\b(?:class|object)\s+([A-Z][A-Za-z0-9]*)", gt):
                if re.search(r"\binterface\s+$", gt[max(0, cm.start() - 12):cm.start()]):
                    continue
                supers = supertypes_of(gt, cm.end())
                if not re.search(r"\b%s\b" % name, supers):
                    continue
                # class body = from the first '{' after the header to its matching '}'
                bstart = gt.find("{", cm.end())
                body = gt[bstart:] if bstart != -1 else ""
                for m in methods:
                    if not re.search(r"\boverride\s+fun\s+%s\s*\(" % re.escape(m), body):
                        problems.append(f"{os.path.basename(g)}: {cm.group(1)} implements {name} but has no 'override fun {m}'")
check("implementations cover their interfaces", sorted(set(problems)))

# 5. Version-catalog aliases used by Gradle exist
catalog = read(os.path.join(ROOT, "gradle/libs.versions.toml"))
sections = {}
cur = None
for line in catalog.splitlines():
    m = re.match(r"\[(\w+)\]", line.strip())
    if m: cur = m.group(1); sections[cur] = set(); continue
    m = re.match(r"([A-Za-z0-9_-]+)\s*=", line.strip())
    if m and cur: sections[cur].add(m.group(1).replace("-", ".").replace("_", "."))
problems = []
for gf in [os.path.join(ROOT, "build.gradle.kts"), os.path.join(ROOT, "app/build.gradle.kts")]:
    t = read(gf)
    for alias in set(re.findall(r"libs\.plugins\.([A-Za-z0-9_.]+)", t)):
        if alias.replace("_", ".") not in sections.get("plugins", set()):
            problems.append(f"{os.path.basename(os.path.dirname(gf)) or 'root'}: plugin alias libs.plugins.{alias} missing")
    for alias in set(re.findall(r"\blibs\.(?!plugins\.|versions\.)([A-Za-z0-9_.]+)", t)):
        if alias not in sections.get("libraries", set()):
            problems.append(f"library alias libs.{alias} missing from the catalog")
for v in re.findall(r"version\.ref\s*=\s*\"([^\"]+)\"", catalog):
    if v not in {n for n in re.findall(r"^([A-Za-z0-9_-]+)\s*=", catalog.split("[libraries]")[0], flags=re.M)}:
        problems.append(f"version.ref '{v}' not defined under [versions]")
check("version catalog aliases resolve", problems)

# 6. Every file the build reads exists
problems = [f"missing {p}" for p in ("app/proguard-rules.pro", "gradle/wrapper/gradle-wrapper.properties")
            if not os.path.exists(os.path.join(ROOT, p))]
check("required build files present", problems)

# 7. Build tooling: Gradle / AGP / Kotlin must agree (source: developer.android.com/build/releases/about-agp, 2026-10)
MIN_GRADLE = (9, 4)                               # project policy: Gradle 9.4 or newer, never older
AGP_MIN_GRADLE = {                                # AGP minor -> minimum Gradle
    "9.6": (9, 6, 0), "9.5": (9, 6, 0), "9.4": (9, 6, 0), "9.3": (9, 5, 0), "9.2": (9, 4, 1), "9.1": (9, 3, 1), "9.0": (9, 1, 0),
    "8.13": (8, 13), "8.12": (8, 13), "8.11": (8, 13), "8.10": (8, 11, 1), "8.9": (8, 11, 1),
}
AGP_MAX_API = {"9.5": 37, "9.4": 37, "9.3": 37, "9.2": 37}   # highest compileSdk each AGP supports
KNOWN_BAD_AGP = {"9.2.0": "R8 'RecordTag' ClassNotFoundException; fixed in 9.2.1"}

def strip_comments(text):
    return "\n".join(re.sub(r"(//|#).*", "", ln) for ln in text.splitlines())

def vtuple(v): return tuple(int(x) for x in re.findall(r"\d+", v))

problems = []
wrapper = read(os.path.join(ROOT, "gradle/wrapper/gradle-wrapper.properties"))
mw = re.search(r"gradle-([0-9.]+)-bin\.zip", wrapper)
cat_versions = dict(re.findall(r'^([A-Za-z0-9_-]+)\s*=\s*"([^"]+)"', catalog.split("[libraries]")[0], flags=re.M))
agp = cat_versions.get("agp")
app_gradle = read(os.path.join(ROOT, "app/build.gradle.kts"))
if not mw:
    problems.append("cannot read the Gradle version from gradle-wrapper.properties")
elif not agp:
    problems.append("cannot read 'agp' from the version catalog")
else:
    gradle = mw.group(1)
    if vtuple(gradle) < MIN_GRADLE:
        problems.append(f"Gradle {gradle} is older than the required {'.'.join(map(str, MIN_GRADLE))}")
    minor = ".".join(agp.split(".")[:2])
    need = AGP_MIN_GRADLE.get(minor)
    if need is None:
        problems.append(f"AGP {agp}: minor '{minor}' is not in this audit's compatibility table (update the table)")
    elif vtuple(gradle) < need:
        problems.append(f"AGP {agp} needs Gradle >= {'.'.join(map(str, need))} but the wrapper has {gradle}")
    if agp in KNOWN_BAD_AGP:
        problems.append(f"AGP {agp} is a known-bad release: {KNOWN_BAD_AGP[agp]}")
    msdk = re.search(r"compileSdk\s*=\s*(\d+)", app_gradle)
    if msdk and minor in AGP_MAX_API and int(msdk.group(1)) > AGP_MAX_API[minor]:
        problems.append(f"compileSdk {msdk.group(1)} is above what AGP {minor} supports (API {AGP_MAX_API[minor]})")
    if vtuple(agp) >= (9, 0):
        build_text = "\n".join(strip_comments(read(f)) for f in
                               [os.path.join(ROOT, "build.gradle.kts"), os.path.join(ROOT, "app/build.gradle.kts"),
                                os.path.join(ROOT, "gradle/libs.versions.toml")])
        for stale in ("org.jetbrains.kotlin.android", "jetbrainsKotlinAndroid", "kotlin-android", "kotlinOptions", "kotlin-kapt"):
            if stale in build_text:
                problems.append(f"'{stale}' must not be used with AGP 9 (built-in Kotlin)")
cp = re.search(r'kotlin-compose\s*=\s*\{[^}]*version\.ref\s*=\s*"([^"]+)"', catalog)
if not cp or cp.group(1) != "kotlin":
    problems.append("the Compose compiler plugin must use version.ref = \"kotlin\"")
props = read(os.path.join(ROOT, "gradle.properties"))
if "org.gradle.java.installations.auto-download=false" not in props:
    problems.append("gradle.properties must set org.gradle.java.installations.auto-download=false (CI installs the JDK)")
if "foojay" in read(os.path.join(ROOT, "settings.gradle.kts")) or os.path.exists(os.path.join(ROOT, "gradle/gradle-daemon-jvm.properties")):
    problems.append("remove the foojay resolver / gradle-daemon-jvm.properties: the build uses the JDK that CI (or JAVA_HOME) provides")
check("build tooling: Gradle >= 9.4, AGP, Kotlin agree", problems)

# 8. Release pipeline: workflows <-> APK file name <-> in-app parser <-> wizard secrets
import yaml
WF = os.path.join(ROOT, ".github/workflows")
problems = []
wf_text = {}
for name in ("build.yml", "release.yml"):
    path = os.path.join(WF, name)
    if not os.path.exists(path):
        problems.append(f".github/workflows/{name} is missing"); continue
    wf_text[name] = read(path)
    try:
        doc = yaml.safe_load(wf_text[name])
    except yaml.YAMLError as e:
        problems.append(f"{name} is not valid YAML: {e}"); continue
    steps = [st for job in doc["jobs"].values() for st in job["steps"]]
    uses = {str(st.get("uses", "")).split("@")[0]: st for st in steps if "uses" in st}
    java = uses.get("actions/setup-java")
    if not java or java["with"].get("distribution") != "temurin" or str(java["with"].get("java-version")) != "21":
        problems.append(f"{name}: JDK must be Temurin 21")
    sa = uses.get("android-actions/setup-android")
    if not sa or str(sa.get("with", {}).get("packages")) != "platform-tools":
        problems.append(f"{name}: setup-android must use 'packages: platform-tools' only (platform package names change)")
    if "gradle/actions/setup-gradle" not in uses:
        problems.append(f"{name}: gradle/actions/setup-gradle is missing")
    if not any("chmod +x gradlew" in str(st.get("run", "")) for st in steps):
        problems.append(f"{name}: needs a 'chmod +x gradlew' step (Windows cannot set the executable bit)")
    if not any("testDebugUnitTest" in str(st.get("run", "")) for st in steps):
        problems.append(f"{name}: unit tests are not run")
    if "DRIVE_API_KEY" in wf_text[name]:
        problems.append(f"{name}: still passes DRIVE_API_KEY (the app signs in with Google now; no API key)")
rel = wf_text.get("release.yml", "")
parser_src = read(os.path.join(MAIN, "java/com/example/familysafetube/update/UpdateParser.kt"))
rx = re.search(r'APK_NAME\s*=\s*Regex\("""(.+?)""",\s*RegexOption\.IGNORE_CASE', parser_src)
nm = re.search(r'NAME="([^"$]+)-\$\{VERSION_NAME//\./_\}-build\$\{CODE\}\.apk"', rel)
if not rx or not nm:
    problems.append("cannot find the APK name pattern in UpdateParser.kt or the name template in release.yml")
else:
    sample = f"{nm.group(1)}-{'1.3.0'.replace('.', '_')}-build107.apk"
    if not re.match(rx.group(1), sample, re.I):
        problems.append(f"release.yml names the APK '{sample}' but UpdateParser's pattern does not accept it")
    root_name = re.search(r'rootProject\.name\s*=\s*"([^"]+)"', read(os.path.join(ROOT, "settings.gradle.kts")))
    if root_name and nm.group(1) != root_name.group(1):
        problems.append(f"release APK prefix '{nm.group(1)}' differs from rootProject.name '{root_name.group(1)}'")
if "[required]" not in rel:
    problems.append("release.yml does not support the [required] marker")
if "apksigner" not in rel:
    problems.append("release.yml does not verify the APK signature with apksigner")
ps1_path = os.path.join(ROOT, "setup/Setup-FamilySafeTube.ps1")
ps1 = read(ps1_path) if os.path.exists(ps1_path) else ""
used_secrets = set(re.findall(r"secrets\.(\w+)", rel)) - {"GITHUB_TOKEN"}
set_secrets = set(re.findall(r"Set-Secret\s+'(\w+)'", ps1))
if used_secrets - set_secrets:
    problems.append(f"release.yml uses secrets the wizard never saves: {sorted(used_secrets - set_secrets)}")
if "DRIVE_API_KEY" in app_gradle:
    problems.append("app/build.gradle.kts must not read or embed a Drive API key (sign-in replaced it)")
if "play.services.auth" not in app_gradle:
    problems.append("app/build.gradle.kts lacks the Google sign-in dependency (play-services-auth)")
_all_kt = "\n".join(read(f) for f in glob.glob(os.path.join(MAIN, "java/**/*.kt"), recursive=True))
if "DRIVE_API_KEY" in _all_kt or "&key=" in _all_kt:
    problems.append("Kotlin sources still use a Drive API key; every Drive call must carry the user's OAuth token")
if "alt=media" not in _all_kt or "Bearer" not in _all_kt:
    problems.append("Drive downloads must use the authorised API (alt=media + Bearer token)")
dep = read(os.path.join(ROOT, ".github/dependabot.yml")) if os.path.exists(os.path.join(ROOT, ".github/dependabot.yml")) else ""
if "package-ecosystem: gradle" not in dep or "package-ecosystem: github-actions" not in dep:
    problems.append(".github/dependabot.yml must cover gradle and github-actions")
check("release pipeline: workflows, APK name, parser, secrets agree", problems)

# 9. In-app updater wiring
problems = []
man = read(os.path.join(MAIN, "AndroidManifest.xml"))
for perm in ("REQUEST_INSTALL_PACKAGES", "POST_NOTIFICATIONS"):
    if f"android.permission.{perm}" not in man:
        problems.append(f"manifest lacks {perm}")
if 'android:launchMode="singleTop"' not in man:
    problems.append("MainActivity must be singleTop so the update notification reuses it")
if "${applicationId}.fileprovider" not in man:
    problems.append("FileProvider authority must be ${applicationId}.fileprovider")
code = "\n".join(read(f) for f in glob.glob(os.path.join(MAIN, "java/**/*.kt"), recursive=True))
if '"${context.packageName}.fileprovider"' not in code:
    problems.append("UpdateRepository must use the same FileProvider authority as the manifest")
fp = read(os.path.join(MAIN, "res/xml/file_paths.xml"))
if 'path="updates/"' not in fp or 'File(context.cacheDir, "updates")' not in code:
    problems.append("file_paths.xml cache path 'updates/' and the code's cache folder 'updates' must match")
for field in ("UPDATE_REPO",):
    if f'buildConfigField("String", "{field}"' not in app_gradle:
        problems.append(f"app/build.gradle.kts lacks the BuildConfig field {field}")
vp = read(os.path.join(ROOT, "version.properties"))
mo = re.search(r"^versionCodeOffset=(\d+)", vp, re.M)
if not re.search(r"^versionName=\S+", vp, re.M) or not mo:
    problems.append("version.properties needs versionName and versionCodeOffset")
elif int(mo.group(1)) <= 3:
    problems.append("versionCodeOffset must be above every versionCode the app has had (the first GitHub build must install over the old one)")
if "UpdateCheckWorker.schedule(" not in code or "announceIfJustUpdated()" not in code:
    problems.append("the update worker is not scheduled / the 'updated to X' message is not wired")
check("in-app updater: manifest, provider, paths, version, wiring", problems)

# 10. Wizard and repository hygiene
problems = []
if not ps1:
    problems.append("setup/Setup-FamilySafeTube.ps1 is missing")
else:
    raw = open(ps1_path, "rb").read()
    bad = [i for i, b in enumerate(raw) if b > 127]
    if bad:
        problems.append(f"the wizard must be pure ASCII (Windows PowerShell 5.1 mangles anything else); first bad byte at {bad[0]}")
    if b"\r\n" not in raw or re.search(rb"(?<!\r)\n", raw):
        problems.append("the wizard must use CRLF line endings")
for bat, mode in (("START-HERE.bat", "menu"), ("Rebuild-APK.bat", "rebuild")):
    bp = os.path.join(ROOT, bat)
    if not os.path.exists(bp):
        problems.append(f"{bat} is missing"); continue
    raw = open(bp, "rb").read()
    if re.search(rb"(?<!\r)\n", raw): problems.append(f"{bat} must use CRLF line endings")
    if b"Setup-FamilySafeTube.ps1" not in raw or f"-Mode {mode}".encode() not in raw:
        problems.append(f"{bat} must start Setup-FamilySafeTube.ps1 with -Mode {mode}")
gi = read(os.path.join(ROOT, ".gitignore"))
for entry in ("*.jks", "keystore.properties", "APK/", "build-error.txt", "local.properties"):
    if entry not in gi: problems.append(f".gitignore lacks {entry}")
ga = read(os.path.join(ROOT, ".gitattributes")) if os.path.exists(os.path.join(ROOT, ".gitattributes")) else ""
for needle in ("gradlew text eol=lf", "*.sh text eol=lf", "*.bat text eol=crlf", "*.ps1 text eol=crlf"):
    if needle not in ga: problems.append(f".gitattributes lacks '{needle}'")
for doc in ("SETUP.md", "README.md"):
    if not os.path.exists(os.path.join(ROOT, doc)): problems.append(f"{doc} is missing")
for f in glob.glob(os.path.join(ROOT, "**/*"), recursive=True):
    if os.path.isfile(f) and f.endswith((".kt", ".kts", ".xml", ".md", ".yml", ".ps1", ".bat", ".toml", ".properties", ".py")) and "/.git/" not in f \
            and not f.endswith("tools/static_audit.py"):
        if re.search(r"youtubly", read(f), re.I):
            problems.append(f"leftover template name 'Youtubly' in {os.path.relpath(f, ROOT)}")
check("wizard and repository hygiene", problems)

# 11. Compose mistakes the plain Kotlin compiler cannot see without the Android SDK (found by real GitHub builds)
sys.path.insert(0, os.path.join(ROOT, "tools"))
import compose_check
check("compose: `by` delegates have getValue/setValue imports; BoxWithConstraints sizes read in their own scope", compose_check.find(ROOT))

# 12. Compose names used without an import (a real build failure class the plain Kotlin compiler cannot see without the Android SDK)
import import_check
check("compose: every layout/modifier/material name used is imported", import_check.find(ROOT))

# 13. Launcher icon, logo and resources: layers exist, no duplicate resource names, PNGs are RGBA at the right size
import struct
problems = []
RES = os.path.join(MAIN, "res")
def png_info(path):
    with open(path, "rb") as fh:
        head = fh.read(33)
    if head[:8] != b"\x89PNG\r\n\x1a\n": return None
    w, h, depth, ctype = struct.unpack(">IIBB", head[16:26]); return w, h, ctype
for xml in ("ic_launcher.xml", "ic_launcher_round.xml"):
    p = os.path.join(RES, "mipmap-anydpi", xml)
    if not os.path.exists(p): problems.append(f"mipmap-anydpi/{xml} is missing"); continue
    for ref in re.findall(r'@drawable/(\w+)', read(p)):
        if not glob.glob(os.path.join(RES, "drawable*", ref + ".*")): problems.append(f"{xml} refers to @drawable/{ref}, which does not exist")
stems = {}
for d in glob.glob(os.path.join(RES, "*")):
    if not os.path.isdir(d): continue
    for f in os.listdir(d):
        stems.setdefault((os.path.basename(d), os.path.splitext(f)[0]), []).append(f)
for (folder, stem), files in stems.items():
    if len(files) > 1: problems.append(f"res/{folder}: duplicate resource '{stem}' ({', '.join(files)})")
for layer in ("ic_launcher_foreground", "ic_launcher_background", "ic_launcher_monochrome", "logo"):
    where = sorted({os.path.basename(os.path.dirname(f)) for f in glob.glob(os.path.join(RES, "drawable*", layer + ".*"))})
    if len(where) != 1: problems.append(f"drawable '{layer}' must exist in exactly one folder (found in: {where or 'none'})")
for rel, want in (("drawable-nodpi/ic_launcher_foreground.png", (432, 432)), ("drawable-nodpi/ic_launcher_monochrome.png", (432, 432))):
    p = os.path.join(RES, rel); info = png_info(p) if os.path.exists(p) else None
    if info is None: problems.append(f"res/{rel} is missing or not a PNG")
    elif info[:2] != want or info[2] != 6: problems.append(f"res/{rel} must be {want[0]}x{want[1]} RGBA (is {info[0]}x{info[1]}, colour type {info[2]})")
p = os.path.join(RES, "drawable-nodpi/logo.png"); info = png_info(p) if os.path.exists(p) else None
if info is None or info[2] != 6: problems.append("res/drawable-nodpi/logo.png must be an RGBA PNG (transparent background)")
for dens, n in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    for name in ("ic_launcher.png", "ic_launcher_round.png"):
        p = os.path.join(RES, f"mipmap-{dens}", name); info = png_info(p) if os.path.exists(p) else None
        if info is None or info[:2] != (n, n): problems.append(f"res/mipmap-{dens}/{name} must be {n}x{n}")
if "AppLogo(" not in read(os.path.join(MAIN, "java/com/example/familysafetube/ui/HomeScreen.kt")):
    problems.append("the home screen header must show the logo (AppLogo)")
check("logo and launcher icon: layers, sizes, no duplicate resources", problems)

# 14. The blurred backdrop of the image viewer must show the WHOLE picture. A CENTER_CROP backdrop that is also loaded with a square
#     override() is cropped twice and shows only a magnified strip from the middle (a regression found on a real phone).
adapter = read(os.path.join(MAIN, "java/com/example/familysafetube/ImageAdapter.kt"))
blk = re.search(r"val ivBg = ImageView\(ctx\)\.apply \{(.*?)\n        \}", adapter, re.S)
problems = []
if not blk:
    problems.append("cannot find the ivBg (blurred backdrop) definition in ImageAdapter.kt")
else:
    if "ScaleType.FIT_XY" not in blk.group(1):
        problems.append("the blurred backdrop (ivBg) must use ScaleType.FIT_XY so it shows the whole picture, not a cropped middle strip")
    if "ScaleType.CENTER_CROP" in blk.group(1) and ".override(BG_DECODE_PX" in adapter:
        problems.append("ivBg is CENTER_CROP and loaded with override(): the picture is cropped twice")
check("image viewer: blurred backdrop shows the whole picture", problems)

# 15. The full-app lock, exact timing, stacked quiz pictures, the celebration and the quiz-round limit
J = os.path.join(MAIN, "java/com/example/familysafetube")
def src(rel): return read(os.path.join(J, rel))
problems = []
overlay = src("ui/LockOverlay.kt")
if re.search(r"\bText\(", overlay) or "R.drawable.ic_lock" not in overlay:
    problems.append("the lock screen must show only the padlock (no Text, uses R.drawable.ic_lock)")
approot = src("ui/AppRoot.kt")
m = re.search(r"if \(state\.lock != LockKind\.NONE\) \{(.*?)\} else \{(.*?)\n    \}\n", approot, re.S)
if not m or "LockOverlay(" not in m.group(1) or "HomeScreen(" not in m.group(2) or "HomeScreen(" in m.group(1):
    problems.append("AppRoot must show ONLY the LockOverlay while locked and compose the HomeScreen only otherwise")
for act in ("ImagePlayerActivity", "OfflinePlayerActivity", "YoutubePlayerActivity", "QuizActivity", "MatchActivity"):
    t = src(act + ".kt")
    if t.count("Policy.nextCheckDelayMs(") < 2:
        problems.append(f"{act}: both the ticker and onResume must schedule with Policy.nextCheckDelayMs (exact lock, no fixed 10 s wait)")
    if re.search(r"postDelayed\((this|ticker|usageTicker),\s*(checkInterval\w*|10_000L?)\)", t):
        problems.append(f"{act}: still re-checks on a fixed interval instead of exactly when time runs out")
    if "Policy.lockKind(" not in t:
        problems.append(f"{act}: must leave silently (no toast) when the full lock takes over")
for f, needle in (("ui/HomeScreen.kt", "TimeRules.minutesLabel("), ("ImagePlayerActivity.kt", "TimeRules.minutesLabel("),
                  ("OfflinePlayerActivity.kt", "TimeRules.minutesLabel("), ("YoutubePlayerActivity.kt", "TimeRules.minutesLabel(")):
    if needle not in src(f): problems.append(f"{f}: the time-left label must round UP (TimeRules.minutesLabel), it showed 0m with time still running")
if "leftMins.toInt()" in src("YoutubePlayerActivity.kt"): problems.append("YoutubePlayerActivity: time-left label rounds down again")
lv = dict(re.findall(r'(BABY|TODDLER|OLDER)\("\w+", (\d),', src("QuizLevel.kt")))
if lv != {"BABY": "2", "TODDLER": "2", "OLDER": "3"}:
    problems.append(f"quiz levels must be Baby=2, Toddler=2, Older=3 pictures (found {lv})")
if "only = ImageArrangement.COLUMN" not in src("ui/QuizScreen.kt"):
    problems.append("the quiz must stack its pictures (QuizImageLayout.plan(..., only = ImageArrangement.COLUMN))")
rc = src("ui/QuizScreen.kt"); cel = src("ui/CelebrationLayer.kt")
if "CelebrationLayer(" not in rc or "DancingLogo(" not in rc:
    problems.append("the end-of-round screen must show the CelebrationLayer and the DancingLogo")
for needle, what in (("activeBursts(", "fireworks"), ("starPoints(", "falling stars"), ("drawRect(", "confetti")):
    if needle not in cel: problems.append(f"CelebrationLayer lacks {what}")
if "fun lockNow(" not in approot or "fun unlock(" not in approot or "btn_lock_now" not in src("ui/HomeScreen.kt"):
    problems.append("the parent 'Lock now' button / HomeActions.lockNow / unlock are missing")
main_act = src("MainActivity.kt")
if "override fun lockNow(" not in main_act or "override fun unlock(" not in main_act:
    problems.append("MainActivity must implement lockNow() and unlock()")
if "quizRoundsReached()" not in main_act or "recordQuizRound()" not in rc or "Keys.QUIZ_ROUNDS_LIMIT" not in src("ui/ParentFlows.kt"):
    problems.append("the daily quiz-round limit is not wired (MainActivity.openQuiz, QuizScreen.finishRound, Settings)")
for f in ["MainActivity.kt"] + [os.path.join("ui", n) for n in os.listdir(os.path.join(J, "ui")) if n.endswith(".kt") and n not in ("QuizScreen.kt", "MatchScreen.kt")]:
    if re.search(r"KEEP_SCREEN_ON|keepScreenOn", src(f)):
        problems.append(f"{f}: must not keep the screen on (the screen has to turn off by itself while locked)")
vm = src("MainViewModel.kt")
if "MutableStateFlow(MainUiState(lock = Policy.lockKind(app)))" not in vm or "copy(lock = Policy.lockKind(" not in vm:
    problems.append("MainViewModel must know the lock instantly (a locked app must never flash its home screen)")
check("lock screen, exact timing, stacked quiz, celebration, round limit", problems)

# 16. The wizard must never merge (a merge left conflict markers inside files and pushed them, breaking the build),
#     and no file in the project may contain merge-conflict markers
problems = []
ps_code = "\n".join(l for l in ps1.splitlines() if not l.strip().startswith("#"))
for bad in ("pull --rebase", "--autostash", "git merge ", "git stash", "git pull"):
    if bad in ps_code:
        problems.append(f"the wizard must never merge or stash ('{bad}'): it can leave conflict markers in files and push them")
for need in ("reset --soft origin/main", "function Assert-NoConflictMarkers"):
    if need not in ps_code: problems.append(f"the wizard lacks '{need}'")
if ps_code.count("Assert-NoConflictMarkers") < 3:
    problems.append("Assert-NoConflictMarkers must run in Commit-All and in Do-Rebuild (definition + 2 calls)")
conflict_re = re.compile(r"^(<{7}|>{7}) ", re.M)
TEXT = (".kt", ".kts", ".xml", ".md", ".yml", ".yaml", ".ps1", ".bat", ".toml", ".properties", ".py", ".pro", ".txt", ".gitignore", ".gitattributes")
for f in glob.glob(os.path.join(ROOT, "**/*"), recursive=True) + glob.glob(os.path.join(ROOT, ".*")) + glob.glob(os.path.join(ROOT, ".github/**/*"), recursive=True):
    if os.path.isfile(f) and f.endswith(TEXT) and "/.git/" not in f:
        try:
            if conflict_re.search(open(f, encoding="utf-8", errors="ignore").read()):
                problems.append(f"merge-conflict markers in {os.path.relpath(f, ROOT)}")
        except OSError:
            pass
check("no merge, no conflict markers", problems)

# 18. The celebration voice: configurable in Settings, used by the quiz and the matching game, every setting saved and applied
problems = []
for scr in ("ui/QuizScreen.kt", "ui/MatchScreen.kt"):
    t = src(scr)
    for need in ("CelebrationVoice(CelebrationStore.load()", "celebrationVoice.steps(CelebrationEvent.ANSWER)", "celebrationVoice.steps(CelebrationEvent.ROUND)"):
        if need not in t: problems.append(f"{scr}: lacks '{need}'")
    if "audio.cheer()" in t: problems.append(f"{scr}: must not play the fixed cheer; the celebration is configurable (Settings > Celebration)")
    if "quiz_well_done" in t: problems.append(f"{scr}: the hard-coded 'Well done' is gone; phrases come from CelebrationPhrases")
qa = src("QuizAudio.kt")
for need in ("is AudioStep.Effect -> playEffect(step, gen, next)", "handler.postDelayed({ if (gen == generation) done() }, ms.toLong())", "setPitch(step.pitch)", "setSpeechRate(step.rate)"):
    if need not in qa: problems.append(f"QuizAudio.kt lacks '{need}' (sound effects must be waited for; the voice style must be applied)")
pf = src("ui/ParentFlows.kt")
for need in ("CelebrationSection(", "CelebrationStore.write(p, celebration)", "CelebrationPlan.build("):
    if need not in pf: problems.append(f"ParentFlows.kt (Settings) lacks '{need}'")
store = src("CelebrationStore.kt")
for key in re.findall(r"val (CELEB_\w+)\s*=", src("PrefsStore.kt")):
    if store.count("Keys." + key) < 2: problems.append(f"Keys.{key} must be both read and written by CelebrationStore")
sec = src("ui/CelebrationSection.kt")
for control in ("config.copy(onAnswer", "config.copy(onRound", "config.copy(effect", "config.copy(voice", "config.copy(useName", "config.copy(name",
                "CelebrationSettings.withPhrase(", "config.copy(style", "config.withClip(", "onTest(CelebrationEvent.ANSWER)", "onTest(CelebrationEvent.ROUND)"):
    if control not in sec: problems.append(f"Settings > Celebration has no control for '{control}'")
check("celebration voice: configurable, used by both games, saved and applied", problems)

# 17. setup/files.txt (the list of files in this project, used by the wizard to delete OLD leftovers) must match the real files exactly
problems = []
SKIP_DIRS = {".git", "__pycache__", "build", ".gradle", "APK", ".idea"}
SKIP_FILES = {"local.properties", "keystore.properties", "build-error.txt"}
actual = set()
for d, dirs, fs in os.walk(ROOT):
    dirs[:] = [x for x in dirs if x not in SKIP_DIRS]
    for f in fs:
        if f in SKIP_FILES or f.endswith((".jks", ".keystore", ".pyc", ".apk")): continue
        actual.add(os.path.relpath(os.path.join(d, f), ROOT).replace(os.sep, "/"))
list_path = os.path.join(ROOT, "setup/files.txt")
if not os.path.exists(list_path):
    problems.append("setup/files.txt is missing (the wizard needs it to remove old leftover files)")
else:
    listed = {l.strip() for l in open(list_path, encoding="utf-8").read().splitlines() if l.strip()}
    if len(listed) < 50: problems.append(f"setup/files.txt has only {len(listed)} entries; it looks damaged")
    if "setup/files.txt" not in listed: problems.append("setup/files.txt must list itself (otherwise the wizard would delete it)")
    missing = sorted(actual - listed); extra = sorted(listed - actual)
    if missing: problems.append(f"{len(missing)} file(s) are not in setup/files.txt, e.g. {missing[:3]}")
    if extra: problems.append(f"setup/files.txt lists {len(extra)} file(s) that do not exist, e.g. {extra[:3]}")
check("setup/files.txt matches the files in the project", problems)

# 18. Parent check with the phone lock, easy maths, timed lock
problems = []
flows = src("ui/ParentFlows.kt")
if "PhoneLock.prompt(" not in flows or "GateRules.start(" not in flows:
    problems.append("ParentGate must start with the phone-lock prompt (PhoneLock.prompt / GateRules.start)")
if "GateRules.gateOpenForAttempts(" not in flows:
    problems.append("a PIN / maths lockout must not shut the parent out of the phone lock (GateRules.gateOpenForAttempts)")
for key in ("Keys.PHONE_LOCK]", "Keys.PHONE_LOCK_BIO_ONLY]"):
    if flows.count(key) < 1: problems.append(f"Settings does not save {key}")
pl = src("PhoneLock.kt")
if "setAllowedAuthenticators(" not in pl or "setNegativeButtonText(" not in pl:
    problems.append("PhoneLock.prompt: with biometric-only the prompt needs a negative button; without it, none (the phone throws otherwise)")
if "DEVICE_CREDENTIAL" not in pl or "BIOMETRIC_WEAK" not in pl:
    problems.append("PhoneLock must accept face/fingerprint (BIOMETRIC_WEAK) and the phone screen lock (DEVICE_CREDENTIAL)")
if "androidx.biometric" not in read(os.path.join(ROOT, "gradle/libs.versions.toml")) or "libs.androidx.biometric" not in read(os.path.join(ROOT, "app/build.gradle.kts")):
    problems.append("the androidx.biometric dependency is missing")
if "USE_BIOMETRIC" not in manifest:
    problems.append("AndroidManifest lacks the USE_BIOMETRIC permission")
gr = src("GateRules.kt")
if "random.nextInt(6, 13)" in gr or "×" in gr:
    problems.append("the maths question must stay easy (no multiplication)")
if "LockChoiceDialog(" not in approot or "Policy.lockNow(minutes)" not in main_act or "Policy.timedLockUntilMs()" not in main_act:
    problems.append("'Lock now' must offer a duration and the home timer must look again when a timed lock ends")
check("phone lock, easy maths, timed lock", problems)

# 19. Arabic celebration phrases carry their vowel marks, switchable, each phrase can be heard, a missing voice is explained
problems = []
cv = src("CelebrationVoice.kt")
if "AR_VOWELED" not in cv or "config.diacritics" not in cv:
    problems.append("CelebrationPhrases must speak Arabic with vowel marks (AR_VOWELED) according to config.diacritics")
if "diacritics = st.getOr(Keys.CELEB_DIACRITICS" not in src("CelebrationStore.kt"):
    problems.append("CelebrationStore must load the tashkeel setting")
sec = src("ui/CelebrationSection.kt")
for need in ("config.copy(diacritics", "config.copy(gender", "onHear(id)", "onOpenVoiceSettings", "ttsAvailable"):
    if need not in sec: problems.append(f"Settings > Celebration lacks '{need}'")
for need in ("onHear = {", "celebration.diacritics", "isTtsAvailable(quizLang)"):
    if need not in src("ui/ParentFlows.kt"): problems.append(f"ParentFlows.kt (Settings) lacks '{need}'")
check("celebration: Arabic with tashkeel, hear each phrase, voice check", problems)

print()
if failures:
    print(f"{len(failures)} problem(s) found.")
    sys.exit(1)
print("All static checks passed. (This is not a substitute for a real Gradle build.)")
