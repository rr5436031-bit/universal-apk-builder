# Pengaturan GitHub Token

Fine-grained token (disarankan), Repository access: hanya repository tujuan.

| Permission | Akses |
|---|---|
| Contents | Read and write |
| Actions | Read and write |
| Workflows | Read and write |
| Metadata | Read-only |

Token klasik: scope `repo` dan `workflow`.

Keamanan: token hanya dikirim di header Authorization ke api.github.com, disimpan terenkripsi (AES-256 via Keystore), ditampilkan hanya dalam bentuk tertutup, tidak masuk log/URL/commit, dan `allowBackup` dimatikan. Cabut token di GitHub jika perangkat hilang.
