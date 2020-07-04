package io.github.bluelhf.nxxt;

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
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.List;
import java.util.*;
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
    @FXML Label minCPS;
    @FXML Label maxCPS;
    @FXML ChoiceBox<String> clickTypeBox;
    @FXML CheckBox jitterBox;
    @FXML HBox jitterControl;
    @FXML Slider jitterSlider;
    @FXML TextField jitterText;
    @FXML CheckBox lfoBox;
    @FXML HBox lfoControl;
    @FXML Slider lfoSlider;
    @FXML TextField lfoText;
    @FXML Slider delaySlider;
    @FXML TextField delayText;
    @FXML Text version;
    @FXML Button startButton;
    @FXML Button stopButton;
    @FXML TitledPane optionsPane;
    @FXML Button changeKeybindButton;

    @FXML DialogPane errorDialog;
    @FXML Label exception;
    @FXML Text exception_st;

    KeyEvent keybindEvent = null;
    Stage stage;

    Clicker clicker;
    Timer ticker = new Timer();
    // Yay.
    int[] konami = {57416, 57416, 57424, 57424, 57419, 57421, 57419, 57421, 48, 30};
    int idx = 0;
    private double mergeDelay = 69;
    private double mergeLFO = 1;
    private double mergeJitter = 4;

    // Called by the Launcher class - sets everything up.
    public void initialise(Stage stage) {
        this.stage = stage;
        try {
            clicker = new Clicker( mergeDelay, mergeJitter, mergeLFO);
            initJNI();
        } catch (NativeHookException | AWTException e) {
            _fail(e);
        }

        // Make sure sliders edit our internal values
        delaySlider.valueProperty().addListener((observableValue, oldValue, newValue) -> delaySliderOverride());
        jitterSlider.valueProperty().addListener((observableValue, number, newValue) -> jitterSliderOverride());
        lfoSlider.valueProperty().addListener((observableValue, number, newValue) -> lfoSliderOverride());

        // Read properties file to get the project version
        Properties properties = new Properties();
        InputStream propertyStream = Nxxt.class.getResourceAsStream("/info.properties");
        try {
            properties.load(propertyStream);
        } catch (Exception e) {
            Nxxt.getLogger().severe("Failed to read properties!");
        }
        this.version.setText(properties.getProperty("version"));
        ticker.scheduleAtFixedRate(new TimerTask() {@Override public void run() { tick(); }}, 0, 100);
    }

    // Method to shut down properly - very important, actually!
    public void shutdown() {
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            Nxxt.getLogger().severe("Failed to unregister JNativeHook!");
        }
        turnOff();
        ticker.cancel();
    }

    /**
     * @return The value of the drop-down click type box.
     */
    public String getClickType() {
        return this.clickTypeBox.getValue();
    }

    // Shorthand for JNI initialisation
    private void initJNI() throws NativeHookException {
        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeKeyListener(this);
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
    }

    /**
     * @return The Clicker object this Controller talks to
     */
    @SuppressWarnings("unused")
    public Clicker getClicker() {
        return clicker;
    }

    private ClickerSettings.ClickType clickerType() {
        String val = getClickType();
        return val.equals("Left") ? ClickerSettings.ClickType.LEFT
            : (val.equals("Right") ? ClickerSettings.ClickType.RIGHT
            : ClickerSettings.ClickType.MIDDLE);
    }

    public void turnOn() {
        // Disable options, toggle start and stop buttons, set active to true
        this.optionsPane.setDisable(true);
        this.startButton.setDisable(true);
        this.stopButton.setDisable(false);
        clicker.getSettings().setClickType(clickerType());
        clicker.start();
    }

    public void turnOff() {
        clicker.stop();

        this.optionsPane.setDisable(false);
        this.startButton.setDisable(false);
        this.stopButton.setDisable(true);
    }

    public void toggle() {
        if (clicker.isEnabled()) turnOff(); else turnOn();
    }

    private void tick() {
        Platform.runLater(() -> {

            // Update sliders from merged values
            delaySlider.setValue(mergeDelay);
            jitterSlider.setValue(mergeJitter);
            lfoSlider.setValue(mergeLFO);

            // Update our Clicker object with the merged values
            clicker.getSettings().setClickType(clickerType());
            clicker.getSettings().setLFO(lfoBox.isSelected() ? mergeLFO : -1);
            clicker.getSettings().setDelay(mergeDelay);
            clicker.getSettings().setJitter(jitterBox.isSelected() ? mergeJitter : -1);

            // Update our CPS
            if (mergeDelay > clicker.calcMinSlice().getNow(-1)) {
                lfoBox.setVisible(true);
                lfoControl.getChildren().forEach(node -> node.setVisible(lfoBox.isSelected()));
                DecimalFormat df = new DecimalFormat("###0.##");
                double minDelay = 1000.0D / (mergeDelay);
                double maxDelay = 1000.0D / (mergeDelay);
                if (lfoBox.isSelected()) {
                    minDelay -= mergeLFO;
                    maxDelay += mergeLFO;

                    minDelay = Math.max(0.0D, minDelay);
                    minCPS.setText("Min. CPS: " + df.format(minDelay));
                    maxCPS.setText("Max. CPS: " + df.format(maxDelay));
                } else {
                    minCPS.setText("CPS: " + df.format(minDelay));
                    maxCPS.setText("");
                }
            } else {
                lfoBox.setVisible(false);
                lfoControl.getChildren().forEach(node -> node.setVisible(false));
                minCPS.setText("CPS: Max");
                maxCPS.setText("");
            }
        });
    }

    // These methods are called by FXML or by our sliders' chaneg listeners to decide how to merge everything
    @FXML private void delayTextOverride() {
        mergeDelay = textOverride(delaySlider, delayText);
    }

    @FXML private void delaySliderOverride() {
        mergeDelay = sliderOverride(delaySlider, delayText);
    }

    @FXML private void jitterTextOverride() {
        mergeJitter = textOverride(jitterSlider, jitterText);
    }

    @FXML private void jitterSliderOverride() {
        mergeJitter = sliderOverride(jitterSlider, jitterText);
    }

    @FXML private void lfoTextOverride() {
        mergeLFO = textOverride(lfoSlider, lfoText);
    }

    @FXML private void lfoSliderOverride() {
        mergeLFO = sliderOverride(lfoSlider, lfoText);
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

    // These just handle overriding sliders with text or text with sliders
    private double textOverride(Slider slider, TextField text) {
        String textField = text.getText();
        String stripped = Arrays.stream(textField.split("")).filter(s -> s.matches("[0-9]")).collect(Collectors.joining());
        text.setText(stripped);
        slider.setValue(Integer.parseInt(stripped));
        return slider.getValue();
    }

    public void nativeKeyReleased(NativeKeyEvent ev) {
    }

    public void nativeKeyTyped(NativeKeyEvent ev) {
    }

    private double sliderOverride(Slider slider, TextField text) {
        text.setText(String.valueOf((int) slider.getValue()));
        return slider.getValue();
    }

    private boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    private String keyEventText(KeyEvent event) {
        String text = event.getText();
        HashMap<String, Boolean> modifiers = new HashMap<>();
        modifiers.put("ALT", event.isAltDown());
        modifiers.put("SHIFT", event.isShiftDown());
        modifiers.put(isMac() ? "CMD" : "CTRL", event.isShortcutDown());

        // Edge-case: Function keys
        if (event.getCode().getCode() >= 112 && event.getCode().getCode() <= 123) text = event.getCode().getName();

        // Edge-case: Modifier only (-1 means incomplete key)
        if (modifiers.values().stream().anyMatch(Boolean::booleanValue) && text.length() == 0) return "-1";

        // Edge-case: Default to NONE
        if (modifiers.values().stream().noneMatch(Boolean::booleanValue) && text.length() == 0) return "NONE";

        // Edge-case: Tabs
        if (text.length() != 0 && text.charAt(0) == 9) text = "TAB";


        List<String> keys = modifiers.keySet().stream().filter(modifiers::get).collect(Collectors.toList());
        if (text.length() != 0) keys.add(text.toUpperCase());
        return String.join(" + ", keys);
    }

    public void nativeKeyPressed(NativeKeyEvent ev) {
        String modifiers = NativeInputEvent.getModifiersText(ev.getModifiers());
        if (modifiers.equals("Ctrl") && ev
                .getKeyCode() == 3663) {
            shutdown();
            Platform.exit();
            Runtime.getRuntime().exit(0);
        }
        if (modifiers.equals("Ctrl") && ev.getKeyCode() == 3667) {
            Platform.runLater(() -> _fail(new Exception("Ctrl + Delete force-kill")));
        }

        int evModifiers = ev.getModifiers();
        Nxxt.getLogger().info("Pressed key " + ev.getRawCode() + " and keybind was " + (keybindEvent != null ? keybindEvent.getCode().getCode() : "not set"));
        if (keybindEvent != null
                && NativeInputEvent.getModifiersText(evModifiers).contains(NativeInputEvent.getModifiersText(getModifiers(keybindEvent)))
                && ev.getRawCode() == keybindEvent.getCode().getCode()) { toggle(); }


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
            this.idx = 0;
        } else if (ev.getKeyCode() == this.konami[this.idx]) {
            this.idx++;
            this.idx %= this.konami.length;
        } else {
            this.idx = 0;
        }
    }

    @SuppressWarnings("unused")
    public void processKeybindChange(ActionEvent event) {
        changeKeybindButton.setDisable(true);
        Parent root;
        try {
            root = FXMLLoader.load(getClass().getResource("/keybind_ui.fxml"));
            Stage stage = new Stage();
            Scene scene = new Scene(root, 600, 400);
            scene.setOnKeyPressed(keyEvent -> {
                if (this.keybindEvent != null && keyEventText(this.keybindEvent).equals(keyEventText(keyEvent))) return;
                this.keybindEvent = keyEvent;
                String text = keyEventText(keyEvent).toUpperCase();
                if (!text.equals("-1")) {
                    changeKeybindButton.setText("Change Keybind (" + text + ")");
                    stage.close();
                    changeKeybindButton.setDisable(false);
                }

            });
            stage.setOnCloseRequest(windowEvent -> changeKeybindButton.setDisable(false));

            stage.setTitle("Keybind Changer");
            stage.setScene(scene);
            stage.initStyle(StageStyle.UTILITY);
            stage.show();
        } catch (IOException e) {
            Nxxt.getLogger().severe("Failed to start Keybind Changer UI: Could not find file!");
        }
    }

    protected void _fail(Exception e) {
        shutdown();
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

    @FXML private void _onJitterBox() {
        jitterControl.setVisible(jitterBox.isSelected());
    }

    @FXML private void _onLFOBox() {
        lfoControl.setVisible(lfoBox.isSelected());
    }
}