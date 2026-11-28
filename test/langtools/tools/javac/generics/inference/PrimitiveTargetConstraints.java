/*
 * @test /nodynamiccopyright/
 * @bug 8365849
 * @summary Primitive targets require equality with the boxed type during inference
 * @compile/fail/ref=PrimitiveTargetConstraints.out -XDrawDiagnostics PrimitiveTargetConstraints.java
 */
class PrimitiveTargetConstraints {
    interface Constraint<T> { }
    static <U extends Constraint<U>> U get() { return null; }

    boolean z = get();
    byte b = get();
    short s = get();
    char c = get();
    int i = get();
    long l = get();
    float f = get();
    double d = get();
}

// Original reproduction from JDK-8365849.
class OriginalTest {

    public static void main(String[] args) {
        IntegralChecker<MyInteger> checker = new IntegralChecker<>();
        int result = checker.checkIntegral(42);
        System.out.println(result);
    }
}

interface IntegralConstraint<T> {

    boolean isIntegral();
}

class MyInteger implements IntegralConstraint<MyInteger> {

    private final int value;

    public MyInteger(int value) {
        this.value = value;
    }

    @Override
    public boolean isIntegral() {
        return true;
    }
}


class IntegralChecker<T extends IntegralConstraint<T>> {
    public <U extends IntegralConstraint<U>> U checkIntegral(int value) {
        return (U) new MyInteger(value);
    }
}
