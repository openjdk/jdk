/*
 * @test /nodynamiccopyright/
 * @bug 5059679
 * @summary Verify proper detection of name class when parameter types narrow
 * @compile/fail/ref=NarrowingNameClash.out -XDrawDiagnostics NarrowingNameClash.java
 */

public class NarrowingNameClash {

    public interface Upper<T> {
        void method(T param);
    }

    public interface Lower<R> extends Upper<Class<R>> {
        void method(Class<?> param);        // erasure name clash here
    }
}
