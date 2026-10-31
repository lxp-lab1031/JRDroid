#!/usr/bin/env python3
"""Collect license text for normal runtime dependencies from the frozen Cargo graph."""
import json
from html.parser import HTMLParser
import pathlib
import subprocess

root = pathlib.Path(__file__).resolve().parent.parent
metadata = json.loads(subprocess.check_output([
    'cargo', 'metadata', '--manifest-path', str(root/'native/Cargo.toml'),
    '--locked', '--format-version', '1', '--filter-platform', 'aarch64-linux-android',
], text=True))
packages = {p['id']: p for p in metadata['packages']}
nodes = {n['id']: n for n in metadata['resolve']['nodes']}
start = next(p['id'] for p in packages.values() if p['name'] == 'jrdroid')
seen = set()
def visit(key):
    if key in seen: return
    seen.add(key)
    for dep in nodes[key]['deps']:
        if any(d['kind'] is None for d in dep['dep_kinds']): visit(dep['pkg'])
visit(start)
parts = ['JRDroid / third-party notices\n\n'
         'JR receiver: https://github.com/soltia48/jr-conventional-line-radio-receiver\n'
         'Source revision: '+(root/'native/vendor/UPSTREAM_COMMIT').read_text().strip()+'\n'
         'Vendored JR sources and CELP are MIT licensed. Original source notices are retained.\n\n'
         +(root/'native/vendor/LICENSE').read_text()+'\n\n'
         +(root/'native/vendor/crates/jr-voice/src/celp/LICENSE').read_text()]
class Text(HTMLParser):
    def __init__(self): super().__init__(); self.chunks = []
    def handle_data(self, data): self.chunks.append(data)
rust_notice = Text()
rust_notice.feed((root/'docs/licenses/rust-1.90.0-standard-library.html').read_text())
parts.append('\n\n===== Rust standard library 1.90.0 =====\n'+''.join(rust_notice.chunks))
for key in sorted(seen):
    p = packages[key]
    if p['source'] is None: continue
    folder = pathlib.Path(p['manifest_path']).parent
    files = sorted(f for f in folder.iterdir() if f.is_file() and f.name.upper().startswith(('LICENSE','LICENCE','COPYING','COPYRIGHT','NOTICE','UNLICENSE')))
    if not files:
        files = sorted((root/'docs/licenses').glob(p['name']+'-'+p['version']+'.*'))
    if not files: raise RuntimeError('No license text found for '+p['name'])
    parts.append('\n\n===== '+p['name']+' '+p['version']+' ('+str(p['license'])+') =====\n')
    for f in files: parts.append('\n--- '+f.name+' ---\n'+f.read_text())
out = root/'app/src/main/assets/THIRD_PARTY_NOTICES.txt'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text('\n'.join(parts))
print('Wrote notices for',len(seen)-1,'runtime dependencies:',out)
