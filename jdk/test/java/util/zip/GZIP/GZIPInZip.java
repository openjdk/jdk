/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 7021870 8023431
 * @summary Reading last gzip chain member must not close the input stream.
 *          Garbage following gzip entry must be ignored.
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


public class GZIPInZip {

    private static volatile Throwable trouble;

    public static void main(String[] args) throws Throwable {
        doTest(false, false);
        doTest(false, true);
        doTest(true, false);
        doTest(true, true);
    }

    private static void doTest(final boolean appendGarbage,
                               final boolean limitGISBuff)
            throws Throwable {

        final PipedOutputStream pos = new PipedOutputStream();
        final PipedInputStream pis = new PipedInputStream(pos);

        Thread compressor = new Thread() {
            public void run() {
                final byte[] xbuf = { 'x' };
                try (ZipOutputStream zos = new ZipOutputStream(pos)) {

                    zos.putNextEntry(new ZipEntry("a.gz"));
                    try (GZIPOutputStream gos1 = new GZIPOutputStream(zos)) {
                        gos1.write(xbuf);
                        gos1.finish();
                    }
                    if (appendGarbage)
                        zos.write(xbuf);

                    zos.closeEntry();

                    zos.putNextEntry(new ZipEntry("b.gz"));
                    try (GZIPOutputStream gos2 = new GZIPOutputStream(zos)) {
                        gos2.write(xbuf);
                        gos2.finish();
                    }
                    zos.closeEntry();

                } catch (Throwable t) {
                    trouble = t;
                }
            }
        };

        Thread uncompressor = new Thread() {
            public void run() {
                try (ZipInputStream zis = new ZipInputStream(pis)) {
                    zis.getNextEntry();
                    try (InputStream gis = limitGISBuff ?
                            new GZIPInputStream(zis, 4) :
                            new GZIPInputStream(zis)) {
                        // try to read more than the entry has
                        gis.skip(2);
                    }

                    try {
                        zis.getNextEntry();
                    } catch (IOException e) {
                        throw new AssertionError("ZIP stream was prematurely closed");
                    }
                } catch (Throwable t) {
                    trouble = t;
                }
            }
        };

        compressor.start(); uncompressor.start();
        compressor.join();  uncompressor.join();

        if (trouble != null)
            throw trouble;
    }
}
