/*
 * @test /nodynamiccopyright/
 * @summary
 * @enablePreview
 */
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class ForEachPatterns {
    public static void main(String[] args) {

        List<Point>             in                   = List.of(new Point(1, 2), new Point(2, 3));
        List<IPoint>            in_iface             = List.of(new Point(1, 2), new Point(2, 3));
        List                    inRaw                = List.of(new Point(1, 2), new Point(2, 3), new Frog(3, 4));
        List<PointEx>           inWithPointEx        = List.of(new PointEx(1, 2));
        byte[]                  inBytes              = { (byte) 127, (byte) 127 };
        List<Point>             inWithNullComponent  = List.of(new Point(1, null), new Point(2, 3));
        Point[]                 inArray              = in.toArray(Point[]::new);
        List<WithPrimitives>    inWithPrimitives     = List.of(new WithPrimitives(1, 2), new WithPrimitives(2, 3));
        IParent                 recs []              = { new Rec(1) };
        List<Point>             inWithNull           = new ArrayList<>();
        {
            inWithNull.add(new Point(2, 3));
            inWithNull.add(null);
        }

        assertEquals(8, iteratorEnhancedFor(in));
        assertEquals(8, arrayEnhancedFor(inArray));
        assertEquals(8, simpleDecostructionPatternWithAccesses(in));
        assertEx(ForEachPatterns::simpleDecostructionPatternWithAccesses, null, NullPointerException.class);
        assertMatchExceptionWithNested(ForEachPatterns::simpleDecostructionPatternWithAccesses, inWithNull, NullPointerException.class);
        assertEx(ForEachPatterns::simpleDecostructionPatternWithAccesses, inWithNullComponent, NullPointerException.class);
        assertMatchExceptionWithNested(ForEachPatterns::simpleDecostructionPatternException, inWithPointEx, TestPatternFailed.class);
        assertEx(ForEachPatterns::simpleDecostructionPatternWithAccesses, (List<Point>) inRaw, ClassCastException.class);
        assertEquals(2, simpleDecostructionPatternNoComponentAccess(in));
        assertMatchExceptionWithNested(ForEachPatterns::simpleDecostructionPatternNoComponentAccess, inWithNull, NullPointerException.class);
        assertEquals(2, simpleDecostructionPatternNoComponentAccess(inWithNullComponent));
        assertEquals(8, varAndConcrete(in));
        assertEquals(3, returnFromEnhancedFor(in));
        assertEquals(0, breakFromEnhancedFor(in));
        assertEquals(254, primitiveWidening(inBytes));
        assertEquals(8, sealedRecordPassBaseType(in_iface));
        assertEquals(8, withPrimitives(inWithPrimitives));
        assertEquals(List.of(Color.RED), JEPExample());
        assertEquals(1, arrayWithSealed(recs));
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

    static int sealedRecordPassBaseType(List<IPoint> points) {
        int result = 0;

        for(Point(var x, var y) : points) {
            result += (x + y);
        }

        return result;
    }

    static int withPrimitives(List<WithPrimitives> points) {
        int result = 0;
        for (WithPrimitives(int a, double b): points) {
            result += a + (int) b;
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

    static List<Color> JEPExample() {
        Rectangle rect = new Rectangle(
                new ColoredPoint(new Point(1,2), Color.RED),
                new ColoredPoint(new Point(3,4), Color.GREEN)
        );
        Rectangle[] rArr = {rect};
        return printUpperLeftColors(rArr);
    }
    //where
    static List<Color> printUpperLeftColors(Rectangle[] r) {
        List<Color> ret = new ArrayList<>();
        for (Rectangle(ColoredPoint(Point p, Color c), ColoredPoint lr): r) {
            ret.add(c);
        }
        return ret;
    }

    static int arrayWithSealed(IParent[] recs){
        for (Rec(int a) : recs) {
            return a;
        }
        return -1;
    }

    enum Color { RED, GREEN, BLUE }
    record ColoredPoint(Point p, Color c) {}
    record Rectangle(ColoredPoint upperLeft, ColoredPoint lowerRight) {}

    sealed interface IParent permits Rec {}
    record Rec(int a) implements IParent {}

    sealed interface IPoint permits Point {}
    record Point(Integer x, Integer y) implements IPoint { }

    record GPoint<T>(T x, T y) { }
    record VoidPoint() { }
    record RecordOfLists(List<Integer> o) {}
    record RecordOfLists2(List<List<Integer>> o) {}

    @Target({ElementType.TYPE_USE, ElementType.LOCAL_VARIABLE})
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
    record WithPrimitives(int x, double y) { }
    static final String EXCEPTION_MESSAGE = "exception-message";
    public static class TestPatternFailed extends AssertionError {
        public TestPatternFailed(String message) {
            super(message);
        }
    }

    // error handling
    static void fail(String message) {
        throw new AssertionError(message);
    }

    static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + "," +
                    "got: " + actual);
        }
    }

    static <T> void assertMatchExceptionWithNested(Function<List<T>, Integer> f, List<T> points, Class<?> nestedExceptionClass) {
        try {
            f.apply(points);
            fail("Expected an exception, but none happened!");
        }
        catch(Exception ex) {
            assertEquals(MatchException.class, ex.getClass());

            MatchException me = (MatchException) ex;

            assertEquals(nestedExceptionClass, me.getCause().getClass());
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
}
