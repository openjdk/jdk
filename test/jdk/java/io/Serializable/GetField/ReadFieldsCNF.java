/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8273660
 * @summary Verify that ObjectInputStream ReadFields correctly reports ClassNotFoundException
 *    while getting the field value. The test uses Vector that calls ReadFields from its readObject.
 * @library /test/lib
 * @run testng ReadFieldsCNF
 * @run testng/othervm -Djdk.serialGetFieldCnfeReturnsNull=true ReadFieldsCNF
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.nio.charset.StandardCharsets;
import java.util.Vector;

import org.testng.annotations.Test;
import org.testng.Assert;

import jdk.test.lib.hexdump.HexPrinter;
import jdk.test.lib.hexdump.ObjectStreamPrinter;

public class ReadFieldsCNF {

    private static final boolean GETFIELD_CNFE_RETURNS_NULL =
            Boolean.getBoolean("jdk.serialGetFieldCnfeReturnsNull");


    /**
     * Test a Vector holding a reference to a class instance that will not be found.
     * @throws IOException If any other exception occurs
     */
    @Test
    private static void testVectorWithRole() throws IOException {
        System.out.println("Property GETFIELD_CNFE_RETURNS_NULL: " + GETFIELD_CNFE_RETURNS_NULL);

        Role role = new Role();
        Vector<Role> vector = new Vector<>();
        vector.add(role);

        // Modify the byte stream to change the classname to be deserialized to
        // XeadFieldsCNF$Role.
        byte[] bytes = writeObject(vector);

        // Locate the name of the class to be deserialize
        String s = new String(bytes, StandardCharsets.ISO_8859_1);  // Map bytes to chars
        int off = s.indexOf(Role.class.getName());
        System.out.printf("Role offset: %d (0x%x) : %s%n", off, off, Role.class.getName());
        if (off < 0) {
            HexPrinter.simple().formatter(ObjectStreamPrinter.formatter()).format(bytes);
            Assert.fail("classname not found");
        }

        bytes[off] = (byte) 'X';  // replace R with X -> Class not found

        // Deserialize the Vector expecting a ClassNotFoundException
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
        try {
            Object obj = in.readObject();
            System.out.println("Read: " + obj);
            Assert.fail("Should not reach here, an exception should always occur");
        } catch (ClassNotFoundException cnfe) {
            // Expected ClassNotFoundException
            String expected = "XeadFieldsCNF$Role";
            Assert.assertEquals(expected, cnfe.getMessage(), "Wrong classname");
            if (GETFIELD_CNFE_RETURNS_NULL) {
                Assert.fail("Expected IOException got ClassNotFoundException", cnfe);
            }
            System.out.println("Normal:  OIS.readObject: " + cnfe);
        } catch (StreamCorruptedException ioe) {
            if (!GETFIELD_CNFE_RETURNS_NULL) {
                Assert.fail("Expected ClassNotFoundException got StreamCorruptedException ", ioe);
            }
            System.out.println("Normal: " + ioe);
        }
        // Other exceptions cause the test to fail
    }

    /**
     * For an object holding a reference to a class that will not be found.
     * @throws IOException If any other exception occurs
     */
    @Test
    private static void testHolderWithRole() throws IOException {
        System.out.println("Property GETFIELD_CNFE_RETURNS_NULL: " + GETFIELD_CNFE_RETURNS_NULL);
        Role role = new Role();
        Holder holder = new Holder(role);

        // Modify the byte stream to change the classname to be deserialized to
        // XeadFieldsCNF$Role.
        byte[] bytes = writeObject(holder);

        String s = new String(bytes, StandardCharsets.ISO_8859_1);  // Map bytes to chars
        int off = s.indexOf(Role.class.getName(), 0);
        off = s.indexOf(Role.class.getName(), off + 1); // 2nd occurrence of classname
        System.out.printf("Role offset: %d (0x%x)%n", off, off);
        if (off < 0) {
            HexPrinter.simple().formatter(ObjectStreamPrinter.formatter()).format(bytes);
            Assert.fail("classname found at index: " + off + " (0x" + Integer.toHexString(off) + ")");
        }

        bytes[off] = (byte) 'X';  // replace R with X -> Class not found

        // Deserialize the Vector expecting a ClassNotFoundException
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
        try {
            Holder obj = (Holder)in.readObject();
            System.out.println("Read: " + obj);
            Assert.fail("Should not reach here, an exception should always occur");
        } catch (ClassNotFoundException cnfe) {
            // Expected ClassNotFoundException
            String expected = "XeadFieldsCNF$Role";
            Assert.assertEquals(expected, cnfe.getMessage(), "Wrong classname");
            System.out.println("Normal: OIS.readObject: " + cnfe);
        } catch (StreamCorruptedException ioe) {
            if (!GETFIELD_CNFE_RETURNS_NULL) {
                Assert.fail("Expected ClassNotFoundException got StreamCorruptedException ", ioe);
            }
            System.out.println("Normal: " + ioe);
        }
        // Other exceptions cause the test to fail
    }

    private static byte[] writeObject(Object o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream os = new ObjectOutputStream(baos)) {
            os.writeObject(o);
        }
        return baos.toByteArray();
    }

    static class Role implements Serializable {
        private static final long serialVersionUID = 0L;

        Role() {}
    }

    static class Holder implements Serializable {
        private static final long serialVersionUID = 1L;

        Role role;

        Holder(Role role) {
            this.role = role;
        }

        private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
            ObjectInputStream.GetField fields = ois.readFields();
            try {
                Object repl = new Object();
                role = (Role)fields.get("role", repl);
                System.out.println("Holder.readObject Role: " + role);
            } catch (Exception ex) {
                // Catch CNFE and ignore it; check elsewhere that CNFE is thrown from OIS.readObject
                System.out.println("Normal: exception in Holder.readObject, ignoring: " + ex);
            }
        }

        public String toString() {
            return "role: " + role;
        }
    }
}
