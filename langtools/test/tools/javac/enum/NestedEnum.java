/*
 * @test /nodynamiccopyright/
 * @bug 5071831
 * @summary javac allows enum in an inner class
 * @author gafter
 *
 * @compile/fail/ref=NestedEnum.out -XDrawDiagnostics  NestedEnum.java
 */

class NestedEnum {
    class Inner {
        enum NotAllowedInNonStaticInner {}
    }
}
