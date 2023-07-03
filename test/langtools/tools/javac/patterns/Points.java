/**
 * @test
 * @enablePreview
 * @compile -parameters Points.java
 * @run main Points
 */
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record Points(Collection<Integer> xs, Collection<Integer> ys) {
    public static void main(String... args) {
        assertEquals(List.of(1), test(new Points(List.of(1), List.of(2))));
    }

    public static List<Integer> test(Object o) {
        if (o instanceof Points(List<Integer> xs, List<Integer> ys)) {
            return xs;
        }
        return List.of(-1);
    }

    @MatcherAnnot
    public __matcher Points(@BindingAnnot Collection<Integer> xs, @BindingAnnot Collection<Integer> ys) {
        xs = this.xs;
        ys = this.ys;
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MatcherAnnot { }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface BindingAnnot { }

    private static <T> void assertEquals(T expected, T actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }
}
