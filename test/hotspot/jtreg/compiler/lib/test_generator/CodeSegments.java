package compiler.lib.test_generator;

public class CodeSegments {
    private final String statics;
    private final String calls;
    private final String methods;
    private final int num;

    public CodeSegments(String statics, String calls, String methods, int num) {
        this.statics = statics;
        this.calls = calls;
        this.methods = methods;
        this.num = num;
    }
}
