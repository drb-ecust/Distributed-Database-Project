package database.resource;

import database.entity.ResourceItem;
import database.exception.DeadlockException;
import database.exception.InvalidTransactionException;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Interface for the Resource Manager of the Distributed Travel
 * Reservation System.
 * <p>
 * Unlike WorkflowController.java, you are supposed to make changes
 * to this file.
 */
public interface ResourceManager<T extends ResourceItem> extends Remote {

    public static final String RMI_NAME_RM_CARS = "rm.cars";
    public static final String RMI_NAME_RM_CUSTOMERS = "rm.customers";
    public static final String RMI_NAME_RM_FLIGHTS = "rm.flights";
    public static final String RMI_NAME_RM_HOTEL = "rm.hotels";
    public static final String RMI_NAME_RM_RESERVATIONS = "rm.reservations";

    public enum RMDieTime {
        BeforeCommit, BeforePrepare, AfterPrepare, AfterEnlist, BeforeAbort, Never
    }

    boolean testConnection() throws RemoteException;

    List<T> query(int xid, String tableName) throws DeadlockException, InvalidTransactionException, RemoteException;

    T query(int xid, String tableName, Object key) throws DeadlockException, InvalidTransactionException, RemoteException;

    boolean update(int xid, String tableName, Object key, T newItem) throws DeadlockException, InvalidTransactionException, RemoteException;

    boolean insert(int xid, String tableName, T newItem) throws DeadlockException, InvalidTransactionException, RemoteException;

    boolean delete(int xid, String tableName, Object key) throws DeadlockException, InvalidTransactionException, RemoteException;

    boolean prepare(int xid) throws InvalidTransactionException, RemoteException;

    void commit(int xid) throws InvalidTransactionException, RemoteException;

    void abort(int xid) throws InvalidTransactionException, RemoteException;

    public void setRMDieTime(RMDieTime dieTime) throws RemoteException;

    public void dieNow() throws RemoteException;

    public String getRMIName() throws RemoteException;
}
