import java.util.Objects;

/**
 * @test
 * @enablePreview
 * @compile --enable-preview -source ${jdk.version} Unnamed.java
 * @run main Unnamed
 */
public class Unnamed {
    public static void main(String[] args) throws Throwable {
        new Unnamed().run();
    }

    public void run() {
        assertEquals(testMultiValuesTopLevel(new R1()), 1);
        assertEquals(testMultiValuesTopLevel(new R3()), 2);
        assertEquals(1, testMultiValuesNested(new Box(new R1())));
        assertEquals(1, testMultiValuesNested(new Box(new R2())));
        assertEquals(2, testMultiValuesNested(new Box(new R3())));
        assertEquals(3, testMultiValuesNested(new Box(new R4())));
    }
    private void unnamedTest() {
        int _ = 0;
        int _ = 1;
        try (Lock _ = null) {
            try (Lock _ = null) {
            } catch (Exception _) {
                try {
                } catch (Exception _) {}
            }
        }
        String[] strs = null;
        for (var _ : strs) {
            for (var _ : strs) {
            }
        }
        TwoParams p1 = (_, _) -> {};
        TwoParams p2 = (var _, var _) -> {};
        R r = new R(null);
        if (r instanceof R(_)) {}
        for (int _ = 0, _ = 1; ;) {}
    }

    sealed abstract class Base permits R1, R2, R3, R4 { }
    final  class R1  extends Base { }
    final  class R2  extends Base { }
    final  class R3  extends Base { }
    final  class R4  extends Base { }
    record Box<T extends Base>(T content) { }

    int testMultiValuesTopLevel(Object o) {
        return switch (o) {
            case R1 _, R2 _ -> 1;
            default -> 2;
        };
    }

    private int testMultiValuesNested(Box<?> b) {
        return switch (b) {
            case Box(R1 _), Box(R2 _) -> 1;
            case Box(R3 _) -> 2;
            case Box(_)  -> 3;
        };
    }

    // TODO list
    // - Default behavior with all the cases with underscores
    //    switch (shape) {
    //        case Circle c -> // use `c`
    //        case Rectangle _, Triangle _ -> // default behavior
    //    }
    // - Box(Rectangle) is dead, but not according to the left-to-right domination order, this is OK
    //    switch (box) {
    //        case Box(Circle c) ->
    //        case Box(Rectangle _), Box(_) ->
    //    }
    // - Guards with unnamed pattern variables disallowed
    // - Domination

    class Lock implements AutoCloseable {
        @Override
        public void close() {}
    }
    interface TwoParams {
        public void run(Object o1, Object o2);
    }
    record R(Object o) {}

    void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }
}