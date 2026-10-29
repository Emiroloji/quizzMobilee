#!/usr/bin/env bash
# Spike: Logto'dan client_credentials token'ı alıp başlığını ve claim'lerini yazdırır (ADR 0024).
# Kullanım: deploy/scripts/logto-token-claims.sh <issuer> <client_id> <client_secret> <resource>
set -euo pipefail
issuer="$1"; client_id="$2"; client_secret="$3"; resource="$4"
token=$(curl -sf -u "$client_id:$client_secret" \
  -d grant_type=client_credentials -d "resource=$resource" -d scope=read:me \
  "$issuer/token" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
python3 - "$token" <<'PY'
import base64, json, sys
def part(i):
    seg = sys.argv[1].split(".")[i]
    return json.loads(base64.urlsafe_b64decode(seg + "=" * (-len(seg) % 4)))
print(json.dumps({"header": part(0), "claims": part(1)}, indent=2))
PY
