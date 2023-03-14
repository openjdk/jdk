/**
 * @test
 * @enablePreview
 * @compile Matcher.java
 * @run main Matcher
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.template.Carriers;
import java.util.Objects;

public record Matcher(String s, int i) {

    public static void main(String... args) {
        test(new Matcher("a", 0));
        assertEquals(2, test2(new Matcher("a", 42)));
    }

    public static void test(Object o) {
        //original code:
        if (o instanceof Matcher(String os, int oi)) {
            System.err.println("os: " + os);
            System.err.println("i: " + oi);
        }
    }

    public static int test2(Matcher o) {
        //original code:
        return switch (o) {
            case Matcher(String s, int i) when s.isEmpty() -> 1;
            case Matcher(String s, int i) when i == 42 -> 2;
            case Matcher mm -> 3;
        };
    }

    //original code:
    public __matcher Matcher(String s, int i) throws Throwable { //XXX: exceptions?
        MethodType returnType = MethodType.methodType(Object.class, String.class, int.class); //TODO: return type of the Carrier constructor?
        return Carriers.factory(returnType).invoke(this.s, this.i);
        // s = this.s;
        // i = this.i;
    }

    /*
        ask about exceptions on matchers
        ask about deprecated for matcher bindings -- makes sense at all?
        generating the matcher body
        separate compilation: Matcher attribute (ClassWriter, ClassReader)
        reflection: JVM and javax.lang.Model support
        test switch with the record Matcher
        clean translation to not use SwitchBootstraps
        clean TransPatterns.java
        flags in Matcher attribute
        overloading selection
    */

    private static void assertEquals(int expected, int actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }
}
