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

Proyek ini merupakan simulator penjadwalan cloud task menggunakan **Genetic
Algorithm (GA)** dan CloudSim Plus. Simulator membaca 100 task dari Google
Cluster Workload Trace yang sudah ditransformasikan ke schema CloudSim, lalu
menjadwalkannya ke 8 VM pada 1 datacenter dengan 4 host heterogen.

Workload menggunakan model **Bag-of-Tasks (BoT)**, sehingga task independen dan
tidak memiliki dependency pada eksperimen ini. Tujuan optimasi adalah:

- meminimalkan makespan;
- meminimalkan konsumsi energi.

## Arsitektur Cloud

### Host

| Host | PE | RAM | Storage | Bandwidth |
| ---- | -- | --- | ------- | --------- |
| Host 1 | 8 | 16 GB | 1000 GB | 10 Gbps |
| Host 2 | 8 | 16 GB | 1000 GB | 10 Gbps |
| Host 3 | 16 | 32 GB | 2000 GB | 10 Gbps |
| Host 4 | 16 | 32 GB | 2000 GB | 10 Gbps |

### Virtual Machine

| VM | Host | PE | RAM | Storage | MIPS/PE |
| -- | ---- | -- | --- | ------- | ------- |
| VM1-VM2 | Host 1 | 2 | 4 GB | 100 GB | 1000 |
| VM3-VM4 | Host 2 | 4 | 8 GB | 200 GB | 1500 |
| VM5-VM6 | Host 3 | 4 | 8 GB | 200 GB | 2000 |
| VM7-VM8 | Host 4 | 8 | 16 GB | 500 GB | 2500 |

`VmAllocationPolicyRoundRobin` memastikan dua VM ditempatkan pada setiap host.

## Dataset

Dataset utama berada di `cloudsim-plus-ga/dataset/tasks.csv`. File ini bukan
format raw Google, melainkan hasil transformasi 100 task dari Google Cluster
Workload Trace Version 1 ke schema yang dibutuhkan CloudSim Plus.

### Sumber dan Transformasi

Raw trace resmi Google disimpan di
`cloudsim-plus-ga/dataset/google-cluster-data-1.csv.gz`. Trace ini berisi data
workload Borg selama 7 jam, dengan kolom raw:

- `Time`: waktu pengamatan dalam detik.
- `ParentID`: ID job yang memiliki task.
- `TaskID`: ID task asli dari trace.
- `JobType`: tipe job.
- `NrmlTaskCores`: kebutuhan CPU yang dinormalisasi.
- `NrmlTaskMem`: kebutuhan memory yang dinormalisasi.

Script `prepare_google_trace.py` mengambil 100 `TaskID` unik pertama yang
memiliki kebutuhan CPU positif. Karena trace Version 1 tidak menyediakan
`lengthMI` dan RAM absolut, dua nilai tersebut diturunkan secara eksplisit:

- `lengthMI = round(NrmlTaskCores * 1.000.000)` dengan minimum 1 MI.
- `pes = ceil(NrmlTaskCores * 32)` dan dibatasi pada 1 sampai 4 PE.
- `ramMB = max(1024, round(NrmlTaskMem * 64 * 1024))`.
- `priority = JobType`.

Artinya, `tasks.csv` adalah dataset trace-derived, bukan salinan mentah.
`lengthMI` dan `ramMB` adalah proxy hasil pemetaan karena field absolut tersebut
memang tidak tersedia pada raw trace. URL resmi, SHA1 checksum, lisensi, dan
aturan transformasi lengkap tersedia di
`cloudsim-plus-ga/dataset/source_metadata.txt`.

Untuk membuat ulang `tasks.csv` setelah raw trace diganti:

```bash
python3 cloudsim-plus-ga/dataset/prepare_google_trace.py
```

### Schema CloudSim

Kolom hasil transformasi adalah:

- `taskId`: ID task hasil pemilihan dari trace.
- `lengthMI`: proxy panjang task yang diturunkan dari CPU normalized demand.
- `pes`: kebutuhan PE yang diturunkan dari CPU normalized demand.
- `ramMB`: kebutuhan RAM yang diturunkan dari memory normalized demand.
- `priority`: `JobType` dari trace.

## Genetic Algorithm

Kromosom merepresentasikan pemetaan task ke VM. Implementasi menggunakan:

- populasi 20 individu;
- 50 generasi;
- tournament selection;
- one-point crossover;
- mutation rate 0,05;
- elitism;
- bobot makespan dan energy masing-masing 0,5.

Constraint utama adalah `PE VM >= PE task`, serta kapasitas RAM, storage, dan
resource host harus mencukupi.

## Persyaratan

- JDK 17 atau lebih baru.
- Maven.
- Python 3.8 atau lebih baru hanya jika ingin membuat ulang `tasks.csv`.

## Cara Menjalankan

Dari root repository:

```bash
./run.sh
```

Script menjalankan Maven dari folder `cloudsim-plus-ga`, menampilkan output ke
terminal, dan menyimpan salinan log di `cloudsim-plus-ga/results/run.log`.
Python dan virtual environment tidak diperlukan untuk menjalankan simulasi.

Perintah manual:

```bash
cd cloudsim-plus-ga
mvn clean compile
mvn exec:java
# Menjalankan baseline FCFS saja
mvn exec:java -Dexec.args=fcfs
```

## File Output

Simulator membuat atau memperbarui:

| File | Keterangan |
| ---- | ---------- |
| `cloudsim-plus-ga/results/ga_mapping.csv` | Mapping cloudlet ke VM hasil GA. |
| `cloudsim-plus-ga/results/metrics.txt` | Fitness, makespan, energi, execution time, utilisasi, dan throughput. |
| `cloudsim-plus-ga/results/fcfs_metrics.txt` | Metrik baseline FCFS dengan scheduler bawaan CloudSim Plus. |
| `cloudsim-plus-ga/results/run.log` | Log lengkap proses dan tabel hasil Cloudlet. |

## Hasil Simulasi Terakhir

Hasil berikut berasal dari eksekusi `./run.sh` menggunakan 100 task hasil
transformasi Google Cluster Workload Trace:

| Metrik | Hasil |
| ------ | -----: |
| Fitness | 0.792023 |
| Makespan | 273.982500 s |
| Energy Consumption | 131769.600000 J |
| Execution Time | 3569.621333 s |
| Average CPU Utilization | 44.52% |
| Throughput | 0.364987 task/s |
| Finished Cloudlets | 100 |

### Perbandingan GA dan FCFS

FCFS menggunakan `CloudletSchedulerSpaceShared` bawaan CloudSim Plus. Urutan
task mengikuti urutan pada `tasks.csv`, sedangkan GA menggunakan mapping hasil
optimasi. Keduanya memakai dataset, host, VM, dan konfigurasi CloudSim yang sama.

| Algoritma | Makespan (s) | Energy (J) | Execution Time (s) | CPU Utilization | Throughput (task/s) |
| --------- | ------------: | ---------: | -----------------: | --------------: | ------------------: |
| Genetic Algorithm | 273.9825 | 131769.60 | 3569.6213 | 44.52% | 0.364987 |
| FCFS | 79.4450 | 38186.40 | 667.3580 | 28.42% | 1.258732 |

Interpretasi hasil dan faktor yang memengaruhi:

- GA menghasilkan utilisasi CPU **44,52%**, atau **16,1 percentage point** lebih
    tinggi daripada FCFS. Ini menunjukkan mapping hasil optimasi GA mampu
    memanfaatkan resource VM secara lebih merata.
- GA menggunakan pencarian berbasis populasi untuk mengevaluasi kombinasi
    penempatan task, sehingga pendekatannya lebih fleksibel untuk workload,
    jumlah VM, dan constraint resource yang lebih beragam daripada aturan urutan
    sederhana seperti FCFS.
- Pada konfigurasi dan dataset ini, FCFS menghasilkan makespan **71,0% lebih
    rendah**, konsumsi energi **71,0% lebih rendah**, execution time **81,3% lebih
    rendah**, serta throughput **244,9% lebih tinggi**. Hasil tersebut merupakan
    karakteristik eksperimen saat ini, bukan batasan umum metode GA.
- Perbedaan ini dipengaruhi oleh bobot fitness GA (makespan dan energi masing-
    masing 0,5), jumlah populasi dan generasi, mutation rate, serta perbedaan
    antara estimasi fitness dan metrik akhir CloudSim. Dengan demikian, hasil GA
    perlu dibaca sebagai trade-off antara pemerataan utilisasi dan waktu/energi
    penyelesaian.

Secara keseluruhan, GA memberikan keunggulan pada pemanfaatan CPU dan
fleksibilitas strategi penjadwalan, sedangkan FCFS menjadi baseline yang lebih
efisien untuk workload ini. Eksperimen lanjutan dapat menyetel bobot fitness,
ukuran populasi, jumlah generasi, dan mutation rate agar optimasi GA lebih
selaras dengan metrik makespan serta energi pada simulasi CloudSim aktual.

### Kasus yang Cocok untuk GA

GA lebih berpotensi memberikan manfaat pada kasus penjadwalan dengan kondisi
berikut:

- jumlah task dan VM lebih besar, sehingga jumlah kemungkinan mapping meningkat
    dan aturan urutan sederhana menjadi kurang adaptif;
- resource VM heterogen, misalnya perbedaan MIPS, jumlah PE, RAM, biaya, dan
    konsumsi energi yang signifikan;
- terdapat banyak constraint sekaligus, seperti deadline, prioritas, batas
    kapasitas, afinitas task-VM, dan batas energi;
- tujuan optimasi bersifat multi-objective, misalnya ingin menyeimbangkan
    makespan, energi, utilisasi, biaya, dan kualitas layanan;
- workload bersifat dinamis atau memiliki pola beban yang berubah, sehingga
    mapping perlu disesuaikan dengan karakteristik task dan resource.

Sebaliknya, FCFS dapat menjadi pilihan yang kuat untuk workload kecil, statis,
dan sederhana seperti 100 task independen pada eksperimen ini, terutama ketika
waktu penyelesaian langsung lebih penting daripada pemerataan utilisasi. Karena
itu, keunggulan GA sebaiknya dinilai pada workload yang lebih besar dan
constraint yang lebih kompleks, bukan hanya dari satu konfigurasi eksperimen.

GA menghasilkan mapping untuk 100 task. Delapan VM berhasil dibuat dan policy
round-robin menempatkan dua VM pada setiap host:

```text
Host 0: VM0, VM4
Host 1: VM1, VM5
Host 2: VM2, VM6
Host 3: VM3, VM7
```

Cuplikan log eksekusi:

```text
==================== OUTPUT ====================
Cloudlet ID  STATUS     Data center ID  VM ID              Time     Start Time    Finish Time
...
Finished Cloudlets     : 100
Makespan               : 273.9825 s
Energy Consumption     : 131769.6000 J
Average CPU Utilization: 44.52%
Throughput             : 0.3650 task/s
[INFO] BUILD SUCCESS
```

Contoh tabel log cloudlet dengan format seperti output CloudSim:

```text
==================== OUTPUT ====================
Cloudlet ID  STATUS     Data center ID  VM ID              Time     Start Time    Finish Time
06           SUCCESS    01              06                  1.25           0.10           1.35
38           SUCCESS    01              06                  1.25           0.10           1.35
54           SUCCESS    01              06                  1.25           0.10           1.35
07           SUCCESS    01              07                  1.25           0.10           1.35
15           SUCCESS    01              07                  1.25           0.10           1.35
...
Finished Cloudlets     : 100
Makespan               : 79.4450 s
CloudletSchedulerSpaceShared FCFS finished!
```

Contoh tersebut berasal dari bagian FCFS pada `run.log`; tabel lengkap tetap
tersimpan di file tersebut.

Log lengkap dan tabel seluruh cloudlet tersedia di
`cloudsim-plus-ga/results/run.log`.

## Struktur Proyek

```text
.
├── README.md
├── SOKA A_Kelompok 4_DESIGN PROJECT.pdf
├── run.sh
└── cloudsim-plus-ga
    ├── dataset
    │   ├── google-cluster-data-1.csv.gz
    │   ├── prepare_google_trace.py
    │   ├── source_metadata.txt
    │   └── tasks.csv
    ├── pom.xml
    ├── results
    │   ├── ga_mapping.csv
    │   ├── fcfs_metrics.txt
    │   ├── metrics.txt
    │   └── run.log
    └── src/main/java/id/its/cloudtaskscheduling
        └── GeneticAlgorithmCloudTaskScheduling.java
```

## Referensi

- CloudSim Plus: https://cloudsimplus.org/
- Google Cluster Workload Trace: https://github.com/google/cluster-data
- Referensi Genetic Algorithm: https://ieeexplore.ieee.org/document/10330885
