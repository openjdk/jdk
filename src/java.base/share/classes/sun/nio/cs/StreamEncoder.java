/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.cs;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import jdk.internal.misc.InternalLock;

public final class StreamEncoder extends Writer {

    private static final int INITIAL_BYTE_BUFFER_CAPACITY = 512;
    private static final int MAX_BYTE_BUFFER_CAPACITY = 8192;

    private volatile boolean closed;

    private void ensureOpen() throws IOException {
        if (closed)
            throw new IOException("Stream closed");
    }

    // Factories for java.io.OutputStreamWriter
    public static StreamEncoder forOutputStreamWriter(OutputStream out,
                                                      Object lock,
                                                      String charsetName)
        throws UnsupportedEncodingException
    {
        try {
            return new StreamEncoder(out, lock, Charset.forName(charsetName));
        } catch (IllegalCharsetNameException | UnsupportedCharsetException x) {
            throw new UnsupportedEncodingException (charsetName);
        }
    }

    public static StreamEncoder forOutputStreamWriter(OutputStream out,
                                                      Object lock,
                                                      Charset cs)
    {
        return new StreamEncoder(out, lock, cs);
    }

    public static StreamEncoder forOutputStreamWriter(OutputStream out,
                                                      Object lock,
                                                      CharsetEncoder enc)
    {
        return new StreamEncoder(out, lock, enc);
    }

    public static StreamEncoder forOutputStreamWriter(OutputStream out,
                                                      CharsetEncoder enc)
    {
        return new StreamEncoder(out, enc);
    }

    // -- Public methods corresponding to those in OutputStreamWriter --

    // All synchronization and state/argument checking is done in these public
    // methods; the concrete stream-encoder subclasses defined below need not
    // do any such checking.

    public String getEncoding() {
        if (isOpen())
            return encodingName();
        return null;
    }

    public void flushBuffer() throws IOException {
        Object lock = this.lock;
        if (lock instanceof InternalLock locker) {
            locker.lock();
            try {
                lockedFlushBuffer();
            } finally {
                locker.unlock();
            }
        } else {
            synchronized (lock) {
                lockedFlushBuffer();
            }
        }
    }

    private void lockedFlushBuffer() throws IOException {
        if (isOpen())
            implFlushBuffer();
        else
            throw new IOException("Stream closed");
    }

    public void write(int c) throws IOException {
        char[] cbuf = new char[1];
        cbuf[0] = (char) c;
        write(cbuf, 0, 1);
    }

    public void write(char[] cbuf, int off, int len) throws IOException {
        Object lock = this.lock;
        if (lock instanceof InternalLock locker) {
            locker.lock();
            try {
                lockedWrite(cbuf, off, len);
            } finally {
                locker.unlock();
            }
        } else {
            synchronized (lock) {
                lockedWrite(cbuf, off, len);
            }
        }
    }

    private void lockedWrite(char[] cbuf, int off, int len) throws IOException {
        ensureOpen();
        if ((off < 0) || (off > cbuf.length) || (len < 0) ||
                ((off + len) > cbuf.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        implWrite(cbuf, off, len);
    }

    public void write(String str, int off, int len) throws IOException {
        /* Check the len before creating a char buffer */
        if (len < 0)
            throw new IndexOutOfBoundsException();
        char[] cbuf = new char[len];
        str.getChars(off, off + len, cbuf, 0);
        write(cbuf, 0, len);
    }

    public void write(CharBuffer cb) throws IOException {
        int position = cb.position();
        try {
            Object lock = this.lock;
            if (lock instanceof InternalLock locker) {
                locker.lock();
                try {
                    lockedWrite(cb);
                } finally {
                    locker.unlock();
                }
            } else {
                synchronized (lock) {
                    lockedWrite(cb);
                }
            }
        } finally {
            cb.position(position);
        }
    }

    private void lockedWrite(CharBuffer cb) throws IOException {
        ensureOpen();
        implWrite(cb);
    }

    public void flush() throws IOException {
        Object lock = this.lock;
        if (lock instanceof InternalLock locker) {
            locker.lock();
            try {
                lockedFlush();
            } finally {
                locker.unlock();
            }
        } else {
            synchronized (lock) {
                lockedFlush();
            }
        }
    }

    private void lockedFlush() throws IOException {
        ensureOpen();
        implFlush();
    }

    public void close() throws IOException {
        Object lock = this.lock;
        if (lock instanceof InternalLock locker) {
            locker.lock();
            try {
                lockedClose();
            } finally {
                locker.unlock();
            }
        } else {
            synchronized (lock) {
                lockedClose();
            }
        }
    }

    private void lockedClose() throws IOException {
        if (closed)
            return;
        try {
            implClose();
        } finally {
            closed = true;
        }
    }

    private boolean isOpen() {
        return !closed;
    }


    // -- Charset-based stream encoder impl --

    private final Charset cs;
    private final CharsetEncoder encoder;
    private ByteBuffer bb;
    private final int maxBufferCapacity;

    private final OutputStream out;

    // Leftover first char in a surrogate pair
    private boolean haveLeftoverChar = false;
    private char leftoverChar;
    private CharBuffer lcb = null;

    private StreamEncoder(OutputStream out, Object lock, Charset cs) {
        this(out, lock,
            cs.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE));
    }

    private StreamEncoder(OutputStream out, Object lock, CharsetEncoder enc) {
        super(lock);
        this.out = out;
        this.cs = enc.charset();
        this.encoder = enc;

        this.bb = ByteBuffer.allocate(INITIAL_BYTE_BUFFER_CAPACITY);
        this.maxBufferCapacity = MAX_BYTE_BUFFER_CAPACITY;
    }

    private StreamEncoder(OutputStream out, CharsetEncoder enc) {
        super();
        this.out = out;
        this.cs = enc.charset();
        this.encoder = enc;

        this.bb = ByteBuffer.allocate(INITIAL_BYTE_BUFFER_CAPACITY);
        this.maxBufferCapacity = MAX_BYTE_BUFFER_CAPACITY;
    }

    private void writeBytes() throws IOException {
        bb.flip();
        int lim = bb.limit();
        int pos = bb.position();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        if (rem > 0) {
            out.write(bb.array(), bb.arrayOffset() + pos, rem);
        }
        bb.clear();
    }

    private void flushLeftoverChar(CharBuffer cb, boolean endOfInput)
        throws IOException
    {
        if (!haveLeftoverChar && !endOfInput)
            return;
        if (lcb == null)
            lcb = CharBuffer.allocate(2);
        else
            lcb.clear();
        if (haveLeftoverChar)
            lcb.put(leftoverChar);
        if ((cb != null) && cb.hasRemaining())
            lcb.put(cb.get());
        lcb.flip();
        while (lcb.hasRemaining() || endOfInput) {
            CoderResult cr = encoder.encode(lcb, bb, endOfInput);
            if (cr.isUnderflow()) {
                if (lcb.hasRemaining()) {
                    leftoverChar = lcb.get();
                    if (cb != null && cb.hasRemaining()) {
                        lcb.clear();
                        lcb.put(leftoverChar).put(cb.get()).flip();
                        continue;
                    }
                    return;
                }
                break;
            }
            if (cr.isOverflow()) {
                assert bb.position() > 0;
                writeBytes();
                continue;
            }
            cr.throwException();
        }
        haveLeftoverChar = false;
    }

    void implWrite(char[] cbuf, int off, int len)
        throws IOException
    {
        CharBuffer cb = CharBuffer.wrap(cbuf, off, len);
        implWrite(cb);
    }

    void implWrite(CharBuffer cb)
        throws IOException
    {
        if (haveLeftoverChar) {
            flushLeftoverChar(cb, false);
        }

        growByteBufferIfNeeded(cb.remaining());

        while (cb.hasRemaining()) {
            CoderResult cr = encoder.encode(cb, bb, false);
            if (cr.isUnderflow()) {
                assert (cb.remaining() <= 1) : cb.remaining();
                if (cb.remaining() == 1) {
                    haveLeftoverChar = true;
                    leftoverChar = cb.get();
                }
                break;
            }
            if (cr.isOverflow()) {
                assert bb.position() > 0;
                writeBytes();
                continue;
            }
            cr.throwException();
        }
    }

    /**
     * Grows bb to a capacity to allow len characters be encoded.
     */
    void growByteBufferIfNeeded(int len) throws IOException {
        int cap = bb.capacity();
        if (cap < maxBufferCapacity) {
            int maxBytes = len * Math.round(encoder.maxBytesPerChar());
            int newCap = Math.min(maxBytes, maxBufferCapacity);
            if (newCap > cap) {
                implFlushBuffer();
                bb = ByteBuffer.allocate(newCap);
            }
        }
    }

    void implFlushBuffer() throws IOException {
        if (bb.position() > 0) {
            writeBytes();
        }
    }

    void implFlush() throws IOException {
        implFlushBuffer();
        out.flush();
    }

    void implClose() throws IOException {
        try (out) {
            flushLeftoverChar(null, true);
            for (;;) {
                CoderResult cr = encoder.flush(bb);
                if (cr.isUnderflow())
                    break;
                if (cr.isOverflow()) {
                    assert bb.position() > 0;
                    writeBytes();
                    continue;
                }
                cr.throwException();
            }

            if (bb.position() > 0)
                writeBytes();
            out.flush();
        } catch (IOException x) {
            encoder.reset();
            throw x;
        }
    }

    String encodingName() {
        return ((cs instanceof HistoricallyNamedCharset)
            ? ((HistoricallyNamedCharset)cs).historicalName()
            : cs.name());
    }
}
