#!/usr/bin/env python3
"""Pembuat kode lisensi Dokumentasi Dinas (HANYA untuk pemilik). Butuh: pip install cryptography
Pemakaian:
  python buat_lisensi.py HO  <ID-PERANGKAT|*> [YYYYMMDD]
  python buat_lisensi.py SITE <ID-PERANGKAT|*> [YYYYMMDD]
Berkas kunci privat 'lisensi_private.pem' harus ada di folder yang sama. JANGAN dibagikan."""
import sys, base64
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

def b64(b): return base64.urlsafe_b64encode(b).decode().rstrip("=")

def buat(role, dev, exp="0", pem="lisensi_private.pem"):
    key = serialization.load_pem_private_key(open(pem, "rb").read(), None)
    payload = f"v1|{role.upper()}|{dev.upper()}|{exp}".encode()
    sig = key.sign(payload, ec.ECDSA(hashes.SHA256()))   # DER
    return b64(payload) + "." + b64(sig)

if __name__ == "__main__":
    if len(sys.argv) < 3: sys.exit(__doc__)
    print(buat(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "0"))
