package org.example;


import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class attempts to test the ZFS approach by having multiple threads work on files.
 * It will be parametrized how many threads are spawned, and how long they take for writing to files
 *
 * Therefore a set of files is instantiated on which the threads will work
 * A thread will randomly select a file and attempt to write to the file where the amount of time he spends on writing will be
 * given by a gaussian distribution to which mean and stVar are parameters
 */

public class Validator {

    private static int numberOfThreads = 3;
    private static int numberOfFiles = 6;
    private static double meanWritingTime = 2000; // milliseconds
    private static double stVarWritingTime = 1500; // milliseconds




    private static final int numberOfIterations = 150;
    private static final List<Integer> threadParameters= new LinkedList<>(List.of(3,3,3,3,10,10,30,30,50,50));
    private static final List<Integer> numberOfFilesParameters= new LinkedList<>(List.of(9,9,9,9,100,500,300,1500,500,2500));
    private static final List<Integer> meanWritingTimeParameters= new LinkedList<>(List.of(2000,2000,600,200,600,600,600,600,600,600));
    private static final List<Integer> stVarWritingTimeParameters= new LinkedList<>(List.of(200,1800,400,100,400,400,400,400,400,400));

    private static List<Integer> transactionsAttempted= new LinkedList<>();
    private static List<Integer> succesfullWrites= new LinkedList<>();
    private static List<Integer> conflictsEncounteredBySingleTransactions= new LinkedList<>();
    private static List<Integer> rollbacks = new LinkedList<>();
    private static List<Double> meanRollbackTimes = new LinkedList<>();
    private static List<Double> conflictRates= new LinkedList<>();


    // Metrics Collection
    private static AtomicInteger conflictCounter = new AtomicInteger(0);
    private static AtomicInteger successCounter = new AtomicInteger(0);
    private static AtomicInteger rollbackCounter = new AtomicInteger(0);



    private static void initSimulation(int iteration){

        numberOfThreads= threadParameters.get(iteration);
        numberOfFiles= numberOfFilesParameters.get(iteration);
        meanWritingTime= meanWritingTimeParameters.get(iteration);
        stVarWritingTime= stVarWritingTimeParameters.get(iteration);
        conflictCounter= new AtomicInteger(0);
        successCounter= new AtomicInteger(0);
        rollbackCounter= new AtomicInteger(0);

        System.out.println("########################################\n" +
                "\nStarting Simulation Iteration "+iteration+" with Parameters \n" +
                "Iterations per thread (same accross sims)= "+ numberOfIterations+"\n"+
                "numberOfThreads= "+numberOfThreads+"\n" +
                "numberOfFiles= "+ numberOfFiles+"\n"+
                "meanWritingTime= "+ meanWritingTime+"ms\n"+
                "stDWritingTime="+ stVarWritingTime+"ms\n"+
                "########################################");

        createFiles();

    }

    public static void main(String[] args) {


        // Delete possible remaining Snapshots from testing and debug, create files for the simulation

        for (int i = 0; i < threadParameters.size(); i++) {
            ZFSMapper.deleteAllSnapshot();
            initSimulation(i);


            // Start threads for concurrent access
            List<Thread> threads = new ArrayList<>();
            for (int y = 0; y < numberOfThreads; y++) {
                String threadName = "Thread-" + y;
                Thread thread = new Thread(() -> runTransactions(threadName));
                threads.add(thread);
                thread.start();
            }

            // Wait for all threads to finish
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Display metrics after simulation
            printMetrics();
        }

        writeToCSV();
    }



    /**
     * Creates a set of files on which threads will work.
     */
    private static void createFiles() {
        for (int i = 0; i < numberOfFiles; i++) {
            String fileName = "file" + i + ".txt";
            ZFSMapper.createFileWithContent(fileName, "Initial content of " + fileName);
        }
    }

    /**
     * Executes transactions for each thread
     * @param threadName The name of the thread
     */
    private static void runTransactions(String threadName) {

        for (int i = 0; i < numberOfIterations; i++) {
            if((i+1)%50==0){
                System.out.println("Iter "+ i+" reached on " + Thread.currentThread().getName());
            }
            // Randomly select a file to write to
            int file = ThreadLocalRandom.current().nextInt(0,numberOfFiles);
            String fileName = "file" + file + ".txt";
            String content = threadName+" writes on iteration "+ i;

            // Notify ZFSMapper about the writing start
            TransactionInformation transactionInformation= ZFSMapper.notifyWrite(threadName, fileName);

            // Simulate writing time using Gaussian distribution
            long writingTime = (long) Math.max(100, ThreadLocalRandom.current().nextGaussian() * stVarWritingTime + meanWritingTime);
            try {
                Thread.sleep(writingTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            //0 = success 1=conflict but solved somewhere else 2=rollback
            int code= ZFSMapper.appendToFile(transactionInformation, content);
            switch(code){
                case 0:{
                    successCounter.incrementAndGet();
                    break;
                }
                case 1:{
                    conflictCounter.incrementAndGet();
                    break;
                }
                case 2:{
                    conflictCounter.incrementAndGet();
                    rollbackCounter.incrementAndGet();
                    break;
                }
            }
        }
    }

    /**
     * Prints metrics of the simulation
     */
    private static void printMetrics() {


        System.out.println("Simulation Metrics:");
        System.out.println("-------------------");
        System.out.println("Conflicts = affected by another rollback || found conflict\n" +
                "(Successfull Writes) actual successfull writes but also those that were rollback" +
                "\n conflict rate = conflicts / (total transactions attempted)");
        System.out.println("Total Transactions Attempted: " + (numberOfThreads * numberOfIterations));
        System.out.println("Successful Writes (including rollback affected): " + successCounter.get());
        System.out.println("Conflicts Occurred: " + conflictCounter.get());
        System.out.println("Rollbacks Executed: " + rollbackCounter.get());
        double meanRollbackTime= ZFSMapper.calculateMeanTime();
        System.out.println("Mean rollback Time in ms: "+ meanRollbackTime);
        double conflictRate = ((double) conflictCounter.get() / (numberOfThreads * numberOfIterations)) * 100;
        System.out.println("Conflict Rate: " + conflictRate + "%");

        transactionsAttempted.add((numberOfThreads * numberOfIterations));
        succesfullWrites.add(successCounter.get());
        conflictsEncounteredBySingleTransactions.add(conflictCounter.get());
        rollbacks.add(rollbackCounter.get());
        meanRollbackTimes.add(meanRollbackTime);
        conflictRates.add(conflictRate);
    }


    private static void writeToCSV() {
        String fileName = "validatorResultsZFSSnapshots_1.csv";

        try (FileWriter writer = new FileWriter(fileName)) {
            // Write header with iteration numbers
            writer.append("Name");
            for (int i = 1; i <= threadParameters.size(); i++) {
                writer.append(",Iteration").append(String.valueOf(i));
            }
            writer.append("\n");

            // Write parameters
            writeListToCSV(writer, "threads", threadParameters);
            writeListToCSV(writer, "numberOfFiles", numberOfFilesParameters);
            writeListToCSV(writer, "meanWritingTime", meanWritingTimeParameters);
            writeListToCSV(writer, "stVarWritingTime", stVarWritingTimeParameters);

            // Write results
            writeListToCSV(writer, "TA_Attempted", transactionsAttempted);
            writeListToCSV(writer, "success_Writes", succesfullWrites);
            writeListToCSV(writer, "conflicts", conflictsEncounteredBySingleTransactions);
            writeListToCSV(writer, "rollbacks", rollbacks);
            writeListToCSV(writer, "mean_Rollback", meanRollbackTimes);
            writeListToCSV(writer, "conflict_Rate", conflictRates);

            writer.flush();
            System.out.println("CSV file created successfully: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static <T> void writeListToCSV(FileWriter writer, String name, List<T> list) throws IOException {
        writer.append(name);
        for (T value : list) {
            writer.append(",").append(String.valueOf(value));
        }
        writer.append("\n");
    }


}
