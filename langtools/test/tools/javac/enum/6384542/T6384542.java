/**
 * @test  /nodynamiccopyright/
 * @bug     6384542
 * @summary crash: test/tools/javac/versions/check.sh
 * @author  Peter von der Ah\u00e9
 * @compile/fail -source 1.4 T6384542.java
 * @compile/fail/ref=T6384542.out -source 1.4 -XDrawDiagnostics T6384542.java
 */

import static java.lang.Math.sin;

public enum A { }
class B {
    int i = 0xCafe.BabeP1;
    List<X> l;
    public static void main(String... args) {
        for (String arg : args) {
            System.out.println(arg);
        }
    }
    @Override
    public String toString() { return null; }
}
public klass C { }
