package jdk.jfr.internal.dcmd;

public enum JfrCommand {
    STARTING("JFR.start", "starting"),
    STOPPING("JFR.stop", "stopping"),
    CHECKING("JFR.check", "checking"),
    DUMPING("JFR.dump", "dumping"),
    CONFIGURE("JFR.configure", "configuring");

    JfrCommand(String command, String name) {
        this.command = command;
        this.name = name;
    }

    private final String command;

    private final String name;

    public String getCommand() {
        return command;
    }

    public String getName() {
        return name;
    }

}
