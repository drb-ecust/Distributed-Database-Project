package cn.edu.fudan.ddb.resource;

import cn.edu.fudan.ddb.entity.Hotel;

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
        myRMIName = ResourceManager.RMI_NAME_RM_HOTEL;
    }

    public static void main(String[] args) {
        myRMIName = ResourceManager.RMI_NAME_RM_HOTEL;

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
            HotelResourceManager hotelResourceManager = new HotelResourceManager();
            _rmiRegistry.bind(myRMIName, hotelResourceManager);
            System.out.println(myRMIName + " bound");
        } catch (Exception e) {
            System.err.println(myRMIName + " not bound:" + e);
            System.exit(1);
        }
    }
}
