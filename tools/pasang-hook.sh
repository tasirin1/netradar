#!/bin/sh
# Pasang kait pre-push: tiap `git push` otomatis menjalankan cek cepat repo.
# Bila cek GAGAL, push dibatalkan sebelum menyentuh GitHub/CI.
# Dipasang sekali per clone: sh tools/pasang-hook.sh
set -e
HOOK=".git/hooks/pre-push"
cat > "$HOOK" <<'HOOK'
#!/bin/sh
# Kait otomatis (dipasang tools/pasang-hook.sh): cek cepat yang akan di-push.
exec bash scripts/cek-cepat.sh
HOOK
chmod +x "$HOOK"
if [ -x "$HOOK" ]; then
    echo "Kait pre-push terpasang dan bisa dieksekusi: $HOOK"
else
    echo "PERINGATAN: $HOOK tak bisa dieksekusi — kait git akan diabaikan."
    echo "  Jalankan manual sebelum push: bash scripts/cek-cepat.sh"
fi
