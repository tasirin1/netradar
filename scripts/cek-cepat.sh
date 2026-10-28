#!/bin/bash
# Cek cepat pra-push tanpa SDK (pola repo Tasirin lain):
# konsistensi repo + whitespace + pola secret yang jelas.
# Keluar bukan-nol = ada masalah, push sebaiknya dibatalkan.
set -e
cd "$(dirname "$0")/.."

python3 scripts/check_repo.py

if ! git diff --check HEAD; then
    echo "GAGAL: whitespace error pada perubahan" >&2
    exit 1
fi

# Pola secret yang jelas-jelas tidak boleh ter-commit.
if grep -rInE "(ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[0-9A-Z]{16})" \
    --exclude-dir=.git --exclude=cek-cepat.sh . >/dev/null; then
    echo "GAGAL: pola secret terdeteksi di berkas repo" >&2
    exit 1
fi

echo "CEK CEPAT OK"
