
import java.util.function.Supplier;

/**
 * @test /nodynamiccopyright/
 * @bug 8278078
 * @summary error: cannot reference super before supertype constructor has been called
 * @compile/fail/ref=InvalidThisAndSuperInConstructorArgTest.out -XDrawDiagnostics InvalidThisAndSuperInConstructorArgTest.java
 */
public class InvalidThisAndSuperInConstructorArgTest  {

    interface InterfaceWithDefault {
        default String get() {
            return "";
        }
    }

    InvalidThisAndSuperInConstructorArgTest(String s) {
    }

    class InnerClass extends AssertionError implements InterfaceWithDefault {
        InnerClass() {
            super(InnerClass.super.toString());
        }
        InnerClass(int i) {
            this(InnerClass.super.toString());
        }
        InnerClass(boolean b) {
            super(InnerClass.this.toString());
        }
        InnerClass(double d) {
            this(InnerClass.this.toString());
        }
        InnerClass(float f) {
            super(AssertionError.super.toString());
        }
        InnerClass(char ch) {
            this(AssertionError.super.toString());
        }
        InnerClass(byte b) {
            super(AssertionError.this.toString());
        }
        InnerClass(Object o) {
            this(AssertionError.this.toString());
        }
        InnerClass(int[] ii) {
            this(InterfaceWithDefault.super.get());
        }
        InnerClass(boolean[] bb) {
            super(InterfaceWithDefault.this.get());
        }
        InnerClass(double[] dd) {
            this(InterfaceWithDefault.this.get());
        }
        InnerClass(float[] ff) {
            super(InterfaceWithDefault.super.get());
        }
        InnerClass(char[] chch) {
            this(InnerClass.this::toString);
        }
        InnerClass(String s) {
            super(s);
        }
        InnerClass(Supplier<String> sup) {
            super(sup);
        }
    }
}
