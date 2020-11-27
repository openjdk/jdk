import java.util.function.Supplier;

/**
 * @test
 */
public class CaptureStringCheck2 {
    static Supplier<Integer> supplier = () -> {
        boolean b0 = false;
        String s0 = "hello";

        class Local {
            int i = s0.length();
        }

        return ((Supplier<Integer>) () -> ((Supplier<Integer>) () -> new Local().i).get()).get();
    };

    public static void main(String args[]) {
        assert supplier.get() == 5;
    }
}
