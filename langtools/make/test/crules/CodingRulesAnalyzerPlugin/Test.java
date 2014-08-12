/**@test /nodynamiccopyright/
 * @compile/fail/ref=Test.out -Xplugin:coding_rules -XDrawDiagnostics Test.java
 */

import com.sun.tools.javac.util.Assert;

public class Test {
    public void check(String value) {
        Assert.check(value.trim().length() > 0, "value=" + value);
    }
}
