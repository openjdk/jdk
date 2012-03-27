package sun.hotspot.parser;

public class DiagnosticCommand {

    public enum DiagnosticArgumentType {
        JLONG, BOOLEAN, STRING, NANOTIME, STRINGARRAY, MEMORYSIZE
    }

    private String name;
    private String desc;
    private DiagnosticArgumentType type;
    private boolean mandatory;
    private String defaultValue;

    public DiagnosticCommand(String name, String desc, DiagnosticArgumentType type,
            boolean mandatory, String defaultValue) {
        this.name = name;
        this.desc = desc;
        this.type = type;
        this.mandatory = mandatory;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public DiagnosticArgumentType getType() {
        return type;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public String getDefaultValue() {
        return defaultValue;
    }
}
