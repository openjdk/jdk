/*
 * @test /nodynamiccopyright/
 * @bug 8262891
 * @summary Check null handling for non-pattern switches.
 * @compile --enable-preview -source ${jdk.version} NullSwitch.java
 * @run main/othervm --enable-preview NullSwitch
 */

public class NullSwitch {

    public static void main(String[] args) {
        new NullSwitch().switchTest();
    }
    
    void switchTest() {
        assertEquals(0, matchingSwitch1(""));
        assertEquals(1, matchingSwitch1("a"));
        assertEquals(100, matchingSwitch1(0));
        assertEquals(-1, matchingSwitch1(null));
        assertEquals(-2, matchingSwitch1(0.0));
        assertEquals(0, matchingSwitch2(""));
        assertEquals(1, matchingSwitch2(null));
        assertEquals(1, matchingSwitch2(0.0));
        assertEquals(0, matchingSwitch3(""));
        assertEquals(1, matchingSwitch3("a"));
        assertEquals(100, matchingSwitch3(0));
        assertEquals(-1, matchingSwitch3(null));
        assertEquals(-2, matchingSwitch3(0.0));
        assertEquals(0, matchingSwitch4(""));
        assertEquals(1, matchingSwitch4(null));
        assertEquals(1, matchingSwitch4(0.0));
        assertEquals(0, matchingSwitch5(""));
        assertEquals(1, matchingSwitch5("a"));
        assertEquals(100, matchingSwitch5(0));
        assertEquals(-1, matchingSwitch5(null));
        assertEquals(-2, matchingSwitch5(0.0));
        assertEquals(0, matchingSwitch6(""));
        assertEquals(1, matchingSwitch6(null));
        assertEquals(1, matchingSwitch6(0.0));
        assertEquals(0, matchingSwitch7(""));
        assertEquals(1, matchingSwitch7("a"));
        assertEquals(100, matchingSwitch7(0));
        assertEquals(-1, matchingSwitch7(null));
        assertEquals(-2, matchingSwitch7(0.0));
        assertEquals(0, matchingSwitch8(""));
        assertEquals(1, matchingSwitch8(null));
        assertEquals(1, matchingSwitch8(0.0));
    }

    private int matchingSwitch1(Object obj) {
        return switch (obj) {
            case String s -> s.length();
            case null, Integer i -> i == null ? -1 : 100 + i;
            default -> -2;
        };
    }

    private int matchingSwitch2(Object obj) {
        return switch (obj) {
            case String s -> 0;
            case null, default -> 1;
        };
    }

    private int matchingSwitch3(Object obj) {
        return switch (obj) {
            case String s -> s.length();
            case Integer i, null -> i == null ? -1 : 100 + i;
            default -> -2;
        };
    }

    private int matchingSwitch4(Object obj) {
        return switch (obj) {
            case String s -> 0;
            case default, null -> 1;
        };
    }

    private int matchingSwitch5(Object obj) {
        return switch (obj) {
            case String s: yield s.length();
            case null:
            case Integer i: yield i == null ? -1 : 100 + i;
            default: yield -2;
        };
    }

    private int matchingSwitch6(Object obj) {
        return switch (obj) {
            case String s: yield 0;
            case null:
            default: yield 1;
        };
    }

    private int matchingSwitch7(Object obj) {
        return switch (obj) {
            case String s: yield s.length();
            case Integer i:
            case null: yield i == null ? -1 : 100 + i;
            default: yield -2;
        };
    }

    private int matchingSwitch8(Object obj) {
        return switch (obj) {
            case String s: yield 0;
            default: 
            case null: yield 1;
        };
    }

    static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }

}
