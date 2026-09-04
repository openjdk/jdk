/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.tool;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import jdk.jfr.internal.consumer.ChunkHeader;
import jdk.jfr.internal.consumer.RecordingInput;

final class HeaderData {
    private static int FILE_STATE_POSITION = (int) ChunkHeader.FILE_STATE_POSITION;
    private static int CHUNK_SIZE_POSITION = (int) ChunkHeader.CHUNK_SIZE_POSITION;
    private static int HEADER_SIZE = (int) ChunkHeader.HEADER_SIZE;

    private final ByteBuffer buffer;

    HeaderData(byte[] bytes) {
        buffer = ByteBuffer.wrap(bytes);
    }

    boolean finished() {
        return buffer.get(FILE_STATE_POSITION) == 0;
    }

    long size() {
        return buffer.getLong(CHUNK_SIZE_POSITION);
    }

    void markFinished() {
        buffer.put(FILE_STATE_POSITION, (byte) 0);
    }

    void write(FileChannel out) throws IOException {
        while (buffer.hasRemaining()) {
            out.write(buffer);
        }
    }

    static HeaderData read(RecordingInput input) throws IOException {
        byte[] first = new byte[HEADER_SIZE];
        byte[] second = new byte[HEADER_SIZE];
        while (true) {
            while (true) {
                input.positionPhysical(0);
                input.readPhysicalFully(first, 0, first.length);
                if (first[FILE_STATE_POSITION] != ChunkHeader.UPDATING_CHUNK_HEADER) {
                    break;
                }
                try {
                    input.pollWait();
                } catch (IOException ioe) {
                    return null;
                }
            }
            input.positionPhysical(0);
            input.readPhysicalFully(second, 0, second.length);
            if (first[FILE_STATE_POSITION] == second[FILE_STATE_POSITION]) {
                return new HeaderData(first);
            }
        }
    }
}