#!/usr/bin/env python3
import xml.etree.ElementTree as ET
import re
import os

SRC_PATH = "app/shared/app-lang/src/androidMain/res/values/strings.xml"
DEST_PATH = "app/shared/app-lang/src/androidMain/res/values-es/strings.xml"

import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from translate_all import MAP, PATTERNS
from generate_es_strings import REPLACEMENTS

ES_DICT = {}

def clean_escapes(s):
    # Ensure unescaped single quotes are escaped for Android XML
    # But do not double escape \'
    parts = s.split("\\'")
    parts = [p.replace("'", "\\'") for p in parts]
    return "\\'".join(parts)

def translate(key, text):
    if not text:
        return text
    if key in ES_DICT:
        return clean_escapes(ES_DICT[key])
    if key in MAP:
        return clean_escapes(MAP[key])
    
    res = text
    # Apply replacements
    for p, r in REPLACEMENTS:
        res = re.sub(p, r, res)
    for p, r in PATTERNS:
        res = re.sub(p, r, res)
        
    return clean_escapes(res)

def main():
    with open(SRC_PATH, "r", encoding="utf-8") as f:
        lines = f.readlines()
        
    out = []
    str_pat = re.compile(r'^(?P<ind>\s*)<string\s+name="(?P<name>[^"]+)"(?P<extra>[^>]*)>(?P<val>.*)</string>(?P<trail>\s*)$')
    
    count = 0
    for l in lines:
        m = str_pat.match(l)
        if m:
            ind = m.group("ind")
            name = m.group("name")
            extra = m.group("extra")
            val = m.group("val")
            trail = m.group("trail")
            
            if 'translatable="false"' in extra:
                out.append(l)
                continue
                
            tr = translate(name, val)
            if tr != val:
                count += 1
            out.append(f'{ind}<string name="{name}"{extra}>{tr}</string>{trail}\n')
        else:
            out.append(l)
            
    with open(DEST_PATH, "w", encoding="utf-8") as f:
        f.writelines(out)
        
    print(f"Successfully generated {DEST_PATH}: {count} strings translated out of {len(lines)}")

if __name__ == "__main__":
    main()
