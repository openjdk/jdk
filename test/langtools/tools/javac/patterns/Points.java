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
import java.lang.invoke.MethodType;
import java.lang.template.Carriers;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record Points(Collection<Integer> is) {
    public static void main(String... args) {
        assertEquals(List.of(1), test(new Points(List.of(1))));
    }

    public static List<Integer> test(Object o) {
        if (o instanceof Points(List<Integer> ss)) {
            return ss;
        }
        return List.of(-1);
    }

    public __matcher Points(@Foo Collection<Integer> is) {
        is = this.is;
    }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Foo { }

    private static <T> void assertEquals(T expected, T actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }
}
