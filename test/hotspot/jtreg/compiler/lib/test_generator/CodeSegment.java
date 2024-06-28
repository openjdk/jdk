package compiler.lib.test_generator;

import java.math.BigInteger;

public class CodeSegment {
    private final String statics;
    private final String calls;
    private final String methods;
    private final BigInteger num;

    public CodeSegment(String statics, String calls, String methods, BigInteger num) {
        this.statics = statics;
        this.calls = calls;
        this.methods = methods;
        this.num = num;
    }
}
