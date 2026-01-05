/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http.qpack.writers;

import jdk.internal.net.http.qpack.QPACK;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.EXTRA;

public class DecoderInstructionsWriter {
    private final QPACK.Logger logger;
    private boolean encoding;
    private static final AtomicLong IDS = new AtomicLong();

    private final IntegerWriter integerWriter = new IntegerWriter();

    public DecoderInstructionsWriter() {
        long id = IDS.incrementAndGet();
        this.logger = QPACK.getLogger().subLogger("DecoderInstructionsWriter#" + id);
    }

    /*
     * Configure the writer for encoding "Section Acknowledgment" decoder instruction:
     *     0   1   2   3   4   5   6   7
     *   +---+---+---+---+---+---+---+---+
     *   | 1 |      Stream ID (7+)       |
     *   +---+---------------------------+
     */
    public int configureForSectionAck(long streamId) {
        checkIfEncodingInProgress();
        encoding = true;
        integerWriter.configure(streamId, 7, 0b1000_0000);
        if (logger.isLoggable(EXTRA)) {
            logger.log(EXTRA, () -> format("Section Acknowledgment for stream id=%s",
                    streamId));
        }
        return IntegerWriter.requiredBufferSize(7, streamId);
    }

    /*
     * Configure the writer for encoding "Stream Cancellation" decoder instruction:
     *     0   1   2   3   4   5   6   7
     *   +---+---+---+---+---+---+---+---+
     *   | 0 | 1 |     Stream ID (6+)    |
     *   +---+---+-----------------------+
     */
    public int configureForStreamCancel(long streamId) {
        checkIfEncodingInProgress();
        encoding = true;
        integerWriter.configure(streamId, 6, 0b0100_0000);
        if (logger.isLoggable(EXTRA)) {
            logger.log(EXTRA, () -> format("Stream Cancellation for stream id=%s",
                    streamId));
        }
        return IntegerWriter.requiredBufferSize(6, streamId);
    }

    /*
     * Configure the writer for encoding "Insert Count Increment" decoder instruction:
     *     0   1   2   3   4   5   6   7
     *   +---+---+---+---+---+---+---+---+
     *   | 0 | 0 |     Increment (6+)    |
     *   +---+---+-----------------------+
     */
    public int configureForInsertCountInc(long increment) {
        checkIfEncodingInProgress();
        encoding = true;
        integerWriter.configure(increment, 6, 0b0000_0000);
        if (logger.isLoggable(EXTRA)) {
            logger.log(EXTRA, () -> format("Insert Count Increment value=%s",
                    increment));
        }
        return IntegerWriter.requiredBufferSize(6, increment);
    }

    public boolean write(ByteBuffer byteBuffer) {
        if (!encoding) {
            throw new IllegalStateException("Writer hasn't been configured");
        }
        boolean done = integerWriter.write(byteBuffer);
        if (done) {
            integerWriter.reset();
            encoding = false;
        }
        return done;
    }

    private void checkIfEncodingInProgress() {
        if (encoding) {
            throw new IllegalStateException(
                    "Previous encoding operation hasn't finished yet");
        }
    }

}
