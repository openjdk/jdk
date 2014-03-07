/**
 * @test /nodynamiccopyright/
 * @bug 8033421
 * @summary Check that \\@SuppressWarnings works properly when overriding deprecated method.
 * @build VerifySuppressWarnings
 * @compile/ref=Overridden.out -XDrawDiagnostics -Xlint:deprecation Overridden.java
 * @run main VerifySuppressWarnings Overridden.java
 */

public class Overridden implements Interface {
    public void test() { }
}

interface Interface {
    @Deprecated void test();
}
