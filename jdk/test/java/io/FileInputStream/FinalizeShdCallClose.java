/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @test
 * @bug 6524062
 * @summary Test to ensure that FIS.finalize() invokes the close() method as per
 * the specification.
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class FinalizeShdCallClose {

    static final String FILE_NAME = "empty.txt";

    public static class MyStream extends FileInputStream {
        private boolean closed = false;

        public MyStream(String name) throws FileNotFoundException {
            super(name);
        }

        public void finalize() {
            try {
                super.finalize();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        public void close() {
            try {
                super.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }
    }

    /* standalone interface */
    public static void main(String argv[]) throws Exception {

        File inFile= new File(System.getProperty("test.dir", "."), FILE_NAME);
        inFile.createNewFile();
        inFile.deleteOnExit();

        String name = inFile.getPath();
        MyStream ms = null;
        try {
            ms = new MyStream(name);
        } catch (FileNotFoundException e) {
            System.out.println("Unexpected exception " + e);
            throw(e);
        }
        ms.finalize();
        if (!ms.isClosed()) {
            throw new Exception("MyStream.close() method is not called");
        }
        System.out.println("OK");
    }
}
