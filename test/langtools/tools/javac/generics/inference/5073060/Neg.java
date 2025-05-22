/*
 * @test    /nodynamiccopyright/
 * @bug     5073060
 * @summary Package private members not found for intersection types
 * @author  Peter von der Ahé
 * @compile/fail/ref=Neg.out -XDrawDiagnostics NegHelper.java Neg.java
 */

public class Neg<T extends test.NegHelper & Cloneable> {
    void test(T t) { t.foo(); }
}
