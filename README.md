# GA Task Scheduling Simulator Kelompok 4


## Anggota Tim

| No | Nama                   | NRP        |
| -- | ---------------------- | ---------- |
| 1  | Kanafira Vanesha Putri | 5027241010 |
| 2  | Tiara Putri Prasetya   | 5027241013 |
| 3  | Dina Rahmadani         | 5027241065 |
| 4  | Zahra Khaalishah       | 5027241070 |
| 5  | S. Farhan Baig         | 5027241097 |

## Deskripsi

Proyek ini merupakan simulator penjadwalan cloud task menggunakan **Genetic Algorithm (GA)**. Repository ini memiliki dua implementasi:

- **Python**: simulator eksperimen mandiri dengan workload sintetis 100 task, baseline Round Robin, Random Scheduling, serta grafik hasil.
- **Java dengan CloudSim Plus**: simulasi berbasis CloudSim Plus yang membaca workload dari file CSV dan menyimpan mapping serta metrik simulasi.

Kedua implementasi memodelkan satu datacenter, empat host heterogen, dan delapan virtual machine (VM).

Tujuan optimasi adalah meminimalkan dua metrik secara bersamaan:

- **Makespan**, yaitu waktu penyelesaian seluruh task.
- **Energy consumption**, yaitu total energi yang digunakan host selama simulasi.

Simulasi menggunakan workload **Bag-of-Tasks (BoT)** yang berisi 100 cloudlet independen. Setiap task dapat dijadwalkan pada VM yang memenuhi kebutuhan processing element (PE)-nya.

## Arsitektur Cloud

### Host

| Host | PE | RAM | Storage | Power |
| ---- | -- | --- | ------- | ----- |
| Host 1 | 8 | 16 GB | 1000 GB | 93-135 W |
| Host 2 | 8 | 16 GB | 1000 GB | 93-135 W |
| Host 3 | 16 | 32 GB | 2000 GB | 175-250 W |
| Host 4 | 16 | 32 GB | 2000 GB | 175-250 W |

### Virtual Machine

| VM | Host | PE | RAM | Storage | MIPS/PE |
| -- | ---- | -- | --- | ------- | ------- |
| VM1-VM2 | Host 1 | 2 | 4 GB | 100 GB | 1000 |
| VM3-VM4 | Host 2 | 4 | 8 GB | 200 GB | 1500 |
| VM5-VM6 | Host 3 | 4 | 8 GB | 200 GB | 2000 |
| VM7-VM8 | Host 4 | 8 | 16 GB | 500 GB | 2500 |

## Dataset dan Workload

### Python

Workload terdiri dari 100 task sintetis dengan atribut berikut:

- `TaskID`: ID task.
- `Length_MI`: panjang task dalam satuan million instructions.
- `PE_Required`: jumlah PE yang dibutuhkan task.
- `VM_Terpilih_GA`: VM yang dipilih oleh GA.

Panjang task dibangkitkan menggunakan distribusi lognormal dengan rentang 500-60.000 MI untuk mengikuti karakteristik right-skewed dari Google Cloud Cluster Workload Traces. Dataset Python dibangkitkan secara lokal dan tidak mengambil data trace secara langsung dari internet.

### Java

Workload Java tersedia di `cloudsim-plus-ga/dataset/tasks.csv` dengan kolom:

- `taskId`: ID task.
- `lengthMI`: panjang task dalam million instructions.
- `pes`: jumlah processing element yang dibutuhkan.
- `ramMB`: kebutuhan RAM task.
- `priority`: prioritas task.

## Genetic Algorithm

Kromosom merepresentasikan pemetaan task ke VM. Setiap gen berisi ID VM yang menjalankan task terkait. Kedua implementasi menggunakan constraint PE agar task hanya dipetakan ke VM yang kapasitasnya mencukupi.

Pada simulator Python, tahapan GA yang digunakan:

1. Inisialisasi populasi secara constraint-aware.
2. Evaluasi fitness berdasarkan makespan dan energy consumption.
3. Tournament selection dengan ukuran tournament 3.
4. Single-point crossover.
5. Random reset mutation yang tetap memperhatikan constraint PE.
6. Elitism untuk mempertahankan dua individu terbaik.
7. Pengulangan selama 150 generasi.

Simulator Java menggunakan konfigurasi yang didefinisikan di `GeneticAlgorithmCloudTaskScheduling.java`: populasi 20 individu, maksimum 50 generasi, mutation rate 0,05, tournament selection, one-point crossover, dan elitism.

Fungsi objektif yang digunakan:

```text
F = 0.5 * Makespan_norm + 0.5 * Energy_norm
Fitness = 1 / (1 + F)
```

Constraint penjadwalan:

```text
PE VM >= PE task
```

Semua mapping yang dibentuk pada inisialisasi, crossover, dan mutasi dipastikan memenuhi constraint tersebut.

## Algoritma Pembanding

Hasil GA dibandingkan dengan dua metode baseline:

- **Round Robin**: task dialokasikan secara bergiliran ke VM yang valid.
- **Random Scheduling**: task dialokasikan secara acak ke VM yang valid.

## Persyaratan

- Python 3.8 atau lebih baru untuk simulator Python.
- `matplotlib` untuk simulator Python.
- JDK 17 atau lebih baru dan Maven untuk simulator Java.

Instal dependensi Python dengan perintah berikut:

```bash
python3 -m pip install matplotlib
```

Disarankan menggunakan virtual environment:

```bash
python3 -m venv venv
source venv/bin/activate
python3 -m pip install matplotlib
```

## Cara Menjalankan

### Simulator Python

Jalankan dari folder `simulator-python`:

```bash
cd simulator-python
python3 ga_task_scheduling_simulator.py
```

Karena simulator menggunakan `random.seed(42)`, hasil dapat direproduksi selama konfigurasi dan versi dependensi tidak mengubah perilaku generator random.

### Simulator Java CloudSim Plus

Jalankan dari folder `cloudsim-plus-ga` agar path dataset relatif dapat ditemukan:

```bash
cd cloudsim-plus-ga
mvn clean compile
mvn exec:java
```

Perintah tersebut membaca `dataset/tasks.csv`, menjalankan Genetic Algorithm, lalu menjalankan simulasi CloudSim Plus.

## File Output

Setelah simulator Python selesai dijalankan, file berikut dibuat atau diperbarui di folder `simulator-python`:

| File | Keterangan |
| ---- | ---------- |
| `hasil_simulasi_GA.csv` | Mapping task ke VM dan ringkasan metrik setiap algoritma. |
| `convergence_chart.png` | Grafik fitness terbaik GA pada setiap generasi. |
| `perbandingan_algoritma.png` | Grafik perbandingan makespan dan energy consumption. |

Simulator Java membuat folder `cloudsim-plus-ga/results/` saat dijalankan:

| File | Keterangan |
| ---- | ---------- |
| `results/ga_mapping.csv` | Mapping cloudlet ke VM hasil Genetic Algorithm. |
| `results/metrics.txt` | Fitness, makespan, energy consumption, execution time, utilisasi CPU, dan throughput. |

## Hasil Simulasi Python

Berikut hasil simulasi yang tersimpan pada `hasil_simulasi_GA.csv`:

| Algoritma | Makespan (s) | Energy (J) | Rata-rata Utilisasi (%) | Throughput (task/s) |
| --------- | ------------: | ---------: | ----------------------: | ------------------: |
| Genetic Algorithm | 24.41 | 18481.65 | 93.3 | 4.097 |
| Round Robin | 42.41 | 28311.76 | 60.4 | 2.358 |
| Random | 34.21 | 23599.30 | 67.5 | 2.923 |

Berdasarkan hasil tersebut, Genetic Algorithm menghasilkan makespan dan konsumsi energi paling rendah serta throughput paling tinggi dibandingkan kedua baseline.

## Struktur Proyek

```text
.
├── README.md
├── cloudsim-plus-ga/
│   ├── pom.xml
│   ├── dataset/
│   │   └── tasks.csv
│   ├── result/                         # folder yang tersedia di repository
│   └── src/main/java/id/its/cloudtaskscheduling/
│       └── GeneticAlgorithmCloudTaskScheduling.java
└── simulator-python/
    ├── ga_task_scheduling_simulator.py
    ├── hasil_simulasi_GA.csv
    ├── convergence_chart.png
    └── perbandingan_algoritma.png
```

Folder `cloudsim-plus-ga/results/` dibuat otomatis oleh program Java saat output disimpan. Folder ini dapat belum ada sebelum program dijalankan.

## Referensi

- CloudSim Plus: https://cloudsimplus.org/
- Google Cloud Cluster Workload Traces
- Referensi Genetic Algorithm: https://ieeexplore.ieee.org/document/10330885
