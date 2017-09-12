/* @test /nodynamiccopyright/
 * @bug 8037385
 * @summary Must not allow static interface method invocation in legacy code
 * @compile -source 8 -Xlint:-options StaticInvokeQualified.java
 * @compile/fail/ref=StaticInvokeQualified7.out -source 7 -Xlint:-options -XDrawDiagnostics StaticInvokeQualified.java
 * @compile/fail/ref=StaticInvokeQualified6.out -source 6 -Xlint:-options -XDrawDiagnostics StaticInvokeQualified.java
 */

class StaticInvokeQualified {
    void test() {
        java.util.stream.Stream.empty();
    }
}
