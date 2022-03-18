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

    // Number of bytes to write/read from Deflater/Inflater
    private static final int INPUT_LENGTH= 512;
    // OutputStream that will throw an exception during a write operation
    private static OutputStream outStream = new OutputStream() {
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            throw new IOException();
        }
        @Override
        public void write(byte[] b) throws IOException {}
        @Override
        public void write(int b) throws IOException {}
    };
    // InputStream that will throw an exception during a read operation
    private static InputStream inStream = new InputStream() {
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            throw new IOException();
        }
        @Override
        public int read(byte[] b) throws IOException { throw new IOException();}
        @Override
        public int read() throws IOException { throw new IOException();}
    };
    // Input bytes for read/write operation
    private static byte[] inputBytes = new byte[INPUT_LENGTH];
    // Random function to add bytes to inputBytes
    private static Random rand = new Random();

    /**
     * DataProvider to specify whether to use close() or finish() of OutputStream
     *
     * @return Entry object indicating which method to use for closing OutputStream
     */
    @DataProvider
    public Object[][] testOutputStreams() {
     return new Object[][] {
      { true },
      { false },
     };
    }

    /**
     * DataProvider to specify on which outputstream closeEntry() has to be called
     *
     * @return Entry object returning either JarOutputStream or ZipOutputStream
     */
    @DataProvider
    public Object[][] testZipAndJar() throws IOException{
     return new Object[][] {
      { new JarOutputStream(outStream)},
      { new ZipOutputStream(outStream)},
     };
    }

    /**
     * Add inputBytes array with random bytes to write into OutputStream
     */
    @BeforeTest
    public void before_test()
    {
       rand.nextBytes(inputBytes);
    }

    /**
     * Test for infinite loop by writing bytes to closed GZIPOutputStream
     *
     * @param useCloseMethod indicates whether to use Close() or finish() method
     * @throws IOException if an error occurs
     */
    @Test(dataProvider = "testOutputStreams")
    public void testGZip(boolean useCloseMethod) throws IOException {
        GZIPOutputStream gzip = new GZIPOutputStream(outStream);
        gzip.write(inputBytes, 0, INPUT_LENGTH);
        assertThrows(IOException.class, () -> {
            // Close GZIPOutputStream
            if (useCloseMethod) {
                gzip.close();
            } else {
                gzip.finish();
            }
        });
        // Write on a closed GZIPOutputStream, closed Deflater IOException expected
        assertThrows(NullPointerException.class , () -> gzip.write(inputBytes, 0, INPUT_LENGTH));
    }

    /**
     * Test for infinite loop by writing bytes to closed DeflaterOutputStream
     *
     * @param useCloseMethod indicates whether to use Close() or finish() method
     * @throws IOException if an error occurs
     */
    @Test(dataProvider = "testOutputStreams")
    public void testDeflaterOutputStream(boolean useCloseMethod) throws IOException {
        DeflaterOutputStream def = new DeflaterOutputStream(outStream);
        assertThrows(IOException.class , () -> def.write(inputBytes, 0, INPUT_LENGTH));
        assertThrows(IOException.class, () -> {
            // Close DeflaterOutputStream
            if (useCloseMethod) {
                def.close();
            } else {
                def.finish();
            }
        });
        // Write on a closed DeflaterOutputStream, 'Deflater has been closed' NPE is expected
        assertThrows(NullPointerException.class , () -> def.write(inputBytes, 0, INPUT_LENGTH));
    }

    /**
     * Test for infinite loop by reading bytes from closed DeflaterInputStream
     *
     * @throws IOException if an error occurs
     */
    @Test
    public void testDeflaterInputStream() throws IOException {
        DeflaterInputStream def = new DeflaterInputStream(inStream);
        assertThrows(IOException.class , () -> def.read(inputBytes, 0, INPUT_LENGTH));
        // Close DeflaterInputStream
        def.close();
        // Read from a closed DeflaterInputStream, closed Deflater IOException expected
        assertThrows(IOException.class , () -> def.read(inputBytes, 0, INPUT_LENGTH));
    }

    /**
     * Test for infinite loop by writing bytes to closed InflaterOutputStream
     *
     * @param useCloseMethod indicates whether to use Close() or finish() method
     * @throws IOException if an error occurs
     */
    @Test(dataProvider = "testOutputStreams")
    public void testInflaterOutputStream(boolean useCloseMethod) throws IOException {
        InflaterOutputStream inf = new InflaterOutputStream(outStream);
        assertThrows(IOException.class , () -> inf.write(inputBytes, 0, INPUT_LENGTH));
        assertThrows(IOException.class , () -> {
            // Close InflaterOutputStream
            if (useCloseMethod) {
                inf.close();
            } else {
                inf.finish();
            }
        });
        // Write on a closed InflaterOutputStream , closed Inflater IOException expected
        assertThrows(IOException.class , () -> inf.write(inputBytes, 0, INPUT_LENGTH));
    }

    /**
     * Test for infinite loop by writing bytes to closed ZipOutputStream/JarOutputStream
     *
     * @param zip will be the instance of either JarOutputStream or ZipOutputStream
     * @throws IOException if an error occurs
     */
    @Test(dataProvider = "testZipAndJar")
    public void testZipCloseEntry(ZipOutputStream zip) throws IOException {
        assertThrows(IOException.class , () -> zip.putNextEntry(new ZipEntry("")));
        zip.write(inputBytes, 0, INPUT_LENGTH);
        assertThrows(IOException.class , () -> zip.closeEntry());
        // Write on a closed ZipOutputStream , 'Deflater has been closed' NPE is expected
        assertThrows(NullPointerException.class , () -> zip.write(inputBytes, 0, INPUT_LENGTH));
    }

}
