@echo off


@REM Test ClientDie2RM
@REM default java version using JDK 1.8

@REM step 0: Set Params
SET CLASS_PATH=".\target\classes"
SET RMI_REGISTRY_PORT=1099

@REM step 1: Start RMI Registry
start "RMI-REGISTRY" /min rmiregistry -J-classpath -J%CLASS_PATH% %RMI_REGISTRY_PORT%

@REM step 2: Start TM
start "TM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.transaction.TransactionManagerImpl

@REM step 3: Start RMs
start "Car-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.rm.CarResourceManager
start "Flight-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.rm.FlightResourceManager
start "Hotel-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.rm.HotelResourceManager
start "Customer-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.rm.CustomerResourceManager
start "Reservation-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.rm.ReservationResourceManager

@REM step 4: Start WC
start "WC" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.workflow.WorkflowControllerImpl

@REM step 5: run ClientDieRMAfterEnlist
@REM wait some time to make sure rms, tm and wc have enough time to start up
timeout /t 5 /nobreak >nul
@REM start "Client-Die-RM-After-Enlist" java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.client.ClientDieRMAfterEnlist


start "ClientDie2RM" java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.client.ClientDie2RM


@REM step 6: recover RM Flight
@REM wait some time to make sure rm already die after enlist
timeout /t 5 /nobreak >nul
@REM start "RMI-REGISTRY-Recovered" /min rmiregistry -J-classpath -J%CLASS_PATH% %RMI_REGISTRY_PORT%
@REM start "TM-Recovered" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.transaction.TransactionManagerImpl
@REM start "Car-RM-Recovered" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.rm.CarResourceManager
start "Flight-RM-Recovered" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.rm.FlightResourceManager
start "Hotel-RM-Recovered" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.rm.HotelResourceManager
@REM start "Customer-RM-Recovered" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.rm.CustomerResourceManager
@REM start "Reservation-RM-Recovered" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.rm.ReservationResourceManager
@REM
@REM @REM step 4: Start WC
@REM start "WC-Recovered" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.workflow.WorkflowControllerImpl
