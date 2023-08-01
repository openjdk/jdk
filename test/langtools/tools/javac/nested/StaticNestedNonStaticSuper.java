/*
 * @test /nodynamiccopyright/
 * @bug 8291154
 * @summary Disallow static nested subclasses of non-static nested classes
 * @compile/fail/ref=StaticNestedNonStaticSuper.out -XDrawDiagnostics StaticNestedNonStaticSuper.java
 */

class StaticNestedNonStaticSuper{
    public abstract class NonStaticNested {
        public static class StaticNested extends NonStaticNested {
        }
    }
}
