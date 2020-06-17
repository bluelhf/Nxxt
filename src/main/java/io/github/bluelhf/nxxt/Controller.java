package io.github.bluelhf.nxxt;

import io.github.bluelhf.nxxt.ext.OpenSimplexNoise;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.NativeInputEvent;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

import java.awt.*;
import java.awt.event.InputEvent;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Controller is the meat and bones of Nxxt.
 * It contains most of the code which interfaces with the GUI (via JavaFX) to read and write values.
 * If you can see it, chances are it's being done by Controller.
 */
public class Controller implements NativeKeyListener {


    // The FXML annotation lets JavaFX know that we want these values replaced with the JavaFX objects whose ids correspond to the field names.
    @FXML
    Label minCPS;
    @FXML
    Label maxCPS;
    @FXML
    ChoiceBox<String> clickTypeBox;
    @FXML
    CheckBox jitterBox;
    @FXML
    HBox jitterControl;
    @FXML
    Slider jitterSlider;
    @FXML
    TextField jitterText;
    @FXML
    CheckBox lfoBox;
    @FXML
    HBox lfoControl;
    @FXML
    Slider lfoSlider;
    @FXML
    TextField lfoText;
    @FXML
    Slider delaySlider;
    @FXML
    TextField delayText;
    @FXML
    Text version;
    @FXML
    Button startButton;
    @FXML
    Button stopButton;
    @FXML
    TitledPane optionsPane;
    @FXML
    Button changeKeybindButton;

    @FXML
    DialogPane errorDialog;
    @FXML
    Label exception;
    @FXML
    Text exception_st;

    KeyEvent keybindEvent = null;

    // These are Nxxt's own values it has to keep track of outside the GUI
    boolean active = false;
    double delay;
    double noLFOdelay;

    Stage stage;

    // These are the external parts - Robot for controlling the mouse, OpenSimplexNoise for generating LFO delay.
    Robot robot;
    OpenSimplexNoise noise;

    // minDelay is the smallest possible delay we can wait in the program.
    // If we fail to calculate it for some reason, it defaults to 20.
    double minDelay = 20.0D;

    // Yay.
    int[] konami = {57416, 57416, 57424, 57424, 57419, 57421, 57419, 57421, 48, 30};
    int idx = 0;


    // Method to shut down properly - very important, actually!
    public void shutdown() {
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            Nxxt.getLogger().severe("Failed to unregister JNativeHook!");
        }
        turnOff();
    }

    // Shorthand for JNI initialisation
    private void initJNI() throws NativeHookException {
        GlobalScreen.registerNativeHook();
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
    }

    // Called by the Launcher class - sets everything up.
    public void initialise(Stage stage) {
        this.stage = stage;
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            Nxxt.getLogger().severe("Failed to create a Robot instance!");
            shutdown();
            Runtime.getRuntime().exit(1);
        }
        try {
            initJNI();
            GlobalScreen.addNativeKeyListener(this);
        } catch (NativeHookException e) {
            _fail(e);
        }

        noise = new OpenSimplexNoise();

        _updateDelay();

        // Make sure sliders edit our internal values
        this.delaySlider.valueProperty().addListener((observableValue, oldValue, newValue) -> {
            lfoBounder();
            this.delayText.setText(String.valueOf(newValue.intValue()));
            _updateDelay();
        });
        this.jitterSlider.valueProperty().addListener((observableValue, number, newValue) -> this.jitterText.setText(String.valueOf(newValue.intValue())));
        this.lfoSlider.valueProperty().addListener((observableValue, number, newValue) -> {
            this.lfoText.setText(String.valueOf(newValue.intValue()));
            _updateCPS();
        });

        // Read properties file to get the project version
        Properties properties = new Properties();
        InputStream propertyStream = Nxxt.class.getResourceAsStream("/info.properties");
        try {
            properties.load(propertyStream);
        } catch (Exception e) {
            Nxxt.getLogger().severe("Failed to read properties!");
        }
        this.version.setText(properties.getProperty("version"));
        _updateCPS();
        Nxxt.getLogger().fine("Initialised controller");


        // We calculate the length of a time slice here. This is the minimum time Thread#sleep(long ms) can wait.
        new Thread(() -> {
            long start = System.currentTimeMillis();
            try {
                Thread.sleep(1L);
            } catch (InterruptedException ignored) {
            }
            this.minDelay = (System.currentTimeMillis() - start);
        });
    }

    /**
     * @return True if the LFO box is checked in the GUI.
     */
    public boolean doLFO() {
        return this.lfoBox.isSelected();
    }

    /**
     * @return The value of the LFO slider.
     */
    public double getLFO() {
        return this.lfoSlider.getValue();
    }

    /**
     * @return True if the Jitter box is checked in the GUI.
     */
    public boolean doJitter() {
        return this.jitterBox.isSelected();
    }

    /**
     * @return The value of the Jitter slider.
     */
    public double getJitter() {
        return this.jitterSlider.getValue();
    }

    /**
     * @return The delay, as specified by a combination of the associated text box and slider. The textbox overrides the value of the slider when this is called.
     */
    public double getDelay() {
        _updateDelay();
        return this.delay;
    }

    /**
     * Sets our internal delay and saves it, displaying it in the GUI.
     */
    public void setDelay(double newDelay) {
        this.delay = newDelay;
        _saveDelay();
    }


    /**
     * @return The value of the drop-down click type box.
     */
    public String getClickType() {
        return this.clickTypeBox.getValue();
    }



    private void lfoBounder() {
        if (_getDelay() < this.minDelay) {
            this.lfoBox.setDisable(true);
            this.lfoBox.setSelected(false);
        } else {
            this.lfoBox.setDisable(false);
        }
        _onLFOBox();

    }
    private double _getDelay() {
        return this.delay;
    }

    private void _setDelay(double newDelay) {
        this.delay = newDelay;
    }

    @FXML
    private void _updateDelay() {
        String delayField = this.delayText.getText();
        String stripped = Arrays.stream(delayField.split("")).filter(s -> s.matches("[0-9]")).collect(Collectors.joining());
        this.delayText.setText(stripped);
        this.delaySlider.setValue(Integer.parseInt(stripped));
        Nxxt.getLogger().finest("Updated delay parameters via text field");
        _setDelay(this.delaySlider.getValue());
        if (!this.active)
            this.noLFOdelay = this.delay;
        _updateCPS();
    }

    private void _saveDelay() {
        Nxxt.getLogger().finest("saving delay " + this.delay);
        this.delaySlider.setValue(this.delay);
        this.delayText.setText(String.valueOf(Math.round(this.delay)));
    }

    /**
     * Not required by outsiders, only here for JavaFX.
     * Triggered when the Jitter checkbox' state changes.
     */
    public void _onJitterBox() {
        this.jitterControl.setVisible(doJitter());
        Nxxt.getLogger().fine("Toggled jitter box");
    }

    /**
     * Not required by outsiders, only here for JavaFX.
     * Triggered when the LFO checkbox' state changes.
     */
    public void _onLFOBox() {
        _updateDelay();
        this.lfoControl.setVisible(doLFO());
        Nxxt.getLogger().fine("Toggled LFO box");
    }

    public void _updateCPS() {
        if (this.delay > this.minDelay) {
            DecimalFormat df = new DecimalFormat("###0.##");
            double minDelay = 1000.0D / (doLFO() ? this.noLFOdelay : this.delay);
            double maxDelay = 1000.0D / (doLFO() ? this.noLFOdelay : this.delay);
            if (doLFO()) {
                minDelay -= getLFO();
                maxDelay += getLFO();
            }
            minDelay = Math.max(0.0D, minDelay);
            this.minCPS.setText("Min. CPS: " + df.format(minDelay));
            this.maxCPS.setText("Max. CPS: " + df.format(maxDelay));
        } else {
            this.minCPS.setText("");
            this.maxCPS.setText("");
        }
    }


    /**
     * Turns Nxxt on.
     */
    public void turnOn() {
        // Disable options, toggle start and stop buttons, set active to true
        this.optionsPane.setDisable(true);
        this.startButton.setDisable(true);
        this.stopButton.setDisable(false);
        this.active = true;

        String val = getClickType();
        int mask = val.equals("Left") ? InputEvent.BUTTON1_DOWN_MASK : (val.equals("Right") ? InputEvent.BUTTON2_DOWN_MASK : InputEvent.BUTTON3_DOWN_MASK);

        // This is the part that actually clicks!
        (new Thread(() -> {
            this.noLFOdelay = getDelay();
            while (this.active) {

                // Handles Jitter
                if (doJitter()) {
                    Point location = MouseInfo.getPointerInfo().getLocation();
                    location.x = (int) (location.x + (Math.random()/Math.nextDown(1) - 0.5D) * 2.0D * getJitter());
                    location.y = (int) (location.y + (Math.random()/Math.nextDown(1) - 0.5D) * 2.0D * getJitter());
                    robot.mouseMove(location.x, location.y);
                }

                // Click-click!
                robot.mousePress(mask);
                robot.mouseRelease(mask);

                if (doLFO()) {
                    // Yay OpenSimplexNoise! Thank you Kurt Spencer :)
                    this.delay = this.noLFOdelay + noise.eval(((System.currentTimeMillis() % 1337.69420) + 1) * getLFO(), 0) * getLFO();
                    Platform.runLater(this::_saveDelay);
                }

                // This is the delay in milliseconds - if it's less than one timeslice, do nothing and go as fast as you can.
                if (this.delay > this.minDelay)
                    try {
                        Thread.sleep((long) this.delay);
                    } catch (Exception ignored) {
                    }
            }
        })).start();
        Nxxt.getLogger().info("Started with delay of " + this.delaySlider.getValue() + " and button mask of " + mask);
    }

    /**
     * Turns Nxxt off.
     */
    public void turnOff() {
        if (!this.active)
            return;

        // Reset the delay to what it was before Nxxt was started
        if (doLFO()) {
            this.delay = this.noLFOdelay;
            _saveDelay();
            _updateCPS();
        }

        // Undo the things we did when we started Nxxt
        this.active = false;
        this.optionsPane.setDisable(false);
        this.startButton.setDisable(false);
        this.stopButton.setDisable(true);
        Nxxt.getLogger().info("Stopped");
    }


    /**
     * Toggles Nxxt.
     */
    public void toggle() {
        if (this.active) {
            turnOff();
        } else {
            turnOn();
        }
    }

    @FXML
    private void _updateJitter() {
        String jitterField = this.jitterText.getText();
        String stripped = Arrays.stream(jitterField.split("")).filter(s -> s.matches("[0-9]")).collect(Collectors.joining());
        this.jitterText.setText(stripped);
        this.jitterSlider.setValue(Integer.parseInt(stripped));
        Nxxt.getLogger().finest("Updated jitter parameters via text field");
    }


    private int getModifiers(KeyEvent ev) {
        if (ev == null) return 0;
        int value = 0;
        if (ev.isAltDown()) value |= NativeInputEvent.ALT_MASK;
        if (ev.isControlDown()) value |= NativeInputEvent.CTRL_MASK;
        if (ev.isMetaDown()) value |= NativeInputEvent.META_MASK;
        if (ev.isShiftDown()) value |= NativeInputEvent.SHIFT_MASK;
        return value;
    }

    public void nativeKeyPressed(NativeKeyEvent ev) {
        int evModifiers = ev.getModifiers();
        if (keybindEvent != null && NativeInputEvent.getModifiersText(evModifiers).contains(NativeInputEvent.getModifiersText(getModifiers(keybindEvent)))
                && ev.getRawCode() == keybindEvent.getCode().getCode())
            toggle();


        Nxxt.getLogger().finest("Key " + ev.getKeyCode() + ", idx = " + this.idx + ", konami: " + this.konami[this.idx % this.konami.length]);
        if (ev.getKeyCode() == this.konami[this.idx] && this.idx == this.konami.length - 1) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Hello!");
                alert.setHeaderText("You have entered the konami code!");
                ImageView image = new ImageView("img.jpg");
                image.setSmooth(true);
                image.setCache(true);
                image.setPreserveRatio(true);
                alert.setGraphic(new ImageView("img.jpg"));
                alert.setContentText("© Suhoset");
                alert.show();
            });
            this.idx = 0; } else if (ev.getKeyCode() == this.konami[this.idx]) { this.idx++;
            this.idx %= this.konami.length; } else { this.idx = 0; }


        String modifiers = NativeInputEvent.getModifiersText(ev.getModifiers());
        Nxxt.getLogger().finest("Got key press " + modifiers + " + " + ev.getKeyCode());
        if (modifiers.equals("Ctrl") && ev
                .getKeyCode() == 49) {
            shutdown();
            Platform.exit();
            Runtime.getRuntime().exit(0);
        }
    }

    public void nativeKeyReleased(NativeKeyEvent ev) {
    }

    public void nativeKeyTyped(NativeKeyEvent ev) {
    }

    public void processKeybindChange(ActionEvent event) {
        changeKeybindButton.setDisable(true);
        Parent root;
        try {
            root = FXMLLoader.load(getClass().getResource("/keybind_ui.fxml"));
            Stage stage = new Stage();
            Scene scene = new Scene(root, 600, 400);
            scene.setOnKeyReleased(keyEvent -> {
                this.keybindEvent = keyEvent;
                changeKeybindButton.setText("Change Keybind (" + keyEventText(keyEvent).toUpperCase() + ")");
                stage.close();
                changeKeybindButton.setDisable(false);
            });
            stage.setOnCloseRequest(windowEvent -> {
                changeKeybindButton.setDisable(false);
            });

            stage.setTitle("Keybind Changer");
            stage.setScene(scene);
            stage.initStyle(StageStyle.UTILITY);
            stage.show();
        } catch (IOException e) {
            Nxxt.getLogger().severe("Failed to start Keybind Changer UI: Could not find file!");
        }
    }

    private boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    private String keyEventText(KeyEvent event) {
        String text = event.getText();
        if (text.charAt(0) == 9) text = "TAB";
        HashMap<String, Boolean> modifiers = new HashMap<>();
        modifiers.put("ALT", event.isAltDown());
        modifiers.put("SHIFT", event.isShiftDown());
        modifiers.put(isMac() ? "CMD" : "CTRL", event.isShortcutDown());

        List<String> keys = modifiers.keySet().stream().filter(modifiers::get).collect(Collectors.toList());
        keys.add(text.toUpperCase());
        return String.join(" + ", keys);
    }

    protected void _fail(Exception e) {
        Parent root = null;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fail_ui.fxml"));
            loader.setController(this);
            root = loader.load();
        } catch (IOException ex) {
            Nxxt.getLogger().severe("Oh, fuck me... Failed to load error dialog. Seriously?");
            ex.printStackTrace();
            Runtime.getRuntime().exit(-1);
        }

        if (exception != null && exception_st != null) {
            exception.setText(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : (e.getMessage() != null ? e.getMessage() : "No details found."));
            exception_st.setText("");
            Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).forEachOrdered((s) -> exception_st.setText(exception_st.getText() + "\n" + s));
        }

        Stage stage = new Stage();
        stage.initStyle(StageStyle.UTILITY);


        try {
            Class<?> dialogClass = errorDialog.getClass();
            Field detailsButtonField = dialogClass.getDeclaredField("detailsButton");
            detailsButtonField.setAccessible(true);
            Node detailsButton = (Node) detailsButtonField.get(errorDialog);
            EventHandler<? super MouseEvent> oldMouseClicked = detailsButton.getOnMouseClicked();
            stage.setResizable(false);
            detailsButton.setOnMouseReleased(mouseEvent -> {
                if (oldMouseClicked != null) oldMouseClicked.handle(mouseEvent);
                Platform.runLater(() -> errorDialog.getScene().getWindow().sizeToScene());
            });

        } catch (NoSuchFieldException | IllegalAccessException exc) {
            exc.printStackTrace();
        }

        errorDialog.lookupButton(ButtonType.CLOSE).setOnMouseClicked(mouseEvent -> {
            stage.close();
            this.shutdown();
            this.stage.close();
            Runtime.getRuntime().exit(1);
        });


        Scene scene = new Scene(root);

        stage.setTitle("An exception occurred.");
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(windowEvent -> Runtime.getRuntime().exit(1));

        Platform.runLater(() -> this.stage.hide());

    }
}