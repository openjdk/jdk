/*
 * @test /nodynamiccopyright/
 * @bug 8206986
 * @summary Verify switch over boolean/long/float/double is not allowed.
 * @compile/fail/ref=SwitchNoExtraTypes.out -XDrawDiagnostics SwitchNoExtraTypes.java
 * @compile --enable-preview --source 22 SwitchNoExtraTypes.java
 */

public class SwitchNoExtraTypes {

    private void switchBoolean(boolean b) {
        switch (b) {
            case true: return ;
            default:
        }
    }

    private void switchLong(long l) {
        switch (l) {
            case 0l: return ;
            default:
        }
    }

    private void switchFloat(float f) {
        switch (f) {
            case 0f: return ;
            default:
        }
    }

    private void switchDouble(double d) {
        switch (d) {
            case 0d: return ;
            default:
        }
    }

}
