## optimistic concurrency modification of files using ZFS Snapshots
-  only runs on linux with zfs installed, a pool "mypool" and a zfs on it -> "mypool/myfs", if named different, the paths need to be adjusted in ZFSMapper
-  Java 21 used
-  strictly uses command line
-  files are in package org.example, therefore navigate to /java and then compile with **javac org/example/*.java**, run with f.e. **java org/example/Validator.java**
-  Two files that can be run, **Validator.java** (maximizing conflicts writing simulation results to csv) and **BrainstormingTool.java** (simple demostration of snapshot rollback on conflict)
## ZFSMapper
- implements all methods that use zfs commands like create snapshot, deleteSnapshot, rollbackToSnapshot
- further handles file accesses like, create File, get File names, append to file
- implements snapshot on creation logic and conflict handling basically by three methods, notifyOnWrite, AppendToFIle, rollbackToSnapshot
## TransactionInformation
- initialized when a thread starts its writing process (simulated user writing throug sleeping) creating a snapshot and saving the last modified of the file the thread is writing on
- further saves filename and threadname
- used for easy ref and acts as a mirror on the snapshot held by the zfs with additional timestamp information
## BrainstormingTool
- simulates a Brainstorming Tool conflict in which a user is prompted to either create a file or select a file for modification.
- On modify the behaviour is always that a thread simulates woring on this file, such that wenn the user commits his changes from the texteditor that is opened by the programm, the thread attempts a write and encounters a conflict resulting in a rollback if the user saved his changes
## Validator
- the validator simulates 8 runs with different parameter configurations
- in a simulation x threads attempt y times to write on a file which is randomly selected from an amount z files. The time they spend writing is given by a gaussion distribution
  give by the mean a and the std b
- The numbers of conflicts and rollbacks is counted as well as the number of successfull writes although this number is corrupted by successfull writes that are later
  subject to a rollback and therefore don't hold much information.
- further the avg time of a rollback in ms is calculated as well as the cnflictRate based on the number of conflicts/transactionAttempts
