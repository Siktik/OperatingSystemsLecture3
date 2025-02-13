package org.example;

import java.io.*;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Map;
import java.util.Scanner;

public class BrainstormingTool {

    /**
     * stores the fileOptions, they are updated on init and if a new file is created
     */
    private static Map<Integer, String> fileOptions = new HashMap<>();

    /**
     * This models the brainstorming tool.
     * It basically allows for creating of files for which a title and a basic idea as content is specified
     * If files exist, then one can be chosen to work on. I tried to allow for multiple processes to spawn terminals but that didnt work well
     * so now modifying is composed of the programm simulating a longer write to the selected file while opening
     * a GUI for you to edit sth. If you edit though and save, this will automatically lead to a rollback as the outer
     * simulated write will as well attempt to write.
     *
     * The main process will stay alive and allow you to try different combinations on files which should all be handled correctly
     *
     * 1. write and save in gui (no rollback)
     * 2. do not write and save in gui (no rollback)
     * 3. just close gui (no rollback)
     *
     * as on a successfull transaction e.g. you do 2 or 3, the snapshot is deleted, and a new one created if you access the file again
     * thereby the rollback will always only revert the changes of the current transaction by program and gui
     *
     * won't terminate unless you do so, meaning you can endlessly test the behaviour
     */

    public static void main(String[] args) {


        Scanner scanner = new Scanner(System.in);
        while (true) {
            updateFiles();
            System.out.println(fileOptionString());
            int auswahl;
            try {
                auswahl = scanner.nextInt();
                scanner.nextLine(); // Puffer leeren
            } catch (InputMismatchException e) {
                System.out.println("Wrong input, write a number corresponding to the option and hit enter");
                scanner.nextLine(); // Puffer leeren
                continue;
            }
            if (auswahl == 0) {
                // create new File
                System.out.println("create file");
                onFileCreation(scanner);
            } else if (!fileOptions.containsKey(auswahl)) {
                System.out.println("wrong input");
            } else {
                //guaranteed conflict
               conflictOnUserChangeBehaviour(fileOptions.get(auswahl));
            }

        }

    }


    /**
     * starts programmatically a transaction by notifying zfs that transaction is going to occur simulating a
     * longer input time than what the second transaction -> in the gedit takes. Therefore if some changes
     * are made in the ui and SAVED then a rollback will happen. If no changes are made by gedit, the timestamp
     * last modified remains the same, therefore the outer thread will push its content, delete its snapshot and carry on
     * @param fileName well, the fileName
     */
    private static void conflictOnUserChangeBehaviour(String fileName) {

        // simulate that someone is writing on this file
        TransactionInformation transactionInformation= ZFSMapper.notifyWrite("mainThread", fileName);
        try {
            // Create a ProcessBuilder to open gedit with the specified file
            ProcessBuilder processBuilder = new ProcessBuilder("gedit", "/mypool/myfs/"+fileName);

            // Inherit IO so that gedit is displayed normally
            processBuilder.inheritIO();

            // Start the gedit process
            Process process = processBuilder.start();

            // Wait for the gedit process to terminate
            int exitCode = process.waitFor();
            System.out.println("gedit terminated with exit code: " + exitCode);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        //guaranteed conflict if the user of gedit changed the files contents
        ZFSMapper.appendToFile(transactionInformation,"\njus append sth ");


    }

    /**
     * called after file creation to be an option for the next selection
     */
    private static void updateFiles(){
        fileOptions = ZFSMapper.getAllFiles();
    }

    /**
     * just some file creation logic, here no snapshots are taken as conflicts will not occur
     * @param sc
     */
    private static void onFileCreation(Scanner sc){

        while(true){
            System.out.println("Enter the name of the file");
            String line = sc.nextLine();
            if(line.isEmpty()){
                System.out.println("empty file name");
                continue;
            }
            if(line.endsWith(".txt") && line.length() == 4){
                System.out.println("only .txt specified");
                continue;
            }
            if(!line.endsWith(".txt")){
                System.out.println("datatype needs to be .txt");
                continue;
            }
            if(fileOptions.containsValue(line)){
                System.out.println("File already existing, use other name");
                continue;
            }

            System.out.println("is this the name the file should have :\n" + line);
            System.out.println("confirm with y, abort with n");
            String confirm = sc.nextLine();
            if (confirm.equals("y") || confirm.equals("Y")){
                //confirmed
                while (true){
                    System.out.println("enter the idea that is displayed at the beginning of the file");
                    String content= sc.nextLine();
                    if(content.isEmpty()){
                        System.out.println("no content is not possible, retry");
                        continue;
                    }
                    ZFSMapper.createFileWithContent(line, "idea:\n"+content);
                    return;
                }

            }else if(confirm.equals("n") || confirm.equals("N")){
                //abort
                System.out.println("aborted");
            }else{
                System.out.println("wrong input, start again");
            }

        }
    }

    /**
     * string formatting of file options
     * @return the formatted String
     */
    private static String fileOptionString(){
        StringBuilder builder= new StringBuilder("select one of the options by entering the corresponding number\n" +
                "0. create File\n");
        fileOptions.forEach((key, value) -> builder.append(key).append(". append To File: ").append(value).append("\n"));
        return builder.toString();
    }


}
