#!/usr/bin/env python3
"""Bundle source files and the already built debug APK; exclude caches and keys."""
import hashlib
import pathlib
import shutil
import subprocess
import zipfile

root = pathlib.Path(__file__).resolve().parent.parent
out = pathlib.Path('/workspace/artifacts/JRDroid')
out.mkdir(parents=True,exist_ok=True)
apk = out/'JRDroid-0.1.0-alpha-debug.apk'
shutil.copy2(root/'app/build/outputs/apk/debug/app-debug.apk',apk)
artifacts = [apk]
standalone = root/'app/build/outputs/apk/standalone/app-standalone.apk'
if standalone.is_file():
    separate = out/'JRDroid-0.1.0-alpha-standalone.apk'
    shutil.copy2(standalone,separate)
    artifacts.append(separate)
files = subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=root).split(b'\0')
source = out/'JRDroid-0.1.0-alpha-source.zip'
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as z:
    for name in sorted(set(files)):
        if name:
            p = root/name.decode()
            if p.is_file(): z.write(p,'JRDroid/'+p.relative_to(root).as_posix())
artifacts.append(source)
(out/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in artifacts))
print('Artifacts:',out)
