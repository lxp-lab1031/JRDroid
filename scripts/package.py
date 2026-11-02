#!/usr/bin/env python3
"""Bundle source files and the already built debug APK; exclude caches and keys."""
import hashlib
import pathlib
import re
import shutil
import subprocess
import zipfile

root = pathlib.Path(__file__).resolve().parent.parent
out = pathlib.Path('/workspace/artifacts/JRDroid')
out.mkdir(parents=True,exist_ok=True)
version = re.search(r"versionName\s+'([^']+)'",(root/'app/build.gradle').read_text()).group(1)
apk = out/('JRDroid-'+version+'-debug.apk')
shutil.copy2(root/'app/build/outputs/apk/debug/app-debug.apk',apk)
artifacts = [apk]
standalone = root/'app/build/outputs/apk/standalone/app-standalone.apk'
if standalone.is_file():
    separate = out/('JRDroid-'+version+'-standalone.apk')
    shutil.copy2(standalone,separate)
    artifacts.append(separate)
files = subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=root).split(b'\0')
source = out/('JRDroid-'+version+'-source.zip')
source_sums = []
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as z:
    for name in sorted(set(files)):
        if name:
            p = root/name.decode()
            relative = p.relative_to(root).as_posix()
            if p.is_file() and relative != 'SOURCE_SHA256SUMS.txt' and not relative.startswith('downloads/'):
                z.write(p,'JRDroid/'+relative)
                source_sums.append(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+relative+'\n')
    z.writestr('JRDroid/SOURCE_SHA256SUMS.txt',''.join(source_sums))
artifacts.append(source)
sums = ''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in artifacts)
(out/'SHA256SUMS.txt').write_text(sums)
(out/('SHA256SUMS-'+version+'.txt')).write_text(sums)
print('Artifacts:',out)
