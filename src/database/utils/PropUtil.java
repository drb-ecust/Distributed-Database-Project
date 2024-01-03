package database.utils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class PropUtil {
    public static String getRmiPort(String type){
        Properties prop = new Properties();
        try {
            prop.load(Files.newInputStream(Paths.get("conf/ddb.conf")));
        } catch (Exception e) {
            System.out.println(type + " fail to load configuration file!");
            e.printStackTrace();
            System.exit(1);
        }
        String rmiPort = prop.getProperty(type + ".port");
        return rmiPort;

    }
}
