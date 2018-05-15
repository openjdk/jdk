/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.instrument;

import java.io.IOException;
import java.nio.ByteBuffer;

import jdk.jfr.events.FileForceEvent;
import jdk.jfr.events.FileReadEvent;
import jdk.jfr.events.FileWriteEvent;

/**
 * See {@link JITracer} for an explanation of this code.
 */
@JIInstrumentationTarget("sun.nio.ch.FileChannelImpl")
final class FileChannelImplInstrumentor {

    private FileChannelImplInstrumentor() {
    }

    private String path;

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public void force(boolean metaData) throws IOException {
        FileForceEvent event = FileForceEvent.EVENT.get();
        if (!event.isEnabled()) {
            force(metaData);
            return;
        }
        try {
            event.begin();
            force(metaData);
        } finally {
            event.path = path;
            event.metaData = metaData;
            event.commit();
            event.reset();
        }
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public int read(ByteBuffer dst) throws IOException {
        FileReadEvent event = FileReadEvent.EVENT.get();
        if (!event.isEnabled()) {
            return read(dst);
        }
        int bytesRead = 0;
        try {
            event.begin();
            bytesRead = read(dst);
        } finally {
            if (bytesRead < 0) {
                event.endOfFile = true;
            } else {
                event.bytesRead = bytesRead;
            }
            event.path = path;
            event.commit();
            event.reset();
        }
        return bytesRead;
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public int read(ByteBuffer dst, long position) throws IOException {
        FileReadEvent event = FileReadEvent.EVENT.get();
        if (!event.isEnabled()) {
            return read(dst, position);
        }
        int bytesRead = 0;
        try {
            event.begin();
            bytesRead = read(dst, position);
        } finally {
            if (bytesRead < 0) {
                event.endOfFile = true;
            } else {
                event.bytesRead = bytesRead;
            }
            event.path = path;
            event.commit();
            event.reset();
        }
        return bytesRead;
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        FileReadEvent event = FileReadEvent.EVENT.get();
        if (!event.isEnabled()) {
            return read(dsts, offset, length);
        }
        long bytesRead = 0;
        try {
            event.begin();
            bytesRead = read(dsts, offset, length);
        } finally {
            if (bytesRead < 0) {
                event.endOfFile = true;
            } else {
                event.bytesRead = bytesRead;
            }
            event.path = path;
            event.commit();
            event.reset();
        }
        return bytesRead;
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public int write(ByteBuffer src) throws IOException {
        FileWriteEvent event = FileWriteEvent.EVENT.get();
        if (!event.isEnabled()) {
            return write(src);
        }
        int bytesWritten = 0;
        try {
            event.begin();
            bytesWritten = write(src);
        } finally {
            event.bytesWritten = bytesWritten > 0 ? bytesWritten : 0;
            event.path = path;
            event.commit();
            event.reset();
        }
        return bytesWritten;
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public int write(ByteBuffer src, long position) throws IOException {
        FileWriteEvent event = FileWriteEvent.EVENT.get();
        if (!event.isEnabled()) {
            return write(src, position);
        }

        int bytesWritten = 0;
        try {
            event.begin();
            bytesWritten = write(src, position);
        } finally {
            event.bytesWritten = bytesWritten > 0 ? bytesWritten : 0;
            event.path = path;
            event.commit();
            event.reset();
        }
        return bytesWritten;
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        FileWriteEvent event = FileWriteEvent.EVENT.get();
        if (!event.isEnabled()) {
            return write(srcs, offset, length);
        }
        long bytesWritten = 0;
        try {
            event.begin();
            bytesWritten = write(srcs, offset, length);
        } finally {
            event.bytesWritten = bytesWritten > 0 ? bytesWritten : 0;
            event.path = path;
            event.commit();
            event.reset();
        }
        return bytesWritten;
    }
}
