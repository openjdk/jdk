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
 * @run main ReadFieldsCNF
 */


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HexFormat;
import java.util.Vector;

public class ReadFieldsCNF {

    public static void main(String[] args) throws IOException, ClassNotFoundException {

        Role role = new Role();
        Vector<Role> vector = new Vector<>();
        vector.add(role);

        // Serialize the Vector with the Role
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(baos);
            objectOutputStream.writeObject(vector);
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }

        // Modify the byte stream to change the classname to be deserialized to
        // XeadFieldsCNF$Role.
        byte[] bytes = baos.toByteArray();
        int off = 160;
        if (bytes[off] != 'R') {
            // Classname not where it was expected, print debug info
            String s = new String(bytes);
            off = s.indexOf(Role.class.getName()) + 1;
            System.out.println("Role offset: " + off);
            System.out.println("dump: " + HexFormat.of().formatHex(bytes, off, off + 16));
            System.out.println("str:  " + new String(bytes, off, 16));
            throw new RuntimeException("class not found at index: " + off);
        }

        bytes[off] = (byte) 'X';  // replace R with X -> Class not found

        // Deserialize the Vector expecting a ClassNotFoundException
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
        try {
            Object obj = in.readObject();
            System.out.println("Read: " + obj);
            throw new RuntimeException("Missing exception, should not reach here");
        } catch (ClassNotFoundException cnfe) {
            // Expected ClassNotFoundException
            String expected = "XeadFieldsCNF$Role";
            if (!(expected.equals(cnfe.getMessage()))) {
                throw new RuntimeException("Expected: " + expected + ", actual: " + cnfe.getMessage());
            }
        }
        // Other exceptions cause the test to fail
    }

    static class Role implements Serializable {
        private static final long serialVersionUID = 0L;

        Role() {}
    }
}
