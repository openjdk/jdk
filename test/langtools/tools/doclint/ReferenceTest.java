/*
 * @test /nodynamiccopyright/
 * @bug 8004832 8020556 8002154
 * @summary Add new doclint package
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-reference ReferenceTest.java
 * @run main DocLintTester -ref ReferenceTest.out ReferenceTest.java
 */

/** */
public class ReferenceTest {
    /**
     * @param x description
     */
    public int invalid_param;

    /**
     * @param x description
     */
    public class InvalidParam { }

    /**
     * @param x description
     */
    public void param_name_not_found(int a) { }

    /**
     * @param <X> description
     */
    public class typaram_name_not_found { }

    /**
     * @see Object#tooStrong()
     */
    public void ref_not_found() { }

    /**
     * @return x description
     */
    public int invalid_return;

    /**
     * @return x description
     */
    public void invalid_return();

    /**
     * @throws Exception description
     */
    public void exception_not_thrown() { }

    /**
     * @param <T> throwable
     * @throws T description
     */
    public <T extends Throwable> void valid_throws_generic() throws T { }

    /**
     * {@link java.util.List<String>}
     * {@link java.util.List<String>#equals}
     * @see java.util.List<String>
     * @see java.util.List<String>#equals
     */
    public void invalid_type_args() { }
}

