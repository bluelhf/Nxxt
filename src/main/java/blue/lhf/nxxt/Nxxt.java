package blue.lhf.nxxt;

import javafx.scene.paint.Color;

import java.util.Date;
import java.util.logging.*;

import static java.lang.Math.floor;

public class Nxxt {
    public static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    public static final boolean MAC = System.getProperty("os.name").startsWith("Mac");
    private static final Logger LOGGER = Logger.getLogger("Nxxt");

    private static String rainbow(String s) {
        StringBuilder builder = new StringBuilder();
        int counter = 0;
        int cycle = s.length() / 2;
        for (char c : s.toCharArray()) {
            Color col = Color.hsb(((counter++ + System.currentTimeMillis() / 10D) % cycle) / (double) cycle * 360, 1, 1);
            builder.append(String.format(
                    "\u001B[38;2;%s;%s;%sm",
                    (int) floor(col.getRed() * 255),
                    (int) floor(col.getGreen() * 255),
                    (int) floor(col.getBlue() * 255)
            )).append(c);
        }
        builder.append("\u001B[0m");
        return builder.toString();
    }

    public static void main(String[] args) {
        getLogger().setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            private static final String format = "[%1tF %1$tT] [%2$s] %3$s%n";
            public String format(LogRecord lr) {
                return rainbow(String.format(format, new Date(lr.getMillis()), lr.getLevel().getLocalizedName(), lr.getMessage()));
            }
        });
        handler.setLevel(Level.ALL);
        getLogger().addHandler(handler);

        if (args.length > 0) {
            try {
                Level lvl = Level.parse(args[0]);
                getLogger().info("Setting log level to " + lvl);
                getLogger().setLevel(lvl);
            } catch (Exception ex) {
                getLogger().warning("Failed to get log level from argument " + args[0]);
            }
        }
        Launcher.main(args);
    }


    public static Logger getLogger() { return LOGGER; }
}
