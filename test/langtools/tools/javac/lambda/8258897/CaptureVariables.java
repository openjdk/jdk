import java.util.function.Supplier;

/**
 * @test /nodynamiccopyright/
 * @bug 8258897
 * @summary Checks translation of capturing local classes inside nested lambdas
 * @run main CaptureVariables
 */
public class CaptureVariables {
    static Supplier<Integer> supplier1 = () -> {
        boolean b0 = false;
        int i0 = 6;
        boolean b1 = false;
        String s0 = "hello";

        class Local {
            int i = s0.length() + i0;
        }

        return ((Supplier<Integer>) () -> new Local().i).get();
    };

    static Supplier<Integer> supplier2 = () -> {
        boolean b0 = false;
        int i0 = 6;
        boolean b1 = false;
        String s0 = "hello";

        class Local {
            int i = s0.length() + i0;
        }

        return ((Supplier<Integer>) () -> ((Supplier<Integer>) () -> new Local().i).get()).get();
    };

    Supplier<Integer> supplier3 = () -> {
        boolean b0 = false;
        int i0 = 6;
        boolean b1 = false;
        String s0 = "hello";

        class Local {
            int i = s0.length() + i0;
        }

        return ((Supplier<Integer>) () -> new Local().i).get();
    };

    Supplier<Integer> supplier4 = () -> {
        boolean b0 = false;
        int i0 = 6;
        boolean b1 = false;
        String s0 = "hello";

        class Local {
            int i = s0.length() + i0;
        }

        return ((Supplier<Integer>) () -> ((Supplier<Integer>) () -> new Local().i).get()).get();
    };

    public static void main(String[] args) {
        assert supplier1.get() == 11;
        assert supplier2.get() == 11;
        assert new CaptureVariables().supplier3.get() == 11;
        assert new CaptureVariables().supplier4.get() == 11;
    }
}
