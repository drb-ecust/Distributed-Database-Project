#!/bin/bash


# Test ClientDieRMBeforeAbort
# default java version using JDK 1.8

# step 0: Set Params
CLASS_PATH="./target/classes"
RMI_REGISTRY_PORT=1099

# Create output and error log files
LOG_DIR="logs"
mkdir -p "$LOG_DIR"

output_logs=("$LOG_DIR/output1.log" "$LOG_DIR/output2.log" "$LOG_DIR/output3.log" "$LOG_DIR/output4.log" "$LOG_DIR/output5.log" "$LOG_DIR/output6.log", "$LOG_DIR/output7.log", "$LOG_DIR/output8.log")
error_logs=("$LOG_DIR/error1.log" "$LOG_DIR/error2.log" "$LOG_DIR/error3.log" "$LOG_DIR/error4.log" "$LOG_DIR/error5.log" "$LOG_DIR/error6.log" "$LOG_DIR/error7.log" "$LOG_DIR/error8.log")
# step 1: Start RMI Registry
rmiregistry -J-classpath -J"$CLASS_PATH" "$RMI_REGISTRY_PORT" 1>"${output_logs[0]}" 2>"${error_logs[0]}" &

# step 2: Start TM
java -classpath "$CLASS_PATH" -DrmiPort="$RMI_REGISTRY_PORT" database.transaction.TransactionManagerImpl 1>"${output_logs[1]}" 2>"${error_logs[1]}" &

# step 3: Start RMs
java -classpath "$CLASS_PATH" -DrmiPort="$RMI_REGISTRY_PORT" database.resource.CarResourceManager 1>"${output_logs[2]}" 2>"${error_logs[2]}" &
java -classpath "$CLASS_PATH" -DrmiPort="$RMI_REGISTRY_PORT" database.resource.FlightResourceManager 1>"${output_logs[3]}" 2>"${error_logs[3]}" &
java -classpath "$CLASS_PATH" -DrmiPort="$RMI_REGISTRY_PORT" database.resource.HotelResourceManager 1>"${output_logs[4]}" 2>"${error_logs[4]}" &
java -classpath "$CLASS_PATH" -DrmiPort="$RMI_REGISTRY_PORT" database.resource.CustomerResourceManager 1>"${output_logs[5]}" 2>"${error_logs[5]}" &
java -classpath "$CLASS_PATH" -DrmiPort="$RMI_REGISTRY_PORT" database.resource.ReservationResourceManager 1>"${output_logs[6]}" 2>"${error_logs[6]}" &

# step 4: Start WC
java -classpath "$CLASS_PATH" -DrmiPort="$RMI_REGISTRY_PORT" database.workflow.WorkflowControllerImpl 1>"${output_logs[7]}" 2>"${error_logs[7]}" &

#wait some time to make sure rms, tm and wc have enough time to start up
sleep 5

# step 5: run ClientDieRMBeforeAbort
# wait some time to make sure rms, tm and wc have enough time to start up

java -classpath "$CLASS_PATH" -DrmiPort="$RMI_REGISTRY_PORT" database.client.ClientDieRMBeforeAbort &

# step 6: recover RM Flight
# wait some time to make sure rm already die after prepare
sleep 5
java -classpath "$CLASS_PATH" -DrmiPort="$RMI_REGISTRY_PORT" database.resource.FlightResourceManager 1>"${output_logs[3]}" 2>"${error_logs[3]}" &
