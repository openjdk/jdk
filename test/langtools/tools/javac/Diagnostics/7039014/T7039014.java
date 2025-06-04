/*
 * @test /nodynamiccopyright/
 * @bug     7039014
 * @summary Confusing error message for method conflict
 *
 * @compile/fail/ref=T7039014.out -XDrawDiagnostics T7039014.java
 */
public class T7039014 {

// Test 1: this should fail to compile

    interface A1<T> {
        byte m(String x);
        char m(T x);
    }

    interface B1 extends A1<String> {
    }

// Test 2: this should fail to compile

    interface A2<T> {
        default byte m(String x) { return 0; }
        char m(T x);
    }

    interface B2 extends A2<String> {
    }

// Test 3: this should compile

    interface A3<T> {
        private byte m(String x) { return 0; }
        char m(T x);
    }

    interface B3 extends A3<String> {
    }
}
