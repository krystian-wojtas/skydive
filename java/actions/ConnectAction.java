package com.skydive.java.actions;

import com.skydive.java.CommHandler;
import com.skydive.java.UavEvent;
import com.skydive.java.data.CalibrationSettings;
import com.skydive.java.data.SignalData;
import com.skydive.java.events.CommEvent;
import com.skydive.java.events.SignalPayloadEvent;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Bartosz Nawrot on 2016-10-13.
 */
public class ConnectAction extends CommHandlerAction {

    private static final long CONNECTION_TIMEOUT = 5000;

    public enum ConnectState {
        IDLE,
        INITIAL_COMMAND,
        WAITING_FOR_CALIBRATION,
        WAITING_FOR_CALIBRATION_DATA,
        FINAL_COMMAND,
    }

    private ConnectState state;

    private boolean connectionProcedureDone;

    private Timer timer;

    public ConnectAction(CommHandler commHandler) {
        super(commHandler);
        state = ConnectState.IDLE;
        connectionProcedureDone = false;
    }

    @Override
    public boolean isActionDone() {
        return connectionProcedureDone;
    }

    @Override
    public void start() {
        System.out.println("Starting connection procedure");
        connectionProcedureDone = false;
        state = ConnectState.INITIAL_COMMAND;
        commHandler.send(new SignalData(SignalData.Command.START_CMD, SignalData.Parameter.START).getMessage());
        startConnectionTimer();
    }

    @Override
    public void handleEvent(CommEvent event) throws Exception {
        ConnectState actualState = state;
        switch (state) {
            case INITIAL_COMMAND:
                if (event.matchSignalData(
                        new SignalData(SignalData.Command.START_CMD, SignalData.Parameter.ACK))) {
                    timer.cancel();
                    state = ConnectState.WAITING_FOR_CALIBRATION;
                    System.out.println("Initial command received successfully");
                } else {
                    System.out.println("Unexpected event received at state " + state.toString());
                }
                break;

            case WAITING_FOR_CALIBRATION:
                if (event.matchSignalData(
                        new SignalData(SignalData.Command.CALIBRATION_SETTINGS, SignalData.Parameter.READY))) {
                    state = ConnectState.WAITING_FOR_CALIBRATION_DATA;
                    System.out.println("Calibration done successfully, data ready");
                } else if (event.matchSignalData(
                        new SignalData(SignalData.Command.CALIBRATION_SETTINGS, SignalData.Parameter.NON_STATIC))) {
                    System.out.println("Calibration non static");
                    commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.CALIBRATION_NON_STATIC));
                } else {
                    System.out.println("Unexpected event received at state " + state.toString());
                }
                break;

            case WAITING_FOR_CALIBRATION_DATA:
                if (event.getType() == CommEvent.EventType.SIGNAL_PAYLOAD_RECEIVED
                        && ((SignalPayloadEvent) event).getDataType() == SignalData.Command.CALIBRATION_SETTINGS_DATA) {
                    SignalPayloadEvent signalEvent = (SignalPayloadEvent) event;

                    CalibrationSettings calibrationSettings = (CalibrationSettings) signalEvent.getData();
                    if (calibrationSettings.isValid()) {
                        System.out.println("Calibration settings received after adHoc calibration");
                        state = ConnectState.FINAL_COMMAND;
                        commHandler.send(new SignalData(SignalData.Command.CALIBRATION_SETTINGS, SignalData.Parameter.ACK).getMessage());
                        commHandler.getUavManager().setCalibrationSettings(calibrationSettings);
                        // send final start command
                        commHandler.send(new SignalData(SignalData.Command.APP_LOOP, SignalData.Parameter.START).getMessage());
                    } else {
                        System.out.println("Calibration settings received but the data is invalid, responding with DATA_INVALID");
                        commHandler.send(new SignalData(SignalData.Command.CALIBRATION_SETTINGS, SignalData.Parameter.DATA_INVALID).getMessage());
                    }
                } else {
                    System.out.println("Unexpected event received at state " + state.toString());
                }
                break;

            case FINAL_COMMAND:
                if (event.matchSignalData(
                        new SignalData(SignalData.Command.APP_LOOP, SignalData.Parameter.ACK))) {
                    connectionProcedureDone = true;
                    state = ConnectState.IDLE;
                    System.out.println("Final command received successfully, connection procedure done");
                    commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.CONNECTED));
                } else {
                    System.out.println("Unexpected event received at state " + state.toString());
                }
                break;

            default:
                throw new Exception("Event: " + event.toString() + " received at unknown state");
        }
        if (actualState != state) {
            System.out.println("HandleEvent done, transition: " + actualState.toString() + " -> " + state.toString());
        } else {
            System.out.println("HandleEvent done, no state change");
        }
    }

    private void startConnectionTimer() {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (state == ConnectState.INITIAL_COMMAND) {
                    commHandler.getUavManager().notifyUavEvent(
                            new UavEvent(UavEvent.Type.ERROR,
                                    "Timeout waiting for initial command response."));
                }
            }
        }, CONNECTION_TIMEOUT);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.CONNECT;
    }
}