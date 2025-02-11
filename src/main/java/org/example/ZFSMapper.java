package org.example;

import jdk.jfr.Description;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZFSMapper {

    private static String pathFileAccess = "/mypool/myfs/";
    private static String pathZFSCommands = "mypool/myfs";

    /**
     * speichert threadnamen mit all ihren snapshots und transaktionsinformationen
     */
    private static Map<String, Map<String,TransactionInformation>> transactions = new HashMap<>(new HashMap<>());

    /**
     *
     */

    private static boolean rollbackIsActive=false;
    private static long snapshotCounter = 0;

    public synchronized static void doCommand(String command, String successMessage) {
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

            if (!successMessage.isEmpty()) {
                System.out.println(successMessage);
            }

        } catch (IOException e) {
            System.err.println("Failure on command: " + command);
            e.printStackTrace();
        }
    }



    @Description("provide a file name with datatype")
    public static void createFileWithContent(String name, String content){
        doCommand("echo '"+content+"' > "+ pathFileAccess +name, "File "+ pathFileAccess +name+" was created");
    }




    public static void createSnapshot(String nameOfSnapshot){
        String path= pathZFSCommands +"@"+nameOfSnapshot;
        doCommand("sudo zfs snapshot "+path,"snapshot "+path+" was created");
    }

    public static void deleteSnapshot(String nameOfSnapshot){
        String path= pathZFSCommands +"@"+nameOfSnapshot;
        doCommand("sudo zfs destroy "+path,"snapshot "+ path+" was destroyed ");
    }

    public static void rollbackToSnapshot(String nameOfSnapshot) {
        rollbackIsActive=true;
        String path = pathZFSCommands + "@" + nameOfSnapshot;
        doCommand("sudo zfs rollback " + path,
                "Rollback to snapshot " + path + " was successful"
        );
        rollbackIsActive=false;
    }

    private static long getLastModified(String fileName) {
        String path = pathFileAccess + fileName;
        File file = new File(path);

        // Get the last modified time in milliseconds
        long lastModified = file.lastModified();

        if (lastModified == 0) {
            System.err.println("Error: The file does not exist or cannot retrieve the last modified time.");
            return -1; // Return -1 in case of error (file not found)
        }

        // Optional: Convert milliseconds to a human-readable date
        Date date = new Date(lastModified);
        System.out.println("Last modified: " + date);

        return lastModified;
    }

    public static void showSnapshots() {
        // Run zfs command to show snapshots
        String command = "sudo zfs list -t snapshot";
        doCommand(command, "Snapshots displayed successfully");
    }

    public static void showFiles() {
        // Run ls command to list files in the ZFS mounted directory
        String command = "ls -l " + pathFileAccess;
        doCommand(command, "Files listed successfully");
    }

    public static void removeFile(String fileName){
        String command = "sudo rm "+ fileName;
        doCommand(command, fileName+" removed successfully");
    }
    public static void showFileContent(String fileName) {
        // Run ls command to list files in the ZFS mounted directory
        System.out.println("showing content of "+ fileName);
        String command = "cat " + pathFileAccess+fileName;
        doCommand(command, "FileContent shown successfully");
    }


    public static synchronized void notifyWrite(String threadName, String fileName){
        if(rollbackIsActive)
            return;
        String snapshotName= threadName+"-"+ snapshotCounter++;
        long lastModified= getLastModified(fileName);
        createSnapshot(snapshotName);
        transactions
                .get(threadName)
                .put(
                        snapshotName,
                        new TransactionInformation(threadName, fileName, snapshotName, lastModified)
                );

    }

    public static synchronized void appendToFile(TransactionInformation transactionInformation, String content) {
        if(rollbackIsActive)
            return;
        long lastModified= getLastModified(transactionInformation.getFileName());
        if(lastModified != transactionInformation.getFileLastModified()){
            // file wurde modifziert während ich zugange war, jetzt rollback
            //problem, ander threads könnten auch noch am schreiben sein
            //  - prozesse die ebenfalls begonnen haben zu schrieben vor lastModified können aufhören da Änderung eh nicht übernommen wird
            //  - prozesse die nach lastModified begonnen haben sind ja so gesehen auch von rollback betroffen und müssen aufhören zu schreiben
            //  - rollback führt

        }


        doCommand("echo '"+content+"' >> "+ pathFileAccess +transactionInformation.getFileName(),
                "Appended to file " + pathFileAccess + transactionInformation.getFileName()
        );
        String snapshotName= transactionInformation.getThreadName()+"-"+ snapshotCounter++;
        createSnapshot(snapshotName);
        transactionInformation.setAfterEditSnapshotName(snapshotName);
    }







}
