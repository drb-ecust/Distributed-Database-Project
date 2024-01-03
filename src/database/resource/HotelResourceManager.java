package database.resource;

import database.entity.Hotel;
import database.utils.PropUtil;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.Properties;

/**
 * RM for Hotel
 */
public class HotelResourceManager extends ResourceManagerImpl<Hotel> {

    private HotelResourceManager() throws RemoteException {
        super();
        myRMIName = RMI_NAME_RM_HOTEL;
    }

    public static void main(String[] args) {
        myRMIName = RMI_NAME_RM_HOTEL;

        String rmiPort = PropUtil.getRmiPort(myRMIName);
        try {
            _rmiRegistry = LocateRegistry.createRegistry(Integer.parseInt(rmiPort));
        } catch (RemoteException e2) {
            e2.printStackTrace();
            return;
        }
        try {
            HotelResourceManager hotelResourceManager = new HotelResourceManager();
            _rmiRegistry.bind(myRMIName, hotelResourceManager);
            System.out.println(myRMIName + " bound");
        } catch (Exception e) {
            System.err.println(myRMIName + " not bound:" + e);
            System.exit(1);
        }
    }
}
