# IDN Live Recorder

Aplikasi Android untuk memantau live JKT48 dari JKT48Connect, merekam stream langsung ke video MP4, dan mengelola hasil rekaman di perangkat. Aplikasi tidak membutuhkan server atau hosting. Koneksi ke JKT48Connect dilakukan langsung dari perangkat.

## Fitur

- Memeriksa daftar live JKT48 melalui API JKT48Connect v3.
- Merekam hingga **5 live secara bersamaan**. Setiap sesi memiliki status dan kontrol berhenti sendiri.
- Menjalankan perekaman melalui foreground service agar dapat berlanjut saat aplikasi berada di latar belakang.
- Menyimpan file video ke MediaStore Android pada folder `Movies/IDNLiveRecorder`.
- Menelusuri dan memutar rekaman, menandai favorit, membuat bookmark waktu, serta mengekspor file.
- Mendeteksi live dari notifikasi aplikasi target jika fitur deteksi dan akses notifikasi Android diaktifkan.
- Menyimpan pengaturan dan metadata aplikasi secara lokal di perangkat.

## Teknologi

| Komponen | Teknologi |
|---|---|
| Bahasa dan UI | Kotlin, Jetpack Compose, Material 3 |
| Arsitektur UI | Android ViewModel, StateFlow, repository |
| Networking | Retrofit 2, Gson, OkHttp |
| Perekaman stream | HLS parser/downloader, `MediaExtractor`, `MediaMuxer` |
| Pemutaran | AndroidX Media3 ExoPlayer |
| Penyimpanan file | Android MediaStore dengan scoped storage |
| Database lokal | Room dan SQLite |
| Gambar | Coil Compose |
| Build | Gradle 8.12, Android Gradle Plugin 8.9.1, Kotlin 2.1.0, KSP |

## Arsitektur

```text
MainActivity
└── DashboardScreen (Jetpack Compose)
    ├── DashboardViewModel ── pengaturan deteksi dan izin
    ├── Jkt48LiveViewModel ── Jkt48ConnectRepository ── Jkt48Connect API v3
    │   └── DirectRecordingService (foreground service)
    │       └── DirectStreamRecordingEngine (satu engine per sesi, maksimum 5)
    │           ├── HlsPlaylistResolver / HlsSegmentDetector
    │           └── MediaStoreRecorderStorage
    └── RecordingLibraryViewModel ── RecordingLibraryRepository
        ├── MediaStore (file video)
        └── IDNRecorderDatabase (Room, metadata dan operasi library)
```

Alur perekaman langsung: pengguna memilih live → ViewModel mengirim URL stream dan ID sesi ke service → engine mengambil segmen HLS dan menggabungkan track audio/video dengan `MediaExtractor` dan `MediaMuxer` → file sementara difinalisasi ke MediaStore → library menerima event bahwa rekaman selesai.

Service membatasi jumlah sesi yang sedang berjalan atau difinalisasi sampai lima. Tombol di layar Live menghentikan sesi tertentu; aksi Stop pada notifikasi menghentikan seluruh sesi. Setiap sesi memakai cache dan nama file unik.

Untuk memberi waktu finalisasi sebelum batas foreground service Android, aplikasi menghentikan dan menyimpan sesi setelah sekitar **5 jam 45 menit terus-menerus di latar belakang**. Saat aplikasi dibuka kembali, hitungan latar belakang dimulai ulang. Batas ini berlaku pada aplikasi, bukan per stream.

## Struktur kode utama

```text
app/src/main/java/com/example/idnliverecorder/
├── ui/                 Layar Compose, ViewModel, dan tema
├── monitoring/         Deteksi notifikasi, pengaturan, favorit live, integrasi API
├── recording/direct/   Service, engine, parser playlist, dan pengunduh segmen HLS
├── storage/            MediaStore serta validasi file rekaman
├── library/            Daftar/pemutar rekaman, bookmark, favorit, dan ekspor
├── database/           Room database, entity, dan DAO
├── audio/              Fitur dan utilitas audio
├── core/               Konstanta dan ekstensi umum
└── data/               Model data aplikasi
```

## Database dan penyimpanan

Video tidak disimpan sebagai blob di Room. File MP4 disimpan oleh Android MediaStore di `Movies/IDNLiveRecorder`, sehingga dapat ditemukan melalui library aplikasi dan aplikasi lain yang memiliki akses sesuai aturan Android.

Room menyimpan data pendukung berikut pada database lokal `idn_recorder_db.db`:

| Tabel | Isi |
|---|---|
| `recording_bookmarks` | Penanda waktu dan catatan untuk rekaman |
| `recording_favorites` | Status favorit rekaman |
| `export_tasks` | Status dan progres ekspor rekaman |
| `recording_metadata_cache` | Cache metadata video seperti MIME type, bitrate, dan frame rate |

API key JKT48Connect disimpan terenkripsi menggunakan Android Keystore. Pengaturan lain disimpan secara lokal. Tidak ada backend milik aplikasi atau sinkronisasi cloud.

## Persyaratan pengembangan

- Android Studio dengan Android SDK Platform 35 dan Build Tools yang sesuai.
- JDK 17.
- Perangkat Android API 29 (Android 10) atau lebih baru untuk menjalankan aplikasi.
- Untuk memasang langsung lewat USB: Android Debug Bridge (ADB) dan USB debugging aktif.

## Build dan instalasi (Windows PowerShell)

Dari folder root proyek:

```powershell
.\gradlew.bat assembleDebug
```

APK debug dibuat di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Pasang ke perangkat yang tersambung melalui USB:

```powershell
& "$env:LOCALAPPDATA\Android\sdk\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Jika `adb` sudah tersedia di `PATH`, perintahnya dapat disingkat menjadi:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Alternatifnya, salin APK ke ponsel dan buka file tersebut untuk memasang. Android mungkin meminta izin untuk memasang aplikasi dari sumber tersebut.

## Konfigurasi aplikasi

1. Buka tab **Live** dan masukkan API key JKT48Connect melalui tombol pengaturan kunci API.
2. Muat ulang daftar live, lalu pilih **Rekam** pada live yang diinginkan.
3. Untuk merekam lebih dari satu live, mulai sesi lainnya. Aplikasi menolak sesi keenam sampai salah satu sesi berhenti atau selesai.
4. Beri izin notifikasi bila diminta agar status perekaman dapat ditampilkan.
5. Untuk fitur deteksi live dari notifikasi, pilih aplikasi target pada **Setelan**, aktifkan deteksi, lalu berikan **Notification Access** melalui Pengaturan Android. Izin ini tidak diperlukan untuk memulai rekaman dari daftar Live JKT48.

API yang digunakan adalah JKT48Connect v3 (`https://v3.jkt48connect.com/`). API key dikirim melalui HTTPS; jangan menaruh key pribadi di source code atau membagikan log yang berisi kredensial.

## Pengujian

Jalankan unit test lokal dengan:

```powershell
.\gradlew.bat testDebugUnitTest
```

Uji perekaman beberapa live di perangkat nyata juga diperlukan. Jumlah stream yang dapat berjalan stabil dapat dipengaruhi bandwidth, panas, memori, ketersediaan stream, dan kebijakan baterai perangkat.

## Batasan yang diketahui

- Perekaman langsung bergantung pada URL stream yang diberikan API dan format HLS yang dapat diproses aplikasi.
- Batas lima adalah batas aplikasi; perangkat dengan jaringan atau sumber daya terbatas mungkin tidak mampu menjaga lima stream tetap stabil.
- Android dapat menghentikan service lebih awal jika batas `dataSync` aplikasi telah digunakan oleh service lain; callback batas sistem menjadi pengaman terakhir dan file yang sedang diproses mungkin tidak sempat difinalisasi sepenuhnya.
- Perekaman berjalan dalam service yang tidak meminta Android memulai ulang otomatis setelah proses aplikasi dihentikan paksa atau proses dimatikan sistem.
- API key tetap berada di memori aplikasi dan dikirim sebagai parameter permintaan HTTPS; enkripsi lokal tidak menjadikannya rahasia bersama yang aman untuk ditanamkan ke APK publik.
