/*
 * @test /nodynamiccopyright/
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-syntax EmptySerialDataTest.java
 * @run main DocLintTester -Xmsgs:syntax -ref EmptySerialDataTest.out EmptySerialDataTest.java
 */

import java.io.ObjectOutputStream;
import java.io.Serializable;

/** . */
public class EmptySerialDataTest implements Serializable {
    /** @serialData */
    private void writeObject(ObjectOutputStream s) { }
}
