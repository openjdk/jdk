/*
 * @test /nodynamiccopyright/
 * @summary Verify errors for enhanced variable declarations in enhanced for loops
 * @enablePreview
 * @compile/fail/ref=EnhancedVariableDeclInEnhancedForErrors.out -XDrawDiagnostics -XDshould-stop.at=FLOW EnhancedVariableDeclInEnhancedForErrors.java
 */
import java.util.List;

public class EnhancedVariableDeclInEnhancedForErrors {

    static void exhaustivity_error1(List<Object> points) {
        for (Point(var x, var y): points) {
            System.out.println();
        }
    }

    static void exhaustivity_error2(List points) {
        for (Point(var x, var y): points) {
            System.out.println();
        }
    }

    static void exhaustivity_error3(List<OPoint> opoints) {
        for (OPoint(String s, String t) : opoints) {
            System.out.println(s);
        }
    }

    static void exhaustivity_error4(List<?> f) {
        for (Rec(var x): f){
        }
    }

    static void applicability_error(List<Object> points) {
        for (Interface p: points) {
            System.out.println(p);
        }
    }

    record Rec(String x) { }
    interface Interface {}
    sealed interface IPoint permits Point {}
    record Point(Integer x, Integer y) implements IPoint { }
    record OPoint(Object x, Object y) { }
}
