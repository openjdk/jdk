/*
 * @test /nodynamiccopyright/
 * @bug 8219810
 * @summary Verify ClassReader detects invalid field access flags combinations
 * @build BadFieldFlags
 * @compile/fail/ref=BadFieldFlagsTest.out -XDrawDiagnostics BadFieldFlagsTest.java
 */
public class BadFieldFlagsTest {
    {
        System.out.println(new BadFieldFlags().my_field);
    }
}
