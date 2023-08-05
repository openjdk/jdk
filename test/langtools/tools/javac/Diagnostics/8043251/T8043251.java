/**
 * @test /nodynamiccopyright/
 * @bug     8043251
 * @summary Confusing error message with wrong number of type parameters
 * @compile/fail/ref=T8043251.out -XDrawDiagnostics T8043251.java
 */
import java.util.function.Function;
class T8043251 {
    Function<String, String> f = Function.<String, String>identity();
}
