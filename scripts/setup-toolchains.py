#!/usr/bin/env python3
"""Install pinned official archives into /workspace, preserving TLS and checksums."""
import concurrent.futures
import hashlib
import os
import pathlib
import shutil
import subprocess
import urllib.parse
import xml.etree.ElementTree as ET

base = pathlib.Path('/workspace/toolchains')
downloads = base/'downloads'
downloads.mkdir(parents=True, exist_ok=True)
sdk = base/'android'
sdk.mkdir(exist_ok=True)

def download(url, name, algorithm=None, expected=None):
    dest = downloads/name
    def valid():
        if not dest.is_file(): return False
        if not expected: return True
        h = hashlib.new(algorithm)
        with dest.open('rb') as f:
            for block in iter(lambda: f.read(1024*1024), b''): h.update(block)
        return h.hexdigest() == expected
    if not valid():
        subprocess.run(['curl','-fLsS','--retry','2',url,'-o',str(dest)], check=True)
    if not valid(): raise RuntimeError('Checksum mismatch: '+name)
    return dest

jdk = base/'jdk/usr/lib/jvm/java-21-openjdk-amd64'
if not (jdk/'bin/javac').is_file():
    deb = download('https://deb.debian.org/debian/pool/main/o/openjdk-21/openjdk-21-jdk-headless_21.0.12.1+1-1~deb13u1_amd64.deb',
                   'jdk.deb','sha256','f3abafb6c644b03df042824e707cd211ea33254761d7f7b75be3f8dc0df97c7a')
    subprocess.run(['dpkg-deb','-x',str(deb),str(base/'jdk')],check=True)
    runtime = pathlib.Path('/usr/lib/jvm/java-21-openjdk-amd64')
    if not (runtime/'bin/java').exists(): raise RuntimeError('This cloud bootstrap requires the Debian OpenJDK 21 runtime from the base image')
    shutil.copytree(runtime,jdk,dirs_exist_ok=True,symlinks=False)

# SHA-1 checksums published in Google's HTTPS repository2-1.xml, pinned here.
packages = [
    ('platform-36_r02.zip','2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576','platforms/android-36','android.jar'),
    ('build-tools_r36_linux.zip','b0b6376977657e8ad9b969bacf4093601da2c6fb','build-tools/36.0.0','aapt2'),
    ('android-ndk-r28c-linux.zip','a7b54a5de87fecd125a17d54f73c446199e72a64','ndk/28.2.13676358','source.properties'),
    ('platform-tools_r37.0.1-linux.zip','477254aa5f903c15cf51001717bdf347fb6b53e0','platform-tools','adb'),
]
def install_sdk(item):
    name, checksum, relative, marker = item
    target = sdk/relative
    if (target/marker).is_file(): return
    archive = download('https://dl.google.com/android/repository/'+name,name,'sha1',checksum)
    temporary = downloads/('unpack-'+name)
    if temporary.exists(): shutil.rmtree(temporary)
    temporary.mkdir()
    subprocess.run(['unzip','-q',str(archive),'-d',str(temporary)],check=True)
    entries = list(temporary.iterdir())
    if len(entries)!=1: raise RuntimeError('Unexpected archive structure: '+name)
    if target.exists(): raise RuntimeError('Incomplete existing SDK directory: '+str(target))
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.move(str(entries[0]),str(target)); temporary.rmdir()
    print('Installed verified SDK package:',relative,flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    list(pool.map(install_sdk,packages))

manifest = download('https://dl.google.com/android/repository/repository2-1.xml','android-repository.xml')
(sdk/'licenses').mkdir(exist_ok=True)
for license in ET.parse(manifest).getroot().findall('license'):
    target = sdk/'licenses'/license.attrib['id']
    digest = hashlib.sha1((license.text or '').strip().encode()).hexdigest()
    lines = target.read_text().splitlines() if target.exists() else []
    if digest not in lines: target.write_text('\n'.join(lines+[digest])+'\n')

gradle = base/'gradle-8.13'
if not (gradle/'bin/gradle').is_file():
    archive = download('https://services.gradle.org/distributions/gradle-8.13-bin.zip','gradle.zip','sha256',
                       '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78')
    subprocess.run(['unzip','-q',str(archive),'-d',str(base)],check=True)
(base/'android-user').mkdir(exist_ok=True)
gh = base/'gradle-home'
gh.mkdir(exist_ok=True)
proxy = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
if proxy.hostname:
    props = gh/'gradle.properties'
    lines = props.read_text().splitlines() if props.exists() else []
    keys = ['systemProp.'+protocol+'.proxy'+field for protocol in ['http','https'] for field in ['Host','Port']]
    lines = [line for line in lines if not any(line.startswith(k+'=') for k in keys)]
    for protocol in ['http','https']:
        lines.extend(['systemProp.'+protocol+'.proxyHost='+proxy.hostname,
                      'systemProp.'+protocol+'.proxyPort='+str(proxy.port or 80)])
    props.write_text('\n'.join(lines)+'\n')
(base/'activate.sh').write_text('''export JAVA_HOME=/workspace/toolchains/jdk/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/workspace/toolchains/android
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
export ANDROID_USER_HOME=/workspace/toolchains/android-user
export RUSTUP_HOME=/workspace/toolchains/rustup
export CARGO_HOME=/workspace/toolchains/cargo
export GRADLE_USER_HOME=/workspace/toolchains/gradle-home
export PATH="$JAVA_HOME/bin:$CARGO_HOME/bin:/workspace/toolchains/gradle-8.13/bin:$ANDROID_HOME/platform-tools:$PATH"
''')
print('Toolchains ready. Source /workspace/toolchains/activate.sh')
