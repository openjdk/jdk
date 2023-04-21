import java.util.Objects;

/**
 * @test /nodynamiccopyright/
 * @bug 8304246
 * @summary Compiler Implementation for Unnamed patterns and variables
 * @enablePreview
 * @compile --enable-preview -source ${jdk.version} Unnamed.java
 * @run main Unnamed
 */
public class Unnamed {
    public static void main(String[] args) throws Throwable {
        new Unnamed().run();
    }

    public void run() {
        assertEquals(1, testMultiValuesTopLevel(new R1()));
        assertEquals(2, testMultiValuesTopLevel(new R3()));
        assertEquals(1, testMultiValuesTopLevel2(new R1()));
        assertEquals(2, testMultiValuesTopLevel2(new R2()));
        assertEquals(2, testMultiValuesTopLevel2(new R4()));
        assertEquals(1, testMultiValuesNested(new Box<>(new R1())));
        assertEquals(1, testMultiValuesNested(new Box<>(new R2())));
        assertEquals(2, testMultiValuesNested(new Box<>(new R3())));
        assertEquals(3, testMultiValuesNested(new Box<>(new R4())));
        assertEquals(1, testMultiValuesNestedUnnamedVarAndPattern(new Box<>(new R1())));
        assertEquals(2, testMultiValuesNestedUnnamedVarAndPattern(new Box<>(new R4())));
        assertEquals(1, testMultiValuesNestedMix(new Box<>(new R1())));
        assertEquals(1, testMultiValuesNestedMix(new Box2<>(new R1())));
        assertEquals(1, testMultiValuesNestedMix2(new Box<>(new R1())));
        assertEquals(1, testMultiValuesNestedMix2("BOX"));
        assertEquals(2, testMultiValuesNestedMix2(new Box2<>(new R1())));
        assertEquals(1, testMultiValuesStatementBlock(42));
        assertEquals(1, testMultiValuesStatementBlock(42.0f));
        assertEquals(2, testMultiValuesStatementBlock("BOX"));
        assertEquals(1, testMultiValuesStatementBlock2(new Box<>(new R1())));
        assertEquals(1, testMultiValuesStatementBlock2("BOX"));
        assertEquals(2, testMultiValuesStatementBlock2(new Box2<>(new R1())));
//        assertEquals(2, testMultiValuesGuards(new R3(), 1));
//        assertEquals(3, testMultiValuesGuards(new R4(), 42));

//        assertEquals(3, testMultiValuesGuards(new R3(), 42));
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

    int testMultiValuesTopLevel(Object o) {
        return switch (o) {
            case R1 _, R2 _ -> 1;
            default -> 2;
        };
    }

    int testMultiValuesTopLevel2(Base o) {
        return switch (o) {
            case R1 r -> 1;
            case R2 _, R3 _, R4 _ -> 2;
        };
    }

    int testMultiValuesNested(Box<?> b) {
        return switch (b) {
            case Box(R1 _), Box(R2 _) -> 1;
            case Box(R3 _) -> 2;
            case Box(_)  -> 3;
        };
    }

    int testMultiValuesNestedUnnamedVarAndPattern(Box<?> b) {
        return switch (b) {
            case Box(R1 _), Box(R2 _) -> 1;
            case Box(R3 _), Box(_) -> 2;
        };
    }

    int testMultiValuesNestedMix(Object b) {
        return switch (b) {
            case Box(_), Box2(_) -> 1;
            default -> 2;
        };
    }

    int testMultiValuesNestedMix2(Object b) {
        return switch (b) {
            case Box(_), String _ -> 1;
            default -> 2;
        };
    }

    int testMultiValuesStatementBlock(Object o) {
        switch (o) {
            case Integer _:
            case Number _:
                return 1;
            default:
                return 2;
        }
    }

    int testMultiValuesStatementBlock2(Object o) {
        switch (o) {
            case Box(_):
            case String _:
                return 1;
            default:
                return 2;
        }
    }

//    int testMultiValuesGuards(Base b, int x) {        // TODO
//        return switch (b) {
//            case R1 r -> 1;
//            case R2 _, R3 _, R4 _ when x == 1 -> 2;
//            case R2 _, R3 _, R4 _ -> 3;
//        };
//    }

//    int testMultiValuesNestedGuards(Box<?> b, int x) { // TODO
//        return switch (b) {
//            case Box(R1 _), Box(R2 _) -> 1;
//            case Box(R3 _), Box(_) when x == 1 -> 2;
//            case Box(R3 _), Box(_) -> 3;
//        };
//    }

    class Lock implements AutoCloseable {
        @Override
        public void close() {}
    }
    interface TwoParams {
        public void run(Object o1, Object o2);
    }
    record R(Object o) {}

    sealed abstract class Base permits R1, R2, R3, R4 { }
    final  class R1  extends Base { }
    final  class R2  extends Base { }
    final  class R3  extends Base { }
    final  class R4  extends Base { }
    record Box<T extends Base>(T content) { }
    record Box2<T extends Base>(T content) { }

    void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }
}