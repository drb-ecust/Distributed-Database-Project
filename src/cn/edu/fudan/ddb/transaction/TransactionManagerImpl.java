package cn.edu.fudan.ddb.transaction;

import cn.edu.fudan.ddb.entity.ResourceItem;
import cn.edu.fudan.ddb.exception.InvalidTransactionException;
import cn.edu.fudan.ddb.exception.TransactionAbortedException;
import cn.edu.fudan.ddb.resource.ResourceManager;
import cn.edu.fudan.ddb.utils.IOUtil;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transaction Manager for the Distributed Travel Reservation System.
 * <p>
 * Description: Implement 2 Phase Commit
 */

public class TransactionManagerImpl extends java.rmi.server.UnicastRemoteObject implements TransactionManager {
    private Integer xidCounter;
    private static final String xidCounterPath = "data/xidCounter.log";

    /**
     * the RMs that related to each transaction
     */
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<String, ResourceManager<? extends ResourceItem>>> rms;

    /**
     * record each trxn's status
     */
    private final ConcurrentHashMap<Integer, TMStatus> TMTrxnStatus;
    private static final String TMTrxnStatusPath = "data/TMTrxnStatus.log";

    private TMDieTime dieTime;

    protected static Registry _rmiRegistry = null;


    @SuppressWarnings("unchecked")
    public TransactionManagerImpl() throws RemoteException {
        super();

        this.rms = new ConcurrentHashMap<>();
        this.dieTime = TMDieTime.Never;

        // recover from disk
        Object temp;
        temp = IOUtil.loadObject(xidCounterPath);
        if (temp != null)
            this.xidCounter = (Integer) temp;
        else {
            System.out.println("Fail to load previous xidCounter from xidCounterPath: " + xidCounterPath + " , set xidCounter to 1 instead");
            this.xidCounter = 1;
        }
        temp = IOUtil.loadObject(TMTrxnStatusPath);
        if (temp != null)
            this.TMTrxnStatus = (ConcurrentHashMap<Integer, TMStatus>) temp;
        else {
            System.out.println("Fail to load previous TMTrxnStatus from TMTrxnStatusPath: " + TMTrxnStatusPath + " , new an empty TMTrxnStatus instead");
            this.TMTrxnStatus = new ConcurrentHashMap<>();
        }
    }


    public static void main(String[] args) {
        Properties prop = new Properties();
        try {
            prop.load(Files.newInputStream(Paths.get("conf/ddb.conf")));
        } catch (Exception e) {
            System.out.println("TM fail to load configuration file!");
            e.printStackTrace();
            System.exit(1);
        }
        String rmiPort = prop.getProperty("tm.port");
        try {
            _rmiRegistry = LocateRegistry.createRegistry(Integer.parseInt(rmiPort));
        } catch (RemoteException e) {
            e.printStackTrace();
            System.exit(1);
        }
        rmiPort = "//localhost:" + rmiPort + "/";
        try {
            TransactionManagerImpl obj = new TransactionManagerImpl();
            Naming.rebind(rmiPort + TransactionManager.RMIName, obj);
            System.out.println("TM bound");
        } catch (Exception e) {
            System.err.println("TM not bound:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public boolean testConnection() throws RemoteException {
        return true;
    }

    @Override
    public void setTMDieTime(TMDieTime dieTime) throws RemoteException {
        this.dieTime = dieTime;
    }

    public void dieNow() throws RemoteException {
        System.out.printf("TM Die at %s!\n", this.dieTime);
        System.exit(1);
    }

    public <T extends ResourceItem> TMStatus enlist(int xid, ResourceManager<T> rm) throws RemoteException, InvalidTransactionException {
        System.out.printf("Trxn ID %d: Enter TM.enlist().\n", xid);

        // abnormal case, happen when TM recover from die and RM call TM.enlist() to reconnect TM
        if (!TMTrxnStatus.containsKey(xid))
            throw new InvalidTransactionException(xid, "Invalid Trxn ID " + xid + "xid to enlist, should start first!");
        else if (TMTrxnStatus.get(xid) == TMStatus.COMMITTED) {
            System.out.println("TM state is: " + TMTrxnStatus.get(xid) + ". Tell RM " + rm.getRMIName() + "that Trxn ID " + xid + " should commit!");
            return TMStatus.COMMITTED;
        } else if (TMTrxnStatus.get(xid) == TMStatus.ABORTED) {
            System.out.println("TM state is: " + TMTrxnStatus.get(xid) + ". Tell RM " + rm.getRMIName() + "that Trxn ID " + xid + " should abort!");
            return TMStatus.ABORTED;
        } else if (TMTrxnStatus.get(xid) == TMStatus.PREPARING) {
            System.out.println("TM state is: " + TMTrxnStatus.get(xid) + ". Tell RM " + rm.getRMIName() + "that Trxn ID " + xid + " should abort!");
            synchronized (TMTrxnStatus) {
                TMTrxnStatus.put(xid, TMStatus.ABORTED);
                IOUtil.storeObject(TMTrxnStatus, TMTrxnStatusPath);
            }
            return TMStatus.ABORTED;
        }

        // normal case, trxnStatus.get(xid) == TMStatus.INITIATED
        synchronized (rms) {
            if (!rms.containsKey(xid)) // maybe happen when TM die before preparing
                rms.put(xid, new ConcurrentHashMap<>());
            System.out.printf("Trxn ID %d: RM %s enlist to TM.\n", xid, rm.getRMIName());
            rms.get(xid).put(rm.getRMIName(), rm);
        }
        return TMStatus.INITIATED;
    }


    @Override
    public int start() throws RemoteException {
        System.out.println("Enter TM.start().");
        Integer newXid = xidCounter++;
        IOUtil.storeObject(xidCounter, xidCounterPath);

        synchronized (TMTrxnStatus) {
            TMTrxnStatus.put(newXid, TMStatus.INITIATED);
            IOUtil.storeObject(TMTrxnStatus, TMTrxnStatusPath);
        }

        synchronized (rms) {
            rms.put(newXid, new ConcurrentHashMap<>());
        }
        System.out.println("TM start a new Trxn ID: " + newXid);
        return newXid;
    }

    @Override
    public void commit(int xid) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        System.out.printf("Trxn ID %d: Enter TM.commit().\n", xid);

        // abnormal case
        if (!TMTrxnStatus.containsKey(xid))
            throw new InvalidTransactionException(xid, "Invalid Trxn ID " + xid + "to commit, it needs to start first");
        else if (TMTrxnStatus.get(xid) == TMStatus.COMMITTED) {
            throw new InvalidTransactionException(xid, "Invalid Trxn ID " + xid + "to commit, it is already committed!");
        } else if (TMTrxnStatus.get(xid) == TMStatus.ABORTED) {
            throw new InvalidTransactionException(xid, "Invalid Trxn ID " + xid + "to commit, it is already aborted!");
        } else if (TMTrxnStatus.get(xid) == TMStatus.PREPARING) {
            // perhaps never reach this
            throw new InvalidTransactionException(xid, "Invalid Trxn ID " + xid + "to commit, it is already preparing!");
        }

        // normal case, trxnStatus.get(xid) == TMStatus.INITIATED
        ConcurrentHashMap<String, ResourceManager<? extends ResourceItem>> relatedRMs = rms.get(xid);
        // 2pc
        // phase 1: prepare phase
        synchronized (TMTrxnStatus) {
            TMTrxnStatus.put(xid, TMStatus.PREPARING);
            IOUtil.storeObject(TMTrxnStatus, TMTrxnStatusPath);
        }
        for (Map.Entry<String, ResourceManager<? extends ResourceItem>> temp : relatedRMs.entrySet()) {
            String rmName = temp.getKey();
            ResourceManager<? extends ResourceItem> rm = temp.getValue();
            try {
                if (!rm.prepare(xid)) {
                    // rm is not prepared.
                    throw new TransactionAbortedException(xid, "When committing Trxn ID " + xid + ", RM " + rmName + " is not prepared!");
                }
            } catch (RemoteException e) {
                // catch RemoteException, abort all if any rm is not prepared
                System.err.printf("Detect RM %s die when prepare Trxn ID %d!\n", rmName, xid);
                e.printStackTrace();
                this.abort(xid, "When committing Trxn ID " + xid + ", RM " + rmName + " is not prepared!");
                throw new TransactionAbortedException(xid, "When committing Trxn ID " + xid + ", RM " + rmName + " is not prepared!");
            }
        }

        // prepared, die before commit if set
        if (this.dieTime == TMDieTime.BeforeCommit)
            dieNow();
        // log commit with xid
        synchronized (TMTrxnStatus) {
            TMTrxnStatus.put(xid, TMStatus.COMMITTED);
            IOUtil.storeObject(TMTrxnStatus, TMTrxnStatusPath);
        }
        // finish log commit, die before commit if set
        if (this.dieTime == TMDieTime.AfterCommit)
            dieNow();

        // phase 2: commit phase
        for (Map.Entry<String, ResourceManager<? extends ResourceItem>> temp : relatedRMs.entrySet()) {
            String rmName = temp.getKey();
            ResourceManager<? extends ResourceItem> rm = temp.getValue();
            try {
                rm.commit(xid);
            } catch (RemoteException e) {
                // catch RemoteException, the rm will recommit when it recovers
                System.err.printf("Detect RM %s die when commit Trxn ID %d!\n", rmName, xid);
                e.printStackTrace();
            }
        }

        // remove committed transactions
        synchronized (rms) {
            rms.remove(xid);
        }

        //todo: perhaps we can do some design and remove trxnStatus here

        // since TM already enter COMMITTED state, the trxn will eventually commit, even though the TM or RM will suddenly die
        System.out.printf("Successfully commit Trxn ID %d.\n", xid);
    }

    @Override
    public void abort(int xid, String msg) throws RemoteException, InvalidTransactionException {
        System.out.printf("Trxn ID %d: Enter TM.abort(). The reason for abort is: %s\n", xid, msg);

        // abnormal case
        if (!TMTrxnStatus.containsKey(xid))
            throw new InvalidTransactionException(xid, "Invalid Trxn ID " + xid + "to abort, it needs to start first");
        else if (TMTrxnStatus.get(xid) == TMStatus.COMMITTED) {
            throw new InvalidTransactionException(xid, "Invalid Trxn ID " + xid + "to abort, it is already committed!");
        } else if (TMTrxnStatus.get(xid) == TMStatus.ABORTED) {
            throw new InvalidTransactionException(xid, "Invalid Trxn ID " + xid + "to abort, is already aborted!");
        }

        // log aborted with xid, trxnStatus.get(xid) == TMStatus.INITIATED or TMStatus.PREPARING
        synchronized (TMTrxnStatus) {
            TMTrxnStatus.put(xid, TMStatus.ABORTED);
            IOUtil.storeObject(TMTrxnStatus, TMTrxnStatusPath);
        }

        // call each rm's abort() method
        ConcurrentHashMap<String, ResourceManager<? extends ResourceItem>> relatedRMs = rms.get(xid);
        for (Map.Entry<String, ResourceManager<? extends ResourceItem>> temp : relatedRMs.entrySet()) {
            String rmName = temp.getKey();
            ResourceManager<? extends ResourceItem> rm = temp.getValue();
            try {
                rm.abort(xid);
            } catch (RemoteException e) {
                // catch RemoteException, the rm will redo abort when it recovers
                System.err.printf("Detect RM %s die when abort Trxn ID %d!\n", rmName, xid);
                e.printStackTrace();
            }
        }

        // remove aborted transactions
        synchronized (rms) {
            rms.remove(xid);
        }

        //todo: perhaps we can do some design and remove trxnStatus here

        System.out.printf("Successfully abort Trxn ID %d.\n", xid);
    }

    @Override
    public boolean ifCommitted(int xid) throws RemoteException {
        return TMTrxnStatus.get(xid) == TMStatus.COMMITTED;
    }
}
