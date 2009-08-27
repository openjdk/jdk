/*
 * @test /nodynamiccopyright/
 * @bug 6521805
 * @summary Regression: JDK5/JDK6 javac allows write access to outer class reference
 * @author mcimadamore
 *
 * @compile/fail/ref=T6521805a_1.out T6521805a.java -XDrawDiagnostics
 * @compile/ref=T6521805a_2.out T6521805a.java -XDwarnOnSyntheticConflicts -XDrawDiagnostics
 */

class T6521805a {

    static class Outer {
        T6521805a this$0 = null;
    }

    public class Inner extends Outer {
        public void foo() {
            this$0 = new T6521805a();
        }
    }
}
