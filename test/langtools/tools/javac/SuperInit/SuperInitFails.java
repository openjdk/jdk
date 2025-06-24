/*
 * @test /nodynamiccopyright/
 * @bug 8194743
 * @summary Permit additional statements before this/super in constructors
 * @compile/fail/ref=SuperInitFails.out -XDrawDiagnostics SuperInitFails.java
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
        super((Runnable)() -> x);       // this should FAIL
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
}
