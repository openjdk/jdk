/*
 * @test /nodynamiccopyright/
 * @bug 8263590
 * @summary Verify correct warnings are produced for raw types in bindings
 * @compile/ref=RawTypeBindingWarning.out -Xlint:rawtypes -XDrawDiagnostics RawTypeBindingWarning.java
 */
public class RawTypeBindingWarning<T> {
    public static boolean t(Object o) {
        return o instanceof RawTypeBindingWarning w;
    }
}
