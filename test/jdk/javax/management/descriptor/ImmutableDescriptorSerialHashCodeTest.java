/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8358624
 * @summary Test ImmutableDescriptor hashcode and serialization
 *
 * @run main ImmutableDescriptorSerialHashCodeTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import javax.management.Descriptor;
import javax.management.ImmutableDescriptor;

public class ImmutableDescriptorSerialHashCodeTest {
    public static void main(String[] args) throws Exception {

        Descriptor d1 = new ImmutableDescriptor("a=aval", "B=Bval", "cC=cCval");
        Descriptor d2 = new ImmutableDescriptor("a=aval", "B=Bval", "cC=cCval");

        test (d1, d2, "Objects created from same String"); // Sanity check
        Descriptor dSer = serialize(d1);
        test(d1, dSer, "After serialization"); // Actual test
        System.out.println("PASSED");
    }

    /**
      * Test that two Descriptor objects are both equal, and have equal hashcodes.
      */
    private static void test(Descriptor d1, Descriptor d2, String msg) throws Exception {
        if (!d1.equals(d2)) {
            throw new RuntimeException(msg + ": Descriptors not equal: " +
                    "\nd1: " + d1 +
                    "\nd2: " + d2);
        }
        if (d1.hashCode() != d2.hashCode()) {
            throw new RuntimeException(msg + ": Hash code mismatch.  hash1: " + d1.hashCode()
                                + ", hash2: " + d2.hashCode());
        }
    }

    private static <T> T serialize(T x) throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(bout);
        oout.writeObject(x);
        oout.close();
        byte[] bytes = bout.toByteArray();
        ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
        ObjectInputStream oin = new ObjectInputStream(bin);
        return (T) oin.readObject();
    }
}
