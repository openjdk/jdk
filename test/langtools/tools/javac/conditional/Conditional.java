/*
 * @test /nodynamiccopyright/
 * @bug 5077054
 * @summary Conditional operator applies assignment conversion
 * @author Tim Hanson, BEA
 *
 * @compile Conditional.java
 * @compile -J-XX:+UnlockExperimentalVMOptions -J-XX:hashCode=2 Conditional.java
 */

import java.util.*;

class Conditional {
    void test() {
        String[] sa = null;
        List<String> ls = sa == null ? Arrays.asList(sa) :
            Collections.emptyList();
    }
}
