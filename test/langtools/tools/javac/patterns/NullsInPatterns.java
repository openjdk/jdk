/*
 * @test /nodynamiccopyright/
 * @bug 8231827
 * @summary Testing pattern matching against the null constant
 * @run main NullsInPatterns
 */
import java.util.List;

public class NullsInPatterns {

    public static void main(String... args) {
        if (null instanceof List t) {
            throw new AssertionError("broken");
        } else {
            System.out.println("null does not match List type pattern");
        }
        //reifiable types not allowed in type test patterns in instanceof:
//        if (null instanceof List<Integer> l) {
//            throw new AssertionError("broken");
//        } else {
//            System.out.println("null does not match List<Integer> type pattern");
//        }
        if (null instanceof List<?> l) {
            throw new AssertionError("broken");
        } else {
            System.out.println("null does not match List<?> type pattern");
        }
    }
}
