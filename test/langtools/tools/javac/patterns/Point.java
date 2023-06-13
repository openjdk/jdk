/**
 * @test
 * @enablePreview
 * @compile Point.java
 * @run main Point
 */
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

public record Point(Integer x, Integer y) {
    public static void main(String... args) {
        assertEquals(1, testX(new Point(1, 2)));
        assertEquals(2, testY(new Point(1, 2)));
        assertEquals(2, test2(new Point(42, 4)));
        assertEquals(3, test2(new Point(4, 42)));
    }

    public static Integer testX(Object o) {
        if (o instanceof Point(Integer x, Integer y)) {
            return x;
        }
        return -1;
    }

    public static Integer testY(Object o) {
        if (o instanceof Point(Integer x, Integer y)) {
            return y;
        }
        return -1;
    }

    public static int test2(Point o) {
        return switch (o) {
            case Point(Integer x, Integer y) when x == 42 -> 2;
            case Point(Integer x, Integer y) when y == 42 -> 3;
            case Point mm -> -1;
        };
    }

    @MyCustomAnnotation(annotField = 42)
    public __matcher Point(Integer x, Integer y) {
         x = this.x;
         y = this.y;
    }

    @Target(ElementType.METHOD) // TODO: element type must target matchers
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyCustomAnnotation{
        int annotField();
    }
    private static <T> void assertEquals(T expected, T actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }
}
