package io.github.bluelhf.nxxt;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeInputEvent;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

import java.awt.*;
import java.awt.event.InputEvent;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;


public class Controller implements NativeKeyListener {
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
    @FXML TextField keybindField;

    boolean active = false;
    double delay;
    double noLFOdelay;
    Robot robot;
    double minDelay = 20.0D;

    int[] konami = { 57416, 57416, 57424, 57424, 57419, 57421, 57419, 57421, 48, 30 };
    int idx = 0;


    public void shutdown() {
        turnOff();
    }

    public void initialise() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }

        Nxxt.getLogger().info("" + GlobalScreen.getNativeMonitors()[0].getX() + ", " + GlobalScreen.getNativeMonitors()[0].getX());





        this.delay = (long)this.delaySlider.getValue();
        this.noLFOdelay = this.delay;
        this.delaySlider.valueProperty().addListener((observableValue, oldValue, newValue) -> {
            this.delayText.setText(String.valueOf(newValue.intValue()));
            _updateDelay();
        });
        this.jitterSlider.valueProperty().addListener((observableValue, number, newValue) -> this.jitterText.setText(String.valueOf(newValue.intValue())));
        this.lfoSlider.valueProperty().addListener((observableValue, number, newValue) -> {
            this.lfoText.setText(String.valueOf(newValue.intValue()));
            updateCPS();
        });
        Properties properties = new Properties();
        InputStream propertyStream = Nxxt.class.getResourceAsStream("/info.properties");
        try {
            properties.load(propertyStream);
        } catch (Exception e) {
            Nxxt.getLogger().severe("Failed to read properties!");
        }
        this.version.setText(properties.getProperty("version"));
        updateCPS();
        Nxxt.getLogger().fine("Initialised controller");






        new Thread(() -> {
            long start = System.currentTimeMillis(); try {
                Thread.sleep(1L);
            } catch (InterruptedException ignored) {}
            this.minDelay = (System.currentTimeMillis() - start);
        });
    }


    public boolean doLFO() { return this.lfoBox.isSelected(); }



    public double getLFO() { return this.lfoSlider.getValue(); }



    public boolean doJitter() { return this.jitterBox.isSelected(); }



    public double getJitter() { return this.jitterSlider.getValue(); }


    public double getDelay() {
        _updateDelay();
        return this.delay;
    }

    public void setDelay(double newDelay) {
        this.delay = newDelay;
        _saveDelay();
    }


    public String getClickType() { return this.clickTypeBox.getValue(); }



    private double _getDelay() { return this.delay; }



    private void _setDelay(double newDelay) { this.delay = newDelay; }


    public void _updateDelay() {
        String delayField = this.delayText.getText();
        String stripped = Arrays.stream(delayField.split("")).filter(s -> s.matches("[0-9]")).collect(Collectors.joining());
        this.delayText.setText(stripped);
        this.delaySlider.setValue(Integer.parseInt(stripped));
        Nxxt.getLogger().finest("Updated delay parameters via text field");
        _setDelay(this.delaySlider.getValue());
        if (!this.active)
            this.noLFOdelay = this.delay;
        updateCPS();
    }

    public void _saveDelay() {
        Nxxt.getLogger().finest("saving delay " + this.delay);
        this.delaySlider.setValue(this.delay);
        this.delayText.setText(String.valueOf(Math.round(this.delay)));
    }

    public void onJitterBox() {
        this.jitterControl.setVisible(doJitter());
        Nxxt.getLogger().fine("Toggled jitter box");
    }

    public void onLFOBox() {
        _updateDelay();
        this.lfoControl.setVisible(doLFO());
        Nxxt.getLogger().fine("Toggled LFO box");
    }

    public void updateCPS() {
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


    public void turnOn() {
        this.optionsPane.setDisable(true);
        this.startButton.setDisable(true);
        this.stopButton.setDisable(false);
        this.active = true;
        String val = getClickType();
        int mask = val.equals("Left") ? InputEvent.BUTTON1_DOWN_MASK : (val.equals("Right") ? InputEvent.BUTTON2_DOWN_MASK : InputEvent.BUTTON3_DOWN_MASK);
        (new Thread(() -> {
            this.noLFOdelay = getDelay();
            while (this.active) {
                Point location = MouseInfo.getPointerInfo().getLocation();

                if (doJitter()) {
                    location.x = (int)(location.x + (Math.random() - Math.nextDown(0.5D)) * 2.0D * getJitter());
                    location.y = (int)(location.y + (Math.random() - Math.nextDown(0.5D)) * 2.0D * getJitter());
                }
                Nxxt.getLogger().finest("Attempting click at " + location.x + ", " + location.y);
                robot.mouseMove(location.x, location.y);
                robot.mousePress(mask);
                robot.mouseRelease(mask);

                if (doLFO()) {
                    double LFO = ThreadLocalRandom.current().nextDouble(-getLFO(), Math.nextUp(getLFO()));
                    if (Math.abs(this.noLFOdelay - this.delay + LFO) < getLFO()) {
                        this.delay = Math.max(this.delaySlider.getMin(), Math.min(this.delaySlider.getMax(), this.delay + LFO));
                        Platform.runLater(this::_saveDelay);
                    }
                }

                if (this.delay > this.minDelay)
                    try {
                        Thread.sleep((long)this.delay);
                    } catch (Exception ignored) {}
            }
        })).start();
        Nxxt.getLogger().info("Started with delay of " + this.delaySlider.getValue() + " and button mask of " + mask);
    }

    public void turnOff() {
        if (!this.active)
            return;
        if (doLFO()) {
            this.delay = this.noLFOdelay;
            _saveDelay();
            updateCPS();
        }
        this.active = false;
        this.optionsPane.setDisable(false);
        this.startButton.setDisable(false);
        this.stopButton.setDisable(true);
        Nxxt.getLogger().info("Stopped");
    }

    public void toggle() {
        if (this.active) {
            turnOff();
        } else {
            turnOn();
        }
    }

    public void updateJitter() {
        String jitterField = this.jitterText.getText();
        String stripped = Arrays.stream(jitterField.split("")).filter(s -> s.matches("[0-9]")).collect(Collectors.joining());
        this.jitterText.setText(stripped);
        this.jitterSlider.setValue(Integer.parseInt(stripped));
        Nxxt.getLogger().finest("Updated jitter parameters via text field");
    }


    public void nativeKeyPressed(NativeKeyEvent ev) {
        Nxxt.getLogger().info("Key " + ev.getKeyCode() + ", idx = " + this.idx + ", konami: " + this.konami[this.idx % this.konami.length]);
        if (ev.getKeyCode() == this.konami[this.idx] && this.idx == this.konami.length - 1)
        { Platform.runLater(() -> {
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
            this.idx = 0; }
        else if (ev.getKeyCode() == this.konami[this.idx])
        { this.idx++;
            this.idx %= this.konami.length; }
        else { this.idx = 0; }


        String modifiers = NativeInputEvent.getModifiersText(ev.getModifiers());
        Nxxt.getLogger().finest("Got key press " + modifiers + " + " + ev.getKeyCode());
        if (modifiers.equals("Ctrl") && ev
                .getKeyCode() == 49) {
            shutdown();
            Platform.exit();
            Runtime.getRuntime().exit(0);
        }
    }

    public void nativeKeyReleased(NativeKeyEvent ev) {}

    public void nativeKeyTyped(NativeKeyEvent ev) {}
}