/*
 * @test /nodynamiccopyright/
 * @bug 8177466
 * @summary Add compiler support for local variable type-inference
 * @compile -source 9 ParserTest.java
 * @compile/fail/ref=ParserTest.out -XDrawDiagnostics ParserTest.java
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.function.Function;
import java.util.List;

class ParserTest<var> {
    static class TestClass {
        static class var { } //illegal
    }

    static class TestInterface {
        interface var { } //illegal
    }

    static class TestEnum {
        enum var { } //illegal
    }

    static class TestAnno {
        @interface var { } //illegal
    }

    @Target(ElementType.TYPE_USE)
    @interface TA { }

    @interface DA { }

    static class var extends RuntimeException { } //illegal

    var x = null; //illegal

    void test() {
        var[] x1 = null; //illegal
        var x2[] = null; //illegal
        var[][] x3 = null; //illegal
        var x4[][] = null; //illegal
        var[] x5 = null; //illegal
        var x6[] = null; //illegal
        var@TA[]@TA[] x7 = null; //illegal
        var x8@TA[]@TA[] = null; //illegal
        var x9 = null, y = null; //illegal
        final @DA var x10 = m(); //ok
        @DA final var x11 = m(); //ok
    }

    var m() { //illegal
        return null;
    }

    void test2(var x) { //error
        List<var> l1; //error
        List<? extends var> l2; //error
        List<? super var> l3; //error
        try {
            Function<var, String> f = (var x2) -> ""; //error
        } catch (var ex) { } //error
    }

    void test3(Object o) {
        boolean b1 = o instanceof var; //error
        Object o2 = (var)o; //error
    }
}
