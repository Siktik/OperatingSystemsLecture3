package org.example;

import org.example.ZFSMapper;

public class Main {

    public static void main(String[] args) {

        //basicExample();
        ZFSMapper.deleteSnapshot("testingSnapshot");




    }


    public static void basicExample() {
        ZFSMapper.deleteSnapshot("tester");// in case it exists from previous runs
        ZFSMapper.removeFile("testerFile.txt");
        ZFSMapper.showSnapshots();
        ZFSMapper.createSnapshot("tester");
        ZFSMapper.showSnapshots();
        ZFSMapper.createFileWithContent("testerFile.txt", "A tester");
        ZFSMapper.showFiles();
        ZFSMapper.showFileContent("testerFile.txt");
        ZFSMapper.rollbackToSnapshot("tester");
        ZFSMapper.showFiles();
        ZFSMapper.deleteSnapshot("tester");
        ZFSMapper.showSnapshots();
    }

    public static void snapshotExample(){
        ZFSMapper.deleteSnapshot("tester");// in case it exists from previous runs
        ZFSMapper.showSnapshots();
        ZFSMapper.createSnapshot("tester");
        ZFSMapper.showSnapshots();
        ZFSMapper.deleteSnapshot("tester");
        ZFSMapper.showSnapshots();
    }


}
