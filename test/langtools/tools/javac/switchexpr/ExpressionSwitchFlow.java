/*
 * @test /nodynamiccopyright/
 * @bug 8212982
 * @summary Verify a compile-time error is produced if switch expression does not provide a value
 * @compile/fail/ref=ExpressionSwitchFlow.out --enable-preview -source ${jdk.version} -XDrawDiagnostics ExpressionSwitchFlow.java
 */

public class ExpressionSwitchFlow {
    private String test1(int i) {
        return switch (i) {
            case 0 -> {}
            default -> { break "other"; }
        };
    }
    private String test2(int i) {
        return switch (i) {
            case 0 -> {
            }
            default -> "other";
        };
    }
    private String test3(int i) {
        return switch (i) {
            case 0 -> {}
            default -> throw new IllegalStateException();
        };
    }
    private String test4(int i) {
        return switch (i) {
            case 0 -> { break "other"; }
            default -> {}
        };
    }
    private String test5(int i) {
        return switch (i) {
            case 0 -> "other";
            default -> {}
        };
    }
    private String test6(int i) {
        return switch (i) {
            case 0 -> throw new IllegalStateException();
            default -> {}
        };
    }
    private String test7(int i) {
        return switch (i) {
            case 0: throw new IllegalStateException();
            default:
        };
    }
    private String test8(int i) {
        return switch (i) {
            case 0: i++;
            default: {
            }
        };
    }
    private String test9(int i) {
        return switch (i) {
            case 0:
            default:
                System.err.println();
        };
    }
}
