package org.example;

public class TransactionInformation {


    private static long idCounter=0;
    private long id;
    private String threadName;
    private String fileName;
    private String beforeEditSnapshotName;
    private String afterEditSnapshotName;
    private long fileLastModified;
    private boolean successfull=false;

    public TransactionInformation(String threadName, String fileName, String beforeEditSnapshotName, long fileLastModified) {
        this.id = idCounter++;
        this.threadName = threadName;
        this.fileName = fileName;
        this.beforeEditSnapshotName = beforeEditSnapshotName;
        this.fileLastModified = fileLastModified;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getBeforeEditSnapshotName() {
        return beforeEditSnapshotName;
    }

    public void setBeforeEditSnapshotName(String beforeEditSnapshotName) {
        this.beforeEditSnapshotName = beforeEditSnapshotName;
    }

    public String getAfterEditSnapshotName() {
        return afterEditSnapshotName;
    }

    public void setAfterEditSnapshotName(String afterEditSnapshotName) {
        this.afterEditSnapshotName = afterEditSnapshotName;
    }

    public long getFileLastModified() {
        return fileLastModified;
    }

    public void setFileLastModified(long fileLastModified) {
        this.fileLastModified = fileLastModified;
    }
}
