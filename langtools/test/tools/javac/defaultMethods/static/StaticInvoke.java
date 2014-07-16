/* @test /nodynamiccopyright/
 * @bug 8037385
 * @summary Must not allow static interface method invocation in legacy code
 * @compile -source 8 -Xlint:-options StaticInvoke.java
 * @compile/fail/ref=StaticInvoke7.out -source 7 -Xlint:-options -XDrawDiagnostics StaticInvoke.java
 * @compile/fail/ref=StaticInvoke6.out -source 6 -Xlint:-options -XDrawDiagnostics StaticInvoke.java
 */
import java.util.stream.Stream;

class StaticInvoke {
    void test() {
        Stream.empty();
        java.util.stream.Stream.empty();
    }
}
