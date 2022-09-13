/*
 * @test /nodynamiccopyright/
 * @summary
 * @compile --enable-preview -source ${jdk.version} ForEachPatterns.java
 * @run main/othervm --enable-preview ForEachPatterns
 */
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class ForEachPatterns {
    public static void main(String[] args) {

        List<Point>   in                  = List.of(new Point(1, 2), new Point(2, 3));
        List          inRaw               = List.of(new Point(1, 2), new Point(2, 3), new Frog(3, 4));
        List<PointEx> inWithPointEx       = List.of(new PointEx(1, 2));
        byte[]        inBytes             = { (byte) 127, (byte) 127 };
        List<Point>   inWithNullComponent = List.of(new Point(1, null), new Point(2, 3));
        List<Point>   inWithNull          = new ArrayList<>();
        Point[]       inArray             = in.toArray(Point[]::new);

        inWithNull.add(new Point(2, 3));
        inWithNull.add(null);

        assertEquals(8, iteratorEnhancedFor(in));
        assertEquals(8, arrayEnhancedFor(inArray));
        assertEquals(8, iteratorEnhancedForWithBinding(in));
        assertEquals(8, simpleDecostructionPatternWithAccesses(in));
        assertEx(ForEachPatterns::simpleDecostructionPatternWithAccesses, inWithNull, MatchException.class);
        assertEx(ForEachPatterns::simpleDecostructionPatternWithAccesses, inWithNullComponent, NullPointerException.class);
        assertEx(ForEachPatterns::simpleDecostructionPatternException, inWithPointEx, MatchException.class);
        assertEx(ForEachPatterns::simpleDecostructionPatternWithAccesses, (List<Point>) inRaw, ClassCastException.class);
        assertEquals(2, simpleDecostructionPatternNoComponentAccess(in));
        assertEx(ForEachPatterns::simpleDecostructionPatternNoComponentAccess, inWithNull, MatchException.class);
        assertEquals(2, simpleDecostructionPatternNoComponentAccess(inWithNullComponent));
        assertEquals(8, varAndConcrete(in));
        assertEquals(3, returnFromEnhancedFor(in));
        assertEquals(0, breakFromEnhancedFor(in));
        assertEquals(254, primitiveWidening(inBytes));
    }

    static int iteratorEnhancedFor(List<Point> points) {
        int result = 0;
        for (Point(Integer a, Integer b) : points) {
            result += a + b;
        }
        return result;
    }

    static int arrayEnhancedFor(Point[] points) {
        int result = 0;
        for (Point(Integer a, Integer b) : points) {
            result += a + b;
        }
        return result;
    }

    static int iteratorEnhancedForWithBinding(List<Point> points) {
        int result = 0;
        for (Point(Integer a, Integer b) p: points) {
            result += p.x() + p.y();
        }
        return result;
    }

    static int simpleDecostructionPatternWithAccesses(List<Point> points) {
        int result = 0;
        for (Point(var a, var b): points) {
            result += a + b;
        }
        return result;
    }

    static int simpleDecostructionPatternException(List<PointEx> points) {
        int result = 0;
        for (PointEx(var a, var b): points) {
            result += a + b;
        }
        return result;
    }

    static int simpleDecostructionPatternNoComponentAccess(List<Point> points) {
        int result = 0;
        for (Point(var a, var b): points) {
            result += 1;
        }
        return result;
    }

    static int varAndConcrete(List<Point> points) {
        int result = 0;
        for (Point(Integer a, var b): points) {
            result += a + b;
        }
        return result;
    }

    static int returnFromEnhancedFor(List<Point> points) {
        for (Point(var a, var b): points) {
            return a + b;
        }
        return -1;
    }

    static int breakFromEnhancedFor(List<Point> points) {
        int i = 1;
        int result = 0;
        for (Point(var a, var b): points) {
            if (i == 1) break;
            else result += a + b;
        }
        return result;
    }

    // Simpler pos tests with local variable declarations
    // Should pass now and in the future if local variable
    // declaration is subsumed by patterns (not just record patterns)
    static int primitiveWidening(byte[] inBytes) {
        int acc = 0;
        for (int i: inBytes) {
            acc += i;
        }
        return acc;
    }

    static int applicability1(List<Point> points) {
        for (IPoint p: points) {
            System.out.println(p);
        }
        return -1;
    }

    static int applicability2(List<Object> points) {
        for (Object p: points) {
            System.out.println(p);
        }
        return -1;
    }

    static <T> void method() {}

    static void for_parsing(int i) {
        List<Point> points = null;
        List<GPoint<Integer>> generic_points = null;

        for (Point(Integer a, Integer b) : points) { }
        for (ForEachPatterns.Point(Integer a, Integer b) : points) { }
        for (GPoint<Integer>(Integer a, Integer b) : generic_points) { }
        for (@Annot(field = "test") Point p : points) {}
        for (method(); i == 0;) { i++; }
        for (method(), method(); i == 0;) { i++; }
        for (ForEachPatterns.<Integer>method(); i == 0;) { i++; }
    }

    static void fail(String message) {
        throw new AssertionError(message);
    }

    static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + "," +
                    "got: " + actual);
        }
    }

    static <T> void assertEx(Function<List<T>, Integer> f, List<T> points, Class<?> exceptionClass) {
        try {
            f.apply(points);
            fail("Expected an exception, but none happened!");
        }
        catch(Exception ex) {
            assertEquals(exceptionClass, ex.getClass());
        }
    }

    sealed interface IPoint permits Point {}
    record Point(Integer x, Integer y) implements IPoint { }
    record GPoint<T>(T x, T y) { }
    @interface Annot {
        String field();
    }
    record Frog(Integer x, Integer y) { }
    record PointEx(Integer x, Integer y) {
        @Override
        public Integer x() {
            throw new TestPatternFailed(EXCEPTION_MESSAGE);
        }
    }
    static final String EXCEPTION_MESSAGE = "exception-message";
    public static class TestPatternFailed extends AssertionError {
        public TestPatternFailed(String message) {
            super(message);
        }
    }
}
