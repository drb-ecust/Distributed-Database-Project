package database.resource;

import database.entity.Car;
import database.utils.PropUtil;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.Properties;

/**
 * RM for Car
 */
public class CarResourceManager extends ResourceManagerImpl<Car> {

    private CarResourceManager() throws RemoteException {
        super();
        myRMIName = RMI_NAME_RM_CARS;
    }

    public static void main(String[] args) {
        myRMIName = RMI_NAME_RM_CARS;

        String rmiPort = PropUtil.getRmiPort(myRMIName);
        try {
            _rmiRegistry = LocateRegistry.createRegistry(Integer.parseInt(rmiPort));
        } catch (RemoteException e2) {
            e2.printStackTrace();
            return;
        }
        try {
            CarResourceManager carResourceManager = new CarResourceManager();
            _rmiRegistry.bind(myRMIName, carResourceManager);
            System.out.println(myRMIName + " bound");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(myRMIName + " not bound:" + e);
            System.exit(1);
        }
    }
}
