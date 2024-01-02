@echo off


@REM Test ClientDieRMBeforeAbort
@REM default java version using JDK 1.8

@REM step 0: Set Params
SET CLASS_PATH=".\target\classes"
SET RMI_REGISTRY_PORT=1099

@REM step 1: Start RMI Registry
start "RMI-REGISTRY" /min rmiregistry -J-classpath -J%CLASS_PATH% %RMI_REGISTRY_PORT%

@REM step 2: Start TM
start "TM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.transaction.TransactionManagerImpl

@REM step 3: Start RMs
start "Car-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.resource.CarResourceManager
start "Flight-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.resource.FlightResourceManager
start "Hotel-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.resource.HotelResourceManager
start "Customer-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.resource.CustomerResourceManager
start "Reservation-RM" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.resource.ReservationResourceManager

@REM step 4: Start WC
start "WC" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.workflow.WorkflowControllerImpl

@REM step 5: run ClientDieRMBeforeAbort
@REM wait some time to make sure rms, tm and wc have enough time to start up
timeout /t 5 /nobreak >nul
start "Client-Die-RM-Before-Abort" java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.client.ClientDieRMBeforeAbort

@REM step 6: recover TM
@REM wait some time to make sure rm already die before abort
timeout /t 5 /nobreak >nul
start "Flight-RM-Recovered" /min java -classpath %CLASS_PATH% -DrmiPort=%RMI_REGISTRY_PORT% database.resource.FlightResourceManager