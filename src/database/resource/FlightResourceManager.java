package database.resource;

import database.entity.Flight;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.Properties;

/**
 * RM for Flight
 */
public class FlightResourceManager extends ResourceManagerImpl<Flight> {

    private FlightResourceManager() throws RemoteException {
        super();
        myRMIName = RMI_NAME_RM_FLIGHTS;
    }

    public static void main(String[] args) {
        myRMIName = RMI_NAME_RM_FLIGHTS;

        Properties prop = new Properties();
        try {
            prop.load(Files.newInputStream(Paths.get("conf/ddb.conf")));
        } catch (Exception e1) {
            e1.printStackTrace();
            return;
        }
        String rmiPort = prop.getProperty(myRMIName + ".port");
        try {
            _rmiRegistry = LocateRegistry.createRegistry(Integer.parseInt(rmiPort));
        } catch (RemoteException e2) {
            e2.printStackTrace();
            return;
        }
        try {
            FlightResourceManager flightResourceManager = new FlightResourceManager();
            _rmiRegistry.bind(myRMIName, flightResourceManager);
            System.out.println(myRMIName + " bound");
        } catch (Exception e) {
            System.err.println(myRMIName + " not bound:" + e);
            System.exit(1);
        }
    }
}
