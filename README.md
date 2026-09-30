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

Dataset utama berada di `cloudsim-plus-ga/dataset/tasks.csv` dengan kolom:

- `taskId`: ID task hasil pemilihan dari trace.
- `lengthMI`: proxy panjang task yang diturunkan dari CPU normalized demand.
- `pes`: kebutuhan PE yang diturunkan dari CPU normalized demand.
- `ramMB`: kebutuhan RAM yang diturunkan dari memory normalized demand.
- `priority`: `JobType` dari trace.

Raw trace resmi disimpan sebagai `google-cluster-data-1.csv.gz`. Sumber,
checksum, lisensi, dan aturan transformasinya tercatat di
`cloudsim-plus-ga/dataset/source_metadata.txt`. Script reproducible untuk
menghasilkan CSV adalah `cloudsim-plus-ga/dataset/prepare_google_trace.py`.

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

## Cara Menjalankan

Dari root repository:

```bash
./run.sh
```

Script menjalankan Maven dari folder `cloudsim-plus-ga`, menampilkan output ke
terminal, dan menyimpan salinan log di `cloudsim-plus-ga/results/run.log`.

Perintah manual:

```bash
cd cloudsim-plus-ga
mvn clean compile
mvn exec:java
```

## File Output

Simulator membuat atau memperbarui:

| File | Keterangan |
| ---- | ---------- |
| `cloudsim-plus-ga/results/ga_mapping.csv` | Mapping cloudlet ke VM hasil GA. |
| `cloudsim-plus-ga/results/metrics.txt` | Fitness, makespan, energi, execution time, utilisasi, dan throughput. |
| `cloudsim-plus-ga/results/run.log` | Log lengkap proses dan tabel hasil Cloudlet. |

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
    │   ├── metrics.txt
    │   └── run.log
    └── src/main/java/id/its/cloudtaskscheduling
        └── GeneticAlgorithmCloudTaskScheduling.java
```

## Referensi

- CloudSim Plus: https://cloudsimplus.org/
- Google Cluster Workload Trace: https://github.com/google/cluster-data
- Referensi Genetic Algorithm: https://ieeexplore.ieee.org/document/10330885
