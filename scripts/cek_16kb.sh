#!/usr/bin/env bash
# Periksa semua pustaka native (.so) di APK/AAB: ELF LOAD segment harus selaras 16 KB (syarat Play untuk target SDK 35+).
set -u
FILE="$1"; TMP=$(mktemp -d); BAD=0
unzip -q -o "$FILE" '*.so' -d "$TMP" 2>/dev/null || true
for abi in arm64-v8a x86_64; do
  while IFS= read -r so; do
    ALIGN=$(readelf -lW "$so" | awk '/LOAD/ {print $NF}' | sort -u | head -1)
    # nilai hex, mis. 0x4000 = 16 KB, 0x1000 = 4 KB
    if [ -n "$ALIGN" ] && [ $((ALIGN)) -lt 16384 ]; then
      echo "::error title=Tidak selaras 16 KB::${so#$TMP/} (align=$ALIGN)"; BAD=1
    fi
  done < <(find "$TMP" -path "*/$abi/*.so" 2>/dev/null)
done
rm -rf "$TMP"
[ $BAD = 0 ] && echo "Semua .so arm64-v8a/x86_64 selaras 16 KB." 
exit $BAD
