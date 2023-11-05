/*
 * @test /nodynamiccopyright/
 * @bug 5071831
 * @summary javac allows enum in an inner class for release >= 16
 * @author gafter
 *
 * @compile/fail/ref=NestedEnum.out -XDrawDiagnostics --release 15 NestedEnum.java
 * @compile NestedEnum.java
 */

class NestedEnum {
    class Inner {
        enum NotAllowedInNonStaticInner {}
    }
}
