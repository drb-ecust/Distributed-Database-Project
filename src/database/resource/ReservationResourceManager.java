package database.resource;

import database.entity.Reservation;
import database.utils.PropUtil;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.Properties;

/**
 * RM for Reservation
 */
public class ReservationResourceManager extends ResourceManagerImpl<Reservation> {

    private ReservationResourceManager() throws RemoteException {
        super();
        myRMIName = RMI_NAME_RM_RESERVATIONS;
    }

    public static void main(String[] args) {
        myRMIName = RMI_NAME_RM_RESERVATIONS;

        String rmiPort = PropUtil.getRmiPort(myRMIName);
        try {
            _rmiRegistry = LocateRegistry.createRegistry(Integer.parseInt(rmiPort));
        } catch (RemoteException e2) {
            e2.printStackTrace();
            return;
        }
        try {
            ReservationResourceManager reservationResourceManager = new ReservationResourceManager();
            _rmiRegistry.bind(myRMIName, reservationResourceManager);
            System.out.println(myRMIName + " bound");
        } catch (Exception e) {
            System.err.println(myRMIName + " not bound:" + e);
            System.exit(1);
        }
    }
}
