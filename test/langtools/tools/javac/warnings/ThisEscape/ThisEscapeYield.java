/*
 * @test /nodynamiccopyright/
 * @bug 8015831
 * @compile/ref=ThisEscapeYield.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeYield.java
 * @summary Verify 'this' escape detection handles leaks via switch expression yields
 */

public class ThisEscapeYield {

    public ThisEscapeYield(int x) {
        ThisEscapeYield y = switch (x) {
            case 3:
                if (x > 17)
                    yield this;
                else
                    yield null;
            default:
                yield null;
        };
        if (y != null)
            y.mightLeak();
    }

    public void mightLeak() {
    }
}
