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

/**
 * @test
 * @bug 8193682
 * @summary Test DeflatorOutputStream for infinite loop while writing on closed stream
 * @run testng GZipLoopTest
 */
import java.io.*;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.fail;


public class GZipLoopTest {

    //number of bytes to write
    private static final int INPUT_LENGTH= 512;
    //OutputStream that will throw an exception during a write operation
    private static OutputStream outStream = new OutputStream() {
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            //throw exception during write
            throw new IOException();
        }
        @Override
        public void write(byte b[]) throws IOException {}
        @Override
        public void write(int b) throws IOException {}
    };
    private static byte[] b = new byte[INPUT_LENGTH];
    private static Random rand = new Random();

    @DataProvider(name = "testinput")
      public Object[][] testInput() {
       return new Object[][] {
        { GZIPOutputStream.class, true },
        { GZIPOutputStream.class, false },
       };
      }

    @BeforeTest
    public void before_test()
    {
       rand.nextBytes(b);
    }

    @Test(dataProvider = "testinput")
    public void testGZip(Class<?> type, boolean useCloseMethod) throws IOException {
        GZIPOutputStream zip = new GZIPOutputStream(outStream);
        try {
            zip.write(b, 0, INPUT_LENGTH);
            //close zip
            if(useCloseMethod) {
               zip.close();
            } else {
               zip.finish();
            }
        } catch (IOException e) {
            //expected
        }
        for (int i = 0; i < 3; i++) {
            try {
                zip.write(b, 0, INPUT_LENGTH);
                fail("Deflater closed exception not thrown");
            } catch (NullPointerException e) {
                //expected , Deflater has been closed exception
            }
        }
    }

}
