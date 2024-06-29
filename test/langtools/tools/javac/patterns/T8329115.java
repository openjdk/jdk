/**
 * @test /nodynamiccopyright/
 * @bug 8329115
 * @summary Crash involving return from inner switch
 * @compile/fail/ref=T8329115.out -XDrawDiagnostics -XDdev T8329115.java
 */
public class T8329115 {
    record R1() {}
    record R2() {}

    int test() {
        return switch (new R1()) {
            case R1() -> {
                return switch (new R2()) { // crashes, instead it should just be the error: attempt to return out of a switch expression
                    case R2() -> 1;
                };
            }
        };
    }
}
