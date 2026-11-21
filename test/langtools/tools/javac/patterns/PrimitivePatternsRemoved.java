/*
 * @test /nodynamiccopyright/
 * @bug 8397218
 * @summary Reject withdrawn primitive patterns with and without preview enabled
 * @compile/fail/ref=PrimitivePatternsRemoved.out -XDrawDiagnostics -Xmaxerrs 200 PrimitivePatternsRemoved.java
 * @compile/fail/ref=PrimitivePatternsRemovedWithPreview.out -XDrawDiagnostics -Xmaxerrs 200 --enable-preview --source ${jdk.version} PrimitivePatternsRemoved.java
 * @compile/fail/ref=PrimitivePatternsRemoved.out -XDrawDiagnostics -Xmaxerrs 200 --release 21 PrimitivePatternsRemoved.java
 * @compile/fail/ref=PrimitivePatternsRemoved.out -XDrawDiagnostics -Xmaxerrs 200 --release 22 PrimitivePatternsRemoved.java
 */

/*
 * The four negative compilations check that primitive patterns are rejected:
 * 1. At the current source level without preview.
 * 2. At the current source level even with preview enabled.
 * 3. With --release 21: reject primitive patterns, first previewed in JDK 23.
 * 4. With --release 22: reject primitive patterns, with the same errors, as release 21.
 */

public class PrimitivePatternsRemoved {
    static class BytePatterns {
        record R(byte value) {}
        boolean typeTest(Object v) { return v instanceof byte; }
        boolean bindingPattern(Object v) { return v instanceof byte x; }
        boolean primitiveOperand(byte v) { return v instanceof Object; }
        int switchPattern(Object v) { return switch (v) { case byte x -> 1; default -> 0; }; }
        boolean referenceComponent(R r) { return r instanceof R(Object x); }
        boolean toShort(R r) { return r instanceof R(short x); }
        boolean toChar(R r) { return r instanceof R(char x); }
        boolean toInt(R r) { return r instanceof R(int x); }
        boolean toLong(R r) { return r instanceof R(long x); }
        boolean toFloat(R r) { return r instanceof R(float x); }
        boolean toDouble(R r) { return r instanceof R(double x); }
        boolean toBoolean(R r) { return r instanceof R(boolean x); }
    }

    static class ShortPatterns {
        record R(short value) {}
        boolean typeTest(Object v) { return v instanceof short; }
        boolean bindingPattern(Object v) { return v instanceof short x; }
        boolean primitiveOperand(short v) { return v instanceof Object; }
        int switchPattern(Object v) { return switch (v) { case short x -> 1; default -> 0; }; }
        boolean referenceComponent(R r) { return r instanceof R(Object x); }
        boolean toByte(R r) { return r instanceof R(byte x); }
        boolean toChar(R r) { return r instanceof R(char x); }
        boolean toInt(R r) { return r instanceof R(int x); }
        boolean toLong(R r) { return r instanceof R(long x); }
        boolean toFloat(R r) { return r instanceof R(float x); }
        boolean toDouble(R r) { return r instanceof R(double x); }
        boolean toBoolean(R r) { return r instanceof R(boolean x); }
    }

    static class CharPatterns {
        record R(char value) {}
        boolean typeTest(Object v) { return v instanceof char; }
        boolean bindingPattern(Object v) { return v instanceof char x; }
        boolean primitiveOperand(char v) { return v instanceof Object; }
        int switchPattern(Object v) { return switch (v) { case char x -> 1; default -> 0; }; }
        boolean referenceComponent(R r) { return r instanceof R(Object x); }
        boolean toByte(R r) { return r instanceof R(byte x); }
        boolean toShort(R r) { return r instanceof R(short x); }
        boolean toInt(R r) { return r instanceof R(int x); }
        boolean toLong(R r) { return r instanceof R(long x); }
        boolean toFloat(R r) { return r instanceof R(float x); }
        boolean toDouble(R r) { return r instanceof R(double x); }
        boolean toBoolean(R r) { return r instanceof R(boolean x); }
    }

    static class IntPatterns {
        record R(int value) {}
        boolean typeTest(Object v) { return v instanceof int; }
        boolean bindingPattern(Object v) { return v instanceof int x; }
        boolean primitiveOperand(int v) { return v instanceof Object; }
        int switchPattern(Object v) { return switch (v) { case int x -> 1; default -> 0; }; }
        boolean referenceComponent(R r) { return r instanceof R(Object x); }
        boolean toByte(R r) { return r instanceof R(byte x); }
        boolean toShort(R r) { return r instanceof R(short x); }
        boolean toChar(R r) { return r instanceof R(char x); }
        boolean toLong(R r) { return r instanceof R(long x); }
        boolean toFloat(R r) { return r instanceof R(float x); }
        boolean toDouble(R r) { return r instanceof R(double x); }
        boolean toBoolean(R r) { return r instanceof R(boolean x); }
    }

    static class LongPatterns {
        record R(long value) {}
        boolean typeTest(Object v) { return v instanceof long; }
        boolean bindingPattern(Object v) { return v instanceof long x; }
        boolean primitiveOperand(long v) { return v instanceof Object; }
        int switchPattern(Object v) { return switch (v) { case long x -> 1; default -> 0; }; }
        boolean referenceComponent(R r) { return r instanceof R(Object x); }
        boolean toByte(R r) { return r instanceof R(byte x); }
        boolean toShort(R r) { return r instanceof R(short x); }
        boolean toChar(R r) { return r instanceof R(char x); }
        boolean toInt(R r) { return r instanceof R(int x); }
        boolean toFloat(R r) { return r instanceof R(float x); }
        boolean toDouble(R r) { return r instanceof R(double x); }
        boolean toBoolean(R r) { return r instanceof R(boolean x); }
    }

    static class FloatPatterns {
        record R(float value) {}
        boolean typeTest(Object v) { return v instanceof float; }
        boolean bindingPattern(Object v) { return v instanceof float x; }
        boolean primitiveOperand(float v) { return v instanceof Object; }
        int switchPattern(Object v) { return switch (v) { case float x -> 1; default -> 0; }; }
        boolean referenceComponent(R r) { return r instanceof R(Object x); }
        boolean toByte(R r) { return r instanceof R(byte x); }
        boolean toShort(R r) { return r instanceof R(short x); }
        boolean toChar(R r) { return r instanceof R(char x); }
        boolean toInt(R r) { return r instanceof R(int x); }
        boolean toLong(R r) { return r instanceof R(long x); }
        boolean toDouble(R r) { return r instanceof R(double x); }
        boolean toBoolean(R r) { return r instanceof R(boolean x); }
    }

    static class DoublePatterns {
        record R(double value) {}
        boolean typeTest(Object v) { return v instanceof double; }
        boolean bindingPattern(Object v) { return v instanceof double x; }
        boolean primitiveOperand(double v) { return v instanceof Object; }
        int switchPattern(Object v) { return switch (v) { case double x -> 1; default -> 0; }; }
        boolean referenceComponent(R r) { return r instanceof R(Object x); }
        boolean toByte(R r) { return r instanceof R(byte x); }
        boolean toShort(R r) { return r instanceof R(short x); }
        boolean toChar(R r) { return r instanceof R(char x); }
        boolean toInt(R r) { return r instanceof R(int x); }
        boolean toLong(R r) { return r instanceof R(long x); }
        boolean toFloat(R r) { return r instanceof R(float x); }
        boolean toBoolean(R r) { return r instanceof R(boolean x); }
    }

    static class BooleanPatterns {
        record R(boolean value) {}
        boolean typeTest(Object v) { return v instanceof boolean; }
        boolean bindingPattern(Object v) { return v instanceof boolean x; }
        boolean primitiveOperand(boolean v) { return v instanceof Object; }
        int switchPattern(Object v) { return switch (v) { case boolean x -> 1; default -> 0; }; }
        boolean referenceComponent(R r) { return r instanceof R(Object x); }
        boolean toByte(R r) { return r instanceof R(byte x); }
        boolean toShort(R r) { return r instanceof R(short x); }
        boolean toChar(R r) { return r instanceof R(char x); }
        boolean toInt(R r) { return r instanceof R(int x); }
        boolean toLong(R r) { return r instanceof R(long x); }
        boolean toFloat(R r) { return r instanceof R(float x); }
        boolean toDouble(R r) { return r instanceof R(double x); }
    }

    record Boxed(Integer value) {}
    boolean unboxComponent(Boxed r) { return r instanceof Boxed(int x); }
    boolean boxOperand(int v) { return v instanceof Integer x; }

    // The extra primitive selectors are forbidden even with no pattern labels.
    int longSelector(long v) { return switch (v) { default -> 0; }; }
    int floatSelector(float v) { return switch (v) { default -> 0; }; }
    int doubleSelector(double v) { return switch (v) { default -> 0; }; }
    int booleanSelector(boolean v) { return switch (v) { default -> 0; }; }

    // Boxed selectors do not permit the withdrawn constant label types.
    int longConstant(Long v) { return switch (v) { case 0L -> 1; default -> 0; }; }
    int floatConstant(Float v) { return switch (v) { case 0.0f -> 1; default -> 0; }; }
    int doubleConstant(Double v) { return switch (v) { case 0.0d -> 1; default -> 0; }; }
    int booleanConstant(Boolean v) { return switch (v) { case true -> 1; default -> 0; }; }
    static final boolean B = 1 < 2;
    int booleanExpression(Boolean v) { return switch (v) { case B -> 1; default -> 0; }; }

    // Pos tests
    record R(int x, double y, boolean z) {}
    record I(int x) {}
    int primitiveComponents(R r) {
        return switch (r) { case R(int x, double y, boolean z) -> x; };
    }
    int inferredComponent(I r) { return switch (r) { case I(var x) -> x; }; }
    int longReferencePattern(Long v) { return switch (v) { case Long x -> 1; }; }
    int floatReferencePattern(Float v) { return switch (v) { case Float x -> 1; }; }
    int doubleReferencePattern(Double v) { return switch (v) { case Double x -> 1; }; }
    int booleanReferencePattern(Boolean v) { return switch (v) { case Boolean x -> 1; }; }

    // Disallowed primitive selectors must not trigger secondary pattern applicability errors.
    int longSelectorPattern(long v) { return switch (v) { case Object o -> 0; }; }

    // Unresolved nested types must not trigger secondary dominance errors.
    int missingPatternAfter(I r) {
        return switch (r) {
            case I(int x) -> 0;
            case I(Missing x) -> 1;
        };
    }
    int missingPatternBefore(I r) {
        return switch (r) {
            case I(Missing x) -> 0;
            case I(int x) -> 1;
        };
    }
}
