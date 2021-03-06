/**
 * tgFX Driver Class Copyright Synthetos.com lgpl
 */
package tgfx.tinyg;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import javafx.beans.binding.NumberBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.apache.log4j.Logger;
import tgfx.Main;
import tgfx.ResponseParser;
import tgfx.SerialDriver;
import tgfx.SerialWriter;
import tgfx.ui.gcode.GcodeLine;
import tgfx.system.Axis;
import tgfx.system.Machine;
import tgfx.system.Motor;

public class TinygDriver extends Observable {

    
    private double MINIMAL_BUILD_VERSIONS[] = {377.08, 13.01};
    static final Logger logger = Logger.getLogger(TinygDriver.class);
    public Machine m = Machine.getInstance();
    public QueueReport qr = QueueReport.getInstance();
    public MnemonicManager mneManager = new MnemonicManager();
    public ResponseManager resManager = new ResponseManager();
    public CommandManager cmdManager = new CommandManager();
    private String[] message = new String[2];
    public SimpleBooleanProperty connectionStatus = new SimpleBooleanProperty(false);
    private String platformHardwareName = "";
    
    /**
     * Static commands for TinyG to get settings from the TinyG Driver Board
     */
    public ArrayList<String> connections = new ArrayList<>();
    private SerialDriver ser = SerialDriver.getInstance();
    public static ArrayBlockingQueue<String> jsonQueue = new ArrayBlockingQueue<>(10);
    public static ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(30);
    public static ArrayBlockingQueue<GcodeLine[]> writerQueue = new ArrayBlockingQueue<>(50000);
    public ResponseParser resParse = new ResponseParser(jsonQueue); // Our
    public SerialWriter serialWriter = new SerialWriter(writerQueue);
    private boolean PAUSED = false;
    public final static int MAX_BUFFER = 240;

    /**
     * Singleton Code for the Serial Port Object
     *
     * @return
     */
    public double[] getMINIMAL_BUILD_VERSIONS() {
        return MINIMAL_BUILD_VERSIONS;
    }

//    public voic addMin
//    
//    public void setMINIMAL_BUILD_VERSION(double MINIMAL_BUILD_VERSION) {
//        this.MINIMAL_BUILD_VERSION = MINIMAL_BUILD_VERSION;
//    }
    public void notifyBuildChanged() {

        int _size = this.getMINIMAL_BUILD_VERSIONS().length;
        double _versions[] = this.getMINIMAL_BUILD_VERSIONS();

        if(this.m.getFirmwareVersion().toString().startsWith("13")){
                setPlatformHardwareName("Arduino Due");
            }
        

        for (int i = 0; i <= _size; i++) {
            //Loop to get our minimal versions.  
            //We have more than one since the Due port does versioning different.
            
            
            if (this.m.getFirmwareBuild() <= _versions[i] && this.m.getFirmwareBuild() != 21.01 && this.m.getFirmwareBuild() != 0.0) {
                //too old of a build  we need to tell the GUI about this... This is where PUB/SUB will fix this 
                //bad way of alerting the gui about model changes.
                message[0] = "BUILD_ERROR";
                message[1] = Double.toString(TinygDriver.getInstance().m.getFirmwareBuild());
                setChanged();
                notifyObservers(message);
                logger.info("Build Version: " + TinygDriver.getInstance().m.getFirmwareBuild() + " is NOT OK");
            } else {
                logger.info("Build Version: " + TinygDriver.getInstance().m.getFirmwareBuild() + " is OK");
                message[0] = "BUILD_OK";
                message[1] = null;
                setChanged();
                notifyObservers(message);
            }
        }
    }

    public String getPlatformHardwareName() {
        return platformHardwareName;
    }

    public void setPlatformHardwareName(String platformHardwareName) {
        this.platformHardwareName = platformHardwareName;
    }

    
    
    public static TinygDriver getInstance() {
        return TinygDriverHolder.INSTANCE;
    }

    public void queryHardwareSingleAxisSettings(char c) {
        //Our queryHardwareSingleAxisSetting function for chars
        queryHardwareSingleAxisSettings(String.valueOf(c));
    }

    public void queryHardwareSingleAxisSettings(String _axis) {
        try {
            switch (_axis.toLowerCase()) {
                case "x":
                    serialWriter.write(CommandManager.CMD_QUERY_AXIS_X);
                    break;
                case "y":
                    ser.write(CommandManager.CMD_QUERY_AXIS_Y);
                    break;
                case "z":
                    ser.write(CommandManager.CMD_QUERY_AXIS_Z);
                    break;
                case "a":
                    ser.write(CommandManager.CMD_QUERY_AXIS_A);
                    break;
                case "b":
                    ser.write(CommandManager.CMD_QUERY_AXIS_B);
                    break;
                case "c":
                    ser.write(CommandManager.CMD_QUERY_AXIS_C);
                    break;
            }
        } catch (Exception ex) {
            System.out.println("[!]Error in queryHardwareSingleMotorSettings() " + ex.getMessage());
        }
    }

    public void applyHardwareAxisSettings(Tab _tab) throws Exception {


        GridPane _gp = (GridPane) _tab.getContent();
        int size = _gp.getChildren().size();
        Axis _axis = this.m.getAxisByName(String.valueOf(_gp.getId().charAt(0)));
        int i;
        for (i = 0; i < size; i++) {
            if (_gp.getChildren().get(i).getClass().toString().contains("TextField")) {
                //This ia a TextField... Lets get the value and apply it if it needs to be applied.
                TextField tf = (TextField) _gp.getChildren().get(i);
                applyHardwareAxisSettings(_axis, tf);

            } else if (_gp.getChildren().get(i) instanceof ChoiceBox) {
                //This ia a ChoiceBox... Lets get the value and apply it if it needs to be applied.
                @SuppressWarnings("unchecked")
                ChoiceBox<Object> cb = (ChoiceBox<Object>) _gp.getChildren().get(i);
                if (cb.getId().contains("AxisMode")) {
                    int axisMode = cb.getSelectionModel().getSelectedIndex();
                    String configObj = String.format("{\"%s%s\":%s}\n", _axis.getAxis_name().toLowerCase(), MnemonicManager.MNEMONIC_AXIS_AXIS_MODE, axisMode);
                    this.write(configObj);
                    continue;
                } else if (cb.getId().contains("switchModeMax")) {
                    int switchMode = cb.getSelectionModel().getSelectedIndex();
                    String configObj = String.format("{\"%s%s\":%s}\n", _axis.getAxis_name().toLowerCase(), MnemonicManager.MNEMONIC_AXIS_MAX_SWITCH_MODE, switchMode);
                    this.write(configObj);
                } else if (cb.getId().contains("switchModeMin")) {
                    int switchMode = cb.getSelectionModel().getSelectedIndex();
                    String configObj = String.format("{\"%s%s\":%s}\n", _axis.getAxis_name().toLowerCase(), MnemonicManager.MNEMONIC_AXIS_MIN_SWITCH_MODE, switchMode);
                    this.write(configObj);
                }
            }
        }


        System.out.println("[+]Applying Axis Settings...");
    }

    public void applyHardwareMotorSettings(Motor _motor, TextField tf) throws Exception {
        if (tf.getId().contains("StepAngle")) {
            if (_motor.getStep_angle() != Float.valueOf(tf.getText())) {
                this.write("{\"" + _motor.getId_number() + MnemonicManager.MNEMONIC_MOTOR_STEP_ANGLE + "\":" + tf.getText() + "}\n");
            }
        } else if (tf.getId().contains("TravelPer")) {
            if (_motor.getStep_angle() != Float.valueOf(tf.getText())) {
                this.write("{\"" + _motor.getId_number() + MnemonicManager.MNEMONIC_MOTOR_TRAVEL_PER_REVOLUTION + "\":" + tf.getText() + "}\n");
            }
        }
    }

    public void applyHardwareAxisSettings(Axis _axis, TextField tf) throws Exception {
        /**
         * Apply Axis Settings to TinyG from GUI
         */
        if (tf.getId().contains("maxVelocity")) {
            if (_axis.getVelocityMaximum() != Double.valueOf(tf.getText())) {
                //We check to see if the value passed was already set in TinyG 
                //To avoid un-needed EEPROM Writes.
                this.write("{\"" + _axis.getAxis_name().toLowerCase() + MnemonicManager.MNEMONIC_AXIS_VELOCITY_MAXIMUM + "\":" + tf.getText() + "}\n");
            }
        } else if (tf.getId().contains("maxFeed")) {
            if (_axis.getFeed_rate_maximum() != Double.valueOf(tf.getText())) {
                //We check to see if the value passed was already set in TinyG 
                //To avoid un-needed EEPROM Writes.
                this.write("{\"" + _axis.getAxis_name().toLowerCase() + MnemonicManager.MNEMONIC_AXIS_FEEDRATE_MAXIMUM + "\":" + tf.getText() + "}\n");
            }
        } else if (tf.getId().contains("maxTravel")) {
            if (_axis.getTravel_maximum() != Double.valueOf(tf.getText())) {
                //We check to see if the value passed was already set in TinyG 
                //To avoid un-needed EEPROM Writes.
                this.write("{\"" + _axis.getAxis_name().toLowerCase() + MnemonicManager.MNEMONIC_AXIS_TRAVEL_MAXIMUM + "\":" + tf.getText() + "}\n");
            }
        } else if (tf.getId().contains("maxJerk")) {
            if (_axis.getJerkMaximum() != Double.valueOf(tf.getText())) {
                //We check to see if the value passed was already set in TinyG 
                //To avoid un-needed EEPROM Writes.
                this.write("{\"" + _axis.getAxis_name().toLowerCase() + MnemonicManager.MNEMONIC_AXIS_JERK_MAXIMUM + "\":" + tf.getText() + "}\n");
            }
        } else if (tf.getId().contains("junctionDeviation")) {
            if (Double.valueOf(_axis.getJunction_devation()).floatValue() != Double.valueOf(tf.getText())) {
                //We check to see if the value passed was already set in TinyG 
                //To avoid un-needed EEPROM Writes.
                this.write("{\"" + _axis.getAxis_name().toLowerCase() + MnemonicManager.MNEMONIC_AXIS_JUNCTION_DEVIATION + "\":" + tf.getText() + "}\n");
            }
        } else if (tf.getId().contains("radius")) {
            if (_axis.getAxisType().equals(Axis.AXIS_TYPE.ROTATIONAL)) {
                //Check to see if its a ROTATIONAL AXIS... 
                if (_axis.getRadius() != Double.valueOf(tf.getText())) {
                    //We check to see if the value passed was already set in TinyG 
                    //To avoid un-needed EEPROM Writes.
                    this.write("{\"" + _axis.getAxis_name().toLowerCase() + MnemonicManager.MNEMONIC_AXIS_RADIUS + "\":" + tf.getText() + "}\n");
                }
            }
        } else if (tf.getId().contains("searchVelocity")) {
            if (_axis.getSearch_velocity() != Double.valueOf(tf.getText())) {
                //We check to see if the value passed was already set in TinyG 
                //To avoid un-needed EEPROM Writes.
                this.write("{\"" + _axis.getAxis_name().toLowerCase() + MnemonicManager.MNEMONIC_AXIS_SEARCH_VELOCITY + "\":" + tf.getText() + "}\n");
            }
        } else if (tf.getId().contains("latchVelocity")) {
            if (_axis.getLatch_velocity() != Double.valueOf(tf.getText())) {
                //We check to see if the value passed was already set in TinyG 
                //To avoid un-needed EEPROM Writes.
                this.write("{\"" + _axis.getAxis_name().toLowerCase() + MnemonicManager.MNEMONIC_AXIS_LATCH_VELOCITY + "\":" + tf.getText() + "}\n");
            }
        } else if (tf.getId().contains("latchBackoff")) {
            if (_axis.getLatch_backoff() != Double.valueOf(tf.getText())) {
                //We check to see if the value passed was already set in TinyG 
                //To avoid un-needed EEPROM Writes.
                this.write("{\"" + _axis.getAxis_name().toLowerCase() + MnemonicManager.MNEMONIC_AXIS_LATCH_BACKOFF + "\":" + tf.getText() + "}\n");
            }
        } else if (tf.getId().contains("zeroBackoff")) {
            if (_axis.getZero_backoff() != Double.valueOf(tf.getText())) {
                //We check to see if the value passed was already set in TinyG 
                //To avoid un-needed EEPROM Writes.
                this.write("{\"" + _axis.getAxis_name().toLowerCase() + MnemonicManager.MNEMONIC_AXIS_ZERO_BACKOFF + "\":" + tf.getText() + "}\n");
            }
        }
        System.out.println("[+]Applying " + _axis.getAxis_name() + " settings");

    }

    public void getMotorSettings(int motorNumber) {
        try {
            if (motorNumber == 1) {
                ser.write(CommandManager.CMD_QUERY_MOTOR_1_SETTINGS);
            } else if (motorNumber == 2) {
                ser.write(CommandManager.CMD_QUERY_MOTOR_2_SETTINGS);
            } else if (motorNumber == 3) {
                ser.write(CommandManager.CMD_QUERY_MOTOR_3_SETTINGS);
            } else if (motorNumber == 4) {
                ser.write(CommandManager.CMD_QUERY_MOTOR_4_SETTINGS);
            } else {
                TinygDriver.logger.error("Invalid Motor Number.. Please try again..");
            }
        } catch (Exception ex) {
            TinygDriver.logger.error(ex.getMessage());
        }
    }

    public void applyResponseCommand(responseCommand rc) {
        char _ax;
        switch (rc.getSettingKey()) {

            case (MnemonicManager.MNEMONIC_STATUS_REPORT_LINE):
                TinygDriver.getInstance().m.setLineNumber(Integer.valueOf(rc.getSettingValue()));
                TinygDriver.logger.info("[APPLIED:" + rc.getSettingParent() + " " + rc.getSettingKey() + ":" + rc.getSettingValue());
                break;

            case (MnemonicManager.MNEMONIC_STATUS_REPORT_MOTION_MODE):
                TinygDriver.logger.info("[DID NOT APPLY NEED TO CODE THIS IN:" + rc.getSettingParent() + " " + rc.getSettingKey() + ":" + rc.getSettingValue());
//                TinygDriver.getInstance().m.setMotionMode(Integer.valueOf(rc.getSettingValue()));
                break;

            case (MnemonicManager.MNEMONIC_STATUS_REPORT_POSA):
                _ax = rc.getSettingKey().charAt(rc.getSettingKey().length() - 1);
                TinygDriver.getInstance().m.getAxisByName(String.valueOf(_ax)).setWorkPosition(Float.valueOf(rc.getSettingValue()));
                TinygDriver.logger.info("[APPLIED:" + rc.getSettingParent() + " " + rc.getSettingKey() + ":" + rc.getSettingValue());

                break;

            case (MnemonicManager.MNEMONIC_STATUS_REPORT_POSX):
                _ax = rc.getSettingKey().charAt(rc.getSettingKey().length() - 1);
                TinygDriver.getInstance().m.getAxisByName(String.valueOf(_ax)).setWorkPosition(Float.valueOf(rc.getSettingValue()));
                TinygDriver.logger.info("[APPLIED:" + rc.getSettingParent() + " " + rc.getSettingKey() + ":" + rc.getSettingValue());
                break;

            case (MnemonicManager.MNEMONIC_STATUS_REPORT_POSY):
                _ax = rc.getSettingKey().charAt(rc.getSettingKey().length() - 1);
                TinygDriver.getInstance().m.getAxisByName(String.valueOf(_ax)).setWorkPosition(Float.valueOf(rc.getSettingValue()));
                TinygDriver.logger.info("[APPLIED:" + rc.getSettingParent() + " " + rc.getSettingKey() + ":" + rc.getSettingValue());
                break;

            case (MnemonicManager.MNEMONIC_STATUS_REPORT_POSZ):
                _ax = rc.getSettingKey().charAt(rc.getSettingKey().length() - 1);
                TinygDriver.getInstance().m.getAxisByName(String.valueOf(_ax)).setWorkPosition(Float.valueOf(rc.getSettingValue()));
                TinygDriver.logger.info("[APPLIED:" + rc.getSettingParent() + " " + rc.getSettingKey() + ":" + rc.getSettingValue());
                break;

            case (MnemonicManager.MNEMONIC_STATUS_REPORT_STAT):
                //TinygDriver.getInstance()(Float.valueOf(rc.getSettingValue()));
                TinygDriver.logger.info("[APPLIED:" + rc.getSettingParent() + " " + rc.getSettingKey() + ":" + rc.getSettingValue());
                break;

            case (MnemonicManager.MNEMONIC_STATUS_REPORT_VELOCITY):
                TinygDriver.getInstance().m.setVelocity(Double.valueOf(rc.getSettingValue()));
                TinygDriver.logger.info("[APPLIED:" + rc.getSettingParent() + " " + rc.getSettingKey() + ":" + rc.getSettingValue());
                break;

            default:
                logger.error("[ERROR] in ApplyResponseCommand:  Command Was:" + rc.getSettingParent() + " " + rc.getSettingKey() + ":" + rc.getSettingValue());
                break;
        }
    }

    public void applyHardwareMotorSettings(Tab _tab) throws Exception {
        /**
         * Apply Motor Settings to TinyG from GUI
         */
        Tab selectedTab = _tab.getTabPane().getSelectionModel().getSelectedItem();
        int _motorNumber = Integer.valueOf(selectedTab.getText().split(" ")[1].toString());
        Motor _motor = this.m.getMotorByNumber(_motorNumber);

        GridPane _gp = (GridPane) _tab.getContent();
        int size = _gp.getChildren().size();
        int i;
        //Iterate though each gridpane child... Picking out text fields and choice boxes
        for (i = 0; i < size; i++) {

            if (_gp.getChildren().get(i).toString().contains("TextField")) {
                TextField tf = (TextField) _gp.getChildren().get(i);
                try {
                    applyHardwareMotorSettings(_motor, tf);
                } catch (Exception _ex) {
                    logger.error("[!]Exception in applyHardwareMotorSettings(Tab _tab)");
                }
            } else if (_gp.getChildren().get(i) instanceof ChoiceBox) {
                @SuppressWarnings("unchecked")
                ChoiceBox<Object> _cb = (ChoiceBox<Object>) _gp.getChildren().get(i);
                if (_cb.getId().contains("MapAxis")) {
                    int mapAxis;
                    switch (_cb.getSelectionModel().getSelectedItem().toString()) {
                        case "X":
                            mapAxis = 0;
                            break;
                        case "Y":
                            mapAxis = 1;
                            break;
                        case "Z":
                            mapAxis = 2;
                            break;
                        case "A":
                            mapAxis = 3;
                            break;
                        case "B":
                            mapAxis = 4;
                            break;
                        case "C":
                            mapAxis = 5;
                            break;
                        default:
                            mapAxis = 0;  //Defaults to map to X
                    }
                    String configObj = String.format("{\"%s\":{\"%s\":%s}}\n", _motorNumber, MnemonicManager.MNEMONIC_MOTOR_MAP_AXIS, mapAxis);
                    this.write(configObj);

                } else if (_cb.getId().contains("MicroStepping")) {
                    //This is the MapAxis Choice Box... Lets apply that
                    int microSteps;
                    switch (_cb.getSelectionModel().getSelectedIndex()) {
                        case 0:
                            microSteps = 1;
                            break;
                        case 1:
                            microSteps = 2;
                            break;
                        case 2:
                            microSteps = 4;
                            break;
                        case 3:
                            microSteps = 8;
                            break;
                        default:
                            microSteps = 1;
                    }
                    String configObj = String.format("{\"%s%s\":%s}\n", _motorNumber, MnemonicManager.MNEMONIC_MOTOR_MICROSTEPS, microSteps);
                    this.write(configObj);

                } else if (_cb.getId().contains("Polarity")) {
                    String configObj = String.format("{\"%s%s\":%s}\n", _motorNumber, MnemonicManager.MNEMONIC_MOTOR_POLARITY, _cb.getSelectionModel().getSelectedIndex());
                    this.write(configObj);

                } else if (_cb.getId().contains("PowerMode")) {
                    String configObj = String.format("{\"%s%s\":%s}\n", _motorNumber, MnemonicManager.MNEMONIC_MOTOR_POWER_MANAGEMENT, _cb.getSelectionModel().getSelectedIndex());
                    this.write(configObj);
                }
            }
        }
    }

    public void queryHardwareSingleMotorSettings(int motorNumber) {
        try {
            if (motorNumber == 1) {
                ser.write(CommandManager.CMD_QUERY_MOTOR_1_SETTINGS);
            } else if (motorNumber == 2) {
                ser.write(CommandManager.CMD_QUERY_MOTOR_2_SETTINGS);
            } else if (motorNumber == 3) {
                ser.write(CommandManager.CMD_QUERY_MOTOR_3_SETTINGS);
            } else if (motorNumber == 4) {
                ser.write(CommandManager.CMD_QUERY_MOTOR_4_SETTINGS);
            } else {
                System.out.println("Invalid Motor Number.. Please try again..");
                setChanged();
            }
        } catch (Exception ex) {
            System.out.println("[!]Error in queryHardwareSingleMotorSettings() " + ex.getMessage());


        }
    }

    private TinygDriver() {

        //Setup Logging for TinyG Driver
        if (Main.LOGLEVEL.equals("INFO")) {
//            logger.setLevel(org.apache.log4j.Level.INFO);
        } else if (Main.LOGLEVEL.equals("ERROR")) {
            logger.setLevel(org.apache.log4j.Level.ERROR);
        } else {
            logger.setLevel(org.apache.log4j.Level.OFF);
        }
    }

    private static class TinygDriverHolder {

        private static final TinygDriver INSTANCE = new TinygDriver();
    }

    @Override
    public synchronized void addObserver(Observer obsrvr) {
        super.addObserver(obsrvr);
    }

    public void appendJsonQueue(String line) {
        // This adds full normalized json objects to our jsonQueue.
        TinygDriver.jsonQueue.add(line);
    }

    public synchronized void appendResponseQueue(byte[] queue) {
        // Add byte arrays to the buffer queue from tinyG's responses.
        try {
            TinygDriver.queue.put((byte[]) queue);
        } catch (Exception e) {
            System.out.println("ERROR n shit");
        }
    }

    public boolean isPAUSED() {
        return PAUSED;
    }

    public void setPAUSED(boolean choice) throws Exception {
        if (choice) { // if set to pause
            ser.priorityWrite(CommandManager.CMD_APPLY_PAUSE);
            PAUSED = choice;
        } else { // set to resume
            ser.priorityWrite(CommandManager.CMD_QUERY_OK_PROMPT);
            ser.priorityWrite(CommandManager.CMD_APPLY_RESUME);
            ser.priorityWrite(CommandManager.CMD_QUERY_OK_PROMPT);
            PAUSED = false;
        }
    }

    /**
     * Connection Methods
     */
    public void setConnected(boolean choice) {
        this.ser.setConnected(choice);
    }

    public boolean initialize(String portName, int dataRate) {
        return (this.ser.initialize(portName, dataRate));
    }

    public void disconnect() {
        this.ser.disconnect();
    }

    public SimpleBooleanProperty isConnected() {
        //Our binding to keep tabs in the us of if we are connected to TinyG or not.
        //This is mostly used to disable the UI if we are not connected.
        connectionStatus.set(this.ser.isConnected());
        return (connectionStatus);
    }

    /**
     * All Methods involving writing to TinyG.. This messages will call the
     * SerialDriver write methods from here.
     */
    public synchronized void write(String msg) throws Exception {

        TinygDriver.getInstance().serialWriter.addCommandToBuffer(msg);
        logger.info("Send to Command Buffer >> " + msg);
    }

    public void priorityWrite(Byte b) throws Exception {
        this.ser.priorityWrite(b);
        System.out.println("+" + String.valueOf(b));
    }

    public void priorityWrite(String msg) throws Exception {
        if (!msg.contains("\n")) {
            msg = msg + "\n";
        }
        ser.write(msg);
        System.out.println("+" + msg);
    }

    /**
     *
     *
     *
     *
     * Utility Methods
     *
     * @return
     */
    public String[] listSerialPorts() {
        // Get a listing current system serial ports
        String portArray[] = null;
        portArray = SerialDriver.listSerialPorts();
        return portArray;
    }

    public String getPortName() {
        // Return the serial port name that is connected.
        return ser.serialPort.getName();
    }

    public List<Axis> getInternalAllAxis() {
        return (Machine.getInstance().getAllAxis());
    }
}
