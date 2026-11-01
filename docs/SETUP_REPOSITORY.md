# Setup repository tujuan

1. Buat repository GitHub (private disarankan) dan pastikan ada minimal satu commit (mis. README). Repository kosong ditolak oleh Git Data API.
2. Pastikan GitHub Actions aktif (Settings -> Actions -> Allow).
3. Catat nama branch (mis. `main`).
4. Di aplikasi: Pengaturan -> isi username (pemilik), repository, branch, token -> Simpan -> Uji koneksi.
5. Opsional (Secrets repository, Settings -> Secrets and variables -> Actions):
   - Release terpercaya: `KEYSTORE_BASE64` (base64 file .jks), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Tanpa ini, Release APK ditandatangani kunci uji sementara dan log memberi peringatan.
   - Unity: `UNITY_LICENSE`, `UNITY_EMAIL`, `UNITY_PASSWORD`.

## Perilaku penting
- Tidak ada file repository yang dihapus. Setiap upload membuat folder baru `projects/<nama>-<id>/`; bersihkan folder lama secara manual jika repo membesar.
- ZIP dengan satu folder teratas akan dilepas foldernya agar file build ada di root project.
- Folder `.git`, `node_modules`, `.gradle`, `.idea`, `__MACOSX` dilewati (dependency dipasang ulang di CI).
- Batas: ZIP 200 MB, 50 MB per file, 600 MB total, 20.000 file.
- Artifact disimpan 14 hari; setelah itu tombol unduh akan menampilkan pesan kedaluwarsa.
- Kebutuhan per framework: Flutter (stable), Node 20, JDK 11/17/21 (otomatis), Android SDK/NDK, .NET 8/9. Unity butuh lisensi; Godot hanya 4.x dengan `export_presets.cfg` preset Android (dukungan best-effort).
