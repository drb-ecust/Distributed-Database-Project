package database.resource;

import database.entity.Customer;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.Properties;

/**
 * RM for Customer
 */
public class CustomerResourceManager extends ResourceManagerImpl<Customer> {

    private CustomerResourceManager() throws RemoteException {
        super();
        myRMIName = ResourceManager.RMI_NAME_RM_CUSTOMERS;
    }

    public static void main(String[] args) {
        myRMIName = ResourceManager.RMI_NAME_RM_CUSTOMERS;

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
            CustomerResourceManager customerResourceManager = new CustomerResourceManager();
            _rmiRegistry.bind(myRMIName, customerResourceManager);
            System.out.println(myRMIName + " bound");
        } catch (Exception e) {
            System.err.println(myRMIName + " not bound:" + e);
            System.exit(1);
        }
    }
}
