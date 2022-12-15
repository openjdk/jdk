/*
 * @test /nodynamiccopyright/
 * @bug 8194743
 * @compile/ref=ThisEscapeReturnValue.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeReturnValue.java
 * @summary Verify 'this' escape detection properly handles leaks via method return values
 */

public class ThisEscapeReturnValue {

    public ThisEscapeReturnValue() {
        final Object rval = ThisEscapeReturnValue.method(this);
        ((ThisEscapeReturnValue)rval).mightLeak();
    }

    public static Object method(Object obj) {
        return obj;
    }

    public void mightLeak() {
    }
}
