/*
 * @test /nodynamiccopyright/
 * @bug 8206986
 * @summary Check expression switch works.
 * @compile/fail/ref=ExpressionSwitch-old.out -source 9 -Xlint:-options -XDrawDiagnostics ExpressionSwitch.java
 * @compile --enable-preview -source 12 ExpressionSwitch.java
 * @run main/othervm --enable-preview ExpressionSwitch
 */

import java.util.Objects;
import java.util.function.Supplier;

public class ExpressionSwitch {
    public static void main(String... args) {
        new ExpressionSwitch().run();
    }

    private void run() {
        check(T.A, "A");
        check(T.B, "B");
        check(T.C, "other");
        assertEquals(exhaustive1(T.C), "C");
        assertEquals(scopesIsolated(T.B), "B");
        assertEquals(lambdas1(T.B).get(), "B");
        assertEquals(lambdas2(T.B).get(), "B");
        localClass(T.A);
    }

    private String print(T t) {
        return switch (t) {
            case A -> "A";
            case B -> { break "B"; }
            default -> { break "other"; }
        };
    }

    private String exhaustive1(T t) {
        return switch (t) {
            case A -> "A";
            case B -> { break "B"; }
            case C -> "C";
            case D -> "D";
        };
    }

    private String exhaustive2(T t) {
        return switch (t) {
            case A -> "A";
            case B -> "B";
            case C -> "C";
            case D -> "D";
        };
    }

    private String scopesIsolated(T t) {
        return switch (t) {
            case A -> { String res = "A"; break res;}
            case B -> { String res = "B"; break res;}
            default -> { String res = "default"; break res;}
        };
    }

    private Supplier<String> lambdas1(T t) {
        return switch (t) {
            case A -> () -> "A";
            case B -> { break () -> "B"; }
            default -> () -> "default";
        };
    }

    private Supplier<String> lambdas2(T t) {
        return switch (t) {
            case A: break () -> "A";
            case B: { break () -> "B"; }
            default: break () -> "default";
        };
    }

    private void localClass(T t) {
        String good = "good";
        class L {
            public String c() {
                STOP: switch (t) {
                    default: break STOP;
                }
                return switch (t) {
                    default: break good;
                };
            }
        }
        String result = new L().c();
        if (!Objects.equals(result, good)) {
            throw new AssertionError("Unexpected result: " + result);
        }
    }

    private void check(T t, String expected) {
        String result = print(t);
        assertEquals(result, expected);
    }

    private void assertEquals(Object result, Object expected) {
        if (!Objects.equals(result, expected)) {
            throw new AssertionError("Unexpected result: " + result);
        }
    }

    enum T {
        A, B, C, D;
    }
    void t() {
        Runnable r = () -> {};
        r.run();
    }
}
