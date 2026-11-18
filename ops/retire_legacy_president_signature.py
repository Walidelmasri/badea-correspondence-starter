#!/usr/bin/env python3
"""One-time guarded retirement of the old test-only signature. Never touches new slots."""
import base64
import getpass
import json
import urllib.error
import urllib.request

BASE = 'https://alfresco.internal.badea.org/alfresco/api/-default-/public/alfresco/versions/1'
OLD_ID = '144dc51d-8c06-498b-8dc5-1d8c06398ba1'
EXPECTED_PARENT = '136b91ea-c2f3-472a-ab91-eac2f3072a1a'
EXPECTED_NAME = '.badea-presidential-signature-private'
EXPECTED_OWNER = '25446'
EXPECTED_REVISION = 1

with open('/dev/tty', 'r') as tty:
    print('Alfresco administrator username: ', end='', flush=True)
    username = tty.readline().strip()
password = getpass.getpass('Alfresco administrator password: ')
if not username or not password:
    raise SystemExit('Credentials required')
header = 'Basic ' + base64.b64encode(f'{username}:{password}'.encode()).decode()
del password


def call(method):
    req = urllib.request.Request(f'{BASE}/nodes/{OLD_ID}' +
                                 ('?permanent=false' if method == 'DELETE' else ''),
                                 headers={'Authorization': header, 'Accept': 'application/json'},
                                 method=method)
    with urllib.request.urlopen(req, timeout=60) as response:
        return response.status, (json.load(response) if method == 'GET' else None)

status, payload = call('GET')
entry = payload['entry']
props = entry.get('properties') or {}
checks = {
    'id': entry.get('id') == OLD_ID,
    'name': entry.get('name') == EXPECTED_NAME,
    'type': entry.get('nodeType') == 'bc:presidentSignature',
    'parent': entry.get('parentId') == EXPECTED_PARENT,
    'test owner': str(props.get('bc:signatureOwnerEmployeeId')) == EXPECTED_OWNER,
    'test revision': int(props.get('bc:signatureRevision', -1)) == EXPECTED_REVISION,
}
for label, match in checks.items():
    print(label, 'OK' if match else 'MISMATCH')
if not all(checks.values()):
    raise SystemExit('ABORT: Legacy signature node changed. No deletion performed.')

print('Exact old test signature verified. New signature slots will NOT be touched.')
with open('/dev/tty', 'r') as tty:
    print('Type RETIRE OLD TEST SIGNATURE 25446 to proceed: ', end='', flush=True)
    answer = tty.readline().strip()
if answer != 'RETIRE OLD TEST SIGNATURE 25446':
    raise SystemExit('Cancelled. No deletion performed.')
status, _ = call('DELETE')
if status != 204:
    raise SystemExit(f'Unexpected DELETE status {status}')
print('Legacy test signature removed from active repository (recoverable Trashcan deletion).')
