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
 * @bug 8276241
 * @summary Throw ClassFormatError exception for an old class file whose name ends in a '/'.
 * @run main/othervm -Xverify:remote TrailingSlashTest
 */

public class TrailingSlashTest extends ClassLoader {

    @Override
    public Class findClass(String fileName) throws ClassNotFoundException {
        return defineClass(null, oldSlashClass, 0, oldSlashClass.length);
    }

    public static void main(String args[]) throws Throwable {
        try {
            TrailingSlashTest cl = new TrailingSlashTest();
            cl.findClass("oldSlashClass");
            throw new RuntimeException("Expected exception not thrown");
        } catch (ClassFormatError e) {
            if (!e.getMessage().contains("Illegal class name")) {
               throw new RuntimeException("Wrong ClassFormatError exception: " + e.getMessage());
            }
        }
    }


    // This byte array comprises the compiled bytes of the following class.  Note that the class's
    // name ends in a '/' and has a class file version of 45.3.
    /*
        package has;
        public class slashe/ { }
    */
    public static byte[] oldSlashClass = {
        (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe, (byte) 0x0, (byte) 0x3, (byte) 0x0, (byte) 0x2d,
        (byte) 0x0, (byte) 0xd, (byte) 0xa, (byte) 0x0, (byte) 0x2, (byte) 0x0, (byte) 0x3, (byte) 0x7,
        (byte) 0x0, (byte) 0x4, (byte) 0xc, (byte) 0x0, (byte) 0x5, (byte) 0x0, (byte) 0x6, (byte) 0x1,
        (byte) 0x0, (byte) 0x10, (byte) 0x6a, (byte) 0x61, (byte) 0x76, (byte) 0x61, (byte) 0x2f, (byte) 0x6c,
        (byte) 0x61, (byte) 0x6e, (byte) 0x67, (byte) 0x2f, (byte) 0x4f, (byte) 0x62, (byte) 0x6a, (byte) 0x65,
        (byte) 0x63, (byte) 0x74, (byte) 0x1, (byte) 0x0, (byte) 0x6, (byte) 0x3c, (byte) 0x69, (byte) 0x6e,
        (byte) 0x69, (byte) 0x74, (byte) 0x3e, (byte) 0x1, (byte) 0x0, (byte) 0x3, (byte) 0x28, (byte) 0x29,
        (byte) 0x56, (byte) 0x7, (byte) 0x0, (byte) 0x8, (byte) 0x1, (byte) 0x0, (byte) 0xb, (byte) 0x68,
        (byte) 0x61, (byte) 0x73, (byte) 0x2f, (byte) 0x73, (byte) 0x6c, (byte) 0x61, (byte) 0x73, (byte) 0x68,
        (byte) 0x65, (byte) 0x2f, (byte) 0x1, (byte) 0x0, (byte) 0x4, (byte) 0x43, (byte) 0x6f, (byte) 0x64,
        (byte) 0x65, (byte) 0x1, (byte) 0x0, (byte) 0xf, (byte) 0x4c, (byte) 0x69, (byte) 0x6e, (byte) 0x65,
        (byte) 0x4e, (byte) 0x75, (byte) 0x6d, (byte) 0x62, (byte) 0x65, (byte) 0x72, (byte) 0x54, (byte) 0x61,
        (byte) 0x62, (byte) 0x6c, (byte) 0x65, (byte) 0x1, (byte) 0x0, (byte) 0xa, (byte) 0x53, (byte) 0x6f,
        (byte) 0x75, (byte) 0x72, (byte) 0x63, (byte) 0x65, (byte) 0x46, (byte) 0x69, (byte) 0x6c, (byte) 0x65,
        (byte) 0x1, (byte) 0x0, (byte) 0xc, (byte) 0x73, (byte) 0x6c, (byte) 0x61, (byte) 0x73, (byte) 0x68,
        (byte) 0x65, (byte) 0x73, (byte) 0x2e, (byte) 0x6a, (byte) 0x61, (byte) 0x76, (byte) 0x61, (byte) 0x0,
        (byte) 0x21, (byte) 0x0, (byte) 0x7, (byte) 0x0, (byte) 0x2, (byte) 0x0, (byte) 0x0, (byte) 0x0,
        (byte) 0x0, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x5, (byte) 0x0,
        (byte) 0x6, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x9, (byte) 0x0, (byte) 0x0, (byte) 0x0,
        (byte) 0x1d, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x0, (byte) 0x0,
        (byte) 0x5, (byte) 0x2a, (byte) 0xb7, (byte) 0x0, (byte) 0x1, (byte) 0xb1, (byte) 0x0, (byte) 0x0,
        (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0xa, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x6,
        (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x3, (byte) 0x0, (byte) 0x1,
        (byte) 0x0, (byte) 0xb, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x2, (byte) 0x0, (byte) 0xc,
    };

}
