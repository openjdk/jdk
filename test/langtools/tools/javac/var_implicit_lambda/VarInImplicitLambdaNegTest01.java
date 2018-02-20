/*
 * @test /nodynamiccopyright/
 * @bug 8194892
 * @summary add compiler support for local-variable syntax for lambda parameters
 * @compile/fail/ref=VarInImplicitLambdaNegTest01.out -XDrawDiagnostics VarInImplicitLambdaNegTest01.java
 */

import java.util.function.*;

class VarInImplicitLambdaNegTest01 {
    IntBinaryOperator f1 = (x, var y) -> x + y;
    IntBinaryOperator f2 = (var x, y) -> x + y;
    IntBinaryOperator f3 = (int x, var y) -> x + y;
    IntBinaryOperator f4 = (int x, y) -> x + y;

    BiFunction<String[], String, String> f5 = (var s1[], var s2) -> s2;
}
