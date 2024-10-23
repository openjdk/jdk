/*
 * @test /nodynamiccopyright/
 * @bug 5019609 8246774
 * @summary javac fails to reject local enums
 * @author gafter
 * @compile/fail/ref=LocalEnum.out -XDrawDiagnostics --release 15 LocalEnum.java
 * @compile LocalEnum.java
 * @compile -J-XX:+UnlockExperimentalVMOptions -J-XX:hashCode=2 LocalEnum.java
 */

public class LocalEnum {
    void f() {
        enum B {}
    }
}
