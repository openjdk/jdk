/*
 * @test /nodynamiccopyright/
 * @summary Verify enhanced variable declaration statements
 * @enablePreview
 * @compile EnhancedVariableDeclStatementTest.java
 * @run main EnhancedVariableDeclStatementTest
 */
import java.util.Objects;
import java.util.function.Supplier;

public class EnhancedVariableDeclStatementTest {
    static int x = -1;

    public static void main(String[] args) {
        basicTest();
        targetTypingTest();
        scopeAndShadowingTest();
        assertEx(EnhancedVariableDeclStatementTest::nullLiteralTopLevelTest, NullPointerException.class);
        assertMatchExceptionWithNested(EnhancedVariableDeclStatementTest::raiseExceptionTest, TestPatternFailed.class);
    }

    static void basicTest() {
        Point p = new Point(1, 2);
        Point(Integer a, Integer b) = p;
        assertEquals(3, a + b);

        IPoint ip   = new Point(3, 4);
        Point(var c, var d) = ip;
        assertEquals(7, c + d);

        p = new Point(1, null);
        Point(var e, var f) = p;
        assertEquals(null, f);

        PointP wp = new PointP(1, 2);
        PointP(int ap, double bp) = wp;
        assertEquals(2.0d, bp);
    }

    static Integer raiseExceptionTest() {
        PointEx pointEx = new PointEx(1, 2);
        PointEx(Integer a_ex, Integer b_noex) = pointEx;
        return a_ex;
    }

    static Integer nullLiteralTopLevelTest() {
        Point(Integer x, Integer y) = null;
        return x;
    }

    static void scopeAndShadowingTest() {
        Point p = new Point(10, 20);
        int sum;

        {
            Point(Integer x, Integer y) = p;
            assertEquals(10, x);
            sum = x + y;
        }

        assertEquals(30, sum);
        assertEquals(-1, EnhancedVariableDeclStatementTest.x);

        {
            Point(Integer x, Integer y) = new Point(1, 2);
            assertEquals(3, x + y);
        }
    }

    static void targetTypingTest() {
        Box<String> box = new Box<>("ok");
        Box(var value) = box;
        assertEquals(1, pick(value));
    }
    static int pick(String value) { return 1; }
    static int pick(Object value) { return 2; }
    record Box<T>(T value) { }

    sealed interface IPoint permits Point {}
    record Point(Integer x, Integer y) implements IPoint { }
    record PointP(int x, double y) { }
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

    static <T> void assertMatchExceptionWithNested(Supplier<Integer> f, Class<?> nestedExceptionClass) {
        try {
            f.get();
            fail("Expected an exception, but none happened!");
        }
        catch(Exception ex) {
            assertEquals(MatchException.class, ex.getClass());
            MatchException me = (MatchException) ex;
            assertEquals(nestedExceptionClass, me.getCause().getClass());
        }
    }

    static <T> void assertEx(Supplier<Integer> f, Class<?> exceptionClass) {
        try {
            f.get();
            fail("Expected an exception, but none happened!");
        }
        catch(Exception ex) {
            assertEquals(exceptionClass, ex.getClass());
        }
    }
}
