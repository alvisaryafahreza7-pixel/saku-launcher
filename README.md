# Saku — home launcher Android native

Launcher beranda Android berbasis Kotlin. Bisa dipilih sebagai Home app dan membuka aplikasi terpasang dari grid.

## Fitur

- Jam dan tanggal Indonesia.
- Grid semua aplikasi terpasang, bisa dicari.
- Empat pintasan cepat di bawah.
- Tombol Atur berisi cara menjadikan Saku launcher utama.

## Build APK

File build-workflow.yml adalah workflow GitHub Actions. Karena token API tidak punya scope workflow, pindahkan file itu ke .github/workflows/build.yml lewat web GitHub:

1. Buka repo → Add file → Create new file.
2. Nama file: .github/workflows/build.yml
3. Isi: seluruh isi build-workflow.yml.
4. Commit ke main.

Setelah itu buka tab Actions, tunggu workflow hijau, unduh artifact saku-launcher-apk, ekstrak, lalu install app-release.apk di HP.

## Jadikan launcher utama

Settings → Home app / App default → pilih Saku, lalu tekan Home.
