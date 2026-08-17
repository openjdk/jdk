/*
 * @test /nodynamiccopyright/
 * @bug 8324873
 * @summary Permit additional statements before this/super in constructors
 * @compile/fail/ref=ValueClassSuperInitFails.out -XDrawDiagnostics ValueClassSuperInitFails.java
 * @enablePreview
 */
import java.util.function.Function;
abstract value class AR<V> implements java.io.Serializable {
    int b = 5;
    public AR(V initialValue) {
    }
    public AR() {
    }
}

value class ValueClassSuperInitFails extends AR <Object> implements Iterable<Object> {

    private int x;

/// GOOD EXAMPLES

    public ValueClassSuperInitFails() {           // this should be OK
        // super()
    }

    public ValueClassSuperInitFails(Object x) {
        this.x = x.hashCode();          // this should be OK
        // super();  the compiler will introduce the super call at this location
    }

    public ValueClassSuperInitFails(byte x) {
        super();                        // this should be OK
    }

    public ValueClassSuperInitFails(char x) {
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

    public ValueClassSuperInitFails(short x) {
        hashCode();                     // this should FAIL
        //super();
    }

    public ValueClassSuperInitFails(float x) {
        this.hashCode();                // this should FAIL
        //super();
    }

    public ValueClassSuperInitFails(int x) {
        super.hashCode();               // this should FAIL
        //super();
    }

    public ValueClassSuperInitFails(long x) {
        ValueClassSuperInitFails.this.hashCode();      // this should FAIL
        //super();
    }

    public ValueClassSuperInitFails(double x) {
        ValueClassSuperInitFails.super.hashCode();     // this should FAIL
        //super();
    }

    public ValueClassSuperInitFails(byte[] x) {
        {
            super();                    // this should FAIL
        }
    }

    public ValueClassSuperInitFails(char[] x) {
        if (x.length == 0)
            return;                     // this should FAIL
        //super();
    }

    public ValueClassSuperInitFails(short[] x) {
        this.x = x.length;              // this should be OK
        //super();
    }

    public ValueClassSuperInitFails(float[] x) {
        System.identityHashCode(this);  // this should FAIL
        //super();
    }

    public ValueClassSuperInitFails(int[] x) {
        this(this);                     // this should FAIL
    }

    public ValueClassSuperInitFails(long[] x) {
        this(Object.this);              // this should FAIL
    }

    public ValueClassSuperInitFails(double[] x) {
        Iterable.super.spliterator();   // this should FAIL
        //super();
    }

    public ValueClassSuperInitFails(byte[][] x) {
        super(new Object() {
            {
                super();                // this should FAIL
            }
        });
    }

    public ValueClassSuperInitFails(char[][] x) {
        new Inner1();                   // this should FAIL
        //super();
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

    public ValueClassSuperInitFails(short[][] x) {
        class Foo {
            Foo() {
                ValueClassSuperInitFails.this.hashCode();
            }
        };
        new Foo();                      // this should FAIL
        //super();
    }

    public ValueClassSuperInitFails(float[][] x) {
        Runnable r = () -> {
            super();                    // this should FAIL
        };
    }

    public ValueClassSuperInitFails(int[][] z) {
        super((Function<Integer, Integer>) f -> x);
    }

    public ValueClassSuperInitFails(long[][] z) {
        super(new Inner1());            // this should FAIL
    }

    // these two should FAIL
    int a = b;
    int aa = super.b;
}
