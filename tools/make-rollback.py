"""Preserve an old APK's code/data compatibility while making its version upgradeable.

Only the binary manifest versionCode is changed; alignment and signing follow separately.
"""
import argparse
import struct
import zipfile


def patch_version(manifest, version):
    data = bytearray(manifest)
    strings = []
    offset = struct.unpack_from('<H', data, 2)[0]
    changed = 0
    while offset < len(data):
        kind, header, size = struct.unpack_from('<HHI', data, offset)
        if size < header or offset + size > len(data):
            raise ValueError('Invalid binary XML chunk')
        if kind == 1:
            count, _, flags, start = struct.unpack_from('<IIII', data, offset + 8)
            for i in range(count):
                pos = offset + start + struct.unpack_from('<I', data, offset + header + i * 4)[0]
                if flags & 0x100:
                    for _ in range(2):
                        length = data[pos]
                        pos += 1
                        if length & 0x80:
                            length = ((length & 0x7f) << 8) | data[pos]
                            pos += 1
                    strings.append(bytes(data[pos:pos+length]).decode('utf-8'))
                else:
                    length = struct.unpack_from('<H', data, pos)[0]
                    pos += 2
                    if length & 0x8000:
                        length = ((length & 0x7fff) << 16) | struct.unpack_from('<H', data, pos)[0]
                        pos += 2
                    strings.append(bytes(data[pos:pos+length*2]).decode('utf-16-le'))
        if kind == 0x102:
            attr_start, attr_size, attr_count = struct.unpack_from('<HHH', data, offset + 24)
            for i in range(attr_count):
                pos = offset + 16 + attr_start + i * attr_size
                name = struct.unpack_from('<I', data, pos + 4)[0]
                if strings[name] == 'versionCode':
                    if data[pos + 15] not in (0x10, 0x11):
                        raise ValueError('versionCode is not an integer')
                    previous = struct.unpack_from('<I', data, pos + 16)[0]
                    if version <= previous:
                        raise ValueError('Rollback version must be higher than original')
                    struct.pack_into('<I', data, pos + 16, version)
                    changed += 1
        offset += size
    if changed != 1:
        raise ValueError(f'Expected one versionCode, found {changed}')
    return bytes(data)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('original')
    parser.add_argument('unsigned')
    parser.add_argument('--version-code', type=int, required=True)
    args = parser.parse_args()
    with zipfile.ZipFile(args.original) as source, zipfile.ZipFile(args.unsigned, 'w') as target:
        for item in source.infolist():
            if item.filename.startswith('META-INF/') and item.filename.endswith(('.RSA', '.DSA', '.EC', '.SF', 'MANIFEST.MF')):
                continue
            content = source.read(item.filename)
            if item.filename == 'AndroidManifest.xml':
                content = patch_version(content, args.version_code)
            target.writestr(item, content)
    print('Rollback manifest patched; code and resources preserved. Signing still required.')
