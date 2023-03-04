/*
 * @test /nodynamiccopyright/
 * @bug 8219810
 * @summary Verify ClassReader detects invalid method access flags combinations
 * @build BadMethodFlags
 * @compile/fail/ref=BadMethodFlagsTest.out -XDrawDiagnostics BadMethodFlagsTest.java
 */
public class BadMethodFlagsTest {
    {
        new BadMethodFlags().my_method();
    }
}
