package org.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


/**
 * this class implements basic ZFS commands as well as File Accesses
 */
public class ZFSMapper {

    /**
     * all files for this project to run should be inside the zpool and zfs shown here
     */
    private static String pathFileAccess = "/mypool/myfs/";

    /**
     * zfs should be located here
     */
    private static String pathZFSCommands = "mypool/myfs";

    /**
     * saves transaction informations, key = snapshotname, transaction contains thread name, file name, last modified
     * effectively a mirror of the current zfs snapshots, as a snapshots corresponds to the time a thread started modifying a file
     * this holds the same content as the zfs snapshots with timestamp, fileName and threadName
     */
    private static Map<String,TransactionInformation> transactions = new HashMap<>();
    /**
     * collects the timings of Rollbacks in ms
     */
    public static List<Long> timings= new LinkedList<>();



    /**
     * basic command method used to execute basic commands where output is not needed
     * @param command
     */
    public synchronized static void doCommand(String command) {
        try {
            // Create process and execute command
            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
            processBuilder.redirectErrorStream(true);  // Merge stdout and stderr
            Process process = processBuilder.start();

            // Read the output and error streams
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

        } catch (IOException e) {
            System.err.println("Failure on command: " + command);
            e.printStackTrace();
        }
    }




    /**
     * rollback logic to a certain snapshot. When this is called, a conflict was recognized.
     * As the transactions map represents
     *  1. the modified timestamps when a thread started its modification
     *  2. and a missing transaction e.g. snapshots indicates that a rollback has occured that effected newer snapshots
     *  the transactions map is updated with the remaining snapshots after calling zfs rollback -r
     *  the rollback command along with the flag -r ensures that the rollback to a certain snapshot is successfull even if newer snapshots are
     *  available. ZFS default prohibits rolling back to older snapshots when newer snapshots exist. However when it is desired
     *  anyway then the flag -r  ensures that the newer snapshots that are affected by the rollback are deleted as well.
     *  As this implements a tree like inheritance logic i did not try to model the behaviour in code for my transactions map but instead just copy
     *  the snapshots that remain afterwards because these are snapshots of transactions that are not affected by the rollback and which can still
     *  finish their work
     *
     * @param transactionInformation holds the information from the transaction that encountered a modified timestamp and therefore a conflict
     *                               this holds the snapshot name to which the rollback should be executed
     */
    public static void rollbackToSnapshot(TransactionInformation transactionInformation) {
        //System.out.println("Attempting rollback on "+ transactionInformation.getSnapshotName());
        String path = pathZFSCommands + "@" + transactionInformation.getSnapshotName();
        //System.out.println("rolling back on path: "+ path);
        doCommand("sudo zfs rollback -r " + path);
        deleteSnapshot(transactionInformation.getSnapshotName());
        Set<String> remainingSnapshot= getAllSnapshots();
        Set<String> toDelete = transactions.keySet().stream().filter(key -> !remainingSnapshot.contains(key)).collect(Collectors.toSet());
        toDelete.forEach(key -> transactions.remove(key));
        //System.out.println("showing remaining snapshots");
        //showSnapshots();
        //System.out.println("showing remaining transactions saved");
        //transactions.keySet().forEach(System.out::println);
        //System.out.println("end showing");
        Timer.stop();
        //System.out.println("TIMER Appending "+Timer.getElapsedTimeMillis());
        timings.add(Timer.getElapsedTimeMillis());
    }





    /**
     * the last modified timestamp
     * @param fileName well, the fileName
     * @return the last modified timestamp of the file
     */
    private static long getLastModified(String fileName) {
        String path = pathFileAccess + fileName;
        File file = new File(path);

        // Get the last modified time in milliseconds
        long lastModified = file.lastModified();

        if (lastModified == 0) {
            System.err.println("Error: The file does not exist or cannot retrieve the last modified time.");
            return -1; // Return -1 in case of error (file not found)
        }

        return lastModified;
    }


    /**
     * As i simulate long writing, e.g. a user opens a file and writes sth, this
     * methods corresponds to the opening of a file. The ZFSMapper registers this and gives a initiates a transaction
     * saving the current lastModified timestamp and a Snapshot to which i refer when later after simulating user usage
     * content is commited to the file
     * @param threadName well, the threadName
     * @param fileName well, the fileName
     * @return the transaction information is returned to ensure that the snapshot name is constructed only once and is referred to later
     * only alongside this object, and yes i did rebuild it wrong once
     */
    public static synchronized TransactionInformation notifyWrite(String threadName, String fileName){

        String snapshotName= threadName+"-"+ fileName.substring(0,fileName.length()-4);
        long lastModified= getLastModified(fileName);
        createSnapshot(snapshotName);
        TransactionInformation transactionInformation= new TransactionInformation(threadName, fileName, snapshotName, lastModified);
        transactions
                .put(
                        snapshotName,
                        transactionInformation
                );
        return transactionInformation;
    }

    /**
     * when the user usage is simulated e.g. the thread awakes after waiting time in exercise 4 or continues execution after waiting in exercise 3
     * then I attempt to append some content to the file.
     * @param transactionInformation
     * @param content
     * @return
     */
    public static synchronized int appendToFile(TransactionInformation transactionInformation, String content) {


        //checks if the snapshot for this transaction is still existing, if not than we had a rollback to an older snapshot
        //which affected this snapshot
        if(!transactions.containsKey(transactionInformation.getSnapshotName())){
            //System.out.println(transactionInformation.getThreadName()+" aborts as its snapshot was deleted");
            return 1;
        }

        //if the snapshot is still existing, i check if the file had been modified, e.g. changes were made as only
        //true changes alter the lastmodified timestamp
        //if so rollback
        long lastModified= getLastModified(transactionInformation.getFileName());
        if(lastModified != transactionInformation.getFileLastModified()){

            Timer.start();
            //System.out.println(transactionInformation.getThreadName()+ ": caused rollback on snapshot"+ transactionInformation.getSnapshotName());
            rollbackToSnapshot(transactionInformation);
            // file wurde modifziert während ich zugange war, jetzt rollback
            //problem, ander threads könnten auch noch am schreiben sein
            //  - prozesse die ebenfalls begonnen haben zu schrieben vor lastModified können aufhören da Änderung eh nicht übernommen wird
            //  - prozesse die nach lastModified begonnen haben sind ja so gesehen auch von rollback betroffen und müssen aufhören zu schreiben
            //  - rollback führt
            return 2;
        }


        //no conflicts were encountered, therefore the content can be written to the file
        //the snapshot for this transaction is therefore no longer needed and deleted.
        //as transactions is a programm intern mirror of the remaining snapshots the entry is deleted here as well
        doCommand("echo '"+content+"' >> "+ pathFileAccess +transactionInformation.getFileName()
        );
        //System.out.println(transactionInformation.getThreadName()+": appended to file "+ transactionInformation.getFileName());
        deleteSnapshot(transactionInformation.getSnapshotName());
        transactions.remove(transactionInformation.getSnapshotName());
        return 0;
    }

    /**
     * creates a File with Content
     * @param fileName well, the fileName
     * @param fileContent well, the file Content
     */
    public static void createFileWithContent(String fileName, String fileContent){
        doCommand("echo '"+fileContent+"' > "+ pathFileAccess +fileName);
    }

    /**
     * creates a zfs snapshot
     * @param nameOfSnapshot the name of the snapshot
     */
    public static void createSnapshot(String nameOfSnapshot){
        String path= pathZFSCommands +"@"+nameOfSnapshot;
        doCommand("sudo zfs snapshot "+path);
        //System.out.println("created snapshot "+ nameOfSnapshot);
    }

    /**
     * deletes a zfs snapshot according to the name, the path is constructed using the basePath
     * @param nameOfSnapshot the name of the snapshot
     */
    public static synchronized void deleteSnapshot(String nameOfSnapshot){
        String path= pathZFSCommands +"@"+nameOfSnapshot;
        doCommand("sudo zfs destroy "+path);
    }

    /**
     * deletes all snapshot, usually used on initialization if some snapshots are remaining from testing
     * avoids collision due to same name conflicts
     */
    public static void deleteAllSnapshot(){
        doCommand("sudo zfs destroy "+pathZFSCommands+"@%");
    }


    /**
     * gets all Files at the base path, only used to build the file options for exercise 3, therefore a Integer e.g. fileoption number is saved
     * here as well
     * @return the fileOptions
     */
    public static Map<Integer,String> getAllFiles(){
        Map<Integer,String> fileOptions= new HashMap<>();
        int counter=1;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(pathFileAccess))) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    // Print the name of the file (without the full path)
                    System.out.println(entry.getFileName());
                    fileOptions.put(counter++, String.valueOf(entry.getFileName()));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileOptions;
    }


    /**
     * this gets all snapshot names and is needed for the filling of the transaction map after a rollback -r command was executed as
     * described for the rollbackToSnapshot method
     * @return all names of the current ZFS snapshots
     */
    public static Set<String> getAllSnapshots(){
        Set<String> allSnapshot= new HashSet<>();
        try {
            // Run the "zfs list -t snapshot" command
            ProcessBuilder processBuilder = new ProcessBuilder("sudo","zfs", "list", "-t", "snapshot");
            Process process = processBuilder.start();

            // Capture the output of the command
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                // Split the line and extract the snapshot name (assuming the name is the first column)
                String[] parts = line.split("\\s+");
                if (parts.length > 0) {
                    StringTokenizer tokenizer= new StringTokenizer(parts[0], "@");
                    if(tokenizer.countTokens()==1)
                        continue;
                    tokenizer.nextToken();
                    String name= tokenizer.nextToken();
                    allSnapshot.add(name);
                }
            }
            return allSnapshot;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new HashSet<>();
    }

    /**
     * DEBUG: showing all Snapshots
     */
    public static void showSnapshots() {
        // Run zfs command to show snapshots
        String command = "sudo zfs list -t snapshot";
        doCommand(command);
    }

    /**
     * DEBUG: showing all Files
     */
    public static void showFiles() {
        // Run ls command to list files in the ZFS mounted directory
        String command = "ls -l " + pathFileAccess;
        doCommand(command);
    }

    /**
     * DEBUG/TESTING: removes the file
     * @param fileName well, the fileName
     */
    public static void removeFile(String fileName){
        String command = "sudo rm "+ fileName;
        doCommand(command);
    }

    /**
     * DEBUG/TESTING: shows the file content
     * @param fileName well, the fileName
     */
    public static void showFileContent(String fileName) {
        // Run ls command to list files in the ZFS mounted directory
        System.out.println("showing content of "+ fileName);
        String command = "cat " + pathFileAccess+fileName;
        doCommand(command);
    }

    /**
     * basic mean approximation of values
     * @return mean of rollbackTimes in ms
     */
    public static double calculateMeanTime() {
        if (timings.isEmpty()) {
            System.out.println("Times taken is empty");
            return 0;
        }

        long sum = 0;
        for (Long time : timings) {
            sum += time;
        }

        return (double) sum / timings.size();
    }

    /**
     * most basic java Timer using System.nanoTime
     */
    private static class Timer {
        private static long startTime;
        private static long endTime;

        public static void start() {
            startTime = System.nanoTime();
        }

        public static void stop() {
            endTime = System.nanoTime();
        }
        public static long getElapsedTimeNanos() {
            return endTime - startTime;
        }


        public static long getElapsedTimeMillis() {
            return (endTime - startTime) / 1_000_000;
        }

        public static long getElapsedTimeSeconds() {
            return (endTime - startTime) / 1_000_000_000;
        }
    }








}
