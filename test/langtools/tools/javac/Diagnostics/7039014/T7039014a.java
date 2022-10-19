/*
 * @test /nodynamiccopyright/
 * @bug     7039014
 * @summary Confusing error message for method conflict
 * @author archiecobbs
 *
 * @compile/fail/ref=T7039014a.out -XDrawDiagnostics T7039014a.java
 */
public class T7039014a {

    interface A<T> {
        byte m(String x);
        char m(T x);
    }

    interface B extends A<String> {
    }
}
