/*
 * Copyright (c) 1996, 2019, Oracle and/or its affiliates. All rights reserved.
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

package java.io;


/**
 * A buffered character-input stream that keeps track of line numbers.  This
 * class defines methods {@link #setLineNumber(int)} and {@link
 * #getLineNumber()} for setting and getting the current line number
 * respectively.
 *
 * <p> By default, line numbering begins at 0. This number increments at every
 * <a href="#lt">line terminator</a> as the data is read, and can be changed
 * with a call to {@code setLineNumber(int)}.  Note however, that
 * {@code setLineNumber(int)} does not actually change the current position in
 * the stream; it only changes the value that will be returned by
 * {@code getLineNumber()}.
 *
 * <p> A line is considered to be <a id="lt">terminated</a> by any one of a
 * line feed ('\n'), a carriage return ('\r'), or a carriage return followed
 * immediately by a linefeed.
 *
 * @author      Mark Reinhold
 * @since       1.1
 */

public class LineNumberReader extends BufferedReader {

    /** The current line number */
    private int lineNumber = 0;

    /** The line number of the mark, if any */
    private int markedLineNumber; // Defaults to 0

    /** If the next character is a line feed, skip it */
    private boolean skipLF;

    /** The skipLF flag when the mark was set */
    private boolean markedSkipLF;

    /**
     * Create a new line-numbering reader, using the default input-buffer
     * size.
     *
     * @param  in
     *         A Reader object to provide the underlying stream
     */
    public LineNumberReader(Reader in) {
        super(in);
    }

    /**
     * Create a new line-numbering reader, reading characters into a buffer of
     * the given size.
     *
     * @param  in
     *         A Reader object to provide the underlying stream
     *
     * @param  sz
     *         An int specifying the size of the buffer
     */
    public LineNumberReader(Reader in, int sz) {
        super(in, sz);
    }

    /**
     * Set the current line number.
     *
     * @param  lineNumber
     *         An int specifying the line number
     *
     * @see #getLineNumber
     */
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    /**
     * Get the current line number.
     *
     * @return  The current line number
     *
     * @see #setLineNumber
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Read a single character.  <a href="#lt">Line terminators</a> are
     * compressed into single newline ('\n') characters.  Whenever a line
     * terminator is read the current line number is incremented.
     *
     * @return  The character read, or -1 if the end of the stream has been
     *          reached
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    @SuppressWarnings("fallthrough")
    public int read() throws IOException {
        synchronized (lock) {
            int c = super.read();
            if (skipLF) {
                if (c == '\n')
                    c = super.read();
                skipLF = false;
            }
            switch (c) {
            case '\r':
                skipLF = true;
            case '\n':          /* Fall through */
                lineNumber++;
                return '\n';
            }
            return c;
        }
    }

    /**
     * Read characters into a portion of an array.  Whenever a <a
     * href="#lt">line terminator</a> is read the current line number is
     * incremented.
     *
     * @param  cbuf
     *         Destination buffer
     *
     * @param  off
     *         Offset at which to start storing characters
     *
     * @param  len
     *         Maximum number of characters to read
     *
     * @return  The number of bytes read, or -1 if the end of the stream has
     *          already been reached
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  IndexOutOfBoundsException {@inheritDoc}
     */
    @SuppressWarnings("fallthrough")
    public int read(char cbuf[], int off, int len) throws IOException {
        synchronized (lock) {
            int n = super.read(cbuf, off, len);

            for (int i = off; i < off + n; i++) {
                int c = cbuf[i];
                if (skipLF) {
                    skipLF = false;
                    if (c == '\n')
                        continue;
                }
                switch (c) {
                case '\r':
                    skipLF = true;
                case '\n':      /* Fall through */
                    lineNumber++;
                    break;
                }
            }

            return n;
        }
    }

    /**
     * Read a line of text.  Whenever a <a href="#lt">line terminator</a> is
     * read the current line number is incremented.
     *
     * @return  A String containing the contents of the line, not including
     *          any <a href="#lt">line termination characters</a>, or
     *          {@code null} if the end of the stream has been reached
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public String readLine() throws IOException {
        synchronized (lock) {
            String l = super.readLine(skipLF);
            skipLF = false;
            if (l != null)
                lineNumber++;
            return l;
        }
    }

    /** Maximum skip-buffer size */
    private static final int maxSkipBufferSize = 8192;

    /** Skip buffer, null until allocated */
    private char skipBuffer[] = null;

    /**
     * Skip characters.
     *
     * @param  n
     *         The number of characters to skip
     *
     * @return  The number of characters actually skipped
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  IllegalArgumentException
     *          If {@code n} is negative
     */
    public long skip(long n) throws IOException {
        if (n < 0)
            throw new IllegalArgumentException("skip() value is negative");
        int nn = (int) Math.min(n, maxSkipBufferSize);
        synchronized (lock) {
            if ((skipBuffer == null) || (skipBuffer.length < nn))
                skipBuffer = new char[nn];
            long r = n;
            while (r > 0) {
                int nc = read(skipBuffer, 0, (int) Math.min(r, nn));
                if (nc == -1)
                    break;
                r -= nc;
            }
            return n - r;
        }
    }

    /**
     * Mark the present position in the stream.  Subsequent calls to reset()
     * will attempt to reposition the stream to this point, and will also reset
     * the line number appropriately.
     *
     * @param  readAheadLimit
     *         Limit on the number of characters that may be read while still
     *         preserving the mark.  After reading this many characters,
     *         attempting to reset the stream may fail.
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public void mark(int readAheadLimit) throws IOException {
        synchronized (lock) {
            // If the most recently read character is '\r', then increment the
            // read ahead limit as in this case if the next character is '\n',
            // two characters would actually be read by the next read().
            if (skipLF)
                readAheadLimit++;
            super.mark(readAheadLimit);
            markedLineNumber = lineNumber;
            markedSkipLF     = skipLF;
        }
    }

    /**
     * Reset the stream to the most recent mark.
     *
     * @throws  IOException
     *          If the stream has not been marked, or if the mark has been
     *          invalidated
     */
    public void reset() throws IOException {
        synchronized (lock) {
            super.reset();
            lineNumber = markedLineNumber;
            skipLF     = markedSkipLF;
        }
    }

}
