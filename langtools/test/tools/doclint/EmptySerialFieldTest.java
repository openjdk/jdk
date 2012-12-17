/*
 * @test /nodynamiccopyright/
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
