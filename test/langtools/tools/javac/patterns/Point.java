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
import java.lang.invoke.MethodType;
import java.lang.template.Carriers;
import java.util.Objects;

public record Point(Integer x, Integer y) {
    /*
        TODO
        ask about exceptions on matchers
        ask about deprecated for matcher bindings -- makes sense at all?
        generating the matcher body
        separate compilation: Matcher attribute (ClassWriter, ClassReader)
        reflection: JVM and javax.lang.Model support
        clean TransPatterns.java
        flags in Matcher attribute
        overloading selection

        OK
        clean translation to not use SwitchBootstraps
        test switch with the record Matcher
    */

    public static void main(String... args) {
        assertEquals(1, test(new Point(1, 2)));
        assertEquals(2, test2(new Point(42, 4)));
    }

    public static Integer test(Object o) {
        if (o instanceof Point(Integer x, Integer y)) {
            return x;
        }
        return -1;
    }

    public static int test2(Point o) {
        return switch (o) {
            case Point(Integer x, Integer y) when x == 42 -> 2;
            case Point mm -> 3;
        };
    }

    //original code:
    @MyCustomAnnotation(annotField = 42)
    public __matcher Point(Integer x, Integer y) throws Throwable { //XXX: exceptions?
        // s = this.s;
        // i = this.i;

        MethodType returnType = MethodType.methodType(Object.class, Integer.class, Integer.class); //TODO: return type of the Carrier constructor?
        return Carriers.factory(returnType).invoke(this.x, this.y);
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
