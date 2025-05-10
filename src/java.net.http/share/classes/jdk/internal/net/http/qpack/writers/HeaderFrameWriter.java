/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.net.http.qpack.TableEntry;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.EXTRA;

public class HeaderFrameWriter {
    private BinaryRepresentationWriter writer;
    private final QPACK.Logger logger;
    private final FieldLineIndexedWriter indexedWriter;
    private final FieldLineIndexedNameWriter literalWithNameReferenceWriter;
    private final FieldLineLiteralsWriter literalWithLiteralNameWriter;
    private boolean encoding;
    private static final AtomicLong HEADER_FRAME_WRITER_IDS = new AtomicLong();

    public HeaderFrameWriter() {
        this(QPACK.getLogger());
    }

    public HeaderFrameWriter(QPACK.Logger parentLogger) {
        long id = HEADER_FRAME_WRITER_IDS.incrementAndGet();
        this.logger = parentLogger.subLogger("HeaderFrameWriter#" + id);

        indexedWriter = new FieldLineIndexedWriter(logger.subLogger("FieldLineIndexedWriter"));
        literalWithNameReferenceWriter = new FieldLineIndexedNameWriter(
                logger.subLogger("FieldLineIndexedNameWriter"));
        literalWithLiteralNameWriter = new FieldLineLiteralsWriter(
                logger.subLogger("FieldLineLiteralsWriter"));
    }

    public void configure(TableEntry e, boolean sensitive, long base) {
        checkIfEncodingInProgress();
        encoding = true;
        writer = switch (e.type()) {
            case NAME_VALUE -> indexedWriter.configure(e, base);
            case NAME -> literalWithNameReferenceWriter.configure(e, sensitive, base);
            case NEITHER -> literalWithLiteralNameWriter.configure(e, sensitive);
        };
    }

    /**
     * Writes the {@linkplain #configure(TableEntry, boolean, long)
     * set up} header into the given buffer.
     *
     * <p> The method writes as much as possible of the header's binary
     * representation into the given buffer, starting at the buffer's position,
     * and increments its position to reflect the bytes written. The buffer's
     * mark and limit will not be modified.
     *
     * <p> Once the method has returned {@code true}, the configured header is
     * deemed encoded. A new header may be set up.
     *
     * @param headerFrame the buffer to encode the header into, may be empty
     * @return {@code true} if the current header has been fully encoded,
     * {@code false} otherwise
     * @throws NullPointerException  if the buffer is {@code null}
     * @throws IllegalStateException if there is no set up header
     */
    public boolean write(ByteBuffer headerFrame) {
        if (!encoding) {
            throw new IllegalStateException("A header hasn't been set up");
        }
        if (logger.isLoggable(EXTRA)) {
            logger.log(EXTRA, () -> format("writing to %s", headerFrame));
        }
        boolean done = writer.write(headerFrame);
        if (done) {
            writer.reset();
            encoding = false;
        }
        return done;
    }

    private void checkIfEncodingInProgress() {
        if (encoding) {
            throw new IllegalStateException("Previous encoding operation hasn't finished yet");
        }
    }
}
