/*
 * @test /nodynamiccopyright/
 * @bug 8015831
 * @compile/ref=ThisEscapeParameter.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeParameter.java
 * @summary Verify 'this' escape detection handles leaks via passing 'this' as a parameter
 */

public class ThisEscapeParameter {

    public ThisEscapeParameter() {
        ThisEscapeParameter.method(this);
    }

    public static void method(Object obj) {
        obj.hashCode();
    }
}
