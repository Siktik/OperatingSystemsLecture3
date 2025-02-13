package org.example;

public class TransactionInformation {


    /**
     * static counter, although I do not need the id here it generally makes sense for such objects
     */
    private static long idCounter=0;
    /**
     * as said above
     */
    private long id;
    /**
     * the thread that is doing this transaction
     */
    private String threadName;
    /**
     * the file on which this transaction attempts modification
     */
    private String fileName;
    /**
     * the name of the snapshot taken on initialization of this object
     */
    private String snapshotName;
    /**
     * the timestamp of the file on initialization of this object
     */
    private long fileLastModified;

    /**
     * basic constructor
     * @param threadName see above
     * @param fileName see above
     * @param snapshotName see above
     * @param fileLastModified see above
     */
    public TransactionInformation(String threadName, String fileName, String snapshotName, long fileLastModified) {
        this.id = idCounter++;
        this.threadName = threadName;
        this.fileName = fileName;
        this.snapshotName = snapshotName;
        this.fileLastModified = fileLastModified;
    }

    /**
     * GETTERS
     */


    public String getThreadName() {
        return threadName;
    }


    public String getFileName() {
        return fileName;
    }

    public String getSnapshotName() {
        return snapshotName;
    }

    public long getFileLastModified() {
        return fileLastModified;
    }


}
