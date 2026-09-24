"""
================================================================================
 SIMULATOR CLOUD TASK SCHEDULING DENGAN GENETIC ALGORITHM (GA)
 Tugas Minggu 4 - Heuristik Task Scheduling

 Simulator ini mengikuti DESAIN ARSITEKTUR PROJECT KELOMPOK 4
 (dokumen "SOKA_A_Kelompok_4_DESIGN_PROJECT"):
   - Tools acuan   : CloudSim Plus (dimodelkan ulang dengan Python murni pada
                      simulator ini karena lingkungan sandbox tidak memiliki
                      akses ke Maven Central untuk mengunduh dependency Java
                      CloudSim Plus; logika datacenter/host/VM/cloudlet & alur
                      eksperimen tetap mengikuti model CloudSim Plus)
   - Datacenter    : 1 datacenter, 4 host heterogen, 8 VM (2 VM per host)
   - Workload      : Bag-of-Tasks (BoT) - 100 cloudlet independen
   - Dataset       : Google Cloud Cluster Workload Traces (lihat catatan di
                      generate_task_dataset)
   - Objective     : Multi-objective -> minimize (Makespan, Energy Consumption)
                      F = w1*Makespan_norm + w2*Energy_norm, w1=w2=0.5
   - Constraint    : Resource/VM Capacity (task hanya boleh dijadwalkan ke VM
                      yang PE-nya mencukupi kebutuhan task)

 Algoritma penjadwalan yang diimplementasikan: Genetic Algorithm (GA)
 Referensi: https://ieeexplore.ieee.org/document/10330885

 Cara pakai:
     python3 ga_task_scheduling_simulator.py
================================================================================
"""

import random
import csv
import statistics
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

random.seed(42)  # agar hasil dapat direproduksi

# ==============================================================================
# 1. DESAIN ARSITEKTUR CLOUD (sesuai draft desain awal Kelompok 4)
#    1 Datacenter -> 4 Host heterogen -> 8 VM (2 VM per host)
# ==============================================================================

class Host:
    def __init__(self, host_id, pe_count, ram_gb, storage_gb, bw_mbps, p_idle_w, p_max_w):
        self.id = host_id
        self.pe_count = pe_count
        self.ram_gb = ram_gb
        self.storage_gb = storage_gb
        self.bw_mbps = bw_mbps
        # Model daya linear sederhana ala CloudSim Plus PowerModel:
        # P(u) = Pidle + (Pmax - Pidle) * utilisasi
        # Pidle/Pmax diskalakan mengikuti kapasitas PE host (host lebih besar
        # -> konsumsi daya lebih besar). Nilai ini adalah ASUMSI pemodelan
        # karena dokumen desain tidak menetapkan power model spesifik.
        self.p_idle_w = p_idle_w
        self.p_max_w = p_max_w


class VM:
    def __init__(self, vm_id, host_id, pe_count, ram_gb, storage_gb, mips_per_pe):
        self.id = vm_id
        self.host_id = host_id
        self.pe_count = pe_count
        self.ram_gb = ram_gb
        self.storage_gb = storage_gb
        self.mips_per_pe = mips_per_pe
        self.ready_time = 0.0


def build_datacenter():
    """
    Datacenter 1 dengan 4 host heterogen (spesifikasi persis dari draft desain):
      Host1: 8 PE | 16 GB | 1 TB | 10 Gbps  -> VM1, VM2 (1000 MIPS)
      Host2: 8 PE | 16 GB | 1 TB | 10 Gbps  -> VM3, VM4 (1500 MIPS)
      Host3: 16 PE| 32 GB | 2 TB | 10 Gbps  -> VM5, VM6 (2000 MIPS)
      Host4: 16 PE| 32 GB | 2 TB | 10 Gbps  -> VM7, VM8 (2500 MIPS)
    """
    hosts = [
        Host(host_id=1, pe_count=8,  ram_gb=16, storage_gb=1000, bw_mbps=10000, p_idle_w=93,  p_max_w=135),
        Host(host_id=2, pe_count=8,  ram_gb=16, storage_gb=1000, bw_mbps=10000, p_idle_w=93,  p_max_w=135),
        Host(host_id=3, pe_count=16, ram_gb=32, storage_gb=2000, bw_mbps=10000, p_idle_w=175, p_max_w=250),
        Host(host_id=4, pe_count=16, ram_gb=32, storage_gb=2000, bw_mbps=10000, p_idle_w=175, p_max_w=250),
    ]

    vms = [
        VM(vm_id=1, host_id=1, pe_count=2, ram_gb=4,  storage_gb=100, mips_per_pe=1000),
        VM(vm_id=2, host_id=1, pe_count=2, ram_gb=4,  storage_gb=100, mips_per_pe=1000),
        VM(vm_id=3, host_id=2, pe_count=4, ram_gb=8,  storage_gb=200, mips_per_pe=1500),
        VM(vm_id=4, host_id=2, pe_count=4, ram_gb=8,  storage_gb=200, mips_per_pe=1500),
        VM(vm_id=5, host_id=3, pe_count=4, ram_gb=8,  storage_gb=200, mips_per_pe=2000),
        VM(vm_id=6, host_id=3, pe_count=4, ram_gb=8,  storage_gb=200, mips_per_pe=2000),
        VM(vm_id=7, host_id=4, pe_count=8, ram_gb=16, storage_gb=500, mips_per_pe=2500),
        VM(vm_id=8, host_id=4, pe_count=8, ram_gb=16, storage_gb=500, mips_per_pe=2500),
    ]
    return hosts, vms


# ==============================================================================
# 2. DATASET / WORKLOAD: Bag-of-Tasks (BoT), 100 cloudlet independen
#    Dataset acuan: Google Cloud Cluster Workload Traces (sesuai draft desain
#    Kelompok 4). Sandbox simulasi ini tidak memiliki akses jaringan ke Google
#    Cluster Trace asli (BigQuery/GCS), sehingga panjang task (MI) dan
#    kebutuhan PE dibangkitkan mengikuti KARAKTERISTIK STATISTIK trace
#    tersebut, yaitu distribusi durasi task yang condong (right-skewed /
#    lognormal) - pola ini dilaporkan pada studi Google Cluster Trace maupun
#    Paper 3 (Abraham et al., 2024). Task tidak memiliki dependency (Bag-of-Tasks).
# ==============================================================================

class Task:
    def __init__(self, task_id, length_mi, pe_required, file_size_mb, output_size_mb):
        self.id = task_id
        self.length_mi = length_mi
        self.pe_required = pe_required
        self.file_size_mb = file_size_mb
        self.output_size_mb = output_size_mb


def generate_task_dataset(n_tasks=100):
    """Bangkitkan 100 cloudlet Bag-of-Tasks mengikuti pola Google Cluster Trace."""
    tasks = []
    for i in range(n_tasks):
        # Panjang task: distribusi lognormal (khas trace Google Cluster),
        # dibatasi ke rentang realistis 500 - 60.000 MI.
        length_mi = int(min(60000, max(500, random.lognormvariate(8.3, 0.9))))
        # Kebutuhan PE task bervariasi (task ringan s.d. paralel),
        # dibatasi agar tetap bisa dipetakan ke VM terkecil sekalipun (VM1/VM2 = 2 PE)
        pe_required = random.choice([1, 1, 1, 2, 2, 4])
        file_size = random.randint(50, 500)
        output_size = random.randint(50, 500)
        tasks.append(Task(i, length_mi, pe_required, file_size, output_size))
    return tasks


# ==============================================================================
# 3. FUNGSI EVALUASI MULTI-OBJECTIVE (Makespan & Energy Consumption)
#    F = w1 * Makespan_norm + w2 * Energy_norm  (w1 = w2 = 0.5, sesuai desain)
# ==============================================================================

def evaluate_mapping(mapping, tasks, vms, hosts):
    """
    mapping[i] = id VM (bukan index) yang menjalankan tasks[i]
    Mengembalikan: makespan, total_energy(Joule), finish_time per VM, util per host
    """
    vm_by_id = {vm.id: vm for vm in vms}
    finish_time = {vm.id: 0.0 for vm in vms}

    for task_idx, vm_id in enumerate(mapping):
        vm = vm_by_id[vm_id]
        task = tasks[task_idx]
        # Model eksekusi CloudSim Plus: cloudlet length (MI) dieksekusi paralel
        # pada sejumlah PE yang diminta task -> speedup linear terhadap PE.
        exec_time = task.length_mi / (vm.mips_per_pe * task.pe_required)
        finish_time[vm.id] += exec_time

    makespan = max(finish_time.values()) if finish_time else 0.0

    total_energy = 0.0
    host_util = {}
    for h in hosts:
        vms_on_host = [v for v in vms if v.host_id == h.id]
        if makespan > 0 and vms_on_host:
            util = sum(finish_time[v.id] for v in vms_on_host) / (len(vms_on_host) * makespan)
        else:
            util = 0.0
        util = min(util, 1.0)
        power_w = h.p_idle_w + (h.p_max_w - h.p_idle_w) * util
        energy_j = power_w * makespan  # Watt x detik = Joule; host menyala selama makespan
        total_energy += energy_j
        host_util[h.id] = util

    return makespan, total_energy, finish_time, host_util


def check_constraint_feasible(vm, task):
    """Constraint: Resource/VM Capacity - PE VM harus mencukupi kebutuhan task."""
    return vm.pe_count >= task.pe_required


# ==============================================================================
# 4. GENETIC ALGORITHM (GA) UNTUK MULTI-OBJECTIVE TASK SCHEDULING
#    Encoding -> Populasi awal (constraint-aware) -> Fitness (multi-objective)
#    -> Seleksi (tournament) -> Crossover (single-point) -> Mutasi (constraint-
#    aware) -> Elitisme -> ulangi hingga generasi maksimum.
# ==============================================================================

class GAScheduler:
    def __init__(self, tasks, vms, hosts, baseline_makespan, baseline_energy,
                 w1=0.5, w2=0.5, pop_size=50, generations=150,
                 crossover_rate=0.85, mutation_rate=0.10, elitism=2, tournament_k=3):
        self.tasks = tasks
        self.vms = vms
        self.hosts = hosts
        self.n_tasks = len(tasks)
        self.baseline_makespan = baseline_makespan
        self.baseline_energy = baseline_energy
        self.w1 = w1
        self.w2 = w2
        self.pop_size = pop_size
        self.generations = generations
        self.crossover_rate = crossover_rate
        self.mutation_rate = mutation_rate
        self.elitism = elitism
        self.tournament_k = tournament_k
        self.history_best_fitness = []

        # Constraint: daftar VM valid (memenuhi kebutuhan PE) per task,
        # dibangun sekali di awal agar seluruh kromosom yang dihasilkan
        # (inisialisasi, crossover, mutasi) SELALU feasible.
        self.valid_vms_per_task = [
            [vm.id for vm in vms if check_constraint_feasible(vm, t)] for t in tasks
        ]

    # --- (a) Inisialisasi Populasi (constraint-aware) ---
    def init_population(self):
        return [
            [random.choice(self.valid_vms_per_task[i]) for i in range(self.n_tasks)]
            for _ in range(self.pop_size)
        ]

    # --- (b) Evaluasi Fitness Multi-Objective ---
    def fitness(self, chromosome):
        makespan, energy, _, _ = evaluate_mapping(chromosome, self.tasks, self.vms, self.hosts)
        f_makespan = makespan / self.baseline_makespan
        f_energy = energy / self.baseline_energy
        cost = self.w1 * f_makespan + self.w2 * f_energy
        return 1.0 / (1.0 + cost)

    # --- (c) Seleksi: Tournament Selection ---
    def tournament_selection(self, population, fitnesses):
        contenders = random.sample(range(len(population)), self.tournament_k)
        best = max(contenders, key=lambda idx: fitnesses[idx])
        return population[best]

    # --- (d) Crossover: Single-Point Crossover ---
    def crossover(self, parent1, parent2):
        if random.random() > self.crossover_rate or self.n_tasks < 2:
            return parent1[:], parent2[:]
        point = random.randint(1, self.n_tasks - 1)
        child1 = parent1[:point] + parent2[point:]
        child2 = parent2[:point] + parent1[point:]
        return child1, child2

    # --- (e) Mutasi: Random Reset Mutation (constraint-aware) ---
    def mutate(self, chromosome):
        mutated = chromosome[:]
        for i in range(self.n_tasks):
            if random.random() < self.mutation_rate:
                mutated[i] = random.choice(self.valid_vms_per_task[i])
        return mutated

    # --- (f) Loop Evolusi Utama ---
    def run(self):
        population = self.init_population()

        for gen in range(self.generations):
            fitnesses = [self.fitness(chrom) for chrom in population]

            ranked = sorted(zip(population, fitnesses), key=lambda x: x[1], reverse=True)
            new_population = [ind for ind, _ in ranked[:self.elitism]]

            while len(new_population) < self.pop_size:
                parent1 = self.tournament_selection(population, fitnesses)
                parent2 = self.tournament_selection(population, fitnesses)
                child1, child2 = self.crossover(parent1, parent2)
                new_population.append(self.mutate(child1))
                if len(new_population) < self.pop_size:
                    new_population.append(self.mutate(child2))

            population = new_population
            self.history_best_fitness.append(max(fitnesses))

        final_fitnesses = [self.fitness(chrom) for chrom in population]
        best_idx = final_fitnesses.index(max(final_fitnesses))
        return population[best_idx], self.history_best_fitness


# ==============================================================================
# 5. BASELINE PEMBANDING: Round Robin & Random Scheduling (constraint-aware)
# ==============================================================================

def round_robin_mapping(tasks, vms):
    mapping = []
    cursor = 0
    for t in tasks:
        valid = [vm.id for vm in vms if check_constraint_feasible(vm, t)]
        mapping.append(valid[cursor % len(valid)])
        cursor += 1
    return mapping


def random_mapping(tasks, vms):
    mapping = []
    for t in tasks:
        valid = [vm.id for vm in vms if check_constraint_feasible(vm, t)]
        mapping.append(random.choice(valid))
    return mapping


# ==============================================================================
# 6. MAIN EXPERIMENT
# ==============================================================================

def print_header(title):
    print("\n" + "=" * 82)
    print(title)
    print("=" * 82)


def main():
    # ---- 1. Bangun Datacenter (sesuai draft desain Kelompok 4) ----
    hosts, vms = build_datacenter()
    print_header("1. DESAIN ARSITEKTUR CLOUD (Kelompok 4) - 1 Datacenter, 4 Host, 8 VM")
    for h in hosts:
        print(f"Host {h.id}: {h.pe_count} PE, RAM {h.ram_gb} GB, Storage {h.storage_gb} GB, "
              f"Power {h.p_idle_w}-{h.p_max_w} W")
    for v in vms:
        print(f"  VM{v.id} (Host {v.host_id}): {v.pe_count} PE, RAM {v.ram_gb} GB, "
              f"{v.mips_per_pe} MIPS/PE")

    # ---- 2. Bangkitkan Dataset (100 cloudlet Bag-of-Tasks) ----
    tasks = generate_task_dataset(n_tasks=100)
    print_header("2. DATASET / WORKLOAD: 100 CLOUDLET BAG-OF-TASKS (pola Google Cluster Trace)")
    print(f"{'ID':<4}{'Length(MI)':<12}{'PE Req':<8}{'FileSize(MB)':<14}{'OutputSize(MB)':<15}")
    for t in tasks[:8]:
        print(f"{t.id:<4}{t.length_mi:<12}{t.pe_required:<8}{t.file_size_mb:<14}{t.output_size_mb:<15}")
    print(f"... ({len(tasks)} cloudlet total)")

    # ---- 3. Baseline (untuk normalisasi objective GA & pembanding akhir) ----
    rr_mapping = round_robin_mapping(tasks, vms)
    rr_makespan, rr_energy, rr_finish, rr_util = evaluate_mapping(rr_mapping, tasks, vms, hosts)

    rand_mapping = random_mapping(tasks, vms)
    rand_makespan, rand_energy, rand_finish, rand_util = evaluate_mapping(rand_mapping, tasks, vms, hosts)

    # ---- 4. Jalankan GA (multi-objective: makespan + energy) ----
    print_header("3. MENJALANKAN GENETIC ALGORITHM (Multi-Objective: Makespan & Energy)")
    ga = GAScheduler(tasks, vms, hosts, baseline_makespan=rr_makespan, baseline_energy=rr_energy,
                      w1=0.5, w2=0.5, pop_size=50, generations=150)
    best_mapping, history = ga.run()

    for gen in [0, 4, 9, 24, 49, 99, 149]:
        if gen < len(history):
            print(f"Generasi {gen+1:>4}: fitness terbaik = {history[gen]:.6f}")

    ga_makespan, ga_energy, ga_finish, ga_util = evaluate_mapping(best_mapping, tasks, vms, hosts)

    # ---- 5. Hasil & Metrik ----
    print_header("4. HASIL AKHIR (MAPPING TERBAIK - GA)")
    print(f"{'TaskID':<8}{'VM Terpilih':<12}")
    for i in range(10):
        print(f"{i:<8}{best_mapping[i]:<12}")
    print(f"... ({len(best_mapping)} cloudlet total)")

    print("\nUtilisasi tiap Host (rata-rata, GA):")
    for h in hosts:
        print(f"  Host{h.id}: {ga_util[h.id]*100:.1f}%")

    def throughput(makespan, n):
        return n / makespan if makespan > 0 else 0.0

    print_header("5. PERBANDINGAN ALGORITHM (Metrik Optimasi)")
    header = f"{'Algoritma':<20}{'Makespan(s)':<14}{'Energy(kJ)':<13}{'AvgUtil(%)':<12}{'Throughput':<12}"
    print(header)
    rows = [
        ("Genetic Algorithm", ga_makespan, ga_energy, ga_util),
        ("Round Robin", rr_makespan, rr_energy, rr_util),
        ("Random", rand_makespan, rand_energy, rand_util),
    ]
    csv_rows = []
    for name, mk, en, util in rows:
        avg_util = statistics.mean(util.values()) * 100
        tp = throughput(mk, len(tasks))
        print(f"{name:<20}{mk:<14.2f}{en/1000:<13.2f}{avg_util:<12.1f}{tp:<12.3f}")
        csv_rows.append((name, mk, en, avg_util, tp))

    imp_rr_mk = (rr_makespan - ga_makespan) / rr_makespan * 100
    imp_rr_en = (rr_energy - ga_energy) / rr_energy * 100
    imp_rand_mk = (rand_makespan - ga_makespan) / rand_makespan * 100
    imp_rand_en = (rand_energy - ga_energy) / rand_energy * 100
    print(f"\nGA vs Round Robin : makespan -{imp_rr_mk:.1f}%, energy -{imp_rr_en:.1f}%")
    print(f"GA vs Random      : makespan -{imp_rand_mk:.1f}%, energy -{imp_rand_en:.1f}%")

    # ---- 6. Simpan hasil ke CSV ----
    with open("hasil_simulasi_GA.csv", "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["TaskID", "Length_MI", "PE_Required", "VM_Terpilih_GA"])
        for i, t in enumerate(tasks):
            writer.writerow([t.id, t.length_mi, t.pe_required, best_mapping[i]])
        writer.writerow([])
        writer.writerow(["Algoritma", "Makespan(s)", "Energy(J)", "AvgUtil(%)", "Throughput(task/s)"])
        for name, mk, en, avg_util, tp in csv_rows:
            writer.writerow([name, f"{mk:.2f}", f"{en:.2f}", f"{avg_util:.1f}", f"{tp:.3f}"])

    # ---- 7. Grafik Konvergensi GA ----
    plt.figure(figsize=(8, 5))
    plt.plot(range(1, len(history) + 1), history, color="#028090", linewidth=2)
    plt.title("Konvergensi Genetic Algorithm - Multi-Objective Task Scheduling")
    plt.xlabel("Generasi")
    plt.ylabel("Fitness Terbaik")
    plt.grid(alpha=0.3)
    plt.tight_layout()
    plt.savefig("convergence_chart.png", dpi=150)
    plt.close()

    # ---- 8. Grafik Perbandingan Makespan & Energy ----
    fig, axes = plt.subplots(1, 2, figsize=(11, 5))
    algos = ["Genetic\nAlgorithm", "Round\nRobin", "Random"]
    colors = ["#028090", "#F9A03F", "#B85042"]

    makespans = [ga_makespan, rr_makespan, rand_makespan]
    bars1 = axes[0].bar(algos, makespans, color=colors)
    axes[0].set_title("Makespan (detik)")
    for bar, val in zip(bars1, makespans):
        axes[0].text(bar.get_x() + bar.get_width() / 2, val, f"{val:.1f}", ha="center", va="bottom", fontweight="bold")

    energies_kj = [ga_energy/1000, rr_energy/1000, rand_energy/1000]
    bars2 = axes[1].bar(algos, energies_kj, color=colors)
    axes[1].set_title("Energy Consumption (kJ)")
    for bar, val in zip(bars2, energies_kj):
        axes[1].text(bar.get_x() + bar.get_width() / 2, val, f"{val:.1f}", ha="center", va="bottom", fontweight="bold")

    fig.suptitle("Perbandingan Multi-Objective: Makespan vs Energy Consumption")
    plt.tight_layout()
    plt.savefig("perbandingan_algoritma.png", dpi=150)
    plt.close()

    print_header("SELESAI")
    print("File hasil disimpan: hasil_simulasi_GA.csv, convergence_chart.png, perbandingan_algoritma.png")


if __name__ == "__main__":
    main()
