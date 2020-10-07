/*
 * @test /nodynamiccopyright/
 * @bug 5071831
 * @summary javac should allow enum in an inner class
 * @author gafter
 *
 * @compile NestedEnum.java
 */

class NestedEnum {
    class Inner {
        enum NotAllowedInNonStaticInner {}
    }
}
