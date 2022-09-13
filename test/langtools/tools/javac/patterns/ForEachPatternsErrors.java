/*
 * @test /nodynamiccopyright/
 * @summary
 * @compile/fail/ref=ForEachPatternsErrors.out -XDrawDiagnostics -XDshould-stop.at=FLOW --enable-preview -source ${jdk.version} ForEachPatternsErrors.java
 */

import java.util.List;

public class ForEachPatternsErrors {

    static void applicability_error1(List<Object> points) { // error
        for (Point(var x, var y): points) {
            System.out.println();
        }
    }

    static void applicability_error2(List points) { // error
        for (Point(var x, var y): points) {
            System.out.println();
        }
    }

    static void applicability_error3(List<Object> points) { // error
        for (Interface p: points) {
            System.out.println(p);
        }
    }

    static void exhaustivity_error(List<OPoint> opoints) { // error
        for (OPoint(String s, String t) : opoints) {
            System.out.println(s);
        }
    }
    interface Interface {}
    sealed interface IPoint permits Point {}
    record Point(Integer x, Integer y) implements IPoint { }
    record OPoint(Object x, Object y) { }
}
