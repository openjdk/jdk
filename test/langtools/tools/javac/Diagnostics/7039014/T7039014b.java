/*
 * @test /nodynamiccopyright/
 * @bug     7039014
 * @summary Confusing error message for method conflict
 * @author archiecobbs
 *
 * @compile/fail/ref=T7039014b.out -XDrawDiagnostics T7039014b.java
 */
public class T7039014b {

    interface A<T> {
        default byte m(String x) { return 0; }
        char m(T x);
    }

    interface B extends A<String> {
    }
}
