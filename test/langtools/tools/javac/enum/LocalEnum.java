/*
 * @test /nodynamiccopyright/
 * @bug 5019609
 * @summary javac fails to reject local enums
 * @author gafter
 * @compile/fail/ref=LocalEnum.out -XDrawDiagnostics  LocalEnum.java
 */

public class LocalEnum {
    void f() {
        enum B {}
    }
}
