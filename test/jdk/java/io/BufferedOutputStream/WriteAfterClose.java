/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4799358
 * @summary BufferOutputStream.write() should immediately throw IOException on
 * closed stream
 * @run main WriteAfterClose
 *
 */
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class WriteAfterClose {

    static void testWrite(OutputStream os) throws IOException {
        // close the stream first
        os.close();
        byte[] buf = {'a', 'b', 'c', 'd'};
        try {
            os.write(buf);
            throw new RuntimeException("Should not allow write(byte[]) on a closed stream");
        } catch (IOException e) {
            System.out.println("Caught the IOException as expected: " + e.getMessage());
        }

        try {
            os.write(1);
            throw new RuntimeException("Should not allow write(int) on a closed stream");
        } catch (IOException e) {
            System.out.println("Caught the IOException as expected: " + e.getMessage());
        }
    }

    public static void main(String argv[]) throws IOException {
        var file = new File(System.getProperty("test.dir", "."), "test.txt");
        file.createNewFile();
        file.deleteOnExit();
        var bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file));
        testWrite(bufferedOutputStream);
    }
}
