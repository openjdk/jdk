/* @test /nodynamiccopyright/
 * @bug 8037385
 * @summary Must not allow static interface method invocation in legacy code
 * @compile -Xlint:-options StaticInvokeQualified.java
 */

class StaticInvokeQualified {
    void test() {
        java.util.stream.Stream.empty();
    }
}
