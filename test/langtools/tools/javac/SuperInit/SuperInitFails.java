/*
 * @test /nodynamiccopyright/
 * @bug 8194743
 * @summary Permit additional statements before this/super in constructors
 * @enablePreview
 * @compile/fail/ref=SuperInitFails.out -XDrawDiagnostics SuperInitFails.java
 * @build InitializationWarningTester
 * @run main InitializationWarningTester SuperInitFails SuperInitFailsWarnings.out
 */
import java.util.concurrent.atomic.AtomicReference;
public class SuperInitFails extends AtomicReference<Object> implements Iterable<Object> {

    private int x;

/// GOOD EXAMPLES

    public SuperInitFails() {           // this should be OK
    }

    public SuperInitFails(Object x) {
        this.x = x.hashCode();          // this should be OK
    }

    public SuperInitFails(byte x) {
        super();                        // this should be OK
    }

    public SuperInitFails(char x) {
        this((int)x);                   // this should be OK
    }

/// FAIL EXAMPLES

    {
        this(1);                        // this should FAIL
    }

    {
        super();                        // this should FAIL
    }

    void normalMethod1() {
        super();                        // this should FAIL
    }

    void normalMethod2() {
        this();                         // this should FAIL
    }

    void normalMethod3() {
        Runnable r = () -> super();     // this should FAIL
    }

    void normalMethod4() {
        Runnable r = () -> this();      // this should FAIL
    }

    public SuperInitFails(short x) {
        hashCode();                     // this should FAIL
        super();
    }

    public SuperInitFails(float x) {
        this.hashCode();                // this should FAIL
        super();
    }

    public SuperInitFails(int x) {
        super.hashCode();               // this should FAIL
        super();
    }

    public SuperInitFails(long x) {
        SuperInitFails.this.hashCode();      // this should FAIL
        super();
    }

    public SuperInitFails(double x) {
        SuperInitFails.super.hashCode();     // this should FAIL
        super();
    }

    public SuperInitFails(byte[] x) {
        {
            super();                    // this should FAIL
        }
    }

    public SuperInitFails(char[] x) {
        if (x.length == 0)
            return;                     // this should FAIL
        super();
    }

    public SuperInitFails(short[] x) {
        this.x++;                       // this should FAIL
        super();
    }

    public SuperInitFails(float[] x) {
        System.identityHashCode(this);  // this should FAIL
        super();
    }

    public SuperInitFails(int[] x) {
        this(this);                     // this should FAIL
    }

    public SuperInitFails(long[] x) {
        this(Object.this);              // this should FAIL
    }

    public SuperInitFails(double[] x) {
        Iterable.super.spliterator();   // this should FAIL
        super();
    }

    public SuperInitFails(byte[][] x) {
        super(new Object() {
            {
                super();                // this should FAIL
            }
        });
    }

    public SuperInitFails(char[][] x) {
        new Inner1();                   // this should FAIL
        super();
    }

    class Inner1 {
    }

    record Record1(int value) {
        Record1(float x) {              // this should FAIL
        }
    }

    record Record2(int value) {
        Record2(float x) {              // this should FAIL
            super();
        }
    }

    @Override
    public java.util.Iterator<Object> iterator() {
        return null;
    }

    public SuperInitFails(float[][] x) {
        Runnable r = () -> {
            super();                    // this should FAIL
        };
    }

    public SuperInitFails(int[][] z) {
        super((Runnable)() -> System.err.println(x));       // this should FAIL
    }

    public SuperInitFails(long[][] z) {
        super(new Inner1());            // this should FAIL
    }

    public static class Inner2 {
        int x;
    }
    public static class Inner3 extends Inner2 {
        int y;
        Inner3(byte z) {
            x = z;                      // this should FAIL
            super();
        }
        Inner3(short z) {
            this.x = z;                 // this should FAIL
            super();
        }
        Inner3(char z) {
            Inner3.this.x = z;          // this should FAIL
            super();
        }
        Inner3(int z) {
            super.x = z;                // this should FAIL
            super();
        }
    }

    public SuperInitFails(double[][] x) {
        Runnable r = () -> this.x = 7;  // this should FAIL
        super();
    }

    public int xx;

    SuperInitFails(short[][] ignore) {
        int i = new SuperInitFails(){
            void foo() {
                System.err.println(xx);  // this one is OK, reading field `xx` in the anonymous class
            }
        }.xx;  // this one is OK too, field of a fully constructed class
        super(null);
    }

    public static class Inner4 {
        Inner4() {
            Runnable r = () -> {
                class A {
                    A() {
                        return;         // this should FAIL
                        super();
                    }
                    A(int x) {
                        {
                            this();     // this should FAIL
                        }
                    }
                    A(char x) {
                        super();
                        this();         // this should FAIL
                    }
                }
            };
            super();
        };
    }

    static class Inner5 {
        int x = 4;
        static String m1(Runnable r) { return null; }
        static String m2(Object r) { return null; }
        Inner5() {
            m1(() -> System.out.println(x)).toString();
            m2(x).toString();
            super();
        }
    }

    static class Inner6 {
        Inner6() {
            class Bar {
                Bar() {
                    Object o = Bar.this;
                    super();
                }
            }
            super();
        }
    }

    static class Inner7 {
        private int x;

        public Inner7(byte y) {
            x = y;
            this((int)y);
        }
        public Inner7(int x) {
            this.x = x;
            super();
        }
    }

    static class Inner8 {
        final int x;

        Inner8() {
            this(x = 3); // error
        }
        Inner8(int i) {
            x = 4;
        }
    }

    static class Inner9 {
        interface Parent {
            boolean check = true;
        }

        class Medium implements Parent {}

        class Inner9Test extends Medium {
            Inner9Test() {
                boolean check1 = Inner9Test.super.check;
                boolean check2 = super.check;
                super();
            }
        }
    }

    static class Inner10 {
        static boolean testMethod() { return true; }
        Inner10() {}
        Inner10(int a) {
            Inner10.this.testMethod();
            this();
        }
    }

    static class Inner11 {
        class Inner11_1 {
            static <T> void m() {}
        }

        class Inner11_2 extends Inner11_1 {
            Inner11_2() {
                Inner11_1.super.<String>m();
                super();
            }
        }
    }

    static class Inner12 {
        static final boolean check = true;
        Inner12() {}
        Inner12(int a) {
            boolean b = Inner12.this.check; //compilation error expected here
            this();
        }
    }

    static class Inner13 {
        static final boolean check = true;
        class Inner13_1 {
            Inner13_1(int a) {
                boolean b = Inner13.this.check;
                this();
            }
            Inner13_1() {}
        }
    }
}
