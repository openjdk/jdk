/*
 * @test  /nodynamiccopyright/
 * @bug 8187978
 * @summary javac can show overload error messages that include non-valid candidates
 * @compile/fail/ref=FilterOutCandidatesForDiagnosticsTest.out -XDrawDiagnostics FilterOutCandidatesForDiagnosticsTest.java
 */

import java.util.*;

class FilterOutCandidatesForDiagnosticsTest {
    void test() {
        make(new ArrayList<String>(), new ArrayList<Integer>()).add("");
    }

    <Z> Z make(Z z1, Z z2) {
        return null;
    }
}
