/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.xml.internal.org.jvnet.mimepull;

import java.io.*;

/**
 * Use {@link RandomAccessFile} for concurrent access of read
 * and write partial part's content.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
final class DataFile {
    private WeakDataFile weak;
    private long writePointer;

    DataFile(File file) {
        writePointer=0;
        weak = new WeakDataFile(this, file);
    }

    /**
     *
     */
    void close() {
        weak.close();
    }

    /**
     * Read data from the given file pointer position.
     *
     * @param pointer read position
     * @param buf that needs to be filled
     * @param offset the start offset of the data.
     * @param length of data that needs to be read
     */
    synchronized void read(long pointer, byte[] buf, int offset, int length ) {
        weak.read(pointer, buf, offset, length);
    }

    void renameTo(File f) {
        weak.renameTo(f);
    }

    /**
     * Write data to the file
     *
     * @param data that needs to written to a file
     * @param offset start offset in the data
     * @param length no bytes to write
     * @return file pointer before the write operation(or at which the
     *         data is written)
     */
    synchronized long writeTo(byte[] data, int offset, int length) {
        long temp = writePointer;
        writePointer = weak.writeTo(writePointer, data, offset, length);
        return temp;
    }

}
