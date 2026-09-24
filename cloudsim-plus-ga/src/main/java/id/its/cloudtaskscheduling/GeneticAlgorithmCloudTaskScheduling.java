package id.its.cloudtaskscheduling;

import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Genetic Algorithm + CloudSim Plus
 *
 * Project:
 * Cloud Task Scheduling
 *
 * Architecture:
 * 1 Datacenter
 * 4 heterogeneous Hosts
 * 8 heterogeneous VMs
 * 100 Cloudlets
 *
 * GA:
 * Population = 20
 * Generations = 50
 * Tournament Selection
 * One-Point Crossover
 * Mutation Rate = 0.05
 * Elitism
 *
 * Objective:
 * Minimize Makespan
 * Minimize Energy Consumption
 */
public class GeneticAlgorithmCloudTaskScheduling {

    /* =========================================================
       GA PARAMETERS
       ========================================================= */

    private static final int POPULATION_SIZE = 20;
    private static final int MAX_GENERATIONS = 50;
    private static final double MUTATION_RATE = 0.05;
    private static final long RANDOM_SEED = 42;

    /* Objective weights */
    private static final double MAKESPAN_WEIGHT = 0.5;
    private static final double ENERGY_WEIGHT = 0.5;

    /* =========================================================
       CLOUD PARAMETERS
       ========================================================= */

    private static final int NUMBER_OF_HOSTS = 4;
    private static final int NUMBER_OF_VMS = 8;

    private static final int HOST_MIPS_PER_PE = 1000;

    /*
     * Host:
     * H1 = 8 PE, 16 GB
     * H2 = 8 PE, 16 GB
     * H3 = 16 PE, 32 GB
     * H4 = 16 PE, 32 GB
     */

    private static final int[] HOST_PES = {
            8, 8, 16, 16
    };

    private static final long[] HOST_RAM_MB = {
            16_384,
            16_384,
            32_768,
            32_768
    };

    private static final long[] HOST_STORAGE_MB = {
            1_000_000,
            1_000_000,
            2_000_000,
            2_000_000
    };

    private static final long[] HOST_BW_MBPS = {
            10_000,
            10_000,
            10_000,
            10_000
    };

    /*
     * VM:
     *
     * VM1-2 = 2 PE, 4 GB, 100 GB, 1000 MIPS
     * VM3-4 = 4 PE, 8 GB, 200 GB, 1500 MIPS
     * VM5-6 = 4 PE, 8 GB, 200 GB, 2000 MIPS
     * VM7-8 = 8 PE, 16 GB, 500 GB, 2500 MIPS
     */

    private static final int[] VM_PES = {
            2, 2,
            4, 4,
            4, 4,
            8, 8
    };

    private static final double[] VM_MIPS = {
            1000, 1000,
            1500, 1500,
            2000, 2000,
            2500, 2500
    };

    private static final long[] VM_RAM_MB = {
            4096, 4096,
            8192, 8192,
            8192, 8192,
            16384, 16384
    };

    private static final long[] VM_SIZE_MB = {
            100_000, 100_000,
            200_000, 200_000,
            200_000, 200_000,
            500_000, 500_000
    };

    /* =========================================================
       CLOUDSIM OBJECTS
       ========================================================= */

        private static CloudSim simulation;
    private static Datacenter datacenter;
    private static DatacenterBrokerSimple broker;

    private static List<Host> hostList;
    private static List<Vm> vmList;
    private static List<Cloudlet> cloudletList;

    /* =========================================================
       DATASET MODEL
       ========================================================= */

    private static class TaskData {

        int taskId;
        long lengthMI;
        int pes;
        long ramMB;
        int priority;

        TaskData(
                int taskId,
                long lengthMI,
                int pes,
                long ramMB,
                int priority
        ) {
            this.taskId = taskId;
            this.lengthMI = lengthMI;
            this.pes = pes;
            this.ramMB = ramMB;
            this.priority = priority;
        }
    }

    /* =========================================================
       MAIN
       ========================================================= */

    public static void main(String[] args) {

        System.out.println();
        System.out.println("=================================================");
        System.out.println(" GENETIC ALGORITHM - CLOUDSIM PLUS");
        System.out.println(" Cloud Task Scheduling");
        System.out.println("=================================================");
        System.out.println();

        try {

            /*
             * 1. Load workload dataset
             */
            List<TaskData> taskDataList = loadDataset(
                    "dataset/tasks.csv"
            );

            System.out.println(
                    "Dataset loaded: "
                            + taskDataList.size()
                            + " tasks"
            );

            /*
             * 2. Create CloudSim Plus simulation
             */
            simulation = new CloudSim();

            /*
             * 3. Create Datacenter
             */
            datacenter = createDatacenter();

            /*
             * 4. Create Broker
             */
            broker = new DatacenterBrokerSimple(simulation);

            /*
             * 5. Create VMs
             */
            vmList = createVms();

            /*
             * 6. Create Cloudlets
             */
            cloudletList = createCloudlets(taskDataList);

            /*
             * 7. GA optimization
             */
            System.out.println();
            System.out.println("Starting Genetic Algorithm...");
            System.out.println();

            GeneticAlgorithm ga = new GeneticAlgorithm(
                    cloudletList,
                    vmList,
                    POPULATION_SIZE,
                    MAX_GENERATIONS,
                    MUTATION_RATE,
                    RANDOM_SEED
            );

            Chromosome bestChromosome = ga.run();

            /*
             * 8. Print best GA result
             */
            System.out.println();
            System.out.println("=================================================");
            System.out.println(" BEST GA SOLUTION");
            System.out.println("=================================================");

            System.out.printf(
                    "Best Fitness : %.6f%n",
                    bestChromosome.fitness
            );

            System.out.printf(
                    "Estimated Makespan : %.4f s%n",
                    bestChromosome.makespan
            );

            System.out.printf(
                    "Estimated Energy   : %.4f J%n",
                    bestChromosome.energy
            );

            /*
             * 9. Apply GA mapping
             */
            applyMapping(
                    cloudletList,
                    vmList,
                    bestChromosome.genes
            );

            /*
             * 10. Submit VMs and Cloudlets
             */
            broker.submitVmList(vmList);
            broker.submitCloudletList(cloudletList);

            /*
             * 11. Start CloudSim Plus
             */
            System.out.println();
            System.out.println(
                    "Starting CloudSim Plus simulation..."
            );

            simulation.start();

            /*
             * 12. Get finished Cloudlets
             */
            List<Cloudlet> finishedCloudlets =
                    broker.getCloudletFinishedList();

            /*
             * 13. Calculate real simulation metrics
             */
            double makespan = calculateMakespan(
                    finishedCloudlets
            );

            double executionTime = calculateExecutionTime(
                    finishedCloudlets
            );

            double throughput = calculateThroughput(
                    finishedCloudlets,
                    makespan
            );

            double utilization = calculateAverageCpuUtilization(
                    finishedCloudlets,
                    vmList,
                    makespan
            );

            /*
             * Energy is estimated from host utilization
             * history after the simulation.
             */
            double energy = calculateEnergy(
                    hostList
            );

            /*
             * 14. Print results
             */
            printResults(
                    finishedCloudlets,
                    makespan,
                    executionTime,
                    throughput,
                    utilization,
                    energy
            );

            /*
             * 15. Save mapping
             */
            saveMapping(
                    cloudletList,
                    bestChromosome.genes
            );

            /*
             * 16. Save metrics
             */
            saveMetrics(
                    makespan,
                    energy,
                    executionTime,
                    utilization,
                    throughput,
                    bestChromosome.fitness
            );

            System.out.println();
            System.out.println(
                    "Results saved to the results/ directory."
            );

        } catch (Exception e) {

            System.err.println();
            System.err.println(
                    "ERROR while running simulation:"
            );

            e.printStackTrace();
        }
    }

    /* =========================================================
       CREATE DATACENTER
       ========================================================= */

    private static Datacenter createDatacenter() {

        hostList = new ArrayList<>();

        for (int hostId = 0;
             hostId < NUMBER_OF_HOSTS;
             hostId++) {

            List<Pe> peList = new ArrayList<>();

            for (int pe = 0;
                 pe < HOST_PES[hostId];
                 pe++) {

                peList.add(
                        new PeSimple(HOST_MIPS_PER_PE)
                );
            }

            Host host = new HostSimple(
                    HOST_RAM_MB[hostId],
                    HOST_BW_MBPS[hostId],
                    HOST_STORAGE_MB[hostId],
                    peList
            );

            /*
             * Use time-shared VM scheduling.
             */
            hostList.add(host);
        }

        Datacenter dc = new DatacenterSimple(
                simulation,
                hostList
        );

        System.out.println();
        System.out.println(
                "Datacenter created."
        );

        System.out.println(
                "Hosts: "
                        + hostList.size()
        );

        return dc;
    }

    /* =========================================================
       CREATE VMS
       ========================================================= */

    private static List<Vm> createVms() {

        List<Vm> list = new ArrayList<>();

        for (int i = 0;
             i < NUMBER_OF_VMS;
             i++) {

            Vm vm = new VmSimple(
                    VM_MIPS[i],
                    VM_PES[i]
            );

            vm.setRam(VM_RAM_MB[i])
                    .setBw(1_000)
                    .setSize(VM_SIZE_MB[i]);

            list.add(vm);

            System.out.printf(
                    "VM%d: %d PE, %.0f MIPS, %d MB RAM%n",
                    i + 1,
                    VM_PES[i],
                    VM_MIPS[i],
                    VM_RAM_MB[i]
            );
        }

        return list;
    }

    /* =========================================================
       CREATE CLOUDLETS
       ========================================================= */

    private static List<Cloudlet> createCloudlets(
            List<TaskData> taskDataList
    ) {

        List<Cloudlet> list = new ArrayList<>();

        for (TaskData task : taskDataList) {

            /*
             * Dynamic utilization model.
             * 100% CPU utilization during execution.
             */
            UtilizationModelDynamic utilizationModel =
                    new UtilizationModelDynamic(1.0);

            UtilizationModelDynamic ramUtilizationModel =
                    new UtilizationModelDynamic(
                            UtilizationModel.Unit.ABSOLUTE,
                            task.ramMB
                    );

            UtilizationModelDynamic bwUtilizationModel =
                    new UtilizationModelDynamic(
                            UtilizationModel.Unit.ABSOLUTE,
                            0
                    );

            Cloudlet cloudlet = new CloudletSimple(
                    task.lengthMI,
                    task.pes,
                    utilizationModel
            );

            cloudlet.setSizes(task.ramMB)
                    .setUtilizationModelRam(ramUtilizationModel)
                    .setUtilizationModelBw(bwUtilizationModel);

            /*
             * Cloudlet ID is generated automatically,
             * but we keep taskId from CSV for reporting.
             */
            list.add(cloudlet);
        }

        return list;
    }

    /* =========================================================
       APPLY GA MAPPING
       ========================================================= */

    private static void applyMapping(
            List<Cloudlet> cloudlets,
            List<Vm> vms,
            int[] genes
    ) {

        System.out.println();
        System.out.println(
                "Applying GA mapping to CloudSim Plus..."
        );

        for (int i = 0;
             i < cloudlets.size();
             i++) {

            int vmIndex = genes[i];

            if (vmIndex < 0 ||
                    vmIndex >= vms.size()) {

                throw new IllegalArgumentException(
                        "Invalid VM index: " + vmIndex
                );
            }

            Cloudlet cloudlet = cloudlets.get(i);
            Vm vm = vms.get(vmIndex);

            /*
             * Bind Cloudlet to the VM selected by GA.
             */
            cloudlet.setVm(vm);
        }

        System.out.println(
                "Mapping applied successfully."
        );
    }

    /* =========================================================
       LOAD CSV DATASET
       ========================================================= */

    private static List<TaskData> loadDataset(
            String filePath
    ) throws IOException {

        List<TaskData> tasks = new ArrayList<>();

        File file = new File(filePath);

        if (!file.exists()) {

            throw new IOException(
                    "Dataset not found: "
                            + file.getAbsolutePath()
            );
        }

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new FileReader(file)
                        )
        ) {

            String line;

            /*
             * Skip CSV header.
             */
            reader.readLine();

            while ((line = reader.readLine()) != null) {

                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                String[] parts =
                        line.split(",");

                if (parts.length < 5) {
                    continue;
                }

                int taskId =
                        Integer.parseInt(parts[0]);

                long lengthMI =
                        Long.parseLong(parts[1]);

                int pes =
                        Integer.parseInt(parts[2]);

                long ramMB =
                        Long.parseLong(parts[3]);

                int priority =
                        Integer.parseInt(parts[4]);

                tasks.add(
                        new TaskData(
                                taskId,
                                lengthMI,
                                pes,
                                ramMB,
                                priority
                        )
                );
            }
        }

        return tasks;
    }

    /* =========================================================
       MAKESPAN
       ========================================================= */

    private static double calculateMakespan(
            List<Cloudlet> cloudlets
    ) {

        if (cloudlets.isEmpty()) {
            return 0;
        }

        double maxFinishTime = 0;

        for (Cloudlet cloudlet : cloudlets) {

            if (cloudlet.isFinished()) {

                maxFinishTime = Math.max(
                        maxFinishTime,
                        cloudlet.getFinishTime()
                );
            }
        }

        return maxFinishTime;
    }

    /* =========================================================
       EXECUTION TIME
       ========================================================= */

    private static double calculateExecutionTime(
            List<Cloudlet> cloudlets
    ) {

        double total = 0;

        for (Cloudlet cloudlet : cloudlets) {

            if (cloudlet.isFinished()) {

                total += cloudlet.getActualCpuTime();
            }
        }

        return total;
    }

    /* =========================================================
       THROUGHPUT
       ========================================================= */

    private static double calculateThroughput(
            List<Cloudlet> cloudlets,
            double makespan
    ) {

        if (makespan <= 0) {
            return 0;
        }

        return cloudlets.size() / makespan;
    }

    /* =========================================================
       VM UTILIZATION
       ========================================================= */

    private static double calculateAverageCpuUtilization(
                        List<Cloudlet> cloudlets,
                        List<Vm> vms,
                        double makespan
    ) {

                if (vms.isEmpty() || makespan <= 0) {
            return 0;
        }

                double busyPeTime = 0;
                long totalPes = 0;

                for (Cloudlet cloudlet : cloudlets) {

                        busyPeTime +=
                                        cloudlet.getActualCpuTime()
                                                        * cloudlet.getNumberOfPes();
        }

                for (Vm vm : vms) {
                        totalPes += vm.getNumberOfPes();
                }

                return totalPes == 0
                                ? 0
                                : busyPeTime / (makespan * totalPes) * 100.0;
    }

    /* =========================================================
       ENERGY
       ========================================================= */

    private static double calculateEnergy(
            List<Host> hosts
    ) {

        double totalEnergy = 0;

        for (Host host : hosts) {

            /*
             * Host power consumption is estimated
             * from CPU utilization.
             *
             * 120 W static
             * 250 W maximum
             */

            final double staticPower = 120.0;
            final double maxPower = 250.0;

            double utilization =
                    host.getCpuPercentUtilization();

            utilization =
                    Math.max(
                            0,
                            Math.min(
                                    1,
                                    utilization
                            )
                    );

            double power =
                    staticPower
                            + (maxPower - staticPower)
                            * utilization;

            /*
             * Use simulation clock as elapsed seconds.
             */
            double time =
                    simulation.clock();

            /*
             * Watt × second = Joule.
             */
            totalEnergy +=
                    power * time;
        }

        return totalEnergy;
    }

    /* =========================================================
       PRINT RESULTS
       ========================================================= */

    private static void printResults(
            List<Cloudlet> finishedCloudlets,
            double makespan,
            double executionTime,
            double throughput,
            double utilization,
            double energy
    ) {

        System.out.println();
        System.out.println(
                "================================================="
        );

        System.out.println(
                " CLOUDSIM PLUS SIMULATION RESULTS"
        );

        System.out.println(
                "================================================="
        );

        System.out.printf(
                "Finished Cloudlets     : %d%n",
                finishedCloudlets.size()
        );

        System.out.printf(
                "Makespan               : %.4f s%n",
                makespan
        );

        System.out.printf(
                "Execution Time         : %.4f s%n",
                executionTime
        );

        System.out.printf(
                "Energy Consumption     : %.4f J%n",
                energy
        );

        System.out.printf(
                "Average CPU Utilization: %.2f%%%n",
                utilization
        );

        System.out.printf(
                "Throughput             : %.4f task/s%n",
                throughput
        );

        System.out.println(
                "================================================="
        );
    }

    /* =========================================================
       SAVE MAPPING
       ========================================================= */

    private static void saveMapping(
            List<Cloudlet> cloudlets,
            int[] genes
    ) throws IOException {

        File directory =
                new File("results");

        if (!directory.exists()) {
            directory.mkdirs();
        }

        File file =
                new File(
                        directory,
                        "ga_mapping.csv"
                );

        try (
                FileWriter writer =
                        new FileWriter(file)
        ) {

            writer.write(
                    "cloudletId,vmId\n"
            );

            for (int i = 0;
                 i < cloudlets.size();
                 i++) {

                writer.write(
                        cloudlets.get(i).getId()
                                + ","
                                + (genes[i] + 1)
                                + "\n"
                );
            }
        }
    }

    /* =========================================================
       SAVE METRICS
       ========================================================= */

    private static void saveMetrics(
            double makespan,
            double energy,
            double executionTime,
            double utilization,
            double throughput,
            double fitness
    ) throws IOException {

        File directory =
                new File("results");

        if (!directory.exists()) {
            directory.mkdirs();
        }

        File file =
                new File(
                        directory,
                        "metrics.txt"
                );

        try (
                FileWriter writer =
                        new FileWriter(file)
        ) {

            writer.write(
                    "Cloud Task Scheduling - Genetic Algorithm\n"
            );

            writer.write(
                    "==========================================\n"
            );

            writer.write(
                    String.format(
                            "Fitness: %.6f%n",
                            fitness
                    )
            );

            writer.write(
                    String.format(
                            "Makespan: %.6f s%n",
                            makespan
                    )
            );

            writer.write(
                    String.format(
                            "Energy Consumption: %.6f J%n",
                            energy
                    )
            );

            writer.write(
                    String.format(
                            "Execution Time: %.6f s%n",
                            executionTime
                    )
            );

            writer.write(
                    String.format(
                            "Average CPU Utilization: %.2f%%%n",
                            utilization
                    )
            );

            writer.write(
                    String.format(
                            "Throughput: %.6f task/s%n",
                            throughput
                    )
            );
        }
    }

    /* =========================================================
       CHROMOSOME
       ========================================================= */

    private static class Chromosome {

        int[] genes;

        double fitness =
                Double.POSITIVE_INFINITY;

        double makespan =
                Double.POSITIVE_INFINITY;

        double energy =
                Double.POSITIVE_INFINITY;

        Chromosome(int numberOfTasks) {

            genes =
                    new int[numberOfTasks];
        }

        Chromosome copy() {

            Chromosome copy =
                    new Chromosome(
                            genes.length
                    );

            System.arraycopy(
                    genes,
                    0,
                    copy.genes,
                    0,
                    genes.length
            );

            copy.fitness = fitness;
            copy.makespan = makespan;
            copy.energy = energy;

            return copy;
        }
    }

    /* =========================================================
       GENETIC ALGORITHM
       ========================================================= */

    private static class GeneticAlgorithm {

        private final List<Cloudlet> cloudlets;
        private final List<Vm> vms;

        private final int populationSize;
        private final int maxGenerations;
        private final double mutationRate;

        private final Random random;

        private double baselineMakespan;
        private double baselineEnergy;

        GeneticAlgorithm(
                List<Cloudlet> cloudlets,
                List<Vm> vms,
                int populationSize,
                int maxGenerations,
                double mutationRate,
                long seed
        ) {

            this.cloudlets = cloudlets;
            this.vms = vms;

            this.populationSize =
                    populationSize;

            this.maxGenerations =
                    maxGenerations;

            this.mutationRate =
                    mutationRate;

            this.random =
                    new Random(seed);

            /*
             * Baseline values are calculated
             * from a simple round-robin mapping.
             */
            Chromosome baseline =
                    createRoundRobinChromosome();

            evaluate(baseline);

            baselineMakespan =
                    baseline.makespan;

            baselineEnergy =
                    baseline.energy;

            /*
             * Prevent division by zero.
             */
            if (baselineMakespan <= 0) {
                baselineMakespan = 1;
            }

            if (baselineEnergy <= 0) {
                baselineEnergy = 1;
            }
        }

        /* =====================================================
           RUN GA
           ===================================================== */

        Chromosome run() {

            List<Chromosome> population =
                    initializePopulation();

            Chromosome globalBest = null;

            for (int generation = 0;
                 generation < maxGenerations;
                 generation++) {

                /*
                 * Evaluate every chromosome.
                 */
                for (Chromosome chromosome :
                        population) {

                    evaluate(chromosome);
                }

                /*
                 * Sort by fitness.
                 * Lower fitness = better.
                 */
                population.sort(
                        Comparator.comparingDouble(
                                c -> c.fitness
                        )
                );

                Chromosome currentBest =
                        population.get(0);

                if (
                        globalBest == null
                                ||
                        currentBest.fitness
                                < globalBest.fitness
                ) {

                    globalBest =
                            currentBest.copy();
                }

                System.out.printf(
                        "Generation %02d | Best Fitness = %.6f | Makespan = %.4f | Energy = %.4f%n",
                        generation + 1,
                        currentBest.fitness,
                        currentBest.makespan,
                        currentBest.energy
                );

                /*
                 * Create next generation.
                 */
                List<Chromosome> nextPopulation =
                        new ArrayList<>();

                /*
                 * ELITISM:
                 * Keep best chromosome.
                 */
                nextPopulation.add(
                        currentBest.copy()
                );

                /*
                 * Fill remaining population.
                 */
                while (
                        nextPopulation.size()
                                < populationSize
                ) {

                    Chromosome parent1 =
                            tournamentSelection(
                                    population
                            );

                    Chromosome parent2 =
                            tournamentSelection(
                                    population
                            );

                    Chromosome[] children =
                            crossover(
                                    parent1,
                                    parent2
                            );

                    mutate(children[0]);
                    mutate(children[1]);

                    nextPopulation.add(
                            children[0]
                    );

                    if (
                            nextPopulation.size()
                                    < populationSize
                    ) {

                        nextPopulation.add(
                                children[1]
                        );
                    }
                }

                population =
                        nextPopulation;
            }

            /*
             * Final evaluation.
             */
            for (Chromosome chromosome :
                    population) {

                evaluate(chromosome);
            }

            population.sort(
                    Comparator.comparingDouble(
                            c -> c.fitness
                    )
            );

            if (
                    population.get(0).fitness
                            < globalBest.fitness
            ) {

                globalBest =
                        population.get(0).copy();
            }

            return globalBest;
        }

        /* =====================================================
           INITIAL POPULATION
           ===================================================== */

        private List<Chromosome>
        initializePopulation() {

            List<Chromosome> population =
                    new ArrayList<>();

            for (int i = 0;
                 i < populationSize;
                 i++) {

                Chromosome chromosome =
                        new Chromosome(
                                cloudlets.size()
                        );

                for (int gene = 0;
                     gene < chromosome.genes.length;
                     gene++) {

                    List<Integer> validVmIndexes =
                            getValidVmIndexes(gene);

                    chromosome.genes[gene] =
                            validVmIndexes.get(
                                    random.nextInt(
                                            validVmIndexes.size()
                                    )
                            );
                }

                population.add(
                        chromosome
                );
            }

            return population;
        }

        /* =====================================================
           ROUND ROBIN BASELINE
           ===================================================== */

        private Chromosome
        createRoundRobinChromosome() {

            Chromosome chromosome =
                    new Chromosome(
                            cloudlets.size()
                    );

            for (int i = 0;
                 i < chromosome.genes.length;
                 i++) {

                List<Integer> validVmIndexes =
                        getValidVmIndexes(i);

                chromosome.genes[i] =
                        validVmIndexes.get(
                                i % validVmIndexes.size()
                        );
            }

            return chromosome;
        }

        /* =====================================================
           FITNESS
           ===================================================== */

        private void evaluate(
                Chromosome chromosome
        ) {

            /*
             * Calculate estimated makespan
             * without starting CloudSim.
             *
             * Each VM has a queue of workloads.
             */
            double[] vmFinishTimes =
                    new double[vms.size()];

            for (int i = 0;
                 i < cloudlets.size();
                 i++) {

                Cloudlet cloudlet =
                        cloudlets.get(i);

                int vmIndex =
                        chromosome.genes[i];

                Vm vm =
                        vms.get(vmIndex);

                double vmMips =
                        vm.getMips();

                long vmPes =
                        vm.getNumberOfPes();

                long taskPes =
                        cloudlet.getNumberOfPes();

                /*
                 * Resource feasibility:
                 * Task PE requirement must not
                 * exceed VM PE capacity.
                 */
                if (taskPes > vmPes) {

                    chromosome.fitness =
                            Double.POSITIVE_INFINITY;

                    return;
                }

                /*
                 * Approximate execution time.
                 *
                 * MI / (MIPS × allocated PE)
                 */
                double effectiveMips =
                        vmMips
                                * Math.min(
                                taskPes,
                                vmPes
                        );

                double executionTime =
                        cloudlet.getLength()
                                / effectiveMips;

                vmFinishTimes[vmIndex] +=
                        executionTime;
            }

            double makespan = 0;

            for (double finishTime :
                    vmFinishTimes) {

                makespan =
                        Math.max(
                                makespan,
                                finishTime
                        );
            }

            /*
             * Approximate energy.
             *
             * 120 W static
             * 250 W maximum
             *
             * We estimate energy based on
             * busy time of each VM.
             */
            double energy = 0;

            for (double finishTime :
                    vmFinishTimes) {

                double utilization;

                if (makespan <= 0) {
                    utilization = 0;
                } else {
                    utilization =
                            finishTime
                                    / makespan;
                }

                double power =
                        120
                                + (250 - 120)
                                * utilization;

                energy +=
                        power
                                * finishTime;
            }

            double normalizedMakespan =
                    makespan
                            / baselineMakespan;

            double normalizedEnergy =
                    energy
                            / baselineEnergy;

            double fitness =
                    MAKESPAN_WEIGHT
                            * normalizedMakespan
                            +
                            ENERGY_WEIGHT
                                    * normalizedEnergy;

            chromosome.makespan =
                    makespan;

            chromosome.energy =
                    energy;

            chromosome.fitness =
                    fitness;
        }

        /* =====================================================
           TOURNAMENT SELECTION
           ===================================================== */

        private Chromosome tournamentSelection(
                List<Chromosome> population
        ) {

            Chromosome candidate1 =
                    population.get(
                            random.nextInt(
                                    population.size()
                            )
                    );

            Chromosome candidate2 =
                    population.get(
                            random.nextInt(
                                    population.size()
                            )
                    );

            if (
                    candidate1.fitness
                            < candidate2.fitness
            ) {

                return candidate1;

            } else {

                return candidate2;
            }
        }

        /* =====================================================
           ONE-POINT CROSSOVER
           ===================================================== */

        private Chromosome[] crossover(
                Chromosome parent1,
                Chromosome parent2
        ) {

            Chromosome child1 =
                    parent1.copy();

            Chromosome child2 =
                    parent2.copy();

            int length =
                    parent1.genes.length;

            if (length <= 1) {

                return new Chromosome[]{
                        child1,
                        child2
                };
            }

            int crossoverPoint =
                    1 + random.nextInt(
                            length - 1
                    );

            for (int i =
                 crossoverPoint;
                 i < length;
                 i++) {

                child1.genes[i] =
                        parent2.genes[i];

                child2.genes[i] =
                        parent1.genes[i];
            }

            /*
             * Fitness needs to be recalculated.
             */
            child1.fitness =
                    Double.POSITIVE_INFINITY;

            child2.fitness =
                    Double.POSITIVE_INFINITY;

            return new Chromosome[]{
                    child1,
                    child2
            };
        }

        /* =====================================================
           MUTATION
           ===================================================== */

        private void mutate(
                Chromosome chromosome
        ) {

            for (int i = 0;
                 i < chromosome.genes.length;
                 i++) {

                if (
                        random.nextDouble()
                                < mutationRate
                ) {

                    List<Integer> validVmIndexes =
                            getValidVmIndexes(i);

                    chromosome.genes[i] =
                            validVmIndexes.get(
                                    random.nextInt(
                                            validVmIndexes.size()
                                    )
                            );
                }
            }

            chromosome.fitness =
                    Double.POSITIVE_INFINITY;
        }

        private List<Integer> getValidVmIndexes(
                int cloudletIndex
        ) {

            long requiredPes =
                    cloudlets.get(cloudletIndex)
                            .getNumberOfPes();

            List<Integer> validVmIndexes =
                    new ArrayList<>();

            for (int vmIndex = 0;
                 vmIndex < vms.size();
                 vmIndex++) {

                if (vms.get(vmIndex).getNumberOfPes()
                        >= requiredPes) {

                    validVmIndexes.add(vmIndex);
                }
            }

            if (validVmIndexes.isEmpty()) {
                throw new IllegalStateException(
                        "No VM satisfies PE requirement for cloudlet "
                                + cloudletIndex
                );
            }

            return validVmIndexes;
        }
    }
}
