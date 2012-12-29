/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
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
}

