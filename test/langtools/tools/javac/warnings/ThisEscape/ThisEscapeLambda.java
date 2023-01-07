/*
 * @test /nodynamiccopyright/
 * @bug 8015831
 * @compile/ref=ThisEscapeLambda.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeLambda.java
 * @summary Verify 'this' escape detection properly handles lambdas
 */

public class ThisEscapeLambda {

    public ThisEscapeLambda() {
        Runnable r = () -> {
            this.mightLeak();
        };
        System.out.println(r);
    }

    public void mightLeak() {
    }
}
