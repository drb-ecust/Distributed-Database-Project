package cn.edu.fudan.ddb.resource;

import cn.edu.fudan.ddb.entity.ResourceItem;
import cn.edu.fudan.ddb.exception.DeadlockException;
import cn.edu.fudan.ddb.exception.InvalidTransactionException;
import cn.edu.fudan.ddb.lockmgr.LockManager;
import cn.edu.fudan.ddb.transaction.TransactionManager;
import cn.edu.fudan.ddb.utils.IOUtil;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

/**
 * Resource Manager for the Distributed Travel Reservation System.
 * <p>
 * Description: toy implementation of the RM
 */
// todo: need to check implementation of shadow page
public abstract class ResourceManagerImpl<T extends ResourceItem> extends UnicastRemoteObject implements ResourceManager<T> {

    // todo: perhaps we don't need to record rm's state
    protected final HashSet<Integer> RMTrxnsNeedProcessing;
    protected static final String RMTrxnsNeedProcessingPath = "data/RMTrxnsNeedProcessing.log";
    protected static final String DataDir = "data";

    protected TransactionManager tm = null;
    protected LockManager lm = new LockManager();
    // todo: HashTable is not perfect structure perhaps
    protected Hashtable<Integer, Hashtable<String, RMTable<T>>> tables = new Hashtable<>();

    protected static String myRMIName; // assign value by subclass such as CarResourceManager
    protected ResourceManager.RMDieTime dieTime;
    protected static Registry _rmiRegistry = null;

    @SuppressWarnings({"BusyWait", "unchecked"})
    public ResourceManagerImpl() throws RemoteException {
        super();

        this.dieTime = ResourceManager.RMDieTime.Never;

        // recover from disk
        Object temp = IOUtil.loadObject(RMTrxnsNeedProcessingPath);
        if (temp != null)
            this.RMTrxnsNeedProcessing = (HashSet<Integer>) temp;
        else {
            System.out.println("Fail to load previous RMTrxnStatus from RMTrxnStatusPath: " + RMTrxnsNeedProcessingPath + " , new an empty RMTrxnStatus instead");
            this.RMTrxnsNeedProcessing = new HashSet<>();
        }
        System.out.printf("RM %s need to processing Trxns: %s\n", myRMIName, RMTrxnsNeedProcessing);
        File dataDir = new File(DataDir);
        if (dataDir.exists()) {
            File[] dataFiles = dataDir.listFiles();
            if (dataFiles != null) {
                // load main table
                for (File dataFile : dataFiles) {
                    if (!dataFile.isDirectory() && !dataFile.getName().endsWith(".log")) {
                        getTable(dataFile.getName());
                    }
                }
                // load trxn table
                for (File dataFile : dataFiles) {
                    if (dataFile.isDirectory()) {
                        int xid = Integer.parseInt(dataFile.getName());
                        if (!RMTrxnsNeedProcessing.contains(xid)) {
                            throw new RuntimeException(String.format("RM %s meet unexpected Trxn ID %d when load data from disk!", myRMIName, xid));
                        }
                        File[] trxnTableFiles = dataFile.listFiles();
                        if (trxnTableFiles != null) {
                            for (File trxnTableFile : trxnTableFiles) {
                                RMTable<T> trxnTable = getTable(xid, trxnTableFile.getName());
                                try {
                                    // reacquire all locks for the transaction
                                    // should ask coordinator for the status of transaction later
                                    trxnTable.relockAll();
                                } catch (DeadlockException e) {
                                    throw new RuntimeException(String.format("RM %s trigger deadlock when relockAll on table %s in Trxn %d", myRMIName, trxnTableFile.getName(), xid));
                                }
                            }
                        }
                    }
                }
            }
        }


        // reconnect to the TM
        while (!reconnectToTM()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // start a thread to monitor connection to TM
        monitorConnectionToTM();
    }

    private boolean reconnectToTM() throws RemoteException {
        System.out.printf("RM %s call reconnectToTM()\n", myRMIName);

        Properties prop = new Properties();
        try {
            prop.load(Files.newInputStream(Paths.get("conf/ddb.conf")));
        } catch (IOException e) {
            System.out.printf("RM %s fail to load configuration file!\n", myRMIName);
            e.printStackTrace();
            System.exit(1);
        }
        String rmiPort = prop.getProperty("tm.port");
        rmiPort = "//localhost:" + rmiPort + "/";
        try {
            tm = (TransactionManager) Naming.lookup(rmiPort + TransactionManager.RMIName);
            // if RMTrxnStatus is not empty, rm must process the remained Trxns
            System.out.printf("Remained Trxns on RM %s that need to be processed: %s \n", myRMIName, RMTrxnsNeedProcessing);
            for (Integer xid : RMTrxnsNeedProcessing) {
                // call tm.enlist() to info tm and get the state of tm
                TransactionManager.TMStatus tmState = tm.enlist(xid, this);
                // todo: check if should die here
                if (dieTime == RMDieTime.AfterEnlist) {
                    dieNow();
                }
                if (tmState == TransactionManager.TMStatus.COMMITTED) {
                    this.commit(xid);
                } else if (tmState == TransactionManager.TMStatus.ABORTED) {
                    this.abort(xid);
                }
                // perhaps the only else case here is tmState== TransactionManager.TMStatus.INITIATED
            }
            System.out.printf("RM %s enlist to TM successfully.\n", myRMIName);
            return true;
        } catch (Exception e) {
            System.out.printf("RM %s enlist to TM failed with error: %s\n", myRMIName, e);
            e.printStackTrace();
            return false;
        }
    }

    @SuppressWarnings("BusyWait")
    private void monitorConnectionToTM() {
        Thread monitorThread = new Thread(() -> {
            while (true) {
                try {
                    if (tm != null) {
                        tm.testConnection();
                    }
                } catch (RemoteException ignored) {
                    tm = null;
                }
                if (tm == null) {
                    System.out.printf("RM %s lose connection to TM, Reconnecting...\n", myRMIName);
                    try {
                        reconnectToTM();
                    } catch (RemoteException ignored) {
                        System.out.println("Fail to reconnect...");
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        monitorThread.start();
    }

    @Override
    public boolean testConnection() throws RemoteException {
        return true;
    }

    private RMTable<T> getTable(String tableName) {
        return getTable(-1, tableName);
    }

    @SuppressWarnings("unchecked")
    private RMTable<T> getTable(int xid, String tableName) {
        Hashtable<String, RMTable<T>> trxnTables = tables.computeIfAbsent(xid, k -> new Hashtable<>());

        synchronized (trxnTables) {
            RMTable<T> trxnTable = trxnTables.get(tableName);
            if (trxnTable == null) {
                Object temp = IOUtil.loadObject(DataDir + File.separator + (xid == -1 ? "" : (xid + File.separator)) + tableName);
                if (temp != null) {
                    trxnTable = (RMTable<T>) temp;
                }
                if (trxnTable == null) {
                    if (xid == -1) {
                        trxnTable = new RMTable<>(tableName, null, -1, lm);
                    } else {
                        trxnTable = new RMTable<>(tableName, getTable(tableName), xid, lm);
                    }
                } else if (xid != -1) {
                    trxnTable.setLockManager(lm);
                    trxnTable.setParent(getTable(tableName));
                }
                trxnTables.put(tableName, trxnTable);
            }
            return trxnTable;
        }
    }


    @Override
    public List<T> query(int xid, String tableName) throws DeadlockException, InvalidTransactionException, RemoteException {
        if (xid < 0) {
            throw new InvalidTransactionException(xid, "Transaction ID must be positive.");
        }

        // record the trxns need processing
        synchronized (RMTrxnsNeedProcessing) {
            RMTrxnsNeedProcessing.add(xid);
            IOUtil.storeObject(RMTrxnsNeedProcessing, RMTrxnsNeedProcessingPath);
        }

        // notify TM this RM will participate in this transaction
        tm.enlist(xid, this);

        if (dieTime == RMDieTime.AfterEnlist) {
            dieNow();
        }

        RMTable<T> trxnTable = getTable(xid, tableName);
        synchronized (trxnTable) {
            // read resource items
            List<T> result = new ArrayList<>();
            for (Object key : trxnTable.keySet()) {
                T item = trxnTable.get(key);
                if (item != null && !item.isDeleted()) {
                    trxnTable.lock(key, 0);
                    result.add(item);
                }
            }

            // save transaction shadow table
            if (!result.isEmpty() && !IOUtil.storeObject(trxnTable, DataDir + File.separator + xid + File.separator + tableName)) {
                throw new RemoteException(String.format("RM %s trigger System Error: Can't write table %s to disk on Trxn ID %d!", myRMIName, tableName, xid));
            }
            return result;
        }
    }

    @Override
    public T query(int xid, String tableName, Object key) throws DeadlockException, InvalidTransactionException, RemoteException {
        if (xid < 0) {
            throw new InvalidTransactionException(xid, "Transaction ID must be positive.");
        }

        // record the trxns need processing
        synchronized (RMTrxnsNeedProcessing) {
            RMTrxnsNeedProcessing.add(xid);
            IOUtil.storeObject(RMTrxnsNeedProcessing, RMTrxnsNeedProcessingPath);
        }


        // notify TM this RM will participate in this transaction
        tm.enlist(xid, this);

        if (dieTime == RMDieTime.AfterEnlist) {
            dieNow();
        }

        // read resource items
        RMTable<T> trxnTable = getTable(xid, tableName);
        T item = trxnTable.get(key);
        if (item != null && !item.isDeleted()) {
            trxnTable.lock(key, 0);

            // save transaction shadow table
            if (!IOUtil.storeObject(trxnTable, DataDir + File.separator + xid + File.separator + tableName)) {
                throw new RemoteException(String.format("RM %s trigger System Error: Can't write table %s to disk on Trxn ID %d!", myRMIName, tableName, xid));
            }
        }
        return item;
    }

    @Override
    public boolean update(int xid, String tableName, Object key, T newItem) throws DeadlockException, InvalidTransactionException, RemoteException {
        if (xid < 0) {
            throw new InvalidTransactionException(xid, "Transaction ID must be positive.");
        }

        // record the trxns need processing
        synchronized (RMTrxnsNeedProcessing) {
            RMTrxnsNeedProcessing.add(xid);
            IOUtil.storeObject(RMTrxnsNeedProcessing, RMTrxnsNeedProcessingPath);
        }

        // notify TM this RM will participate in this transaction
        tm.enlist(xid, this);

        if (dieTime == RMDieTime.AfterEnlist) {
            dieNow();
        }

        // read resource items
        RMTable<T> trxnTable = getTable(xid, tableName);
        T item = trxnTable.get(key);
        if (item != null && !item.isDeleted()) {
            trxnTable.lock(key, 1);
            trxnTable.put(newItem);

            // save transaction shadow table
            if (!IOUtil.storeObject(trxnTable, DataDir + File.separator + xid + File.separator + tableName)) {
                throw new RemoteException(String.format("RM %s trigger System Error: Can't write table %s to disk on Trxn ID %d!", myRMIName, tableName, xid));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean insert(int xid, String tableName, T newItem) throws DeadlockException, InvalidTransactionException, RemoteException {
        if (xid < 0) {
            throw new InvalidTransactionException(xid, "Transaction ID must be positive.");
        }

        // record the trxns need processing
        synchronized (RMTrxnsNeedProcessing) {
            RMTrxnsNeedProcessing.add(xid);
            IOUtil.storeObject(RMTrxnsNeedProcessing, RMTrxnsNeedProcessingPath);
        }

        // notify TM this RM will participate in this transaction
        tm.enlist(xid, this);

        if (dieTime == RMDieTime.AfterEnlist) {
            dieNow();
        }

        // read resource items
        RMTable<T> trxnTable = getTable(xid, tableName);
        T item = trxnTable.get(newItem.getKey());
        if (item != null && !item.isDeleted()) {  // already exist
            return false;
        }
        trxnTable.lock(newItem.getKey(), 1);
        trxnTable.put(newItem);

        // save transaction shadow table
        if (!IOUtil.storeObject(trxnTable, DataDir + File.separator + xid + File.separator + tableName)) {
            throw new RemoteException(String.format("RM %s trigger System Error: Can't write table %s to disk on Trxn ID %d!", myRMIName, tableName, xid));
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean delete(int xid, String tableName, Object key) throws DeadlockException, InvalidTransactionException, RemoteException {
        if (xid < 0) {
            throw new InvalidTransactionException(xid, "Transaction ID must be positive.");
        }

        // record the trxns need processing
        synchronized (RMTrxnsNeedProcessing) {
            RMTrxnsNeedProcessing.add(xid);
            IOUtil.storeObject(RMTrxnsNeedProcessing, RMTrxnsNeedProcessingPath);
        }

        // notify TM this RM will participate in this transaction
        tm.enlist(xid, this);

        if (dieTime == RMDieTime.AfterEnlist) {
            dieNow();
        }

        // read resource items
        RMTable<T> trxnTable = getTable(xid, tableName);
        T item = trxnTable.get(key);
        if (item != null && !item.isDeleted()) {
            trxnTable.lock(key, 1);
            try {
                item = (T) item.clone();
            } catch (CloneNotSupportedException ignored) {
            }
            item.setDeleted(true);
            trxnTable.put(item);

            // save transaction shadow table
            if (!IOUtil.storeObject(trxnTable, DataDir + File.separator + xid + File.separator + tableName)) {
                throw new RemoteException(String.format("RM %s trigger System Error: Can't write table %s to disk on Trxn ID %d!", myRMIName, tableName, xid));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean prepare(int xid) throws InvalidTransactionException, RemoteException {
        System.out.printf("Trxn ID %d: Enter RM.prepare().\n", xid);

        if (dieTime == RMDieTime.BeforePrepare) {
            dieNow();
        }


        if (!RMTrxnsNeedProcessing.contains(xid)) {
            throw new InvalidTransactionException(xid, "Transaction ID to prepare must be contained in RMTrxnsNeedProcessing.");
        }

        // todo: I think here we should require all locks for this trxn
        // todo: but the referenced codes do not
        File trxnTablesDir = new File(DataDir + File.separator + xid);
        if (trxnTablesDir.exists()) {
            File[] trxnTableFiles = trxnTablesDir.listFiles();
            if (trxnTableFiles != null) {
                // load trxn table
                for (File trxnTableFile : trxnTableFiles) {
                    RMTable<T> trxnTable = getTable(xid, trxnTableFile.getName());
                    try {
                        // reacquire all locks and prepared to commit
                        trxnTable.relockAll();
                    } catch (DeadlockException e) {
                        throw new RuntimeException(String.format("RM %s trigger deadlock when relockAll on table %s in Trxn %d", myRMIName, trxnTableFile.getName(), xid));
                    }
                }
            }
        }

        if (dieTime == RMDieTime.AfterPrepare) {
            dieNow();
        }

        System.out.printf("Trxn ID %d: RM.prepare() successfully.\n", xid);
        return true;
    }

    @Override
    public void commit(int xid) throws InvalidTransactionException, RemoteException {
        System.out.printf("Trxn ID %d: Enter RM.commit().\n", xid);

        if (dieTime == RMDieTime.BeforeCommit) {
            dieNow();
        }

        if (xid < 0) {
            throw new InvalidTransactionException(xid, "Transaction ID must be positive.");
        }

        Hashtable<String, RMTable<T>> trxnTables = tables.get(xid);
        if (trxnTables != null) {
            synchronized (trxnTables) {
                for (Map.Entry<String, RMTable<T>> entry : trxnTables.entrySet()) {
                    String tableName = entry.getKey();
                    RMTable<T> trxnTable = entry.getValue(); // trxn shadow table
                    RMTable<T> table = getTable(tableName); // main table

                    // merge changes in transaction shadow table to the original table
                    for (Object key : trxnTable.keySet()) {
                        T item = trxnTable.get(key);
                        if (item.isDeleted()) {
                            table.remove(item);
                        } else {
                            table.put(item);
                        }
                    }

                    // persistence the table
                    if (!IOUtil.storeObject(table, DataDir + File.separator + tableName)) {
                        throw new RemoteException("Can't write table to disk!");
                    }

                    // cleanup the file of transaction shadow table
                    File trxnTableFile = new File(DataDir + File.separator + xid + File.separator + tableName);
                    if (!trxnTableFile.delete()) {
                        System.err.printf("Failed to delete transaction shadow table %s!\n", trxnTableFile);
                    }

                }

                // cleanup the dir containing transaction shadow tables, which assumed to be empty.
                File trxnTableFilesDir = new File(DataDir + File.separator + xid);
                if (!trxnTableFilesDir.delete()) {
                    System.err.printf("Failed to delete transaction shadow tables dir %s!\n", trxnTableFilesDir);
                }

                // delete in-memory shadow table of transaction
                tables.remove(xid);
            }
        }

        // unlock all resources occupied by the transaction
        if (!lm.unlockAll(xid)) {
            throw new RuntimeException("Can not unlock resources of transaction " + xid + ".");
        }

        // remove the transaction from RMTrxnsNeedProcessing
        RMTrxnsNeedProcessing.remove(xid);

        System.out.printf("Trxn ID %d: RM.commit() successfully.\n", xid);
    }

    @Override
    public void abort(int xid) throws InvalidTransactionException, RemoteException {
        System.out.printf("Trxn ID %d: Enter RM.abort().\n", xid);

        if (dieTime == RMDieTime.BeforeAbort) {
            dieNow();
        }

        if (xid < 0) {
            throw new InvalidTransactionException(xid, "Transaction ID must be positive.");
        }

        Hashtable<String, RMTable<T>> trxnTables = tables.get(xid);
        if (trxnTables != null) {
            synchronized (trxnTables) {
                for (Map.Entry<String, RMTable<T>> entry : trxnTables.entrySet()) {
                    String tableName = entry.getKey();
                    // cleanup the file of transaction shadow table
                    File trxnTableFile = new File(DataDir + File.separator + xid + File.separator + tableName);
                    if (!trxnTableFile.delete()) {
                        System.err.printf("Failed to delete transaction shadow table %s!\n", trxnTableFile);
                    }
                }

                // cleanup the dir containing transaction shadow tables, which assumed to be empty.
                File trxnTableFilesDir = new File(DataDir + File.separator + xid);
                if (!trxnTableFilesDir.delete()) {
                    System.err.printf("Failed to delete transaction shadow tables dir %s!\n", trxnTableFilesDir);
                }

                // delete in-memory shadow table of transaction
                tables.remove(xid);
            }
        }

        // unlock all resources occupied by the transaction
        if (!lm.unlockAll(xid)) {
            throw new RuntimeException("Can not unlock resources of transaction " + xid + ".");
        }

        // remove the transaction from RMTrxnsNeedProcessing
        RMTrxnsNeedProcessing.remove(xid);

        System.out.printf("Trxn ID %d: RM.abort() successfully.\n", xid);
    }

    @Override
    public void setRMDieTime(RMDieTime dieTime) throws RemoteException {
        System.out.printf("TM Die at %s!\n", this.dieTime);
        this.dieTime = dieTime;
    }

    @Override
    public void dieNow() throws RemoteException {
        System.out.printf("TM Die at %s!\n", this.dieTime);
        System.exit(1);
    }

    @Override
    public String getRMIName() throws RemoteException {
        return myRMIName;
    }
}
