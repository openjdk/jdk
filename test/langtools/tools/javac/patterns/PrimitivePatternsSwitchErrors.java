/*
 * @test /nodynamiccopyright/
 * @bug 8304487 8325653
 * @summary Compiler Implementation for Primitive types in patterns, instanceof, and switch (Preview)
 * @enablePreview
 * @compile/fail/ref=PrimitivePatternsSwitchErrors.out -XDrawDiagnostics -XDshould-stop.at=FLOW PrimitivePatternsSwitchErrors.java
 */
public class PrimitivePatternsSwitchErrors {
    record R_int(int x) {}

    public static void dominationBetweenPrimitivePatterns() {
        int i = 42;
        switch (i) {
            case short s -> System.out.println("its a short");
            case byte b  -> System.out.println("its a byte"); // Error - dominated!
            default      -> System.out.println("any other integral value");
        }
    }

    public static int dominationWithRecordPatterns() {
        R_int r = new R_int(42);
        return switch (r) {
            case R_int(int x) -> 1;
            case R_int(byte x) -> 2;  // Error - dominated!
        };
    }

    public static int inconvertibleNestedComponent() {
        R_int r = new R_int(42);
        return switch (r) {
            case R_int(Long x) -> 1; // inconvertible
        };
    }

    public static int nonExhaustive1() {
        int i = 42;
        return switch (i) {  // Error - not exhaustive
            case short s -> s;
        };
    }

    public static int nonExhaustive2() {
        int i = 42;
        return switch (i) { // Error - not exhaustive
            case byte  b -> 1;
            case short s -> 2;
        };
    }

    public static int nonExhaustive3() {
        int i = 42;
        return switch (i) { // Error - not exhaustive
            case byte  b -> 1;
            case float f -> 2;
        };
    }

    public static int dominationBetweenBoxedAndPrimitive() {
        int i = 42;
        return switch (i) {
            case Integer ib  -> ib;
            case byte ip     -> ip; // Error - dominated!
        };
    }

    public static int constantDominatedWithPrimitivePattern() {
        int i = 42;
        return switch (i) {
            case int j -> 42;
            case 43    -> -1;   // Error - dominated!
        };
    }

    public static int constantDominatedWithFloatPrimitivePattern() {
        float f = 42.0f;
        return switch (f) {
            case Float ff -> 42;
            case 43.0f    -> -1;   // Error - dominated!
        };
    }

    void switchLongOverByte(byte b) {
        switch (b) {
            case 0L: return ;
        }
    }

    void switchOverPrimitiveFloatFromInt(float f) {
        switch (f) {
            case 16777216:
                break;
            case 16777217:
                break;
            default:
                break;
        }
    }

    void switchOverNotRepresentableFloat(Float f) {
        switch (f) {
            case 1.0f:
                break;
            case 0.999999999f:
                break;
            case Float fi:
                break;
        }
    }

    int switchOverPrimitiveBooleanExhaustiveWithNonPermittedDefault(boolean b) {
        return switch (b) {
            case true -> 1;
            case false -> 2;
            default -> 3;
        };
    }

    int switchOverPrimitiveBooleanExhaustiveWithNonPermittedDefaultStatement(boolean b) {
        switch (b) {
            case true: return 1;
            case false: return 2;
            default: return 3;
        }
    }

    int switchOverPrimitiveBooleanExhaustiveWithNonPermittedUnconditionalStatement(boolean b) {
        switch (b) {
            case true: return 1;
            case false: return 2;
            case boolean bb: return 3; // error
        }
    }

    void switchCombinationsNonIntegral() {
        float f = 0f;
        long l = 0L;
        double d = 0d;
        Float fB = 0F;
        Long lB = 0L;
        Double dB = 0D;

        switch (f) {
            case 1l: return;
            case 2f: return;
            case 3d: return;
            default:
        }

        switch (l) {
            case 1l: return;
            case 2f: return;
            case 3d: return;
            default:
        }

        switch (d) {
            case 1l: return;
            case 2f: return;
            case 3d: return;
            default:
        }

        switch (fB) {
            case 1l: return;
            case 2f: return;
            case 3d: return;
            default:
        }

        switch (lB) {
            case 1l: return;
            case 2f: return;
            case 3d: return;
            default:
        }

        switch (dB) {
            case 1l: return;
            case 2f: return;
            case 3d: return;
            default:
        }
    }

    int switchOverPrimitiveBooleanExhaustiveWithNonPermittedUnconditional(boolean b) {
        return switch (b) {
            case true -> 1;
            case false -> 2;
            case boolean bb -> 3; // error
        };
    }

    int duplicateUnconditionalWithPrimitives(int i) {
        return switch (i) {
            case int ii -> 1;
            case long l -> 2; // error
        };
    }

    int booleanSingleCase1(boolean b) {
        return switch (b) {
            case true -> 1;
        };
    }

    int booleanSingleCase2(boolean b) {
        switch (b) {
            case true: return 1;
        }
    }

    void nullAndPrimitive() {
        int i = 42;
        switch (i) {
            case short s -> System.out.println("its a short");
            case null    -> System.out.println("oops");
            default      -> System.out.println("any other integral value");
        }
    }

    public static int nonExhaustive4() {
        Number n = Byte.valueOf((byte) 42);
        return switch (n) { // Error - not exhaustive
            case byte  b when b == 42 -> 1;
            case byte  b -> -1 ;
        };
    }

    public static int nonExhaustive5() {
        Object n = 42;
        return switch (n) { // Error - not exhaustive
            case int  b when b == 42 -> 1;
            case int  b -> -1 ;
        };
    }

    public static int nonExhaustive6() {
        Object n = 42;
        return switch (n) { // Error - not exhaustive
            case byte b -> -1 ;
            case int b -> -2 ;
        };
    }
}
