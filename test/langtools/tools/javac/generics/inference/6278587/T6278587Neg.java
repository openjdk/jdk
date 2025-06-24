/*
 * @test    /nodynamiccopyright/
 * @bug     6278587 8007464
 * @summary Inference broken for subtypes of subtypes of F-bounded types
 * @author  Peter von der Ahé
 * @compile T6278587Neg.java
 */

public abstract class T6278587Neg {
    interface A<T extends A<T>> {}
    interface B extends A<B> {}
    interface C extends B {}
    interface D<T> {}
    abstract <T extends A<T>, S extends T> D<T> m(S s);
    {
        C c = null;
        m(c);
    }
}
