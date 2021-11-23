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
 * @summary The class of an element of a Vector may not be found; test that Vector allows
 *          the CNFE to be thrown.
 * @run testng VectorElementCNFE
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

public class VectorElementCNFE {

    /**
     * Test a Vector holding a reference to a class instance that will not be found.
     * @throws IOException If any other exception occurs
     */
    @Test
    private static void test1() throws IOException {

        Role role = new Role();
        Vector<Role> vector = new Vector<>();
        vector.add(role);

        // Modify the byte stream to change the classname to be deserialized to
        // XectorElementCNFE$Role.
        byte[] bytes = writeObject(vector);

        String s = new String(bytes, StandardCharsets.ISO_8859_1);  // Map bytes to chars
        int off = s.indexOf(Role.class.getName());
        Assert.assertTrue(off >= 0, "classname Role not found");

        System.out.println("Clasname Role offset: " + off);
        bytes[off] = (byte) 'X';  // replace V with X -> Class not found

        // Deserialize the Vector expecting a ClassNotFoundException
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
        try {
            Object obj = in.readObject();
            System.out.println("Read: " + obj);
            Assert.fail("Should not reach here, an exception should always occur");
        } catch (ClassNotFoundException cnfe) {
            // Expected ClassNotFoundException
            String expected = "XectorElementCNFE$Role";
            Assert.assertEquals(expected, cnfe.getMessage(), "Wrong classname");
            System.out.println("Normal: " + cnfe);
        }
        // Other exceptions cause the test to fail
    }

    /**
     * Test deserializing a Vector in which there is no "elementData" field.
     * @throws IOException If any other exception occurs
     */
    @Test
    private static void test2() throws IOException {

        Role role = new Role();
        Vector<Role> vector = new Vector<>();
        vector.add(role);

        // Modify the byte stream effectively remove the "elementData" field
        // by changing fieldName to be deserialized to "XelementData".
        byte[] bytes = writeObject(vector);

        String s = new String(bytes, StandardCharsets.ISO_8859_1);  // Map bytes to chars
        int off = s.indexOf("elementData");
        Assert.assertTrue(off >= 0, "field elementData not found");

        System.out.println("elementData offset: " + off);
        bytes[off] = (byte) 'X';  // replace 'e' with X -> field elementData not found

        // Deserialize the Vector expecting a StreamCorruptedException
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
        try {
            Object obj = in.readObject();
            System.out.println("Read: " + obj);
            Assert.fail("Should not reach here, an exception should always occur");
        } catch (StreamCorruptedException sce) {
            // Expected StreamCorruptedException
            String expected = "Inconsistent vector internals";
            Assert.assertEquals(expected, sce.getMessage(), "Wrong exception message");
            System.out.println("Normal: " + sce);
        } catch (ClassNotFoundException cnfe) {
            Assert.fail("CNFE not expected", cnfe);
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
}
