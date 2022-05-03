/*
 * @test /nodynamiccopyright/
 * @summary Testing record patterns against the null constant (14.30.2 Pattern Matching)
 * @compile --enable-preview -source ${jdk.version} NullsInDeconstructionPatterns.java
 * @run main/othervm --enable-preview NullsInDeconstructionPatterns
 */

public class NullsInDeconstructionPatterns {

    class Super {}
    class Sub extends Super {}
    record R(Super s) {}

    public static void main(String[] args) {

        R r = new R(null);

        if (r instanceof R(Super s1)) {
            System.out.println("R(Super s1) is resolved to the R(any pattern) and does match");
        } else {
            throw new AssertionError("broken");
        }

        if (r instanceof R(Object o)) {
            System.out.println("R(Object) is resolved to the R(any pattern) and does match");
        } else {
            throw new AssertionError("broken");
        }

        if (r instanceof R(Sub s2)) {
            throw new AssertionError("broken");
        } else {
            System.out.println("R(Sub s2) is resolved to the pattern R(Sub s) and does not match");
        }
    }
}
