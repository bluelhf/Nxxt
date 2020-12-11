package io.github.bluelhf.nxxt;

import com.google.common.util.concurrent.AtomicDouble;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorInput;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Controller is the meat and bones of Nxxt.
 * It contains most of the code which interfaces with the GUI (via JavaFX) to read and write values.
 * If you can see it, chances are it's being done by Controller.
 */
public class Controller implements NativeKeyListener {

    final Delta dragDelta = new Delta();
    // The FXML annotation lets JavaFX know that we want these values replaced with the JavaFX objects whose ids correspond to the field names.
    @FXML Button closeButton;
    @FXML Button minimiseButton;
    @FXML Button resetKeybindButton;

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

    private final int[] konami = {57416, 57416, 57424, 57424, 57419, 57421, 57419, 57421, 48, 30};

    KeyEvent keybindEvent = null;
    Stage stage;

    Clicker clicker;
    Timer ticker = new Timer();
    private final int[] pcmr = {25, 46, 50, 19};
    @FXML Button themeToggle;
    private int pcmrIndex = 0;
    private int konamiIndex = 0;

    private boolean rgb = false;
    private Media song;
    private MediaPlayer player;

    // Assign default values
    private double modelDelay  = 69;
    private double modelLFO    = 1;
    private double modelJitter = 4;

    private boolean toggleLock = false;
    private void setDark(boolean isDark) {
        int to = isDark ? 1 : 0;

        double delta = 0.01;

        AtomicDouble value = new AtomicDouble(Math.abs(to - 1));
        CompletableFuture.runAsync(() -> {
            toggleLock = true;
            themeToggle.setDisable(true);
            while (Math.abs(value.get() - to) > delta) {
                ColorInput color = new ColorInput();
                color.setPaint(Color.gray(value.getAndAdd(to > value.get() ? delta : -delta)));
                color.setWidth(Double.MAX_VALUE);
                color.setHeight(Double.MAX_VALUE);
                Blend blend = new Blend(BlendMode.DIFFERENCE);
                blend.setBottomInput(color);

                Platform.runLater(() -> stage.getScene().getRoot().setEffect(blend));
                LockSupport.parkNanos(1000000);
            }
        }).thenRun(() -> {
            toggleLock = false;
            themeToggle.setDisable(false);
            if (value.get() <= delta) stage.getScene().getRoot().setEffect(null);
        });
    }

    @FXML
    private void toggleTheme() {
        if (toggleLock) return;
        setDark(stage.getScene().getRoot().getEffect() == null);
        themeToggle.setText(stage.getScene().getRoot().getEffect() == null ? "☽" : "☀");
    }

    public void postInit() {
        BorderPane pane = (BorderPane) stage.getScene().getRoot();
        pane.getTop().setOnMousePressed(mouseEvent -> {
            dragDelta.x = stage.getX() - mouseEvent.getScreenX();
            dragDelta.y = stage.getY() - mouseEvent.getScreenY();
        });
        pane.getTop().setOnMouseDragged(mouseEvent -> {
            stage.setX(mouseEvent.getScreenX() + dragDelta.x);
            stage.setY(mouseEvent.getScreenY() + dragDelta.y);
        });

        stage.initStyle(StageStyle.TRANSPARENT);
    }

    // Called by the Launcher class - sets everything up.
    public void initialise(Stage stage) {
        this.stage = stage;
        try {
            clicker = new Clicker(modelDelay, modelJitter, modelLFO);
            initJNI();
        } catch (NativeHookException | AWTException e) {
            fail(e);
        }

        // Top buttons
        closeButton.setOnAction(eventAction -> {
            shutdown();
            Platform.exit();
        });
        minimiseButton.setOnAction(eventAction -> stage.setIconified(true));

        // Make sure sliders edit our internal values
        delaySlider.valueProperty().addListener((observableValue, oldValue, newValue) -> modelDelay = sliderOverride(delaySlider, delayText));
        jitterSlider.valueProperty().addListener((observableValue, number, newValue) -> modelJitter = sliderOverride(jitterSlider, jitterText));
        lfoSlider.valueProperty().addListener((observableValue, number, newValue) -> modelLFO = sliderOverride(lfoSlider, lfoText));

        // Make sure text fields edit our internal values
        delayText.setOnAction(actionEvent -> modelDelay = textOverride(delaySlider, delayText).orElseGet(() -> modelDelay));
        jitterText.setOnAction(actionEvent -> modelJitter = textOverride(jitterSlider, jitterText).orElseGet(() -> modelJitter));
        lfoText.setOnAction(actionEvent -> modelLFO = textOverride(lfoSlider, lfoText).orElseGet(() -> modelLFO));

        // Hide controls for Jitter & LFO if they aren't enabled
        jitterBox.setOnAction(actionEvent -> jitterControl.setVisible(jitterBox.isSelected()));
        lfoBox.setOnAction(actionEvent -> lfoControl.setVisible(lfoBox.isSelected()));

        delayText.setText("" + (int)modelDelay);
        delaySlider.setValue(modelDelay);
        lfoText.setText("" + (int)modelLFO);
        lfoSlider.setValue(modelLFO);
        jitterText.setText("" + (int)modelJitter);
        jitterSlider.setValue(modelJitter);

        resetKeybindButton.setVisible(keybindEvent != null);

        // Read properties file to get the project version
        Properties properties = new Properties();
        InputStream propertyStream = Nxxt.class.getResourceAsStream("/info.properties");
        try {
            properties.load(propertyStream);
        } catch (Exception e) {
            Nxxt.getLogger().severe("Failed to read properties!");
        }
        this.version.setText(properties.getProperty("version"));
        ticker.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                tick();
            }
        }, 0, 50);
    }

    private void tick() {
        Platform.runLater(() -> {

            // Update sliders from merged values
            delaySlider.setValue(modelDelay);
            jitterSlider.setValue(modelJitter);
            lfoSlider.setValue(modelLFO);

            String col = Color.hsb((System.currentTimeMillis() % 1000) / 1000D * 360, 1, 1).toString().replaceFirst("0x", "#");
            stage.getScene().getRoot().setStyle("-fx-border-color: " + (rgb ? col : "#00000000"));

            // Update our Clicker object with the merged values
            clicker.getSettings()
                .setClickType(clickerType())
                .setLFO(lfoBox.isSelected() ? modelLFO : -1)
                .setDelay(modelDelay)
                .setJitter(jitterBox.isSelected() ? modelJitter : -1);

            // Update our CPS
            if (modelDelay > clicker.calcMinSlice().getNow(-1)) {
                lfoBox.setVisible(true);
                lfoControl.getChildren().forEach(node -> node.setVisible(lfoBox.isSelected()));
                DecimalFormat df = new DecimalFormat("###0.##");
                double minDelay = 1000.0D / (modelDelay);
                double maxDelay = 1000.0D / (modelDelay);
                if (lfoBox.isSelected()) {
                    minDelay -= modelLFO;
                    maxDelay += modelLFO;

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

    private String keyEventText(int code, String text, boolean alt, boolean shift, boolean shortcut) {
        HashMap<String, Boolean> modifiers = new HashMap<>();
        modifiers.put("ALT", alt);
        modifiers.put("SHIFT", shift);
        modifiers.put(isMac() ? "CMD" : "CTRL", shortcut);

        KeyCode keyCode = null;
        List<KeyCode> keyCodes = Arrays.stream(KeyCode.values()).collect(Collectors.toList());
        for (KeyCode kc : keyCodes) {
            if (kc.getCode() == code) {
                keyCode = kc;
            }
        }

        // Edge-case: Fn key
        if (code == 0 || code == 255) text = "Fn";

        // Edge-case: Function keys
        if (code >= 112 && code <= 123) text = keyCode.getName();

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

    // These just handle overriding sliders with text or text with sliders
    private OptionalDouble textOverride(Slider slider, TextField text) {
        String textField = text.getText();
        double result;
        try {
            result = Double.parseDouble(textField);
        } catch (NumberFormatException | NullPointerException e) {
            (new Thread(() -> {
                for (int i = 0; i < 3; i++) {
                    Platform.runLater(() -> text.setStyle("-fx-border-color: red; -fx-border-width: 2px;"));
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException interruptedException) {
                        Platform.runLater(() -> fail(interruptedException));
                    }

                    Platform.runLater(() -> text.setStyle("-fx-border-width: 0px;"));
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException interruptedException) {
                        Platform.runLater(() -> fail(interruptedException));
                    }
                }

            })).start();
            return OptionalDouble.empty();

        }
        text.setText(String.valueOf(result));
        slider.setValue(result);
        return OptionalDouble.of(result);
    }

    private double sliderOverride(Slider slider, TextField text) {
        text.setText(String.valueOf((int) slider.getValue()));
        return slider.getValue();
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
        if (clicker.isEnabled()) turnOff();
        else turnOn();
    }

    private String keyEventText(NativeKeyEvent event) {
        int m = event.getModifiers();
        return keyEventText(event.getKeyCode(), NativeKeyEvent.getKeyText(event.getKeyCode()), (m & 136) != 0, (m & 17) != 0, (m & 34) != 0 || (m & 68) != 0);
    }

    // We need these if we want to implement NativeKeyListener - they're not actually used
    public void nativeKeyReleased(NativeKeyEvent ev) {
    }

    public void nativeKeyTyped(NativeKeyEvent ev) {
    }

    private boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    private String keyEventText(KeyEvent event) {
        return keyEventText(event.getCode().getCode(), event.getText(), event.isAltDown(), event.isShiftDown(), event.isShortcutDown());
    }

    public void nativeKeyPressed(NativeKeyEvent ev) {
        Nxxt.getLogger().fine("Pressed " + keyEventText(ev) + ", keybind was " + (keybindEvent != null ? keyEventText(keybindEvent) : "not set"));

        if (keyEventText(ev).equals("CTRL + END")) {
            shutdown();
            Platform.exit();
            Runtime.getRuntime().exit(0);
        }
        if (keyEventText(ev).equals("CTRL + DELETE")) {
            Platform.runLater(() -> fail(new Exception("Ctrl + Delete force-kill")));
        }

        if (keybindEvent != null && keyEventText(ev).equals(keyEventText(keybindEvent))) {
            toggle();
        }


        if (ev.getKeyCode() == this.konami[this.konamiIndex] && this.konamiIndex == this.konami.length - 1) {
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
            this.konamiIndex = 0;
        } else if (ev.getKeyCode() == this.konami[this.konamiIndex]) {
            this.konamiIndex++;
            this.konamiIndex %= this.konami.length;
        } else {
            this.konamiIndex = 0;
        }

        if (ev.getKeyCode() == this.pcmr[this.pcmrIndex] && this.pcmrIndex == this.pcmr.length - 1) {
            rgb = !rgb;
            if (!toggleLock) {
                setDark(rgb);
            }
            if (rgb) {
                try {
                    Platform.runLater(() -> {
                        if (song == null) song = new Media(Nxxt.class.getResource("/song.mp3").toString());
                        if (player == null) player = new MediaPlayer(song);
                        player.setOnError(() -> System.out.println("Error : " + player.getError().toString()));
                        player.play();
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> fail(e));
                }
            } else if (player != null) player.stop();
            this.pcmrIndex = 0;
        } else if (ev.getKeyCode() == this.pcmr[this.pcmrIndex]) {
            this.pcmrIndex++;
            this.pcmrIndex %= this.pcmr.length;
        } else {
            this.pcmrIndex = 0;
        }
    }

    @FXML
    private void processKeybindReset(ActionEvent event) {
        keybindEvent = null;
        changeKeybindButton.setText("Change Keybind (NONE)");
        resetKeybindButton.setVisible(false);
    }

    @SuppressWarnings("unused")
    @FXML
    private void processKeybindChange(ActionEvent event) {
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
                    resetKeybindButton.setVisible(true);
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

    protected void fail(Exception e) {
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

        stage.setResizable(false);
        errorDialog.expandedProperty().addListener((observableValue, newValue, oldValue) -> {
            Platform.runLater(() -> errorDialog.getScene().getWindow().sizeToScene());
        });

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

    // records relative x and y co-ordinates.
    static class Delta {
        double x, y;
    }
}