/**
 * @-test
 */
public class CaptureIntAnonymous {
    static Runnable runnable = () -> {
        boolean b0 = false;
        int i0 = 5;

        class Local {
            int i = i0 + 2;
        }

        Runnable dummy = () -> new Local() {
        };
    };

    public static void main(String args[]) {
    }
}
