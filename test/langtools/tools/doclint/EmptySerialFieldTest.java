/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-syntax EmptySerialFieldTest.java
 * @run main DocLintTester -Xmsgs:syntax -ref EmptySerialFieldTest.out EmptySerialFieldTest.java
 */

import java.io.ObjectStreamField;
import java.io.Serializable;

/** . */
public class EmptySerialFieldTest implements Serializable {

    /**
     * @serialField empty    String
     */
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("empty", String.class),
    };
}
