package org.example;


import java.io.FileWriter;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
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

    private static String pathFileAccess = "/mypool/myfs/";
    private static int numberOfThreads = 3;
    private static int numberOfFiles = 6;
    private static double meanWritingTime = 2000; // milliseconds
    private static double stVarWritingTime = 1500; // milliseconds

    private static int seed=42;
    private static Random random = new Random(seed);


    private static int numberOfIterations = 50;
    private static List<Integer> threadParameters= new LinkedList<>(List.of(3,6,3,6,2,3,2,3));
    private static List<Integer> numberOfFilesParameters= new LinkedList<>(List.of(9,9,12,12,5,5,7,7));
    private static List<Integer> meanWritingTimeParameters= new LinkedList<>(List.of(500,500,2000,2000,500,500,2000,2000));
    private static List<Integer> stVarWritingTimeParameters= new LinkedList<>(List.of(200,200,1500,1500,200,200,1500,1500));

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
        ZFSMapper.deleteAllSnapshot();
        for (int i = 0; i < threadParameters.size(); i++) {
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
            // Randomly select a file to write to
            String fileName = "file" + random.nextInt(numberOfFiles) + ".txt";
            String content = generateRandomString(10000);

            // Notify ZFSMapper about the writing start
            TransactionInformation transactionInformation= ZFSMapper.notifyWrite(threadName, fileName);

            // Simulate writing time using Gaussian distribution
            long writingTime = (long) Math.max(100, random.nextGaussian() * stVarWritingTime + meanWritingTime);
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
        String fileName = "validatorResultsZFSSnapshots.csv";

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

    private static final String CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(CHAR_POOL.length());
            sb.append(CHAR_POOL.charAt(index));
        }
        return sb.toString();
    }









}
