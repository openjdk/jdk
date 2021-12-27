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
 * @summary Test Infinite loop while writing on closed GZipOutputStream , ZipOutputStream and JarOutputStream.
 * @run testng CloseDeflaterTest
 */
import java.io.*;
import java.util.Random;
import java.util.jar.JarOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.fail;


public class CloseDeflaterTest {

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
    private static byte[] inputBytes = new byte[INPUT_LENGTH];
    private static Random rand = new Random();

    @DataProvider(name = "testgzipinput")
    public Object[][] testGZipInput() {
     //testGZip will close the GZipOutputStream using close() method when the boolean
     //useCloseMethod is set to true and finish() method if the value is set to false
     return new Object[][] {
      { GZIPOutputStream.class, true },
      { GZIPOutputStream.class, false },
     };
    }

    @DataProvider(name = "testzipjarinput")
    public Object[][] testZipAndJarInput() {
     //testZipAndJarInput will perfrom write/closeEntry operations on JarOutputStream when the boolean
     //useJar is set to true and on ZipOutputStream if the value is set to false
     return new Object[][] {
      { JarOutputStream.class, true },
      { ZipOutputStream.class, false },
     };
    }

    @BeforeTest
    public void before_test()
    {
       //add inputBytes array with random bytes to write into Zip
       rand.nextBytes(inputBytes);
    }

    //Test for infinite loop by writing bytes to closed GZIPOutputStream
    @Test(dataProvider = "testgzipinput")
    public void testGZip(Class<?> type, boolean useCloseMethod) throws IOException {
        GZIPOutputStream zip = new GZIPOutputStream(outStream);
        try {
            zip.write(inputBytes, 0, INPUT_LENGTH);
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
                //write on a closed GZIPOutputStream
                zip.write(inputBytes, 0, INPUT_LENGTH);
                fail("Deflater closed exception not thrown");
            } catch (NullPointerException e) {
                //expected , Deflater has been closed exception
            }
        }
    }

    //Test for infinite loop by writing bytes to closed ZipOutputStream/JarOutputStream
    @Test(dataProvider = "testzipjarinput")
    public void testZipCloseEntry(Class<?> type,boolean useJar) throws IOException {
        ZipOutputStream zip = null;
        if(useJar) {
           zip = new JarOutputStream(outStream);
        } else {
           zip = new ZipOutputStream(outStream);
        }
        try {
            zip.putNextEntry(new ZipEntry(""));
        } catch (IOException e) {
            //expected to throw IOException since putNextEntry calls write method
        }
        try {
            zip.write(inputBytes, 0, INPUT_LENGTH);
            //close zip entry
            zip.closeEntry();
        } catch (IOException e) {
            //expected
        }
        for (int i = 0; i < 3; i++) {
            try {
                //write on a closed ZipOutputStream
                zip.write(inputBytes, 0, INPUT_LENGTH);
                fail("Deflater closed exception not thrown");
            } catch (NullPointerException e) {
                //expected , Deflater has been closed exception
            }
        }
    }

}
