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
 * @bug 8268435
 * @summary Verify ChannelInputStream methods
 * @library ..
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @modules java.base/jdk.internal.util
 * @run testng/othervm -Xmx6G ChannelInputStream
 * @key randomness
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;
import jdk.internal.util.ArraysSupport;

import jdk.test.lib.RandomFactory;

import org.testng.Assert;
import static org.testng.Assert.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ChannelInputStream {

    static final Random RAND = RandomFactory.getRandom();

    static File createFile(long size) throws IOException {
        File file = File.createTempFile("foo", ".bar");
        file.deleteOnExit();
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        raf.setLength(size);
        raf.close();
        return file;
    }

    @DataProvider
    public Object[][] provider() throws IOException {

        Object[][] result = new Object[][] {
            {createFile(0L)},
            {createFile(RAND.nextInt(Short.MAX_VALUE))},
            {createFile(ArraysSupport.SOFT_MAX_ARRAY_LENGTH)},
            {createFile((long)Integer.MAX_VALUE + 27)}
        };

        return result;
    }

    @Test(dataProvider = "provider")
    public void readAllBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             FileChannel fc = fis.getChannel();
             InputStream cis = Channels.newInputStream(fc)) {
            long size = Files.size(file.toPath());
            if (size == 0L) {
                byte[] bytes = cis.readAllBytes();
                assertNotNull(bytes);
                assertEquals(bytes.length, 0);
            } else if (size > Integer.MAX_VALUE) {
                expectThrows(OutOfMemoryError.class, () -> cis.readAllBytes());
            } else {
                byte[] fisBytes = fis.readAllBytes();
                fc.position(0L);
                byte[] cisBytes = cis.readAllBytes();
                assertTrue(Arrays.equals(fisBytes, cisBytes));

                // check behavior at what should be EOF
                cisBytes = cis.readAllBytes();
                assertNotNull(cisBytes);
                assertEquals(cisBytes.length, 0);
            }
        } finally {
            file.delete();
        }
    }

    @Test(dataProvider = "provider")
    public void readNBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             FileChannel fc = fis.getChannel();
             InputStream cis = Channels.newInputStream(fc)) {
            long size = Files.size(file.toPath());
            if (size == 0L) {
                byte[] bytes = cis.readNBytes(1);
                assertNotNull(bytes);
                assertEquals(bytes.length, 0);
            } else if (size > Integer.MAX_VALUE) {
                expectThrows(OutOfMemoryError.class,
                             () -> cis.readNBytes(Integer.MAX_VALUE));
            } else {
                int length = Math.toIntExact(size);
                int half = length / 2;
                int position = RAND.nextInt(half);
                int count = RAND.nextInt(length - position);
                fc.position(position);
                byte[] fisBytes = fis.readNBytes(count);
                fc.position(position);
                byte[] cisBytes = cis.readNBytes(count);
                assertTrue(Arrays.equals(fisBytes, cisBytes));

                // skip to EOF and check behavior there
                cis.skipNBytes(length - (position + count));
                cisBytes = cis.readNBytes(0);
                assertNotNull(cisBytes);
                assertEquals(cisBytes.length, 0);
            }
        } finally {
            file.delete();
        }
    }
}
