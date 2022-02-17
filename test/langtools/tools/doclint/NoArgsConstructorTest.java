/*
 * @test /nodynamiccopyright/
 * @modules jdk.javadoc/jdk.javadoc.internal.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-missing NoArgsConstructorTest.java
 * @run main DocLintTester -Xmsgs:missing/protected -ref NoArgsConstructorTest.out NoArgsConstructorTest.java
 */

/** Main Comment. */
public class NoArgsConstructorTest {
    // default constructor

    /** PublicConstructor comment. */
    public static class PublicConstructor {
        public PublicConstructor() { }
    }

    /** PrivateConstructor comment. */
    public static class PrivateConstructor {
        private PrivateConstructor() { }
    }

    /** PublicInterface comment. */
    public interface PublicInterface {
    }
}
