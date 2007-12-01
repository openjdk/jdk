/*
 * Copyright 2004-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6211220
 * @summary Test that jmx.serial.form=1.0 works for ObjectName
 * @author Eamonn McManus
 * @run clean SerialCompatTest
 * @run build SerialCompatTest
 * @run main/othervm SerialCompatTest
 */

import java.io.*;
import java.util.*;
import javax.management.ObjectName;

public class SerialCompatTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("jmx.serial.form", "1.0");

        /* Check that we really are in jmx.serial.form=1.0 mode.
           The property is frozen the first time the ObjectName class
           is referenced so checking that it is set to the correct
           value now is not enough.  */
        ObjectStreamClass osc = ObjectStreamClass.lookup(ObjectName.class);
        if (osc.getFields().length != 6) {
            throw new Exception("Not using old serial form: fields: " +
                                Arrays.asList(osc.getFields()));
            // new serial form has no fields, uses writeObject
        }

        ObjectName on = new ObjectName("a:b=c");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(on);
        oos.close();
        byte[] bytes = bos.toByteArray();
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bis);
        ObjectName on1 = (ObjectName) ois.readObject();

        // if the bug is present, these will get NullPointerException
        for (int i = 0; i <= 11; i++) {
            try {
                switch (i) {
                case 0:
                    check(on1.getDomain().equals("a")); break;
                case 1:
                    check(on1.getCanonicalName().equals("a:b=c")); break;
                case 2:
                    check(on1.getKeyPropertyListString().equals("b=c")); break;
                case 3:
                    check(on1.getCanonicalKeyPropertyListString().equals("b=c"));
                    break;
                case 4:
                    check(on1.getKeyProperty("b").equals("c")); break;
                case 5:
                    check(on1.getKeyPropertyList()
                          .equals(Collections.singletonMap("b", "c"))); break;
                case 6:
                    check(!on1.isDomainPattern()); break;
                case 7:
                    check(!on1.isPattern()); break;
                case 8:
                    check(!on1.isPropertyPattern()); break;
                case 9:
                    check(on1.equals(on)); break;
                case 10:
                    check(on.equals(on1)); break;
                case 11:
                    check(on1.apply(on)); break;
                default:
                    throw new Exception("Test incorrect: case: " + i);
                }
            } catch (Exception e) {
                System.out.println("Test failed with exception:");
                e.printStackTrace(System.out);
                failed = true;
            }
        }

        if (failed)
            throw new Exception("Some tests failed");
        else
            System.out.println("All tests passed");
    }

    private static void check(boolean condition) {
        if (!condition) {
            new Throwable("Test failed").printStackTrace(System.out);
            failed = true;
        }
    }

    private static boolean failed;
}
