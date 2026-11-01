# Universal APK Builder

Aplikasi Android native (Kotlin + Jetpack Compose) untuk: upload source project ZIP ke GitHub, menjalankan build APK lewat GitHub Actions, memantau build secara nyata, dan mengunduh APK dari Artifact.

Tanpa Firebase, tanpa backend, tanpa akun aplikasi. Semua data tersimpan lokal; token GitHub dienkripsi (Android Keystore + EncryptedSharedPreferences).

## Alur
1. Settings: isi token, username, repository, branch.
2. Upload ZIP -> divalidasi (magic bytes, Zip Slip, batas ukuran, file berbahaya) dan framework dipratinjau.
3. BUILD APK -> semua file diunggah dalam SATU commit (Git Trees API) ke folder baru `projects/<nama>-<id>/`, bersama file workflow `.github/workflows/universal-build.yml` dan skrip `.github/universal/*`.
4. `workflow_dispatch` dikirim; run dicari lewat `client_id` unik di `run-name`; status dipolling dari GitHub Actions API (queued / in_progress / completed + progres step).
5. Sukses: APK diambil dari artifact `apk-<run_id>` milik run itu. Gagal: `report-<run_id>` (report.json + build.log) dibaca dan ditampilkan sebagai error terstruktur.

## Struktur kode (`app/src/main/java/com/universalapk/builder/`)
- `app/` Application, MainActivity, Graph (dependensi)
- `github/` GitHubApi (REST, rate limit), ArtifactDownloader
- `upload/` ZipRules, ZipScanner, UploadManager
- `build/` ProjectDetector, BuildPipeline (upload -> dispatch -> monitor), ErrorReports, ApkActions
- `storage/` SettingsStore, HistoryStore, Exporter
- `security/` SecureStore
- `models/` Models
- `ui/` Compose screens + ViewModel
- `workers/` BuildPipelineWorker (foreground WorkManager), Notifier
- Aset workflow: `app/src/main/assets/universal/` (universal-build.yml, detect.py, build.sh, make_report.py)

Dokumentasi: `docs/SETUP_REPOSITORY.md`, `docs/GITHUB_TOKEN.md`, `docs/BUILD_APLIKASI.md`.
