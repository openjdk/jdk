/*
 * @test /nodynamiccopyright/
 * @bug 7002837
 *
 * @summary  Diamond: javac generates diamond inference errors when in 'finder' mode
 * @author mcimadamore
 * @compile/fail/ref=T7002837.out -Werror -XDrawDiagnostics -XDfindDiamond T7002837.java
 *
 */

class T7002837<X extends java.io.Serializable & Comparable<?>> {
    T7002837() {}
    { new T7002837<Integer>(); }
}
