#!/usr/bin/env python3
"""Two Compose compile-error classes the plain Kotlin compiler cannot see without the Android SDK:
 (1) `by` delegates on State objects without `import androidx.compose.runtime.getValue` (and setValue for `var`)
 (2) BoxWithConstraints members (maxWidth, maxHeight, minWidth, minHeight, constraints) used inside a nested layout scope (Column/Row/Box ...)"""
import re, sys, glob, os
def find(root):
    problems = []
    for f in sorted(glob.glob(root + '/app/src/main/**/*.kt', recursive=True)):
        src = open(f, encoding='utf-8').read()
        rel = os.path.relpath(f, root)
        wild = 'import androidx.compose.runtime.*' in src
        has_get = wild or 'import androidx.compose.runtime.getValue' in src
        has_set = wild or 'import androidx.compose.runtime.setValue' in src
        # (1) delegates
        for m in re.finditer(r'^\s*(?:private\s+|internal\s+)?(val|var)\s+\w+\s*(?::[^=\n]+)?\s+by\s+([^\n]+)', src, re.M):
            kind, rhs = m.group(1), m.group(2).strip()
            if re.match(r'(lazy|viewModels|activityViewModels|Delegates\.|kotlin\.properties|lifecycleAwareLazy|by lazy)', rhs): continue
            line = src.count('\n', 0, m.start()) + 1
            if not has_get: problems.append(f'{rel}:{line}: delegate `by {rhs[:40]}` needs import androidx.compose.runtime.getValue')
            if kind == 'var' and not has_set: problems.append(f'{rel}:{line}: `var ... by {rhs[:40]}` needs import androidx.compose.runtime.setValue')
        # (2) BoxWithConstraints scope leaks
        for m in re.finditer(r'BoxWithConstraints\s*\(', src):
            i = m.end(); depth = 1
            while i < len(src) and depth:                       # skip the (...) argument list
                depth += (src[i] == '(') - (src[i] == ')'); i += 1
            j = src.find('{', i)
            if j == -1 or src[i:j].strip() not in ('', ): continue   # trailing-lambda form expected
            # walk the lambda body keeping a brace stack with the text that opened each brace
            stack = []; k = j; n = len(src)
            while k < n:
                c = src[k]
                if c == '{':
                    head = src[max(0, k - 120):k]
                    stack.append(head); 
                elif c == '}':
                    stack.pop()
                    if not stack: break
                else:
                    mm = re.match(r'\b(maxWidth|maxHeight|minWidth|minHeight|constraints)\b', src[k:k+12])
                    if mm and not src[k-1].isalnum() and src[k-1] not in '._' and len(stack) > 1:
                        # inside a nested lambda: is any enclosing nested brace a layout-scope lambda?
                        for head in stack[1:]:
                            if re.search(r'\b(Column|Row|Box|Surface|Card|LazyColumn|LazyRow|LazyVerticalGrid|FlowRow|Scaffold|Dialog)\s*(\([^{}]*\))?\s*$', head.rstrip()) or re.search(r'\b(Column|Row|Box|LazyColumn|LazyRow|LazyVerticalGrid)\s*\([^{}]*\)\s*$', head.rstrip()):
                                line = src.count('\n', 0, k) + 1
                                problems.append(f'{rel}:{line}: `{mm.group(1)}` used inside a nested layout scope of BoxWithConstraints (capture it in a local val first)')
                                break
                k += 1
    return sorted(set(problems))
if __name__ == '__main__':
    p = find(sys.argv[1])
    print(f'{len(p)} Compose problem(s)')
    for x in p: print('  ' + x)
    sys.exit(1 if p else 0)
