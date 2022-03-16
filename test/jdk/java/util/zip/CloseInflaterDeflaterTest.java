/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8193682 8278794
 * @summary Test Infinite loop while writing on closed Deflater and Inflater.
 * @run testng CloseInflaterDeflaterTest
 */
import java.io.*;
import java.util.Random;
import java.util.jar.JarOutputStream;
import java.util.zip.DeflaterInputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterOutputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertThrows;


public class CloseInflaterDeflaterTest {

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
        public void write(byte[] b) throws IOException {}
        @Override
        public void write(int b) throws IOException {}
    };
    //InputStream that will throw an exception during a read operation
    private static InputStream inStream = new InputStream() {
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            //throw exception during read
            throw new IOException();
        }
        @Override
        public int read(byte[] b) throws IOException { throw new IOException();}
        @Override
        public int read() throws IOException { throw new IOException();}
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

    @DataProvider(name = "testdeflateroutputstream")
    public Object[][] testDeflaterOutputStream() {
     //testDeflaterOutputStream will close the DeflaterOutputStream using close() method when the boolean
     //useCloseMethod is set to true and finish() method if the value is set to false
     return new Object[][] {
      { DeflaterOutputStream.class, true },
      { DeflaterOutputStream.class, false },
     };
    }

    @DataProvider(name = "testinflateroutputstream")
    public Object[][] testInflaterOutputStream() {
     //testInflaterOutputStream will close the InflaterOutputStream using close() method when the boolean
     //useCloseMethod is set to true and finish() method if the value is set to false
     return new Object[][] {
      { InflaterOutputStream.class, true },
      { InflaterOutputStream.class, false },
     };
    }

    @DataProvider(name = "testzipjarinput")
    public Object[][] testZipAndJarInput() throws IOException{
     //testZipAndJarInput will perfrom write/closeEntry operations on
     //JarOutputStream and ZipOutputStream
     return new Object[][] {
      { new JarOutputStream(outStream)},
      { new ZipOutputStream(outStream)},
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
        zip.write(inputBytes, 0, INPUT_LENGTH);
        assertThrows(IOException.class, () -> {
            //close zip
            if (useCloseMethod) {
                zip.close();
            } else {
                zip.finish();
            }
        });
        //write on a closed GZIPOutputStream, closed Deflater IOException expected
        assertThrows(NullPointerException.class , () -> zip.write(inputBytes, 0, INPUT_LENGTH));
    }

    //Test for infinite loop by writing bytes to closed DeflaterOutputStream
    @Test(dataProvider = "testdeflateroutputstream")
    public void testDeflaterOutputStream(Class<?> type, boolean useCloseMethod) throws IOException {
        DeflaterOutputStream def = new DeflaterOutputStream(outStream);
        assertThrows(IOException.class , () -> def.write(inputBytes, 0, INPUT_LENGTH));
        assertThrows(IOException.class, () -> {
            //close deflater
            if (useCloseMethod) {
                def.close();
            } else {
                def.finish();
            }
        });
        //write on a closed DeflaterOutputStream, closed Deflater IOException expected
        assertThrows(NullPointerException.class , () -> def.write(inputBytes, 0, INPUT_LENGTH));
    }

    //Test for infinite loop by reading bytes from closed DeflaterInputStream
    @Test
    public void testDeflaterInputStream() throws IOException {
        DeflaterInputStream def = new DeflaterInputStream(inStream);
        assertThrows(IOException.class , () -> def.read(inputBytes, 0, INPUT_LENGTH));
        //close deflater
        def.close();
        //read from a closed DeflaterInputStream, closed Deflater IOException expected
        assertThrows(IOException.class , () -> def.read(inputBytes, 0, INPUT_LENGTH));
    }

    //Test for infinite loop by writing bytes to closed InflaterOutputStream
    @Test(dataProvider = "testinflateroutputstream")
    public void testInflaterOutputStream(Class<?> type, boolean useCloseMethod) throws IOException {
        InflaterOutputStream inf = new InflaterOutputStream(outStream);
        assertThrows(IOException.class , () -> inf.write(inputBytes, 0, INPUT_LENGTH));
        assertThrows(IOException.class , () -> {
            //close inflater
            if (useCloseMethod) {
                inf.close();
            } else {
                inf.finish();
            }
        });
        //write on a closed InflaterOutputStream , closed Inflater IOException expected
        assertThrows(IOException.class , () -> inf.write(inputBytes, 0, INPUT_LENGTH));
    }

    //Test for infinite loop by writing bytes to closed ZipOutputStream/JarOutputStream
    @Test(dataProvider = "testzipjarinput")
    public void testZipCloseEntry(ZipOutputStream zip) throws IOException {
        assertThrows(IOException.class , () -> zip.putNextEntry(new ZipEntry("")));
        zip.write(inputBytes, 0, INPUT_LENGTH);
        assertThrows(IOException.class , () -> zip.closeEntry());
        //write on a closed ZipOutputStream , Deflater closed NullPointerException expected
        assertThrows(NullPointerException.class , () -> zip.write(inputBytes, 0, INPUT_LENGTH));
    }

}
