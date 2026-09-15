/*
 * @test /nodynamiccopyright/
 * @summary Verify errors for enhanced variable declaration statements
 * @enablePreview
 * @compile/fail/ref=EnhancedVariableDeclStatementErrors.out -XDrawDiagnostics -XDshould-stop.at=FLOW EnhancedVariableDeclStatementErrors.java
 */

public class EnhancedVariableDeclStatementErrors {

    static void exhaustivity_error1(Object point) {
        Point(var x, var y) = point;
    }

    static void exhaustivity_error2(OPoint opoint) {
        Point(var x, var y) = opoint;
    }

    static void expression_form_error(Point point) {
        Point point2;
        Point p3 = (point2 = point);                 // allowed as assignment op treated as an expression statement
        Point p4 = (Point p2 = point);               // not allowed as LVDS
        Point p5 = (Point(int x, int y) = point);    // not allowed as ELVDS
    }

    static int scope_error(Point point) {
        {
            Point(var sx, var sy) = point;
        }
        return sx;
    }

    static void shadowing_error(Point point) {
        int sx = 0;
        Point(var sx, var sy) = point;
    }

    static void unbraced_if_else_error(Point point, boolean cond) {
        if (cond)
            Point(var x1, var y1) = point;
        else
            Point(var x2, var y2) = point;
    }

    static void unbraced_for_error(Point point) {
        for (int i = 0; i < 1; i++)
            Point(var x, var y) = point;
    }

    static void unbraced_enhanced_for_error(Point point) {
        for (Point current : new Point[] { point })
            Point(var x, var y) = current;
    }

    static void unbraced_while_error(Point point, boolean cond) {
        while (cond)
            Point(var x, var y) = point;
    }

    static void unbraced_do_error(Point point, boolean cond) {
        do
            Point(var x, var y) = point;
        while (cond);
    }

    static void labeled_error(Point point) {
        label:
            Point(var x, var y) = point;
    }

    sealed interface IPoint permits Point {}
    record Point(Integer x, Integer y) implements IPoint { }
    record OPoint(Object x, Object y) { }
}
