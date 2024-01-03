package database.workflow;

import database.entity.*;
import database.entity.*;
import database.exception.DeadlockException;
import database.exception.InvalidTransactionException;
import database.exception.TransactionAbortedException;
import database.resource.ResourceManager;
import database.transaction.TransactionManager;
import database.utils.PropUtil;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Properties;

/**
 * Workflow Controller for the Distributed Travel Reservation System.
 * <p>
 * Description: toy implementation of the WC.  In the real
 * implementation, the WC should forward calls to either RM or TM,
 * instead of doing the things itself.
 */
public class WorkflowControllerImpl extends java.rmi.server.UnicastRemoteObject implements WorkflowController {

    private static final String FlightsTable = "flights";
    private static final String RoomsTable = "hotels";
    private static final String CarsTable = "cars";
    private static final String CustomersTable = "customers";
    private static final String ReservationsTable = "reservations";

    private ResourceManager<Flight> rmFlights = null;
    private ResourceManager<Hotel> rmHotels = null;
    private ResourceManager<Car> rmCars = null;
    private ResourceManager<Customer> rmCustomers = null;
    private ResourceManager<Reservation> rmReservations = null;
    private TransactionManager tm = null;

    protected static Registry _rmiRegistry = null;

    @SuppressWarnings("BusyWait")
    public WorkflowControllerImpl() throws RemoteException {
        while (!reconnect()) {
            try {
                Thread.sleep(1000);
            } catch (Exception ignored) {
            }
        }
    }


    public static void main(String[] args) {

        String rmiPort = PropUtil.getRmiPort("wc");
        try {
            _rmiRegistry = LocateRegistry.createRegistry(Integer.parseInt(rmiPort));
        } catch (RemoteException e2) {
            e2.printStackTrace();
            System.exit(1);
        }
        rmiPort = "//localhost:" + rmiPort + "/";
        try {
            WorkflowControllerImpl obj = new WorkflowControllerImpl();
            Naming.rebind(rmiPort + WorkflowController.RMIName, obj);
            System.out.println("WC bound");
        } catch (Exception e) {
            System.err.println("WC not bound:" + e);
            System.exit(1);
        }
    }

    // TRANSACTION INTERFACE
    @Override
    public int start() throws RemoteException {
        System.out.println("WC call tm.start() to start a new Trxn.");
        try{
            int newXid = tm.start();
            System.out.println("The new Trxn ID is " + newXid);
            return newXid;
        }catch (RemoteException e) {
            e.printStackTrace();
            System.err.println("WC catch RemoteException when call tm.start() to start a new Trxn.");
            return -1;
        }
    }

    @SuppressWarnings("BusyWait")
    @Override
    public boolean commit(int xid) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        System.out.println("WC call tm.commit() to commit Trxn ID " + xid);
        try{
            tm.commit(xid);
        }catch (RemoteException e){
            // tm die before or after commit, we catch the RemoteException triggered by TM
            System.err.println("WC catch RemoteException when call tm.commit() to commit a new Trxn.");
            e.printStackTrace();
            // wait until tm recover
            while (!reconnect()) {
                try {
                    Thread.sleep(1000);
                } catch (Exception ignored) {
                }
            }
            try {
                Thread.sleep(5000);
            } catch (Exception ignored) {
            }
            System.out.println("WC successfully reconnect to all rms and tm.");
            return tm.ifCommitted(xid);
        }
        return true;
    }

    @Override
    public void abort(int xid) throws RemoteException, InvalidTransactionException {
        System.out.println("WC call tm.abort() to abort Trxn ID " + xid);
        tm.abort(xid, String.format("WC manual abort Trxn ID %d.", xid));
    }


    // ADMINISTRATIVE INTERFACE
    @Override
    public boolean addFlight(int xid, String flightNum, int numSeats, int price) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        if (numSeats < 0) {
            return false;
        }
        if (flightNum == null) {
            return false;
        }
        try {
            Flight check = rmFlights.query(xid, FlightsTable, flightNum);
            if (check == null || check.isDeleted()) {
                rmFlights.insert(xid, FlightsTable, new Flight(flightNum, Math.max(price, 0), numSeats, numSeats));
            } else {
                if (price < 0) {
                    price = check.getPrice();
                }
                int total = check.getNumSeats() + numSeats;
                int avail = check.getNumAvail() + numSeats;
                rmFlights.update(xid, FlightsTable, flightNum, new Flight(flightNum, price, total, avail));
            }
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call addFlight() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call addFlight() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call addFlight() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call addFlight() in Trxn ID " + xid);
        }
        return true;
    }

    @Override
    public boolean deleteFlight(int xid, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        if (flightNum == null) {
            return false;
        }
        try {
            Flight check = rmFlights.query(xid, FlightsTable, flightNum);
            if (check == null || check.isDeleted()) {
                return false;
            }
            if (check.getNumAvail() != check.getNumSeats()) {
                return false;
            }
            rmFlights.delete(xid, FlightsTable, flightNum);
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call deleteFlight() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call deleteFlight() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call deleteFlight() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call deleteFlight() in Trxn ID " + xid);
        }
        return true;
    }

    @Override
    public boolean addRooms(int xid, String location, int numRooms, int price) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        if (numRooms < 0) {
            return false;
        }
        if (location == null) {
            return false;
        }
        try {
            Hotel check = rmHotels.query(xid, RoomsTable, location);
            if (check == null || check.isDeleted()) {
                rmHotels.insert(xid, RoomsTable, new Hotel(location, Math.max(price, 0), numRooms, numRooms));
            } else {
                if (price < 0) {
                    price = check.getPrice();
                }
                int total = check.getNumRooms() + numRooms;
                int avail = check.getNumAvail() + numRooms;
                rmHotels.update(xid, RoomsTable, location, new Hotel(location, price, total, avail));
            }
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call addRooms() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call addRooms() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call addRooms() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call addRooms() in Trxn ID " + xid);
        }
        return true;
    }

    @Override
    public boolean deleteRooms(int xid, String location, int numRooms) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        if (numRooms < 0) {
            return false;
        }
        if (location == null) {
            return false;
        }
        try {
            Hotel check = rmHotels.query(xid, RoomsTable, location);
            if (check == null || check.isDeleted()) {
                return false;
            }
            int total = check.getNumRooms() - numRooms;
            int avail = check.getNumAvail() - numRooms;
            if (avail < 0) {
                return false;
            }
            rmHotels.update(xid, RoomsTable, location, new Hotel(location, check.getPrice(), total, avail));
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call deleteRooms() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call deleteRooms() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call deleteRooms() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call deleteRooms() in Trxn ID " + xid);
        }
        return true;
    }

    @Override
    public boolean addCars(int xid, String location, int numCars, int price) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        if (numCars < 0) {
            return false;
        }
        if (location == null) {
            return false;
        }
        try {
            Car check = rmCars.query(xid, CarsTable, location);
            if (check == null || check.isDeleted()) {
                rmCars.insert(xid, CarsTable, new Car(location, Math.max(price, 0), numCars, numCars));
            } else {
                if (price < 0) {
                    price = check.getPrice();
                }
                int total = check.getNumCars() + numCars;
                int avail = check.getNumAvail() + numCars;
                rmCars.update(xid, CarsTable, location, new Car(location, price, total, avail));
            }
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call addCars() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call addCars() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call addCars() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call addCars() in Trxn ID " + xid);
        }
        return true;
    }

    @Override
    public boolean deleteCars(int xid, String location, int numCars) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        if (numCars < 0) {
            return false;
        }
        if (location == null) {
            return false;
        }
        try {
            Car check = rmCars.query(xid, CarsTable, location);
            if (check == null || check.isDeleted()) {
                return false;
            }
            int total = check.getNumCars() - numCars;
            int avail = check.getNumAvail() - numCars;
            if (avail < 0) {
                return false;
            }
            rmCars.update(xid, CarsTable, location, new Car(location, check.getPrice(), total, avail));
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call deleteCars() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call deleteCars() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call deleteCars() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call deleteCars() in Trxn ID " + xid);
        }
        return true;
    }

    @Override
    public boolean newCustomer(int xid, String custName) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        if (custName == null) {
            return false;
        }
        try {
            Customer check = rmCustomers.query(xid, CustomersTable, custName);
            if (check != null && !check.isDeleted()) {
                return true;
            }
            rmCustomers.insert(xid, CustomersTable, new Customer(custName));
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call newCustomer() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call newCustomer() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call newCustomer() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call newCustomer() in Trxn ID " + xid);
        }
        return true;
    }

    @Override
    public boolean deleteCustomer(int xid, String custName) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        if (custName == null) {
            return false;
        }
        try {
            Customer check = rmCustomers.query(xid, CustomersTable, custName);
            if (check == null || check.isDeleted()) {
                return false;
            }
            rmCustomers.delete(xid, CustomersTable, custName);
            List<Reservation> records = rmReservations.query(xid, ReservationsTable);
            for (Reservation r : records) {
                if (r.isDeleted()) {
                    continue;
                }
                if (r.getCustName().equals(custName)) {
                    rmReservations.delete(xid, ReservationsTable, new Reservation(custName, r.getResvType(), r.getResvKey()));
                }
            }
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call deleteCustomer() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call deleteCustomer() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call deleteCustomer() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call deleteCustomer() in Trxn ID " + xid);
        }
        return true;
    }


    // QUERY INTERFACE
    @Override
    public int queryFlight(int xid, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        int avail;
        try {
            Flight res = rmFlights.query(xid, FlightsTable, flightNum);
            if (res == null || res.isDeleted()) {
                avail = -1;
            } else {
                avail = res.getNumAvail();
            }
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call queryFlight() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call queryFlight() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call queryFlight() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call queryFlight() in Trxn ID " + xid);
        }
        return avail;
    }

    @Override
    public int queryFlightPrice(int xid, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        int price;
        try {
            Flight res = rmFlights.query(xid, FlightsTable, flightNum);
            if (res == null || res.isDeleted()) {
                price = -1;
            } else {
                price = res.getPrice();
            }
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call queryFlightPrice() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call queryFlightPrice() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call queryFlightPrice() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call queryFlightPrice() in Trxn ID " + xid);
        }
        return price;
    }

    @Override
    public int queryRooms(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        int avail;
        try {
            Hotel res = rmHotels.query(xid, RoomsTable, location);
            if (res == null || res.isDeleted()) {
                avail = -1;
            } else {
                avail = res.getNumAvail();
            }
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call queryRooms() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call queryRooms() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call queryRooms() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call queryRooms() in Trxn ID " + xid);
        }
        return avail;
    }

    @Override
    public int queryRoomsPrice(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        int price;
        try {
            Hotel res = rmHotels.query(xid, RoomsTable, location);
            if (res == null || res.isDeleted()) {
                price = -1;
            } else {
                price = res.getPrice();
            }
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call queryRoomsPrice() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call queryRoomsPrice() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call queryRoomsPrice() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call queryRoomsPrice() in Trxn ID " + xid);
        }
        return price;
    }

    @Override
    public int queryCars(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        int avail;
        try {
            Car res = rmCars.query(xid, CarsTable, location);
            if (res == null || res.isDeleted()) {
                avail = -1;
            } else {
                avail = res.getNumAvail();
            }
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call queryCars() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call queryCars() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call queryCars() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call queryCars() in Trxn ID " + xid);
        }
        return avail;
    }

    @Override
    public int queryCarsPrice(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        int price;
        try {
            Car res = rmCars.query(xid, CarsTable, location);
            if (res == null || res.isDeleted()) {
                price = -1;
            } else {
                price = res.getPrice();
            }
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call queryCarsPrice() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call queryCarsPrice() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call queryCarsPrice() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call queryCarsPrice() in Trxn ID " + xid);
        }
        return price;
    }

    @Override
    public int queryCustomerBill(int xid, String custName) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        int total = 0;
        try {
            Object res = rmCustomers.query(xid, CustomersTable, custName);
            if (res == null || ((Customer) res).isDeleted()) {
                return -1;
            }
            List<Reservation> records = rmReservations.query(xid, ReservationsTable);
            for (Reservation r : records) {
                if (r.isDeleted()) {
                    continue;
                }
                if (r.getCustName().equals(custName)) {
                    switch (r.getResvType()) {
                        case CAR: {
                            res = rmCars.query(xid, CarsTable, r.getResvKey());
                            total += ((Car) res).getPrice();
                            break;
                        }
                        case FLIGHT: {
                            res = rmFlights.query(xid, FlightsTable, r.getResvKey());
                            total += ((Flight) res).getPrice();
                            break;
                        }
                        case HOTEL: {
                            res = rmHotels.query(xid, RoomsTable, r.getResvKey());
                            total += ((Hotel) res).getPrice();
                            break;
                        }
                        default: {
                            System.out.println("Wrong reservation " + r);
                        }
                    }
                }
            }
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call queryCustomerBill() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call queryCustomerBill() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call queryCustomerBill() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call queryCustomerBill() in Trxn ID " + xid);
        }
        return total;
    }

    // RESERVATION INTERFACE
    @Override
    public boolean reserveFlight(int xid, String custName, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        if (custName == null) {
            return false;
        }
        if (flightNum == null) {
            return false;
        }
        try {
            Customer checkCust = rmCustomers.query(xid, CustomersTable, custName);
            if (checkCust == null || checkCust.isDeleted()) {
                return false;
            }
            Flight checkFlight = rmFlights.query(xid, FlightsTable, flightNum);
            if (checkFlight == null || checkFlight.isDeleted()) {
                return false;
            }
            if (checkFlight.getNumAvail() == 0) {
                return false;
            }
            if (rmReservations.insert(xid, ReservationsTable, new Reservation(custName, ReservationType.FLIGHT, flightNum)))
                rmFlights.update(
                        xid,
                        FlightsTable,
                        flightNum,
                        new Flight(flightNum, checkFlight.getPrice(), checkFlight.getNumSeats() - 1, checkFlight.getNumAvail() - 1)
                );
            else {
                return false;
            }
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call reserveFlight() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call reserveFlight() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call reserveFlight() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call reserveFlight() in Trxn ID " + xid);
        }
        return true;
    }

    @Override
    public boolean reserveCar(int xid, String custName, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        if (custName == null) {
            return false;
        }
        if (location == null) {
            return false;
        }
        try {
            Customer checkCust = rmCustomers.query(xid, CustomersTable, custName);
            if (checkCust == null || checkCust.isDeleted()) {
                return false;
            }
            Car checkCar = rmCars.query(xid, CarsTable, location);
            if (checkCar == null || checkCar.isDeleted() || checkCar.getNumAvail() == 0) {
                return false;
            }
            if (rmReservations.insert(xid, ReservationsTable, new Reservation(custName, ReservationType.CAR, location)))
                rmCars.update(
                        xid,
                        CarsTable,
                        location,
                        new Car(location, checkCar.getPrice(), checkCar.getNumCars() - 1, checkCar.getNumAvail() - 1)
                );
            else {
                return false;
            }
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call reserveCar() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call reserveCar() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call reserveCar() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call reserveCar() in Trxn ID " + xid);
        }
        return true;
    }

    @Override
    public boolean reserveRoom(int xid, String custName, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        if (custName == null) {
            return false;
        }
        if (location == null) {
            return false;
        }
        try {
            Customer checkCust = rmCustomers.query(xid, CustomersTable, custName);
            if (checkCust == null || checkCust.isDeleted()) {
                return false;
            }
            Hotel checkRoom = rmHotels.query(xid, RoomsTable, location);
            if (checkRoom == null || checkRoom.isDeleted() || checkRoom.getNumAvail() == 0) {
                return false;
            }
            if (rmReservations.insert(xid, ReservationsTable, new Reservation(custName, ReservationType.HOTEL, location)))
                rmHotels.update(
                        xid,
                        RoomsTable,
                        location,
                        new Hotel(location, checkRoom.getPrice(), checkRoom.getNumRooms() - 1, checkRoom.getNumAvail() - 1)
                );

            else {
                return false;
            }
        } catch (DeadlockException e) {
            tm.abort(xid, "WC detect deadlock when it call reserveRoom() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect deadlock when it call reserveRoom() in Trxn ID " + xid);
        } catch (RemoteException e) {
            tm.abort(xid, "WC detect RemoteException when it call reserveRoom() in Trxn ID " + xid);
            throw new TransactionAbortedException(xid, "WC detect RemoteException when it call reserveRoom() in Trxn ID " + xid);
        }
        return true;
    }

    @Override
    public boolean reserveItinerary(int xid, String custName, List<String> flightNumList, String location, boolean needCar, boolean needRoom) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
        for (String flightNum : flightNumList) {
            boolean res = reserveFlight(xid, custName, flightNum);
            if (!res) {
                return false;
            }
        }
        if (needCar) {
            boolean res = reserveCar(xid, custName, location);
            if (!res) {
                return false;
            }
        }
        if (needRoom) {
            boolean res = reserveRoom(xid, custName, location);
            if (!res) {
                return false;
            }
        }
        return true;
    }

    // TECHNICAL/TESTING INTERFACE
    @SuppressWarnings("unchecked")
    @Override
    public boolean reconnect() throws RemoteException {
        System.out.println("Enter WC reconnect()!");
        Properties prop = new Properties();
        try {
            prop.load(Files.newInputStream(Paths.get("conf/ddb.conf")));
        } catch (Exception e1) {
            e1.printStackTrace();
            return false;
        }

        try {
            rmFlights = (ResourceManager<Flight>) Naming.lookup("//localhost:" + prop.getProperty(ResourceManager.RMI_NAME_RM_FLIGHTS + ".port") + "/" + ResourceManager.RMI_NAME_RM_FLIGHTS);
            System.out.println("WC bound to RMFlights");
            rmHotels = (ResourceManager<Hotel>) Naming.lookup("//localhost:" + prop.getProperty(ResourceManager.RMI_NAME_RM_HOTEL + ".port") + "/" + ResourceManager.RMI_NAME_RM_HOTEL);
            System.out.println("WC bound to RMRooms");
            rmCars = (ResourceManager<Car>) Naming.lookup("//localhost:" + prop.getProperty(ResourceManager.RMI_NAME_RM_CARS + ".port") + "/" + ResourceManager.RMI_NAME_RM_CARS);
            System.out.println("WC bound to RMCars");
            rmCustomers = (ResourceManager<Customer>) Naming.lookup("//localhost:" + prop.getProperty(ResourceManager.RMI_NAME_RM_CUSTOMERS + ".port") + "/" + ResourceManager.RMI_NAME_RM_CUSTOMERS);
            System.out.println("WC bound to RMCustomers");
            rmReservations = (ResourceManager<Reservation>) Naming.lookup("//localhost:" + prop.getProperty(ResourceManager.RMI_NAME_RM_RESERVATIONS + ".port") + "/" + ResourceManager.RMI_NAME_RM_RESERVATIONS);
            System.out.println("WC bound to RMReservations");
            tm = (TransactionManager) Naming.lookup("//localhost:" + prop.getProperty("tm.port") + "/" + TransactionManager.RMIName);
            System.out.println("WC bound to TM");
        } catch (Exception e) {
            System.err.println("WC cannot bind to some component:" + e);
            return false;
        }
        // todo: maybe redundant
        try {
            if (rmFlights.testConnection() && rmHotels.testConnection() &&
                    rmCars.testConnection() && rmCustomers.testConnection()) {
                return true;
            }
        } catch (Exception e) {
            System.err.println("Some RM cannot reconnect:" + e);
            return false;
        }

        return false;
    }

    @Override
    public boolean dieNow(String who) throws RemoteException {
        boolean success = true;
        if (who.equals(TransactionManager.RMIName) || who.equals("ALL")) {
            try {
                tm.dieNow();
            } catch (RemoteException e) {
                success = false;
            }
        }
        if (who.equals(ResourceManager.RMI_NAME_RM_FLIGHTS) || who.equals("ALL")) {
            try {
                rmFlights.dieNow();
            } catch (RemoteException e) {
                success = false;
            }
        }
        if (who.equals(ResourceManager.RMI_NAME_RM_HOTEL) || who.equals("ALL")) {
            try {
                rmHotels.dieNow();
            } catch (RemoteException e) {
                success = false;
            }
        }
        if (who.equals(ResourceManager.RMI_NAME_RM_CARS) || who.equals("ALL")) {
            try {
                rmCars.dieNow();
            } catch (RemoteException e) {
                success = false;
            }
        }
        if (who.equals(ResourceManager.RMI_NAME_RM_CUSTOMERS) || who.equals("ALL")) {
            try {
                rmCustomers.dieNow();
            } catch (RemoteException e) {
                success = false;
            }
        }
        if (who.equals(ResourceManager.RMI_NAME_RM_RESERVATIONS) || who.equals("ALL")) {
            try {
                rmReservations.dieNow();
            } catch (RemoteException e) {
                success = false;
            }
        }
        if (who.equals(WorkflowController.RMIName) || who.equals("ALL")) {
            System.exit(1);
        }
        return success;
    }

    @Override
    public boolean dieRMAfterEnlist(String who) throws RemoteException {
        return dieRMWhen(who, ResourceManager.RMDieTime.AfterEnlist);
    }

    @Override
    public boolean dieRMBeforePrepare(String who) throws RemoteException {
        return dieRMWhen(who, ResourceManager.RMDieTime.BeforePrepare);
    }

    @Override
    public boolean dieRMAfterPrepare(String who) throws RemoteException {
        return dieRMWhen(who, ResourceManager.RMDieTime.AfterPrepare);
    }

    @Override
    public boolean dieRMBeforeCommit(String who) throws RemoteException {
        return dieRMWhen(who, ResourceManager.RMDieTime.BeforeCommit);
    }

    @Override
    public boolean dieRMBeforeAbort(String who) throws RemoteException {
        return dieRMWhen(who, ResourceManager.RMDieTime.BeforeAbort);
    }

    private boolean dieRMWhen(String who, ResourceManager.RMDieTime dieTime) throws RemoteException {
        boolean success = true;
        if (who.equals(ResourceManager.RMI_NAME_RM_FLIGHTS) || who.equals("ALL")) {
            try {
                rmFlights.setRMDieTime(dieTime);
            } catch (RemoteException e) {
                success = false;
            }
        }
        if (who.equals(ResourceManager.RMI_NAME_RM_HOTEL) || who.equals("ALL")) {
            try {
                rmHotels.setRMDieTime(dieTime);
            } catch (RemoteException e) {
                success = false;
            }
        }
        if (who.equals(ResourceManager.RMI_NAME_RM_CARS) || who.equals("ALL")) {
            try {
                rmCars.setRMDieTime(dieTime);
            } catch (RemoteException e) {
                success = false;
            }
        }
        if (who.equals(ResourceManager.RMI_NAME_RM_CUSTOMERS) || who.equals("ALL")) {
            try {
                rmCustomers.setRMDieTime(dieTime);
            } catch (RemoteException e) {
                success = false;
            }
        }
        if (who.equals(ResourceManager.RMI_NAME_RM_RESERVATIONS) || who.equals("ALL")) {
            try {
                rmReservations.setRMDieTime(dieTime);
            } catch (RemoteException e) {
                success = false;
            }
        }
        return success;
    }

    @Override
    public boolean dieTMBeforeCommit() throws RemoteException {
        try {
            tm.setTMDieTime(TransactionManager.TMDieTime.BeforeCommit);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean dieTMAfterCommit() throws RemoteException {
        try {
            tm.setTMDieTime(TransactionManager.TMDieTime.AfterCommit);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
