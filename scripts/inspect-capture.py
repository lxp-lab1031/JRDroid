#!/usr/bin/env python3
"""Validate a JRDroid IQ capture and print metadata; no private-radio decoder claim."""
import argparse
import json
import pathlib

parser=argparse.ArgumentParser()
parser.add_argument('capture',type=pathlib.Path)
args=parser.parse_args()
with args.capture.open('rb') as f:
    line=f.readline(4097)
    if len(line)>4096 or not line.endswith(b'\n'): raise SystemExit('Invalid or oversized capture header')
    meta=json.loads(line)
    if meta.get('format')!='JRDROID-IQ' or meta.get('version')!=1 or meta.get('encoding')!='u8-iq-interleaved':
        raise SystemExit('Unsupported capture format')
    payload=f.read(2400001)
if len(payload)>2400000 or len(payload)!=meta.get('captured_bytes'): raise SystemExit('Capture length mismatch')
if meta.get('sample_rate_hz')!=240000 or meta.get('requested_bytes')!=2400000: raise SystemExit('Unexpected sample rate or capture size')
if meta.get('complete')!=(len(payload)==2400000): raise SystemExit('Completion flag mismatch')
print(json.dumps(meta,ensure_ascii=False,indent=2))
print('Complex samples:',len(payload)//2)
print('Seconds at requested rate:',len(payload)/2/240000)
print('Trailing unpaired byte:',len(payload)%2)
print('IQ metadata/length validated. Keikyu decoding is not verified by this check.')
