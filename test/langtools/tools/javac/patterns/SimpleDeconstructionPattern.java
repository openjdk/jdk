/**
 * @test
 * @compile/fail/ref=SimpleDeconstructionPatternNoPreview.out -XDrawDiagnostics SimpleDeconstructionPattern.java
 * @compile --enable-preview -source ${jdk.version} SimpleDeconstructionPattern.java
 * @run main/othervm --enable-preview SimpleDeconstructionPattern
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class SimpleDeconstructionPattern {

    public static void main(String... args) throws Throwable {
        if (!test2(new P(42))) {
            throw new IllegalStateException();
        }
        if (test2(new P(41))) {
            throw new IllegalStateException();
        }
        if (!test2a(new P(42))) {
            throw new IllegalStateException();
        }
        if (test2a(new P(41))) {
            throw new IllegalStateException();
        }
        if (!test4(new P2(new P(42), ""))) {
            throw new IllegalStateException();
        }
        if (test4(new P2(new P(41), ""))) {
            throw new IllegalStateException();
        }
        if (test4(new P2(new P(42), "a"))) {
            throw new IllegalStateException();
        }
        if (!test5(new P(42))) {
            throw new IllegalStateException();
        }
        if (test5(new P(41))) {
            throw new IllegalStateException();
        }
        if (!test7(new P3(""))) {
            throw new IllegalStateException();
        }
        if (test7(new P3("a"))) {
            throw new IllegalStateException();
        }
        if (!test7a(new P3(""))) {
            throw new IllegalStateException();
        }
        if (test7a(new P3("a"))) {
            throw new IllegalStateException();
        }
        if (test8(new P4(""))) {
            throw new IllegalStateException();
        }
        if (!test8(new P4(new P3("")))) {
            throw new IllegalStateException();
        }
        if (!test8a(new P4(new P3("")))) {
            throw new IllegalStateException();
        }
        if (test8(new P4(new P3("a")))) {
            throw new IllegalStateException();
        }
        if (test8a(new P4(new P3("a")))) {
            throw new IllegalStateException();
        }
        if (!test9(new P5(new ArrayList<String>(Arrays.asList(""))))) {
            throw new IllegalStateException();
        }
        if (test9(new P5(new LinkedList<String>(Arrays.asList(""))))) {
            throw new IllegalStateException();
        }
        if (testA(new P6(null))) {
            throw new IllegalStateException();
        }
        if (!testA(new P6(new P3(null)))) {
            throw new IllegalStateException();
        }
        if (testB(new P6(null))) {
            throw new IllegalStateException();
        }
        if (!testB(new P6(new P3(null)))) {
            throw new IllegalStateException();
        }
        if (testC(new P6(null))) {
            throw new IllegalStateException();
        }
        if (!testC(new P6(new P3("")))) {
            throw new IllegalStateException();
        }
        if (!testD(new P4("test"))) {
            throw new IllegalStateException();
        }
        if (!testE(new P6(new P3(null)))) {
            throw new IllegalStateException();
        }
        if (!testF(new P7(0, (short) 0))) {
            throw new IllegalStateException();
        }
        if (testF(new P7(0, (short) 1))) {
            throw new IllegalStateException();
        }
        if (testGen3(new GenRecord1<>(3L, ""))) {
            throw new IllegalStateException();
        }
        if (!testGen3(new GenRecord1<>(3, ""))) {
            throw new IllegalStateException();
        }
        if (!testGen3(new GenRecord1<>(3, ""))) {
            throw new IllegalStateException();
        }
    }

    private static void exp(Object o) throws Throwable {
        if (o instanceof P(var i)) {
            System.err.println("i=" + i);
        }
    }

    private static boolean test2(Object o) throws Throwable {
        return o instanceof P(var i) && i == 42;
    }

    private static boolean test2a(Object o) throws Throwable {
        return o instanceof P(int i) && i == 42;
    }

    private static boolean test4(Object o) throws Throwable {
        return o instanceof P2(P(var i), var s) && i == 42 && "".equals(s);
    }

    private static boolean test5(Object o) throws Throwable {
        return o instanceof P(var i) && i == 42;
    }

    private static boolean test7(Object o) throws Throwable {
        return o instanceof P3(var s) && "".equals(s);
    }

    private static boolean test7a(Object o) throws Throwable {
        return o instanceof P3(String s) && "".equals(s);
    }

    private static boolean test8(Object o) throws Throwable {
        return o instanceof P4(P3(var s)) && "".equals(s);
    }

    private static boolean test8a(Object o) throws Throwable {
        return o instanceof P4(P3(String s)) && "".equals(s);
    }

    private static boolean test9(Object o) throws Throwable {
        return o instanceof P5(ArrayList<String> l) && !l.isEmpty();
    }

    private static boolean testA(Object o) throws Throwable {
        return o instanceof P6(P3(var s));
    }

    private static boolean testB(Object o) throws Throwable {
        return o instanceof P6(P3(String s));
    }

    private static boolean testC(Object o) throws Throwable {
        return o instanceof P6(P3(String s)) && s.isEmpty();
    }

    private static boolean testD(Object o) throws Throwable {
        return o instanceof P4(String s) p && (s.isEmpty() || "test".equals(p.o()));
    }

    private static boolean testE(Object o) throws Throwable {
        return o instanceof P6(P3(String s)) && s == null;
    }

    private static boolean testF(Object o) throws Throwable {
        return o instanceof P7(int i, short s) && i == s;
    }

    private static boolean testGen3(Object o) throws Throwable {
        return o instanceof GenRecord1<?, ?>(Integer i, var s) && i.intValue() == 3 && s.length() == 0;
    }

    private static boolean testGen4(GenBase<Integer, String> o) throws Throwable {
        return o instanceof GenRecord1<Integer, String>(var i, var s) && i.intValue() == 3 && s.length() == 0;
    }

    public record P(int i) {
    }

    public record P2(P p, String s) {
    }

    public record P3(String s) {
    }

    public record P4(Object o) {}

    public record P5(List<String> l) {}
    public record P6(P3 p) {}

    public record P7(int i, short s) {}

    public interface Base {}
    public record BaseUse(Base b) {}
    public record BaseSubclass(int i) implements Base {}

    public interface GenBase<T1, T2 extends CharSequence> {}
    public record GenRecord1<T1, T2 extends CharSequence> (T1 i, T2 s) implements GenBase<T1, T2> {}
}
