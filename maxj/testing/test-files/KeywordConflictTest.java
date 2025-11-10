package conflicttest;

/**
 * Tests potential keyword conflicts between MaxJ and Java.
 * This ensures that adding MaxJ keywords doesn't break standard Java.
 */
public class KeywordConflictTest {

    /**
     * Tests that lowercase Java keywords work correctly.
     * MaxJ uses uppercase (CASE, SWITCH, IF, ELSE) so this should be fine.
     */
    public void testLowercaseKeywords() {
        int value = 5;

        // Standard Java 'case' should work (vs MaxJ 'CASE')
        switch (value) {
            case 1:
                System.out.println("Java case 1");
                break;
            case 5:
                System.out.println("Java case 5");
                break;
            default:
                System.out.println("Java default");
                break;
        }

        // Standard Java 'if'/'else' should work (vs MaxJ 'IF'/'ELSE')
        if (value == 5) {
            System.out.println("Java if works");
        } else {
            System.out.println("Java else works");
        }

        // Standard Java 'switch' expression should work (vs MaxJ 'SWITCH')
        String result = switch (value) {
            case 1, 2 -> "low";
            case 3, 4 -> "medium";
            case 5, 6 -> "high";
            default -> "unknown";
        };

        System.out.println("Switch expression: " + result);
    }

    /**
     * Tests Java operators that might conflict with MaxJ operators.
     * Java == vs MaxJ ===, Java assignment = vs MaxJ <==
     */
    public void testOperatorConflicts() {
        int a = 10;
        int b = 10;

        // Java equality (==) should work normally
        boolean equal = (a == b);  // Should not be confused with MaxJ ===
        boolean notEqual = (a != b);

        // Java assignment (=) should work normally
        int c = a;  // Should not be confused with MaxJ <==
        c += b;

        // Java comparison operators
        boolean greater = (a > b);
        boolean less = (a < b);
        boolean greaterEqual = (a >= b);
        boolean lessEqual = (a <= b);

        System.out.println("All Java operators work: " + equal);
    }

    /**
     * Tests case sensitivity - very important for keyword conflicts.
     * Java is case-sensitive, so 'case' != 'CASE'.
     */
    public void testCaseSensitivity() {
        String test = "case";  // lowercase

        // This should compile fine - 'case' is Java keyword, 'CASE' is MaxJ
        switch (test) {
            case "case":
                System.out.println("Lowercase case works");
                break;
            case "switch":
                System.out.println("Lowercase switch works");
                break;
            default:
                System.out.println("Default works");
                break;
        }

        // Variables named similar to MaxJ keywords should work
        int IF = 1;      // Variable named 'IF' (MaxJ keyword)
        int CASE = 2;    // Variable named 'CASE' (MaxJ keyword)
        int SWITCH = 3;  // Variable named 'SWITCH' (MaxJ keyword)
        int ELSE = 4;    // Variable named 'ELSE' (MaxJ keyword)

        System.out.println("Variables with MaxJ keyword names: " + (IF + CASE + SWITCH + ELSE));
    }

    /**
     * Tests edge case: methods named with MaxJ keywords.
     */
    public void IF() {
        System.out.println("Method named IF works");
    }

    public void CASE() {
        System.out.println("Method named CASE works");
    }

    public void SWITCH() {
        System.out.println("Method named SWITCH works");
    }

    public void OTHERWISE() {
        System.out.println("Method named OTHERWISE works");
    }

    /**
     * Tests that we can call methods with MaxJ keyword names.
     */
    public void testMaxJKeywordMethodCalls() {
        IF();
        CASE();
        SWITCH();
        OTHERWISE();
    }
}
