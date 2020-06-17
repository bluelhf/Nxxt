package io.github.bluelhf.nxxt;

import java.util.Date;
import java.util.logging.*;

public class Nxxt {
    static Logger logger = Logger.getLogger("Nxxt");

    public static void main(String[] args) {

        getLogger().setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            private static final String format = "[%1tF %1$tT] [%2$s] %3$s%n";


            public String format(LogRecord lr) {
                return String.format(format, new Date(lr.getMillis()), lr.getLevel().getLocalizedName(), lr.getMessage());
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


    public static Logger getLogger() { return logger; }
}
