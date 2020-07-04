package io.github.bluelhf.nxxt;

import io.github.bluelhf.nxxt.ext.BlueMath;

import java.awt.*;
import java.util.concurrent.CompletableFuture;

public class Clicker {
    private Robot robot;
    private int minTimeslice = -1;
    private boolean enabled = false;

    private ClickerSettings settings;
    private BlueMath.Random blueRandom;

    Clicker(double delay, double jitter, double lfo) throws AWTException {
        settings = new ClickerSettings(delay, lfo, jitter, ClickerSettings.ClickType.LEFT);
        blueRandom = new BlueMath.Random();
        robot = new Robot();
        calcMinSlice();
    }

    public ClickerSettings getSettings() {
        return settings;
    }

    public void start() {
        if (enabled) return;
        enabled = true;
        (new Thread(() -> {
            int mask = settings.getClickMask();
            while (enabled) {
                if (settings.doJitter()) {
                    Point location = MouseInfo.getPointerInfo().getLocation();
                    location.x += (int) blueRandom.randomDouble(-settings.getJitter(), settings.getJitter());
                    location.y += (int) blueRandom.randomDouble(-settings.getJitter(), settings.getJitter());
                    robot.mouseMove(location.x, location.y);
                }
                robot.mousePress(mask);
                robot.mouseRelease(mask);


                double delay = settings.getDelay();

                if (settings.doLFO() && settings.getDelay() > minTimeslice) {
                    // Yay OpenSimplexNoise! Thank you Kurt Spencer :)
                    double x = (System.currentTimeMillis() % 1337.69420 + 1) * settings.getLFO();
                    delay += blueRandom.simplex(x, new BlueMath.Boundary(-settings.getLFO(), settings.getLFO()));
                }

                if (settings.getDelay() > minTimeslice)
                    try {
                        Thread.sleep((long) delay);
                    } catch (Exception ignored) { }
            }
        })).start();

    }

    public void stop() {
        enabled = false;
    }

    public void toggle() {
        if (!enabled) start(); else stop();
    }

    public boolean isEnabled() {
        return enabled;
    }



    public CompletableFuture<Integer> calcMinSlice() {
        if (minTimeslice != -1) return CompletableFuture.completedFuture(minTimeslice);
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            try { Thread.sleep(1L);
            } catch (InterruptedException ignored) {}
            minTimeslice = (int)(System.currentTimeMillis() - start);
            return minTimeslice;
        });
    }
}
