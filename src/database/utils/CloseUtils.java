package database.utils;

import java.io.IOException;
import java.util.Scanner;

public class CloseUtils {
    /**
     * close the created window in Windows
     * @param windowTitle the title of window
     */
    public static void closeWindow(String windowTitle) {

        String command = "taskkill /F /FI \"WINDOWTITLE eq " + windowTitle + "\"";

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void closeProcess(String ProcessTitle){
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("pkill", "-f", ProcessTitle);
            processBuilder.start();
        } catch (IOException e){
            e.printStackTrace();
        }
    }
    public static void removeDataWin(){

        String command = "rd /s /q data";

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    public static void removeDataLinux(){

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("rm", "-rf", "data");
            processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public static void close(){
        String os = System.getProperty("os.name").toLowerCase();
        String[] Windows = {"TM", "Car-RM", "Hotel-RM", "Flight-RM", "Customer-RM", "Reservation-RM","RMI-REGISTRY", "WC",
                "TM-Recovered", "Car-RM-Recovered", "Hotel-RM-Recovered", "Flight-RM-Recovered",
                "Customer-RM-Recovered", "Reservation-RM-Recovered", "RMI-REGISTRY-Recovered", "WC-Recovered"};

        String[] Processes = {"rmiregistry", "TransactionManagerImpl", "CarResourceManager", "FlightResourceManager",
                "HotelResourceManager", "CustomerResourceManager", "ReservationResourceManager", "WorkflowControllerImpl"};
        if (os.contains("win")) {
            // Windows OS
            // press to exit.
            Scanner scanner = new Scanner(System.in);
            System.out.println("Press any key to exit.......");
            scanner.nextLine();

            // close all windows
            for (String window : Windows){
                closeWindow(window);

            }
            // delete data dir.
            removeDataWin();

        } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
            // Unix/Linux/Mac OS
            System.out.println("exit...");
            for (String process : Processes){
                closeProcess(process);

            }
            removeDataLinux();

        } else {
            System.out.println("Unsupported operating system: " + os);
            return;
        }

    }

}
