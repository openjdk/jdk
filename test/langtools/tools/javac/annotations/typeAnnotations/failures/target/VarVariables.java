/*
 * @test /nodynamiccopyright/
 * @bug 8371683
 * @summary Test that type annotations cannot appears on 'var' variables
 * @compile/fail/ref=VarVariables.out -XDrawDiagnostics VarVariables.java
 * @compile/fail/ref=VarVariables-old.out --release 19 -XDrawDiagnostics -XDshould-stop.at=FLOW VarVariables.java
 */

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.util.List;
import java.util.function.Consumer;

class VarVariables {
    private void test(Object o) {
        @DA var v1 = "";
        @DTA var v2 = "";
        @TA var v3 = "";
        Consumer<String> c1 = (@DA var v) -> {};
        Consumer<String> c2 = (@DTA var v) -> {};
        Consumer<String> c3 = (@TA var v) -> {};
        for (@DA var v = ""; !v.isEmpty(); ) {}
        for (@DTA var v = ""; !v.isEmpty(); ) {}
        for (@TA var v = ""; !v.isEmpty(); ) {}
        for (@DA var v : List.of("")) {}
        for (@DTA var v : List.of("")) {}
        for (@TA var v : List.of("")) {}
        try (@DA var v = open()) {
        } catch (Exception ex) {}
        try (@DTA var v = open()) {
        } catch (Exception ex) {}
        try (@TA var v = open()) {
        } catch (Exception ex) {}
        boolean b1 = o instanceof R(@DA var v);
        boolean b2 = o instanceof R(@DTA var v);
        boolean b3 = o instanceof R(@TA var v);
    }

    private AutoCloseable open() {
        return null;
    }
    record R(String str) {}

    @Target(ElementType.TYPE_USE)
    @interface TA { }

    @Target({ElementType.TYPE_USE, ElementType.LOCAL_VARIABLE, ElementType.PARAMETER})
    @interface DTA { }

    @Target({ElementType.LOCAL_VARIABLE, ElementType.PARAMETER})
    @interface DA { }
}
