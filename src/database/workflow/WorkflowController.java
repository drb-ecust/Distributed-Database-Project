package database.workflow;

import database.exception.InvalidTransactionException;
import database.exception.TransactionAbortedException;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Interface for the Workflow Controller of the Distributed Travel
 * Reservation System.
 * <p>
 * Failure reporting is done using two pieces, exceptions and boolean
 * return values.  Exceptions are used for systemy things - like
 * txInProcessing that were forced to abort, or don't exist.  Return
 * values are used for operations that would affect the consistency of
 * the database, like the deletion of more cars than there are.
 * <p>
 * If there is a boolean return value and you're not sure how it would
 * be used in your implementation, ignore it.  We used boolean return
 * values in the interface generously to allow flexibility in
 * implementation.  But don't forget to return true when the operation
 * has succeeded.
 * <p>
 * All methods in the interface are declared to throw RemoteException.
 * This exception is thrown by the RMI system during a remote method
 * call to indicate that either a communication failure or a protocol
 * error has occurred. Your code will never have to directly throw
 * this exception, but any client code that you write must catch the
 * exception and take the appropriate action.
 */

public interface WorkflowController extends Remote {

    //////////
    // TRANSACTION INTERFACE
    //////////

    /**
     * The RMI name a WorkflowController binds to.
     */
    String RMIName = "WC";

    /**
     * Start a new transaction, and return its transaction id.
     *
     * @return A unique transaction ID > 0.  Return <=0 if server is not accepting new txInProcessing.
     * @throws RemoteException on communications failure.
     */
    int start() throws RemoteException;

    /**
     * Commit transaction.
     *
     * @param xid id of transaction to be committed.
     * @return true on success, false on failure.
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    boolean commit(int xid) throws RemoteException, TransactionAbortedException, InvalidTransactionException;


    //////////
    // ADMINISTRATIVE INTERFACE
    //////////

    /**
     * Abort transaction.
     *
     * @param xid id of transaction to be aborted.
     * @throws RemoteException             on communications failure.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    void abort(int xid) throws RemoteException, InvalidTransactionException;

    /**
     * Add seats to a flight.  In general this will be used to create
     * a new flight, but it should be possible to add seats to an
     * existing flight.  Adding to an existing flight should overwrite
     * the current price of the available seats.
     *
     * @param xid       id of transaction.
     * @param flightNum flight number, cannot be null.
     * @param numSeats  number of seats to be added to the flight.(>=0)
     * @param price     price of each seat. If price < 0, don't overwrite the current price; leave price at 0 if price<0 for very first add for this flight.
     * @return true on success, false on failure. (flightNum==null; numSeats<0...)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    boolean addFlight(int xid, String flightNum, int numSeats, int price) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Delete an entire flight.
     * Should fail if a customer has a reservation on this flight.
     *
     * @param xid       id of transaction.
     * @param flightNum flight number, cannot be null.
     * @return true on success, false on failure. (flight doesn't exist;has reservations...)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    boolean deleteFlight(int xid, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Add rooms to a location.
     * This should look a lot like addFlight, only keyed on a location
     * instead of a flight number.
     * @param xid transaction id
     * @param location location
     * @param numRooms number of rooms in the location
     * @param price price of a room in the location
     * @return true on success, false on failure. (location==null; numRooms<0...)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     * @see #addFlight
     */
    boolean addRooms(int xid, String location, int numRooms, int price) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Delete rooms from a location.
     * This subtracts from both the toal and the available room count
     * (rooms not allocated to a customer).  It should fail if it
     * would make the count of available rooms negative.
     * @param xid transaction id.
     * @param location location.
     * @param numRooms the number of rooms.
     * @return true on success, false on failure. (location doesn't exist; numRooms<0; not enough available rooms...)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     * @see #deleteFlight
     */
    boolean deleteRooms(int xid, String location, int numRooms) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Add cars to a location.
     * Cars have the same semantics as hotels (see addRooms).
     * @param xid transaction id.
     * @param location location.
     * @param numCars the number of cars.
     * @param price the price of a car in the location.
     * @return true on success, false on failure.
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     * @see #addRooms
     * @see #addFlight
     */
    boolean addCars(int xid, String location, int numCars, int price) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Delete cars from a location.
     * Cars have the same semantics as hotels.
     * @param xid transaction id.
     * @param location location.
     * @param numCars the number of cars.
     * @return true on success, false on failure.
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     * @see #deleteRooms
     * @see #deleteFlight
     */
    boolean deleteCars(int xid, String location, int numCars) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Add a new customer to database.  Should return success if
     * customer already exists.
     *
     * @param xid      id of transaction.
     * @param custName name of customer.
     * @return true on success, false on failure.
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    boolean newCustomer(int xid, String custName) throws RemoteException, TransactionAbortedException, InvalidTransactionException;


    //////////
    // QUERY INTERFACE
    //////////

    /**
     * Delete this customer and un-reserve associated reservations.
     *
     * @param xid      id of transaction.
     * @param custName name of customer.
     * @return true on success, false on failure. (custName==null or doesn't exist...)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    boolean deleteCustomer(int xid, String custName) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Return the number of empty seats on a flight.
     *
     * @param xid       id of transaction.
     * @param flightNum flight number.
     * @return empty seats on the flight. (-1 if flightNum==null or doesn't exist)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    int queryFlight(int xid, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Return the price of a seat on this flight. Return -1 if flightNum==null or doesn't exist.
     * @param xid       id of transaction.
     * @param flightNum flight number.
     * @return flight price on the flight.(-1 if flightNum==null or doesn't exist)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    int queryFlightPrice(int xid, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Return the number of rooms available at a location.
     * @param location location.
     * @return empty rooms in the location.(-1 if location==null or doesn't exist)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    int queryRooms(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Return the price of rooms at this location.
     * @param location location.
     * @return room price in the location.(-1 if location==null or doesn't exist)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    int queryRoomsPrice(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Return the number of cars available at a location.
     * @param location location.
     * @return empty cars in the location.(-1 if location==null or doesn't exist)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    int queryCars(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Return the price of rental cars at this location.
     * @param location location.
     * @return the price of rental cars at this location. (-1 if location==null or doesn't exist)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    int queryCarsPrice(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException;


    //////////
    // RESERVATION INTERFACE
    //////////

    /**
     * Query the total price of all reservations held for a customer.
     * @param custName customer name.
     * @return the total price of all reservations held for a customer. (-1 if location==null or doesn't exist)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    int queryCustomerBill(int xid, String custName) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Reserve a flight on behalf of this customer.
     *
     * @param xid       id of transaction.
     * @param custName  name of customer.
     * @param flightNum flight number.
     * @return true on success, false on failure. (cust or flight doesn't exist; no seats left...)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    boolean reserveFlight(int xid, String custName, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Reserve a car for this customer at the specified location.
     * @param custName  name of customer.
     * @param location location.
     * @return true on success, false on failure. (cust or location doesn't exist; no cars left...)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    boolean reserveCar(int xid, String custName, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * Reserve a room for this customer at the specified location.
     * @param custName  name of customer.
     * @param location location.
     * @return true on success, false on failure. (cust or location doesn't exist; no rooms left...)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    boolean reserveRoom(int xid, String custName, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException;


    //////////
    // TECHNICAL/TESTING INTERFACE
    //////////

    /**
     * Reserve an entire itinerary on behalf of this customer.
     *
     * @param xid           id of transaction.
     * @param custName      name of customer.
     * @param flightNumList list of String flight numbers.
     * @param location      location of car & hotel, if needed.
     * @param needCar       whether itinerary includes a car reservation.
     * @param needRoom      whether itinerary includes a hotel reservation.
     * @return true on success, false on failure. (Any needed flights/car/room doesn't exist or not available...)
     * @throws RemoteException             on communications failure.
     * @throws TransactionAbortedException if transaction was aborted.
     * @throws InvalidTransactionException if transaction id is invalid.
     */
    boolean reserveItinerary(int xid, String custName, List<String> flightNumList, String location, boolean needCar, boolean needRoom) throws RemoteException, TransactionAbortedException, InvalidTransactionException;

    /**
     * If some component has died and was restarted, this function is
     * called to refresh the RMI references so that everybody can talk
     * to everybody else again.  Specifically, the WC should reconnect
     * to all other components, and each RM's reconnect() is called so
     * that the RM can reconnect to the TM.
     * <p>
     * This method is used for testing and is not part of a transaction.
     *
     * @return true on success, false on failure. (some component not up yet...)
     */
    boolean reconnect() throws RemoteException;

    /**
     * Kill the component immediately.  Used to simulate a system
     * failure such as a power outage.
     * <p>
     * This method is used for testing and is not part of a transaction.
     *
     * @param who which component to kill; must be "TM", "RMFlights", "RMRooms", "RMCars", "RMCustomers", "WC", or "ALL" (which kills all 6 in that order).
     * @return true on success, false on failure.
     */
    boolean dieNow(String who) throws RemoteException;

    /**
     * Sets a flag so that the RM fails after the next enlist()
     * operation.  That is, the RM immediately dies on return of the
     * enlist() call it made to the TM, before it could fulfil the
     * client's query/reservation request.
     * <p>
     * This method is used for testing and is not part of a transaction.
     *
     * @param who which RM to kill; must be "RMFlights", "RMRooms", "RMCars", or "RMCustomers".
     * @return true on success, false on failure.
     */
    boolean dieRMAfterEnlist(String who) throws RemoteException;

    /**
     * Sets a flag so that the RM fails when it next tries to prepare,
     * but before it gets a chance to save the update list to disk.
     * <p>
     * This method is used for testing and is not part of a transaction.
     *
     * @param who which RM to kill; must be "RMFlights", "RMRooms", "RMCars", or "RMCustomers".
     * @return true on success, false on failure.
     */
    boolean dieRMBeforePrepare(String who) throws RemoteException;

    /**
     * Sets a flag so that the RM fails when it next tries to prepare:
     * after it has entered the prepared state, but just before it
     * could reply "prepared" to the TM.
     * <p>
     * This method is used for testing and is not part of a transaction.
     *
     * @param who which RM to kill; must be "RMFlights", "RMRooms", "RMCars", or "RMCustomers".
     * @return true on success, false on failure.
     */
    boolean dieRMAfterPrepare(String who) throws RemoteException;

    /**
     * Sets a flag so that the TM fails after it has received
     * "prepared" messages from all RMs, but before it can log
     * "committed".
     * <p>
     * This method is used for testing and is not part of a transaction.
     *
     * @return true on success, false on failure.
     */
    boolean dieTMBeforeCommit() throws RemoteException;

    /**
     * Sets a flag so that the TM fails right after it logs
     * "committed".
     * <p>
     * This method is used for testing and is not part of a transaction.
     *
     * @return true on success, false on failure.
     */
    boolean dieTMAfterCommit() throws RemoteException;

    /**
     * Sets a flag so that the RM fails when it is told by the TM to
     * commit, by before it could actually change the database content
     * (i.e., die at beginning of the commit() function called by TM).
     * <p>
     * This method is used for testing and is not part of a transaction.
     *
     * @param who which RM to kill; must be "RMFlights", "RMRooms", "RMCars", or "RMCustomers".
     * @return true on success, false on failure.
     */
    boolean dieRMBeforeCommit(String who) throws RemoteException;

    /**
     * Sets a flag so that the RM fails when it is told by the TM to
     * abort, by before it could actually do anything.  (i.e., die at
     * beginning of the abort() function called by TM).
     * <p>
     * This method is used for testing and is not part of a transaction.
     *
     * @param who which RM to kill; must be "RMFlights", "RMRooms", "RMCars", or "RMCustomers".
     * @return true on success, false on failure.
     */
    boolean dieRMBeforeAbort(String who) throws RemoteException;
}
