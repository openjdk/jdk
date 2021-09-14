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
 * @key randomness
 * @run main/othervm/timeout=180 GZipLoopTest
 */
import java.io.*;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

public class GZipLoopTest {
    private static final int FINISH_NUM = 512;

    public static void main(String[] args) {
        test();
    }

    private static void test() {
        try {
            byte[] b = new byte[FINISH_NUM];
            Random rand = new Random();
            rand.nextBytes(b);
            GZIPOutputStream zip = new GZIPOutputStream(new OutputStream() {
                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    throw new IOException();
                }
                @Override
                public void write(byte b[]) throws IOException {}
                @Override
                public void write(int b) throws IOException {}
            });
            try {
                zip.write(b, 0, FINISH_NUM);
                zip.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            for (int i = 0; i < 3; i++) {
                try {
                    zip.write(b, 0, FINISH_NUM);
                } catch (Exception e) {
                    if (e instanceof NullPointerException) {
                        System.out.println("Test Passed : " + e.getMessage());
                    } else {
                        throw new RuntimeException("Test failed : Expected exception is not thrown");
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Test failed");
        }
    }

}
