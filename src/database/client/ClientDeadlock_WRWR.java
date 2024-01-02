package database.client;

import database.exception.TransactionAbortedException;
import database.utils.CloseUtils;
import database.workflow.WorkflowController;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.Naming;
import java.util.Properties;
import java.util.Scanner;

/**
 * Client code for test the case when meeting deadlock
 */
public class ClientDeadlock_WRWR {
    public static void main(String[] args) {
        System.out.println("#################### Begin Test ClientDeadlock_WRWR ####################");

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

        //////////
        // transactions for test deadlock case
        //////////
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
            // new transaction 2: add Car and Flight
            //////////
            xid = wc.start();
            System.out.printf("### Start Transaction xid=%d: add Car and Flight.\n", xid);
            wc.addCars(xid, "ShangHai", 50, 200);
            wc.addFlight(xid, "1001", 100, 1000);
            if (!wc.commit(xid)) {
                System.out.printf("### Commit Transaction xid=%d: failed!\n", xid);
                System.exit(1);
            } else {
                System.out.printf("### Commit Transaction xid=%d: success!\n", xid);
            }

            //////////
            // new transaction 3: some queries after add
            //////////
            xid = wc.start();
            System.out.printf("### Start Transaction xid=%d: some queries after add.\n", xid);
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
            // new transaction 4: require X-Lock on Flight and R-Lock on Car, will cause deadlock when executing with f2
            //////////
            Transaction1 f1 = new Transaction1(wc);
            //////////
            // new transaction 5: require R-Lock on Flight and X-Lock on Car, will cause deadlock when executing with f1
            //////////
            Transaction2 f2 = new Transaction2(wc);
            f1.start();
            f2.start();
            f1.join();
            f2.join();

            //////////
            // new transaction 6: check consistency after processing deadlock
            //////////
            xid = wc.start();
            System.out.printf("### Start Transaction xid=%d: check consistency after processing deadlock.\n", xid);
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

        System.out.println("#################### Finish Test ClientDeadlock ####################");

        CloseUtils.close();
        System.exit(0);
    }

    private static class Transaction1 extends Thread {

        private WorkflowController wc = null;

        public Transaction1(WorkflowController wc) {
            this.wc = wc;
        }

        public void run() {
            int xid = -1;
            try {
                xid = wc.start();
                System.out.printf("### Start Transaction xid=%d: require  X-Lock on Flight and R-Lock on Car.\n", xid);
                if (!wc.addFlight(xid, "1001", 100, 1000)) {
                    System.out.println(xid + " : Add Flight failed");
                }
                System.out.printf("Location ShangHai has %s available cars, the price is %d.\n",
                        wc.queryCars(xid, "ShangHai"), wc.queryCarsPrice(xid, "ShangHai"));
                sleep(5000); // sleep some time so that Transaction 2 will earlier require X-Lock on Car

                if (!wc.commit(xid)) {
                    System.out.printf("### Commit Transaction xid=%d: failed!\n", xid);
                    System.exit(1);
                } else {
                    System.out.printf("### Commit Transaction xid=%d: success!\n", xid);
                }
            } catch (TransactionAbortedException e) {
                e.printStackTrace();
                System.out.printf("### Abort Transaction xid=%d due to deliberately deadlock!\n", xid);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private static class Transaction2 extends Thread {

        private WorkflowController wc = null;

        public Transaction2(WorkflowController wc) {
            this.wc = wc;
        }

        public void run() {
            int xid = -1;
            try {
                xid = wc.start();
                System.out.printf("### Start Transaction xid=%d: require X-Lock on Car and R-Lock on Flight.\n", xid);
                if (!wc.addCars(xid, "ShangHai", 50, 200)) {
                    System.out.println(xid + " : Add car failed");
                }
                System.out.printf("Flight 1001 has %s available seats, the price is %d.\n",
                        wc.queryFlight(xid, "1001"), wc.queryFlightPrice(xid, "1001"));
                if (!wc.commit(xid)) {
                    System.out.printf("### Commit Transaction xid=%d: failed!\n", xid);
                    System.exit(1);
                } else {
                    System.out.printf("### Commit Transaction xid=%d: success!\n", xid);
                }
            } catch (TransactionAbortedException e) {
                e.printStackTrace();
                System.out.printf("### Abort Transaction xid=%d due to deliberately deadlock!\n", xid);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }
}
