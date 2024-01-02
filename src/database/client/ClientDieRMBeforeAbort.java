package database.client;

import database.resource.ResourceManager;
import database.utils.CloseUtils;
import database.workflow.WorkflowController;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.Naming;
import java.util.Properties;
import java.util.Scanner;

/**
 * Client code for test the case when RM die before abort
 */
public class ClientDieRMBeforeAbort {
    public static void main(String[] args) {
        System.out.println("#################### Begin Test ClientDieRMBeforeAbort ####################");

        //////////
        // Read config and get wc.port
        //////////
        Properties prop = new Properties();
        try {
            prop.load(Files.newInputStream(Paths.get("conf/ddb.conf")));
        } catch (Exception e1) {
            e1.printStackTrace();
            return;
        }

        String rmiPort = prop.getProperty("wc.port");
        if (rmiPort == null) {
            rmiPort = "";
        } else if (!rmiPort.isEmpty()) {
            rmiPort = "//:" + rmiPort + "/";
        }

        //////////
        // Bind to WC
        //////////
        WorkflowController wc = null;
        try {
            wc = (WorkflowController) Naming.lookup(rmiPort + WorkflowController.RMIName);
            System.out.println("Bind to WC");
        } catch (Exception e) {
            System.err.println("Cannot bind to WC:" + e);
            System.exit(1);
        }

        int xid;
        try {
            //////////
            // new transaction 1: some queries before add
            //////////
            xid = wc.start();
            System.out.printf("### Start Transaction xid=%d: some queries before add.\n", xid);
            System.out.printf("Flight 1001 has %s available seats, the price is %d.\n",
                    wc.queryFlight(xid, "1001"), wc.queryFlightPrice(xid, "1001"));
            System.out.printf("Location ShangHai has %s available cars, the price is %d.\n",
                    wc.queryCars(xid, "ShangHai"), wc.queryCarsPrice(xid, "ShangHai"));
            System.out.printf("Location ShangHai has %s available rooms, the price is %d.\n",
                    wc.queryRooms(xid, "ShangHai"), wc.queryRoomsPrice(xid, "ShangHai"));
            System.out.printf("Tom costs %d dollars.\n", wc.queryCustomerBill(xid, "Tom"));
            if (!wc.commit(xid)) {
                System.out.printf("### Commit Transaction xid=%d: failed!\n", xid);
                System.exit(1);
            } else {
                System.out.printf("### Commit Transaction xid=%d: success!\n", xid);
            }


            //////////
            // new transaction 2: add Flight, Car, Room and new Customer
            //////////
            xid = wc.start();
            System.out.printf("### Start Transaction xid=%d: add Flight, Car, Room and new Customer.\n", xid);
            if (!wc.addFlight(xid, "1001", 100, 1000)) {
                System.out.println("Add Flight failed");
            }
            if (!wc.addCars(xid, "ShangHai", 50, 200)) {
                System.out.println("Add Car failed");
            }
            if (!wc.addRooms(xid, "ShangHai", 50, 200)) {
                System.out.println("Add Room failed");
            }
            if (!wc.newCustomer(xid, "Tom")) {
                // if custName already exits, newCustomer() also return true
                System.out.println("New customer failed");
            }
            if (!wc.commit(xid)) {
                System.out.printf("### Commit Transaction xid=%d: failed!\n", xid);
                System.exit(1);
            } else {
                System.out.printf("### Commit Transaction xid=%d: success!\n", xid);
            }

            //////////
            // new transaction 3: some queries after add and before reserve
            //////////
            xid = wc.start();
            System.out.printf("### Start Transaction xid=%d: some queries after add and before reserve.\n", xid);
            System.out.printf("Flight 1001 has %s available seats, the price is %d.\n",
                    wc.queryFlight(xid, "1001"), wc.queryFlightPrice(xid, "1001"));
            System.out.printf("Location ShangHai has %s available cars, the price is %d.\n",
                    wc.queryCars(xid, "ShangHai"), wc.queryCarsPrice(xid, "ShangHai"));
            System.out.printf("Location ShangHai has %s available rooms, the price is %d.\n",
                    wc.queryRooms(xid, "ShangHai"), wc.queryRoomsPrice(xid, "ShangHai"));
            System.out.printf("Tom costs %d dollars.\n", wc.queryCustomerBill(xid, "Tom"));
            if (!wc.commit(xid)) {
                System.out.printf("### Commit Transaction xid=%d: failed!\n", xid);
                System.exit(1);
            } else {
                System.out.printf("### Commit Transaction xid=%d: success!\n", xid);
            }


            //////////
            // new transaction 4: reserve Flight, Car and Room, but RM will die before abort
            //////////
            xid = wc.start();
            System.out.printf("### Start Transaction xid=%d: reserve Flight, Car and Room, but RM will die before commit.\n", xid);
            wc.dieRMBeforeAbort(ResourceManager.RMI_NAME_RM_FLIGHTS); // set a flag and info Flight RM to die before the next abort
            if (!wc.reserveFlight(xid, "Tom", "1001")) {
                System.out.println("Reserve Flight failed");
            }
            if (!wc.reserveCar(xid, "Tom", "ShangHai")) {
                System.out.println("Reserve Car failed");
            }
            if (!wc.reserveRoom(xid, "Tom", "ShangHai")) {
                System.out.println("Reserve Room failed");
            }
            try{
                wc.abort(xid);
                System.out.printf("### Abort Transaction xid=%d: success! If RM die before abort, it will redo abort after recover, the consistency is still guaranteed.\n", xid);
            }catch (Exception e){
                e.printStackTrace();
                System.exit(1);
            }


            //////////
            // new transaction 5: check consistency after processing RM die
            //////////
            // wait until rm recover
            while (!wc.reconnect()) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(1000);
                } catch (Exception ignored) {
                }
            }
            try {
                Thread.sleep(5000);
            } catch (Exception ignored) {
            }
            xid = wc.start();
            System.out.printf("### Start Transaction xid=%d: check consistency after processing RM die.\n", xid);
            System.out.printf("Flight 1001 has %s available seats, the price is %d.\n",
                    wc.queryFlight(xid, "1001"), wc.queryFlightPrice(xid, "1001"));
            System.out.printf("Location ShangHai has %s available cars, the price is %d.\n",
                    wc.queryCars(xid, "ShangHai"), wc.queryCarsPrice(xid, "ShangHai"));
            System.out.printf("Location ShangHai has %s available rooms, the price is %d.\n",
                    wc.queryRooms(xid, "ShangHai"), wc.queryRoomsPrice(xid, "ShangHai"));
            System.out.printf("Tom costs %d dollars.\n", wc.queryCustomerBill(xid, "Tom"));
            if (!wc.commit(xid)) {
                System.out.printf("### Commit Transaction xid=%d: failed!\n", xid);
                System.exit(1);
            } else {
                System.out.printf("### Commit Transaction xid=%d: success!\n", xid);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("#################### Finish Test ClientDieRMBeforeAbort ####################");


        CloseUtils.close();
        System.exit(0);
    }
}
