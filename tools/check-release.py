"""Audit the public file list and Git history without printing secret values. Python 3 stdlib only."""
import argparse, hashlib, pathlib, re, subprocess, sys, zipfile
root=pathlib.Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk',type=pathlib.Path,help='Inspect the final clean-built APK too')
args=parser.parse_args()
def git(*args):return subprocess.check_output(['git',*args],cwd=root)
patterns=[
    ('private key',rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    ('GitHub credential',rb'(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})'),
    ('AWS access key',rb'AKIA[A-Z0-9]{16}'),
    ('embedded signing password',rb'pass:[A-Za-z0-9!@#$%^&*()_+=-]{5,}'),
    ('long embedded JWT',rb'eyJ[A-Za-z0-9_-]{30,}\.[A-Za-z0-9_-]{100,}\.[A-Za-z0-9_-]{30,}'),
]
forbidden=re.compile(r'(?i)(?:\.(?:jks|keystore|p12|pfx|pem|key|apk|idsig)$|(?:^|/)(?:backups|build|\.gradle|\.env|integrations|private-before-review-local)(?:/|$)|(?:AppleMusicHistory|MusicHistoryReceiver)\.java$|Restore-AppleMusic-Continue\.ps1$|-local\.[^/]+$)')
excluded=[b'AppleMusicHistory',b'MusicHistoryReceiver',b'com.applemusicktv.quiettv',b'api.bilibili.com',b'ipwho.is']
errors=[];files=git('ls-files','--cached','--others','--exclude-standard','-z').decode().split('\0');files=sorted(set(filter(None,files)))
for name in files:
    if forbidden.search(name):errors.append((name,'private/generated path'))
    path=root/name
    if not path.is_file():continue
    data=path.read_bytes()
    if name.startswith('src/'):
        for marker in excluded:
            if marker in data:errors.append((name,'excluded public-release interface'))
        if path.name not in ('JsonHttp.java',) and b'openConnection(' in data:
            errors.append((name,'network operation outside reviewed weather client'))
    if b'\x00' in data:continue
    for label,pattern in patterns:
        if re.search(pattern,data):errors.append((name,label))
    if path.suffix=='.md':
        text=data.decode('utf-8-sig')
        for match in re.finditer(r'\]\(([^\s)]+)\)',text):
            dest=match.group(1).split('#')[0]
            if not dest or re.match(r'^[a-zA-Z][a-zA-Z0-9+.-]*:',dest):continue
            if not (path.parent/dest).exists():errors.append((name,'missing documentation target: '+dest))
objects=git('rev-list','--objects','--all').decode().splitlines();seen=set()
for line in objects:
    parts=line.split(' ',1)
    if len(parts)!=2:continue
    oid,name=parts
    if oid in seen:continue
    seen.add(oid)
    if git('cat-file','-t',oid).strip()!=b'blob':continue
    if forbidden.search(name):errors.append((name,'private/generated path in Git history'))
    data=git('cat-file','blob',oid)
    if name.startswith('src/'):
        for marker in excluded:
            if marker in data:errors.append((name,'excluded interface in Git history'))
    if b'\x00' in data:continue
    for label,pattern in patterns:
        if re.search(pattern,data):errors.append((name,label+' in Git history'))
if args.apk:
    apk=args.apk.resolve()
    with zipfile.ZipFile(apk) as package:
        for item in package.infolist():
            name=item.filename
            if not (name in ('AndroidManifest.xml','resources.arsc','classes.dex') or name.startswith(('res/','META-INF/'))):
                errors.append(('APK/'+name,'unexpected package entry'))
            if name.endswith(('.apk','.jks','.keystore','.mp4','.webm','.json')):
                errors.append(('APK/'+name,'private/third-party package payload'))
            data=package.read(item)
            for label,pattern in patterns:
                if re.search(pattern,data):errors.append(('APK/'+name,label))
            if name.endswith('.dex'):
                for marker in excluded:
                    if marker in data:errors.append(('APK/'+name,'excluded release interface'))
        image=package.read('res/raw/nature_lake.png')
        notices=package.read('res/raw/legal_notices.txt').decode('utf-8-sig')
        if (root/'LICENSE').read_text(encoding='utf-8-sig').strip() not in notices:
            errors.append(('APK/legal_notices','MIT copyright/license missing'))
        if hashlib.sha256(image).hexdigest()!='a180d8e7a92a85a109ebff8262edd3a8697f78de3139f7fd08588927db421acb':
            errors.append(('APK/wallpaper','unreviewed bundled background'))
    print('APK examined: '+apk.name+'; SHA-256 '+hashlib.sha256(apk.read_bytes()).hexdigest())
if errors:
    for name,label in sorted(set(errors)):print(name+': '+label)
    sys.exit(1)
print('Public audit passed: '+str(len(files))+' files, '+str(len(seen))+' history objects. No secret values printed.')
