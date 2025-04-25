/*
 * @test /nodynamiccopyright/
 * @bug 8354556
 * @summary Expand value-based class warnings to java.lang.ref API
 * @compile/fail/ref=RequiresIdentityTest.out -Werror -XDrawDiagnostics -Xlint:identity RequiresIdentityTest.java
 * @compile/fail/ref=RequiresIdentityTest.out -Werror -XDrawDiagnostics -Xlint:identity --release ${jdk.version} RequiresIdentityTest.java
 */

import java.lang.ref.Reference;
import java.util.Optional;


public class RequiresIdentityTest {

    void test() {
        Reference<Optional<Integer>> r;
    }
}
