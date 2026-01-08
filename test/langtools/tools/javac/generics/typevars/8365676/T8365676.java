/*
 * @test /nodynamiccopyright/
 * @bug 8365676
 * @summary Interface static methods should not be inherited by type variables
 * @compile/fail/ref=T8365676.out -XDrawDiagnostics T8365676.java
 */

import java.text.Collator;
import java.util.Comparator;

class T8365676 {
    // T and P should have equivalent members
    <T extends Comparator<Object>, P extends Object & Comparator<Object>>
    void test() {
        Comparator.reverseOrder();
        Collator.reverseOrder(); // Fails
        P.reverseOrder(); // Fails
        T.reverseOrder(); // Should fail
    }
}
