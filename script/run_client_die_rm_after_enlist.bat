@echo off


@REM Test ClientDieRMAfterEnlist
@REM default java version using JDK 1.8

@REM step 0: Set Params
SET CLASS_PATH=".\target\classes"
SET RMI_REGISTRY_PORT=1099

@REM step 1: Start RMI Registry
start "RMI-REGISTRY" /min rmiregistry -J-classpath -J%CLASS_PATH% %RMI_REGISTRY_PORT%

@REM step 2: Start TM
start "TM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% cn.edu.fudan.ddb.transaction.TransactionManagerImpl

@REM step 3: Start RMs
start "Car-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% cn.edu.fudan.ddb.resource.CarResourceManager
start "Flight-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% cn.edu.fudan.ddb.resource.FlightResourceManager
start "Hotel-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% cn.edu.fudan.ddb.resource.HotelResourceManager
start "Customer-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% cn.edu.fudan.ddb.resource.CustomerResourceManager
start "Reservation-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% cn.edu.fudan.ddb.resource.ReservationResourceManager

@REM step 4: Start WC
start "WC" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% cn.edu.fudan.ddb.workflow.WorkflowControllerImpl

@REM step 5: run ClientDieRMAfterEnlist
@REM wait some time to make sure rms, tm and wc have enough time to start up
timeout /t 5 /nobreak >nul
start "Client-Die-RM-After-Enlist" java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% cn.edu.fudan.ddb.client.ClientDieRMAfterEnlist

@REM step 6: recover RM Flight
@REM wait some time to make sure rm already die after enlist
timeout /t 5 /nobreak >nul
start "Hotel-RM-Recovered" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% cn.edu.fudan.ddb.resource.HotelResourceManager