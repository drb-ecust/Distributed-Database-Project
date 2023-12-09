package cn.edu.fudan.ddb.resource;

import cn.edu.fudan.ddb.entity.Car;

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
        myRMIName = ResourceManager.RMI_NAME_RM_CARS;
    }

    public static void main(String[] args) {
        myRMIName = ResourceManager.RMI_NAME_RM_CARS;

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
