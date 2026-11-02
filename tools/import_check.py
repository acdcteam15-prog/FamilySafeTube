#!/usr/bin/env python3
"""Flags Compose names that a file USES but does not import (the plain Kotlin compiler cannot resolve them without the Android SDK)."""
import re, sys, glob, os
MOD = {  # modifier extension -> import
 'size':'androidx.compose.foundation.layout.size','width':'androidx.compose.foundation.layout.width','height':'androidx.compose.foundation.layout.height',
 'padding':'androidx.compose.foundation.layout.padding','fillMaxWidth':'androidx.compose.foundation.layout.fillMaxWidth','fillMaxSize':'androidx.compose.foundation.layout.fillMaxSize',
 'fillMaxHeight':'androidx.compose.foundation.layout.fillMaxHeight','widthIn':'androidx.compose.foundation.layout.widthIn','heightIn':'androidx.compose.foundation.layout.heightIn',
 'aspectRatio':'androidx.compose.foundation.layout.aspectRatio','safeDrawingPadding':'androidx.compose.foundation.layout.safeDrawingPadding',
 'background':'androidx.compose.foundation.background','border':'androidx.compose.foundation.border','clickable':'androidx.compose.foundation.clickable',
 'verticalScroll':'androidx.compose.foundation.verticalScroll','horizontalScroll':'androidx.compose.foundation.horizontalScroll',
 'clip':'androidx.compose.ui.draw.clip','alpha':'androidx.compose.ui.draw.alpha','graphicsLayer':'androidx.compose.ui.graphics.graphicsLayer',
 'semantics':'androidx.compose.ui.semantics.semantics','clearAndSetSemantics':'androidx.compose.ui.semantics.clearAndSetSemantics',
}
COMP = {  # composable / type used as Name( or Name. -> import
 'Row':'androidx.compose.foundation.layout.Row','Column':'androidx.compose.foundation.layout.Column','Box':'androidx.compose.foundation.layout.Box',
 'BoxWithConstraints':'androidx.compose.foundation.layout.BoxWithConstraints','Spacer':'androidx.compose.foundation.layout.Spacer','Arrangement':'androidx.compose.foundation.layout.Arrangement',
 'Image':'androidx.compose.foundation.Image','Text':'androidx.compose.material3.Text','Button':'androidx.compose.material3.Button','OutlinedButton':'androidx.compose.material3.OutlinedButton',
 'TextButton':'androidx.compose.material3.TextButton','Surface':'androidx.compose.material3.Surface','MaterialTheme':'androidx.compose.material3.MaterialTheme',
 'AlertDialog':'androidx.compose.material3.AlertDialog','Switch':'androidx.compose.material3.Switch','Alignment':'androidx.compose.ui.Alignment','Modifier':'androidx.compose.ui.Modifier',
 'FontWeight':'androidx.compose.ui.text.font.FontWeight','stringResource':'androidx.compose.ui.res.stringResource','painterResource':'androidx.compose.ui.res.painterResource',
 'LaunchedEffect':'androidx.compose.runtime.LaunchedEffect','remember':'androidx.compose.runtime.remember','mutableStateOf':'androidx.compose.runtime.mutableStateOf',
 'rememberScrollState':'androidx.compose.foundation.rememberScrollState','Role':'androidx.compose.ui.semantics.Role','TextAlign':'androidx.compose.ui.text.style.TextAlign',
}
UNITS = {'dp':'androidx.compose.ui.unit.dp','sp':'androidx.compose.ui.unit.sp'}
def strip(src):
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'//[^\n]*', '', src)
    src = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)
    return src
def find(root):
    out = []
    for f in sorted(glob.glob(root + '/app/src/main/**/*.kt', recursive=True)):
        raw = open(f, encoding='utf-8').read(); code = strip(raw); rel = os.path.relpath(f, root)
        imports = set(re.findall(r'^import\s+([\w.]+)(?:\s+as\s+\w+)?\s*$', raw, re.M)); wild = {i[:-2] for i in re.findall(r'^import\s+([\w.]+\.\*)\s*$', raw, re.M)}
        defined = set(re.findall(r'\bfun\s+(?:[\w.<>]+\.)?(\w+)\s*\(', raw)) | set(re.findall(r'\b(?:class|object|interface)\s+(\w+)', raw))
        simple = {i.rsplit('.', 1)[-1] for i in imports}
        def has(imp): return imp in imports or imp.rsplit('.', 1)[0] in wild or imp.rsplit('.', 1)[-1] in simple
        if 'androidx.compose' not in raw and 'import androidx.compose' not in raw: continue
        for name, imp in MOD.items():
            if re.search(r'\.\s*' + name + r'\s*\(', code) and name not in defined and not has(imp):
                # the same name can be a member (e.g. Row's Modifier.weight); only extension functions listed above need imports
                out.append(f'{rel}: uses `.{name}(` but does not import {imp}')
        for name, imp in COMP.items():
            if re.search(r'(?<![\w.])' + name + r'\s*[(.{]', code) and name not in defined and not has(imp):
                out.append(f'{rel}: uses `{name}` but does not import {imp}')
        for name, imp in UNITS.items():
            if re.search(r'\d\.' + name + r'\b|\)\.' + name + r'\b|\w\.' + name + r'\b', code) and not has(imp):
                # only when used as a unit literal such as 16.dp / 12.sp / x.dp
                if re.search(r'(\d|\w|\))\.' + name + r'\b', code): out.append(f'{rel}: uses `.{name}` but does not import {imp}')
    return sorted(set(out))
if __name__ == '__main__':
    p = find(sys.argv[1]); print(f'{len(p)} missing-import problem(s)')
    for x in p: print('  ' + x)
    sys.exit(1 if p else 0)
