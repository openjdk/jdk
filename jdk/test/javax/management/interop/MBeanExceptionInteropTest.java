/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6456269
 * @summary Test that an MBeanException serialized on JDK 6 deserializes
 * correctly on JDK 7.
 * @author Eamonn McManus
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import javax.management.MBeanException;

// In JDK 6, the Throwable.cause field was always null for an MBeanException,
// but it didn't matter because we overrode getCause() to return
// MBeanException.exception instead.  In JDK 7, we no longer override getCause()
// because MBeanException doubles as the serial form of GenericMBeanException.
// So we need some care to make sure that objects deserialized from JDK 6
// have the right getCause() behaviour.
public class MBeanExceptionInteropTest {
    private static final byte[] SERIALIZED_MBEAN_EXCEPTION = {
        -84,-19,0,5,115,114,0,31,106,97,118,97,120,46,109,97,
        110,97,103,101,109,101,110,116,46,77,66,101,97,110,69,120,
        99,101,112,116,105,111,110,56,110,-116,-27,110,87,49,-50,2,
        0,1,76,0,9,101,120,99,101,112,116,105,111,110,116,0,
        21,76,106,97,118,97,47,108,97,110,103,47,69,120,99,101,
        112,116,105,111,110,59,120,114,0,28,106,97,118,97,120,46,
        109,97,110,97,103,101,109,101,110,116,46,74,77,69,120,99,
        101,112,116,105,111,110,4,-35,76,-20,-109,-99,126,113,2,0,
        0,120,114,0,19,106,97,118,97,46,108,97,110,103,46,69,
        120,99,101,112,116,105,111,110,-48,-3,31,62,26,59,28,-60,
        2,0,0,120,114,0,19,106,97,118,97,46,108,97,110,103,
        46,84,104,114,111,119,97,98,108,101,-43,-58,53,39,57,119,
        -72,-53,3,0,3,76,0,5,99,97,117,115,101,116,0,21,
        76,106,97,118,97,47,108,97,110,103,47,84,104,114,111,119,
        97,98,108,101,59,76,0,13,100,101,116,97,105,108,77,101,
        115,115,97,103,101,116,0,18,76,106,97,118,97,47,108,97,
        110,103,47,83,116,114,105,110,103,59,91,0,10,115,116,97,
        99,107,84,114,97,99,101,116,0,30,91,76,106,97,118,97,
        47,108,97,110,103,47,83,116,97,99,107,84,114,97,99,101,
        69,108,101,109,101,110,116,59,120,112,113,0,126,0,8,116,
        0,7,79,104,32,100,101,97,114,117,114,0,30,91,76,106,
        97,118,97,46,108,97,110,103,46,83,116,97,99,107,84,114,
        97,99,101,69,108,101,109,101,110,116,59,2,70,42,60,60,
        -3,34,57,2,0,0,120,112,0,0,0,2,115,114,0,27,
        106,97,118,97,46,108,97,110,103,46,83,116,97,99,107,84,
        114,97,99,101,69,108,101,109,101,110,116,97,9,-59,-102,38,
        54,-35,-123,2,0,4,73,0,10,108,105,110,101,78,117,109,
        98,101,114,76,0,14,100,101,99,108,97,114,105,110,103,67,
        108,97,115,115,113,0,126,0,6,76,0,8,102,105,108,101,
        78,97,109,101,113,0,126,0,6,76,0,10,109,101,116,104,
        111,100,78,97,109,101,113,0,126,0,6,120,112,0,0,0,
        63,116,0,25,77,66,101,97,110,69,120,99,101,112,116,105,
        111,110,73,110,116,101,114,111,112,84,101,115,116,116,0,30,
        77,66,101,97,110,69,120,99,101,112,116,105,111,110,73,110,
        116,101,114,111,112,84,101,115,116,46,106,97,118,97,116,0,
        5,119,114,105,116,101,115,113,0,126,0,12,0,0,0,46,
        113,0,126,0,14,113,0,126,0,15,116,0,4,109,97,105,
        110,120,115,114,0,34,106,97,118,97,46,108,97,110,103,46,
        73,108,108,101,103,97,108,65,114,103,117,109,101,110,116,69,
        120,99,101,112,116,105,111,110,-75,-119,115,-45,125,102,-113,-68,
        2,0,0,120,114,0,26,106,97,118,97,46,108,97,110,103,
        46,82,117,110,116,105,109,101,69,120,99,101,112,116,105,111,
        110,-98,95,6,71,10,52,-125,-27,2,0,0,120,113,0,126,
        0,3,113,0,126,0,21,116,0,3,66,97,100,117,113,0,
        126,0,10,0,0,0,2,115,113,0,126,0,12,0,0,0,
        62,113,0,126,0,14,113,0,126,0,15,113,0,126,0,16,
        115,113,0,126,0,12,0,0,0,46,113,0,126,0,14,113,
        0,126,0,15,113,0,126,0,18,120,
    };

    private static volatile String failure;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            if (args[0].equals("write") && args.length == 1) {
                write();
                return;
            } else {
                System.err.println(
                        "Usage: java MBeanExceptionInteropTest");
                System.err.println(
                        "or:    java MBeanExceptionInteropTest write");
                System.exit(1);
            }
        }

        // Read the serialized object and check it is correct.
        ByteArrayInputStream bin =
                new ByteArrayInputStream(SERIALIZED_MBEAN_EXCEPTION);
        ObjectInputStream oin = new ObjectInputStream(bin);
        MBeanException mbeanEx = (MBeanException) oin.readObject();
        assertEquals("MBeanException message", "Oh dear", mbeanEx.getMessage());
        System.out.println("getCause(): " + mbeanEx.getCause() + "; " +
                "getTargetException(): " + mbeanEx.getTargetException());
        for (Throwable t :
                new Throwable[] {mbeanEx.getCause(), mbeanEx.getTargetException()}) {
            if (!(t instanceof IllegalArgumentException))
                fail("Nested exception not an IllegalArgumentException: " + t);
            else
                assertEquals("Nested exception message", "Bad", t.getMessage());
        }

        if (failure == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: " + failure);
    }

    // Write a file that can be inserted into this source file as the
    // contents of the SERIALIZED_MBEAN_EXCEPTION array.  Run this program
    // on JDK 6 to generate the array, then test on JDK 7.
    private static void write() throws Exception {
        Exception wrapped = new IllegalArgumentException("Bad");
        MBeanException mbeanEx = new MBeanException(wrapped, "Oh dear");
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(bout);
        oout.writeObject(mbeanEx);
        oout.close();
        byte[] bytes = bout.toByteArray();
        for (int i = 0; i < bytes.length; i++) {
            System.out.printf("%d,", bytes[i]);
            if (i % 16 == 15)
                System.out.println();
        }
        if (bytes.length % 16 != 0)
            System.out.println();
    }

    private static void assertEquals(String what, Object expect, Object actual) {
        boolean equal = (expect == null) ? (actual == null) : expect.equals(actual);
        if (equal)
            System.out.println("OK: " + what + ": " + expect);
        else
            fail(what + ": expected " + expect + ", got " + actual);
    }

    private static void fail(String why) {
        System.out.println("FAIL: " + why);
        failure = why;
    }
}
