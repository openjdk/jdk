/*
 * @test /nodynamiccopyright/
 * @bug 5077054
 * @summary Conditional operator applies assignment conversion
 * @author Tim Hanson, BEA
 *
 * @compile Conditional.java
 */

import java.util.*;

class Conditional {
    void test() {
        String[] sa = null;
        List<String> ls = sa == null ? Arrays.asList(sa) :
            Collections.emptyList();
    }
}
