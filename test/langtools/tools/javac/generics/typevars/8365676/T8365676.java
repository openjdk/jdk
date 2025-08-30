/*
 * @test /nodynamiccopyright/
 * @bug 8365676
 * @summary Interface static methods should not be inherited by type variables
 * @compile/fail/ref=T8365676.out -XDrawDiagnostics T8365676.java
 */

import java.text.Collator;
import java.util.Comparator;

class T8365676 {
    <T extends Comparator<Integer>> void test() {
        Comparator.reverseOrder();
        Collator.reverseOrder(); // Fails
        T.reverseOrder(); // Should fail
    }
}
