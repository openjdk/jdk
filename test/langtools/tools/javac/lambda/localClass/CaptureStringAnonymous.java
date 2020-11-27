/**
 * @-test
 */
public class CaptureStringAnonymous {
    static Runnable runnable = () -> {
        boolean b0 = false;
        String s0 = "hello";

        class Local {
            int i = s0.length();
        }

        Runnable dummy = () -> new Local() {
        };
    };

    public static void main(String args[]) {
    }
}
