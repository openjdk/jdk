/*
 * @test
 * @bug 8394667
 * @summary Omitting enclosing instance fields in value classes can provoke undesirable effects
 * @enablePreview
 */

public class ValueClassEnclosingInstance {
    value class MemberValue {
        int value;

        MemberValue(int value) {
            this.value = value;
            super();
        }
    }

    Object newLocalValue(int value) {
        value class LocalValue {
            int value;

            LocalValue(int value) {
                this.value = value;
                super();
            }
        }
        return new LocalValue(value);
    }

    public static void main(String[] args) {
        var firstOuter = new ValueClassEnclosingInstance();
        var secondOuter = new ValueClassEnclosingInstance();

        checkEquality("member class, different enclosing instances",
                firstOuter.new MemberValue(-1), secondOuter.new MemberValue(-1), false);
        checkEquality("member class, same enclosing instance and field values",
                firstOuter.new MemberValue(-1), firstOuter.new MemberValue(-1), true);
        checkEquality("member class, different field values",
                firstOuter.new MemberValue(-1), firstOuter.new MemberValue(0), false);

        checkEquality("local class, different enclosing instances",
                firstOuter.newLocalValue(-1), secondOuter.newLocalValue(-1), false);
        checkEquality("local class, same enclosing instance and field values",
                firstOuter.newLocalValue(-1), firstOuter.newLocalValue(-1), true);
        checkEquality("local class, different field values",
                firstOuter.newLocalValue(-1), firstOuter.newLocalValue(0), false);
    }

    static void checkEquality(String description, Object first, Object second, boolean expected) {
        if ((first == second) != expected) {
            throw new AssertionError(description + ": expected == to return " + expected);
        }
        if (first.equals(second) != expected) {
            throw new AssertionError(description + ": expected equals to return " + expected);
        }
    }
}
