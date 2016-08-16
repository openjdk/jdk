/* @test /nodynamiccopyright/
 * @bug 8037385
 * @summary Must not allow static interface method invocation in legacy code
 * @compile -source 8 -Xlint:-options StaticInvokeSimple.java
 * @compile/fail/ref=StaticInvokeSimple7.out -source 7 -Xlint:-options -XDrawDiagnostics StaticInvokeSimple.java
 * @compile/fail/ref=StaticInvokeSimple6.out -source 6 -Xlint:-options -XDrawDiagnostics StaticInvokeSimple.java
 */
import java.util.stream.Stream;

class StaticInvokeSimple {
    void test() {
        Stream.empty();
    }
}
