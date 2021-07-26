package blue.lhf.nxxt.clicker;

import java.awt.event.InputEvent;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public class ClickerSettings {
    private double lfo;
    private double delay;
    private double jitter;
    private ClickType clickType;
    public ClickerSettings(double delay, double lfo, double jitter, ClickType clickType) {
        this.delay = delay;
        this.lfo = lfo;
        this.jitter = jitter;
        this.clickType = clickType;
    }

    public boolean doLFO() {
        return lfo != -1;
    }
    public double getLFO() {
        return lfo > 0 ? lfo : -1;
    }
    public ClickerSettings setLFO(double lfo) {
        this.lfo = lfo;
        return this;
    }

    public boolean doJitter() {
        return jitter != -1;
    }
    public double getJitter() {
        return jitter > 0 ? jitter : -1;
    }

    public ClickerSettings setJitter(double jitter) {
        this.jitter = jitter;
        return this;
    }

    public double getDelay() {
        return delay;
    }
    public ClickerSettings setDelay(double delay) {
        this.delay = delay;
        return this;
    }

    public ClickType getClickType() {
        return clickType;
    }
    public ClickerSettings setClickType(ClickType clickType) {
        this.clickType = clickType;
        return this;
    }

    public int getClickMask() {
        return clickType.mask;
    }


    public enum ClickType {
        LEFT(InputEvent.BUTTON1_DOWN_MASK),
        RIGHT(InputEvent.BUTTON3_DOWN_MASK),
        MIDDLE(InputEvent.BUTTON2_DOWN_MASK);

        int mask;
        ClickType(int mask) {
            this.mask = mask;
        }
    }

}
