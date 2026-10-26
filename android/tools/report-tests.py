#!/usr/bin/env python3
"""Make completed Gradle report counts visible in the public Actions summary."""
import os
import html as html_module
from pathlib import Path
import re
import sys

report=Path(sys.argv[1])
expected=int(sys.argv[2])
label=sys.argv[3]
if not report.is_file():
    sys.exit('Required current-run Gradle test report is missing: '+str(report))
html=report.read_text(encoding='utf-8')
counts={name:int(value) for name,value in re.findall(r'id="(tests|failures|errors|skipped|ignored)".*?<div class="counter">([0-9]+)</div>',html,re.S)}
if not {'tests','failures'}.issubset(counts) or not {'skipped','ignored'}.intersection(counts):
    sys.exit('Incomplete Gradle test counts; cannot claim readiness')
message=f"{label}: {counts['tests']} tests, {counts['failures']} failures, {counts.get('errors',0)} errors, {counts.get('skipped',counts.get('ignored',0))} skipped"
print(message)
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'],'a',encoding='utf-8') as output:output.write(message+'\n\n')
passed=counts['tests']==expected and not any(counts.get(k,0) for k in ['failures','errors','skipped','ignored'])
if os.environ.get('GITHUB_ACTIONS')=='true':
    print(('::notice' if passed else '::error')+' title=Executed test counts::'+message)
if not passed:
    # These are the project's synthetic-data native tests; publish failure details,
    # rather than hiding a regression behind an inaccessible generic process error.
    for child in sorted(report.parent.glob('*.html')):
        if child == report: continue
        for block in re.findall(r'<pre>(.*?)</pre>',child.read_text(encoding='utf-8'),re.S):
            detail=child.stem+': '+html_module.unescape(re.sub(r'<[^>]+>','',block)).strip()[:2500]
            print(detail)
            if os.environ.get('GITHUB_ACTIONS')=='true':
                escaped=detail.replace('%','%25').replace('\r','%0D').replace('\n','%0A')
                print('::error title=Native test failure::'+escaped)
    sys.exit('Expected exactly '+str(expected)+' completed tests without failures or skips')
