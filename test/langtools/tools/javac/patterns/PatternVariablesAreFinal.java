/*
 * @test /nodynamiccopyright/
 * @bug 8231827
 * @summary Ensure that in type test patterns, the predicate is not trivially provable false.
 * @compile/fail/ref=PatternVariablesAreFinal.out -XDrawDiagnostics --enable-preview -source ${jdk.version} PatternVariablesAreFinal.java
 */
public class PatternVariablesAreFinal {
    public static void main(String[] args) {
        Object o = 32;
        if (o instanceof String s) {
            s = "hello again";
            System.out.println(s);
        }
        System.out.println("test complete");
    }
}
