#!/usr/bin/env python3
"""Sign a built release using local, git-ignored key material. Never print secrets."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-tools', required=True, type=Path)
    parser.add_argument('--output-dir', type=Path,
                        help='Fresh directory inside this project; preserves earlier candidates.')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    unsigned = root / 'app/build/outputs/apk/release/app-release-unsigned.apk'
    key = root / '.signing/suntel-mdm-update-helper-release.p12'
    credentials = root / '.signing/credentials.json'
    for path in (key, credentials):
        if path.stat().st_mode & 0o077:
            raise SystemExit('Signing files must be readable only by their owner (chmod 600).')
    values = json.loads(credentials.read_text())
    output = (root / (args.output_dir or Path('outputs'))).resolve()
    if not output.is_relative_to(root):
        raise SystemExit('Release output must stay inside this project.')
    output.mkdir(parents=True, exist_ok=True)
    aligned = output / 'Suntel-MDM-Update-Helper-1.05-suntel.1-aligned-unsigned.apk'
    signed = output / 'Suntel-MDM-Update-Helper-1.05-suntel.1.apk'
    if aligned.exists() or signed.exists():
        raise SystemExit('Release output already exists; preserve it and choose a new version.')
    env = os.environ.copy()
    env['SUNTEL_HELPER_KEY_PASSWORD'] = values['store_password']
    subprocess.run([str(args.build_tools / 'zipalign'), '-p', '4', str(unsigned), str(aligned)],
                   check=True)
    subprocess.run([str(args.build_tools / 'apksigner'), 'sign', '--ks', str(key),
                    '--ks-key-alias', values['alias'], '--ks-pass', 'env:SUNTEL_HELPER_KEY_PASSWORD',
                    '--key-pass', 'env:SUNTEL_HELPER_KEY_PASSWORD', '--v1-signing-enabled', 'true',
                    '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true',
                    '--out', str(signed), str(aligned)], check=True, env=env)
    verification = subprocess.run([str(args.build_tools / 'apksigner'), 'verify', '--verbose',
                                   '--print-certs', str(signed)], check=True,
                                  capture_output=True, text=True)
    (output / 'signature-verification.txt').write_text(verification.stdout)
    digest = hashlib.sha256(signed.read_bytes()).hexdigest()
    (output / (signed.name + '.sha256')).write_text(digest + '  ' + signed.name + '\n')
    print('Signed release:', signed)
    print('SHA256:', digest)
    print(verification.stdout)


if __name__ == '__main__':
    main()
