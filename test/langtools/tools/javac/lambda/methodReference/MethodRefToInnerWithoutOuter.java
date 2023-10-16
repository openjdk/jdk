/*
 * @test /nodynamiccopyright/
 * @bug 8081271
 * @summary NPE while compiling a program with erroneous use of constructor reference expressions.
 * @compile/fail/ref=MethodRefToInnerWithoutOuter.out -XDrawDiagnostics MethodRefToInnerWithoutOuter.java
*/

import java.util.List;
import java.util.ArrayList;

class MethodRefToInnerBase {
    class TestString {
        String str;
        TestString(String strin) {
            str = strin;
        }
    }
}
public class MethodRefToInnerWithoutOuter extends MethodRefToInnerBase {
    public static void meth() {
        List<String> list = new ArrayList<>();
        list.stream().forEach(TestString::new);
    }
}
