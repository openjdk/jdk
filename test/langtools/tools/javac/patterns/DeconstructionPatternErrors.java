/**
 * @test /nodynamiccopyright/
 * @summary Verify error reports for erroneous deconstruction patterns are sensible
 * @compile/fail/ref=DeconstructionPatternErrors.out -XDrawDiagnostics -XDshould-stop.at=FLOW -XDdev DeconstructionPatternErrors.java
 */

import java.util.ArrayList;
import java.util.List;

public class DeconstructionPatternErrors {

    public static void meth() throws Throwable {
        Object p;
        p = new P(42);
        if (p instanceof P3(ArrayList<Integer> l));
        if (p instanceof P4(ArrayList<Integer> l));
        if (p instanceof P5(int i));
        if (p instanceof P(String s));
        if (p instanceof P5(P(var v)));
        if (p instanceof P2(var v1)); //too few nested patterns
        if (p instanceof P2(Runnable v1)); //too few nested patterns
        if (p instanceof P(var v1, var v2)); //too many nested patterns
        if (p instanceof P(int v1, int v2)); //too many nested patterns
        if (p instanceof P(int v1, Unresolvable v2)); //too many nested patterns
        if (p instanceof GenRecord<String>(var v)); //incorrect generic type
        if (p instanceof P4(GenRecord<String>(var v))); //incorrect generic type
        if (p instanceof GenRecord<String>(Integer v)); //inconsistency in types
        if (p instanceof P2(var v, var v)); //duplicated variables
        if (p instanceof P6(P2(var v1, var v2), P2(var v1, var v2))); //duplicated variables
        GenRecord<String> r1 = null;
        if (r1 instanceof GenRecord(String s)) {}
        switch (r1) {
            case GenRecord(String s) -> {}
        }
        if (r1 instanceof GenRecord<>(String s)) {}
        switch (r1) {
            case GenRecord<>(String s) -> {}
        }
        boolean b = p instanceof P(int i) p; //introducing a variable for the record pattern
    }

    public record P(int i) {
    }

    public record P2(Runnable r1, Runnable r2) {}
    public record P3(List<String> l) {}
    public record P4(Object o) {}
    public record P5(String s) {}
    public record P6(Object o1, Object o2) {}
    public record P7(int i) {}
    public record GenRecord<T>(T s) {}

}
