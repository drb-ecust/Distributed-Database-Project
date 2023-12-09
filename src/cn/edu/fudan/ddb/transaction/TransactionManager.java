package cn.edu.fudan.ddb.transaction;

import cn.edu.fudan.ddb.entity.ResourceItem;
import cn.edu.fudan.ddb.exception.InvalidTransactionException;
import cn.edu.fudan.ddb.exception.TransactionAbortedException;
import cn.edu.fudan.ddb.resource.ResourceManager;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface for the Transaction Manager of the Distributed Travel Reservation System.
 */

public interface TransactionManager extends Remote {
    /**
     * The RMI name a TransactionManager binds to.
     */
    public static final String RMIName = "TM";

    /**
     * Constant to represent Transaction Status
     */
    public enum TMStatus {
        INITIATED, PREPARING, COMMITTED, ABORTED
    }

    public enum TMDieTime {
        BeforeCommit, AfterCommit, Never
    }


    public boolean testConnection() throws RemoteException;

    /**
     * make TM know the transaction id = @xid use RM = @rm
     *
     * @param xid transaction id
     * @param rm  RM
     * @throws InvalidTransactionException maybe the transaction id = @xid is not started or has committed/aborted
     * @throws RemoteException             on communications failure.
     */
    public <T extends ResourceItem> TMStatus enlist(int xid, ResourceManager<T> rm) throws RemoteException, InvalidTransactionException;

    /**
     * Start a new transaction, and return its transaction id.
     *
     * @return A unique transaction ID > 0.  Return <=0 if server is not accepting new transactions.
     * @throws RemoteException on communications failure.
     */
    public int start() throws RemoteException;

    /**
     * attempt to commit the transaction id = @xid
     * attempt at most 10 times, or it will abort
     * return false when the transaction can not commit in 10s, abort it automatically
     * otherwise, return true
     *
     * @param xid transaction id
     * @throws InvalidTransactionException maybe the transaction id = @xid is not started or has committed/aborted
     * @throws TransactionAbortedException if the tm can't commit and thus abort the transaction
     * @throws RemoteException             on communications failure.
     */
    public void commit(int xid) throws RemoteException, InvalidTransactionException, TransactionAbortedException;

    /**
     * abort transaction id = @xid
     *
     * @param xid transaction id
     * @param msg the reason why the transaction was aborted
     * @throws InvalidTransactionException maybe the transaction id = @xid is not started or has committed/aborted
     * @throws RemoteException             on communications failure.
     */
    public void abort(int xid, String msg) throws RemoteException, InvalidTransactionException;

    /**
     * ask if transaction id = @xid is committed.
     *
     * @param xid transaction id
     * @throws RemoteException on communications failure.
     */
    public boolean ifCommitted(int xid) throws RemoteException;

    public void setTMDieTime(TMDieTime dieTime) throws RemoteException;

    public void dieNow() throws RemoteException;
}
