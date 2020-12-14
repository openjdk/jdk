import java.util.function.Supplier;

/**
 * @test
 */
public class CaptureInt {
    static Supplier<Integer> supplier = () -> {
        boolean b0 = false;
        int i0 = 5;

        class Local {
            int i = i0 + 2;
        }

        return ((Supplier<Integer>) () -> new Local().i).get();
    };

    public static void main(String[] args) {
        assert supplier.get() == 7;
    }
}
