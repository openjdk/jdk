/**
 * @test /nodynamiccopyright/
 * @bug 8278078
 * @summary error: cannot reference super before supertype constructor has been called
 * @compile ValidThisAndSuperInConstructorArgTest.java
 * @run main ValidThisAndSuperInConstructorArgTest
 */
public class ValidThisAndSuperInConstructorArgTest  {

    static final String SUPER = "unexpected super call";
    static final String THIS = "unexpected this call";

    public String get() {
        return SUPER;
    }

    static class StaticSubClass extends ValidThisAndSuperInConstructorArgTest {
        @Override
        public String get() {
            return THIS;
        }

        class InnerClass extends AssertionError {
            InnerClass() {
                super(StaticSubClass.super.get());
            }
            InnerClass(int i) {
                this(StaticSubClass.super.get());
            }
            InnerClass(boolean b) {
                super(StaticSubClass.this.get());
            }
            InnerClass(double d) {
                this(StaticSubClass.this.get());
            }
            InnerClass(String s) {
                super(s);
            }
            void assertThis() {
                if (!THIS.equals(getMessage())) throw this;
            }
            void assertSuper() {
                if (!SUPER.equals(getMessage())) throw this;
            }
        }
    }

    public static void main(String...args) {
        var test = new StaticSubClass();
        test.new InnerClass().assertSuper();
        test.new InnerClass(1).assertSuper();
        test.new InnerClass(true).assertThis();
        test.new InnerClass(1.0).assertThis();
    }
}
