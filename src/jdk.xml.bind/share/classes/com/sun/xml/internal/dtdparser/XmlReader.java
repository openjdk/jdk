/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.dtdparser;

import java.io.ByteArrayInputStream;
import java.io.CharConversionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.util.Hashtable;
import java.util.Locale;


// NOTE:  Add I18N support to this class when JDK gets the ability to
// defer selection of locale for exception messages ... use the same
// technique for both.


/**
 * This handles several XML-related tasks that normal java.io Readers
 * don't support, inluding use of IETF standard encoding names and
 * automatic detection of most XML encodings.  The former is needed
 * for interoperability; the latter is needed to conform with the XML
 * spec.  This class also optimizes reading some common encodings by
 * providing low-overhead unsynchronized Reader support.
 * <p/>
 * <P> Note that the autodetection facility should be used only on
 * data streams which have an unknown character encoding.  For example,
 * it should never be used on MIME text/xml entities.
 * <p/>
 * <P> Note that XML processors are only required to support UTF-8 and
 * UTF-16 character encodings.  Autodetection permits the underlying Java
 * implementation to provide support for many other encodings, such as
 * US-ASCII, ISO-8859-5, Shift_JIS, EUC-JP, and ISO-2022-JP.
 *
 * @author David Brownell
 * @author Janet Koenig
 * @version 1.3 00/02/24
 */
// package private
final class XmlReader extends Reader {
    private static final int MAXPUSHBACK = 512;

    private Reader in;
    private String assignedEncoding;
    private boolean closed;

    //
    // This class always delegates I/O to a reader, which gets
    // its data from the very beginning of the XML text.  It needs
    // to use a pushback stream since (a) autodetection can read
    // partial UTF-8 characters which need to be fully processed,
    // (b) the "Unicode" readers swallow characters that they think
    // are byte order marks, so tests fail if they don't see the
    // real byte order mark.
    //
    // It's got do this efficiently:  character I/O is solidly on the
    // critical path.  (So keep buffer length over 2 Kbytes to avoid
    // excess buffering. Many URL handlers stuff a BufferedInputStream
    // between here and the real data source, and larger buffers keep
    // that from slowing you down.)
    //

    /**
     * Constructs the reader from an input stream, auto-detecting
     * the encoding to use according to the heuristic specified
     * in the XML 1.0 recommendation.
     *
     * @param in the input stream from which the reader is constructed
     * @throws IOException on error, such as unrecognized encoding
     */
    public static Reader createReader(InputStream in) throws IOException {
        return new XmlReader(in);
    }

    /**
     * Creates a reader supporting the given encoding, mapping
     * from standard encoding names to ones that understood by
     * Java where necessary.
     *
     * @param in       the input stream from which the reader is constructed
     * @param encoding the IETF standard name of the encoding to use;
     *                 if null, auto-detection is used.
     * @throws IOException on error, including unrecognized encoding
     */
    public static Reader createReader(InputStream in, String encoding)
            throws IOException {
        if (encoding == null)
            return new XmlReader(in);
        if ("UTF-8".equalsIgnoreCase(encoding)
                || "UTF8".equalsIgnoreCase(encoding))
            return new Utf8Reader(in);
        if ("US-ASCII".equalsIgnoreCase(encoding)
                || "ASCII".equalsIgnoreCase(encoding))
            return new AsciiReader(in);
        if ("ISO-8859-1".equalsIgnoreCase(encoding)
        // plus numerous aliases ...
        )
            return new Iso8859_1Reader(in);

        //
        // What we really want is an administerable resource mapping
        // encoding names/aliases to classnames.  For example a property
        // file resource, "readers/mapping.props", holding and a set
        // of readers in that (sub)package... defaulting to this call
        // only if no better choice is available.
        //
        return new InputStreamReader(in, std2java(encoding));
    }

    //
    // JDK doesn't know all of the standard encoding names, and
    // in particular none of the EBCDIC ones IANA defines (and
    // which IBM encourages).
    //
    static private final Hashtable charsets = new Hashtable(31);

    static {
        charsets.put("UTF-16", "Unicode");
        charsets.put("ISO-10646-UCS-2", "Unicode");

        // NOTE: no support for ISO-10646-UCS-4 yet.

        charsets.put("EBCDIC-CP-US", "cp037");
        charsets.put("EBCDIC-CP-CA", "cp037");
        charsets.put("EBCDIC-CP-NL", "cp037");
        charsets.put("EBCDIC-CP-WT", "cp037");

        charsets.put("EBCDIC-CP-DK", "cp277");
        charsets.put("EBCDIC-CP-NO", "cp277");
        charsets.put("EBCDIC-CP-FI", "cp278");
        charsets.put("EBCDIC-CP-SE", "cp278");

        charsets.put("EBCDIC-CP-IT", "cp280");
        charsets.put("EBCDIC-CP-ES", "cp284");
        charsets.put("EBCDIC-CP-GB", "cp285");
        charsets.put("EBCDIC-CP-FR", "cp297");

        charsets.put("EBCDIC-CP-AR1", "cp420");
        charsets.put("EBCDIC-CP-HE", "cp424");
        charsets.put("EBCDIC-CP-BE", "cp500");
        charsets.put("EBCDIC-CP-CH", "cp500");

        charsets.put("EBCDIC-CP-ROECE", "cp870");
        charsets.put("EBCDIC-CP-YU", "cp870");
        charsets.put("EBCDIC-CP-IS", "cp871");
        charsets.put("EBCDIC-CP-AR2", "cp918");

        // IANA also defines two that JDK 1.2 doesn't handle:
        //    EBCDIC-CP-GR        --> CP423
        //    EBCDIC-CP-TR        --> CP905
    }

    // returns an encoding name supported by JDK >= 1.1.6
    // for some cases required by the XML spec
    private static String std2java(String encoding) {
        String temp = encoding.toUpperCase(Locale.ENGLISH);
        temp = (String) charsets.get(temp);
        return temp != null ? temp : encoding;
    }

    /**
     * Returns the standard name of the encoding in use
     */
    public String getEncoding() {
        return assignedEncoding;
    }

    private XmlReader(InputStream stream) throws IOException {
        super(stream);

        PushbackInputStream pb;
        byte buf [];
        int len;

        if (stream instanceof PushbackInputStream)
            pb = (PushbackInputStream) stream;
        else
            pb = new PushbackInputStream(stream, MAXPUSHBACK);

        //
        // See if we can figure out the character encoding used
        // in this file by peeking at the first few bytes.
        //
        buf = new byte[4];
        len = pb.read(buf);
        if (len > 0)
            pb.unread(buf, 0, len);

        if (len == 4)
            switch (buf[0] & 0x0ff) {
            case 0:
                // 00 3c 00 3f == illegal UTF-16 big-endian
                if (buf[1] == 0x3c && buf[2] == 0x00 && buf[3] == 0x3f) {
                    setEncoding(pb, "UnicodeBig");
                    return;
                }
                // else it's probably UCS-4
                break;

            case '<':      // 0x3c: the most common cases!
                switch (buf[1] & 0x0ff) {
                // First character is '<'; could be XML without
                // an XML directive such as "<hello>", "<!-- ...",
                // and so on.
                default:
                    break;

                    // 3c 00 3f 00 == illegal UTF-16 little endian
                case 0x00:
                    if (buf[2] == 0x3f && buf[3] == 0x00) {
                        setEncoding(pb, "UnicodeLittle");
                        return;
                    }
                    // else probably UCS-4
                    break;

                    // 3c 3f 78 6d == ASCII and supersets '<?xm'
                case '?':
                    if (buf[2] != 'x' || buf[3] != 'm')
                        break;
                    //
                    // One of several encodings could be used:
                    // Shift-JIS, ASCII, UTF-8, ISO-8859-*, etc
                    //
                    useEncodingDecl(pb, "UTF8");
                    return;
                }
                break;

                // 4c 6f a7 94 ... some EBCDIC code page
            case 0x4c:
                if (buf[1] == 0x6f
                        && (0x0ff & buf[2]) == 0x0a7
                        && (0x0ff & buf[3]) == 0x094) {
                    useEncodingDecl(pb, "CP037");
                    return;
                }
                // whoops, treat as UTF-8
                break;

                // UTF-16 big-endian
            case 0xfe:
                if ((buf[1] & 0x0ff) != 0xff)
                    break;
                setEncoding(pb, "UTF-16");
                return;

                // UTF-16 little-endian
            case 0xff:
                if ((buf[1] & 0x0ff) != 0xfe)
                    break;
                setEncoding(pb, "UTF-16");
                return;

                // default ... no XML declaration
            default:
                break;
            }

        //
        // If all else fails, assume XML without a declaration, and
        // using UTF-8 encoding.
        //
        setEncoding(pb, "UTF-8");
    }

    /*
     * Read the encoding decl on the stream, knowing that it should
     * be readable using the specified encoding (basically, ASCII or
     * EBCDIC).  The body of the document may use a wider range of
     * characters than the XML/Text decl itself, so we switch to use
     * the specified encoding as soon as we can.  (ASCII is a subset
     * of UTF-8, ISO-8859-*, ISO-2022-JP, EUC-JP, and more; EBCDIC
     * has a variety of "code pages" that have these characters as
     * a common subset.)
     */
    private void useEncodingDecl(PushbackInputStream pb, String encoding)
            throws IOException {
        byte buffer [] = new byte[MAXPUSHBACK];
        int len;
        Reader r;
        int c;

        //
        // Buffer up a bunch of input, and set up to read it in
        // the specified encoding ... we can skip the first four
        // bytes since we know that "<?xm" was read to determine
        // what encoding to use!
        //
        len = pb.read(buffer, 0, buffer.length);
        pb.unread(buffer, 0, len);
        r = new InputStreamReader(new ByteArrayInputStream(buffer, 4, len),
                encoding);

        //
        // Next must be "l" (and whitespace) else we conclude
        // error and choose UTF-8.
        //
        if ((r.read()) != 'l') {
            setEncoding(pb, "UTF-8");
            return;
        }

        //
        // Then, we'll skip any
        //     S version="..."     [or single quotes]
        // bit and get any subsequent
        //     S encoding="..."     [or single quotes]
        //
        // We put an arbitrary size limit on how far we read; lots
        // of space will break this algorithm.
        //
        StringBuffer buf = new StringBuffer();
        StringBuffer keyBuf = null;
        String key = null;
        boolean sawEq = false;
        char quoteChar = 0;
        boolean sawQuestion = false;

        XmlDecl:
        for (int i = 0; i < MAXPUSHBACK - 5; ++i) {
            if ((c = r.read()) == -1)
                break;

            // ignore whitespace before/between "key = 'value'"
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r')
                continue;

            // ... but require at least a little!
            if (i == 0)
                break;

            // terminate the loop ASAP
            if (c == '?')
                sawQuestion = true;
            else if (sawQuestion) {
                if (c == '>')
                    break;
                sawQuestion = false;
            }

            // did we get the "key =" bit yet?
            if (key == null || !sawEq) {
                if (keyBuf == null) {
                    if (Character.isWhitespace((char) c))
                        continue;
                    keyBuf = buf;
                    buf.setLength(0);
                    buf.append((char) c);
                    sawEq = false;
                } else if (Character.isWhitespace((char) c)) {
                    key = keyBuf.toString();
                } else if (c == '=') {
                    if (key == null)
                        key = keyBuf.toString();
                    sawEq = true;
                    keyBuf = null;
                    quoteChar = 0;
                } else
                    keyBuf.append((char) c);
                continue;
            }

            // space before quoted value
            if (Character.isWhitespace((char) c))
                continue;
            if (c == '"' || c == '\'') {
                if (quoteChar == 0) {
                    quoteChar = (char) c;
                    buf.setLength(0);
                    continue;
                } else if (c == quoteChar) {
                    if ("encoding".equals(key)) {
                        assignedEncoding = buf.toString();

                        // [81] Encname ::= [A-Za-z] ([A-Za-z0-9._]|'-')*
                        for (i = 0; i < assignedEncoding.length(); i++) {
                            c = assignedEncoding.charAt(i);
                            if ((c >= 'A' && c <= 'Z')
                                    || (c >= 'a' && c <= 'z'))
                                continue;
                            if (i == 0)
                                break XmlDecl;
                            if (i > 0 && (c == '-'
                                    || (c >= '0' && c <= '9')
                                    || c == '.' || c == '_'))
                                continue;
                            // map illegal names to UTF-8 default
                            break XmlDecl;
                        }

                        setEncoding(pb, assignedEncoding);
                        return;

                    } else {
                        key = null;
                        continue;
                    }
                }
            }
            buf.append((char) c);
        }

        setEncoding(pb, "UTF-8");
    }

    private void setEncoding(InputStream stream, String encoding)
            throws IOException {
        assignedEncoding = encoding;
        in = createReader(stream, encoding);
    }

    /**
     * Reads the number of characters read into the buffer, or -1 on EOF.
     */
    public int read(char buf [], int off, int len) throws IOException {
        int val;

        if (closed)
            return -1;        // throw new IOException ("closed");
        val = in.read(buf, off, len);
        if (val == -1)
            close();
        return val;
    }

    /**
     * Reads a single character.
     */
    public int read() throws IOException {
        int val;

        if (closed)
            throw new IOException("closed");
        val = in.read();
        if (val == -1)
            close();
        return val;
    }

    /**
     * Returns true iff the reader supports mark/reset.
     */
    public boolean markSupported() {
        return in == null ? false : in.markSupported();
    }

    /**
     * Sets a mark allowing a limited number of characters to
     * be "peeked", by reading and then resetting.
     *
     * @param value how many characters may be "peeked".
     */
    public void mark(int value) throws IOException {
        if (in != null) in.mark(value);
    }

    /**
     * Resets the current position to the last marked position.
     */
    public void reset() throws IOException {
        if (in != null) in.reset();
    }

    /**
     * Skips a specified number of characters.
     */
    public long skip(long value) throws IOException {
        return in == null ? 0 : in.skip(value);
    }

    /**
     * Returns true iff input characters are known to be ready.
     */
    public boolean ready() throws IOException {
        return in == null ? false : in.ready();
    }

    /**
     * Closes the reader.
     */
    public void close() throws IOException {
        if (closed)
            return;
        in.close();
        in = null;
        closed = true;
    }

    //
    // Delegating to a converter module will always be slower than
    // direct conversion.  Use a similar approach for any other
    // readers that need to be particularly fast; only block I/O
    // speed matters to this package.  For UTF-16, separate readers
    // for big and little endian streams make a difference, too;
    // fewer conditionals in the critical path!
    //
    static abstract class BaseReader extends Reader {
        protected InputStream instream;
        protected byte buffer [];
        protected int start, finish;

        BaseReader(InputStream stream) {
            super(stream);

            instream = stream;
            buffer = new byte[8192];
        }

        public boolean ready() throws IOException {
            return instream == null
                    || (finish - start) > 0
                    || instream.available() != 0;
        }

        // caller shouldn't read again
        public void close() throws IOException {
            if (instream != null) {
                instream.close();
                start = finish = 0;
                buffer = null;
                instream = null;
            }
        }
    }

    //
    // We want this reader, to make the default encoding be as fast
    // as we can make it.  JDK's "UTF8" (not "UTF-8" till JDK 1.2)
    // InputStreamReader works, but 20+% slower speed isn't OK for
    // the default/primary encoding.
    //
    static final class Utf8Reader extends BaseReader {
        // 2nd half of UTF-8 surrogate pair
        private char nextChar;

        Utf8Reader(InputStream stream) {
            super(stream);
        }

        public int read(char buf [], int offset, int len) throws IOException {
            int i = 0, c = 0;

            if (len <= 0)
                return 0;

            // Consume remaining half of any surrogate pair immediately
            if (nextChar != 0) {
                buf[offset + i++] = nextChar;
                nextChar = 0;
            }

            while (i < len) {
                // stop or read data if needed
                if (finish <= start) {
                    if (instream == null) {
                        c = -1;
                        break;
                    }
                    start = 0;
                    finish = instream.read(buffer, 0, buffer.length);
                    if (finish <= 0) {
                        this.close();
                        c = -1;
                        break;
                    }
                }

                //
                // RFC 2279 describes UTF-8; there are six encodings.
                // Each encoding takes a fixed number of characters
                // (1-6 bytes) and is flagged by a bit pattern in the
                // first byte.  The five and six byte-per-character
                // encodings address characters which are disallowed
                // in XML documents, as do some four byte ones.
                //

                //
                // Single byte == ASCII.  Common; optimize.
                //
                c = buffer[start] & 0x0ff;
                if ((c & 0x80) == 0x00) {
                    // 0x0000 <= c <= 0x007f
                    start++;
                    buf[offset + i++] = (char) c;
                    continue;
                }

                //
                // Multibyte chars -- check offsets optimistically,
                // ditto the "10xx xxxx" format for subsequent bytes
                //
                int off = start;

                try {
                    // 2 bytes
                    if ((buffer[off] & 0x0E0) == 0x0C0) {
                        c = (buffer[off++] & 0x1f) << 6;
                        c += buffer[off++] & 0x3f;

                        // 0x0080 <= c <= 0x07ff

                        // 3 bytes
                    } else if ((buffer[off] & 0x0F0) == 0x0E0) {
                        c = (buffer[off++] & 0x0f) << 12;
                        c += (buffer[off++] & 0x3f) << 6;
                        c += buffer[off++] & 0x3f;

                        // 0x0800 <= c <= 0xffff

                        // 4 bytes
                    } else if ((buffer[off] & 0x0f8) == 0x0F0) {
                        c = (buffer[off++] & 0x07) << 18;
                        c += (buffer[off++] & 0x3f) << 12;
                        c += (buffer[off++] & 0x3f) << 6;
                        c += buffer[off++] & 0x3f;

                        // 0x0001 0000  <= c  <= 0x001f ffff

                        // Unicode supports c <= 0x0010 ffff ...
                        if (c > 0x0010ffff)
                            throw new CharConversionException("UTF-8 encoding of character 0x00"
                                    + Integer.toHexString(c)
                                    + " can't be converted to Unicode.");

                        // Convert UCS-4 char to surrogate pair (UTF-16)
                        c -= 0x10000;
                        nextChar = (char) (0xDC00 + (c & 0x03ff));
                        c = 0xD800 + (c >> 10);

                        // 5 and 6 byte versions are XML WF errors, but
                        // typically come from mislabeled encodings
                    } else
                        throw new CharConversionException("Unconvertible UTF-8 character"
                                + " beginning with 0x"
                                + Integer.toHexString(buffer[start] & 0xff));

                } catch (ArrayIndexOutOfBoundsException e) {
                    // off > length && length >= buffer.length
                    c = 0;
                }

                //
                // if the buffer held only a partial character,
                // compact it and try to read the rest of the
                // character.  worst case involves three
                // single-byte reads -- quite rare.
                //
                if (off > finish) {
                    System.arraycopy(buffer, start,
                            buffer, 0, finish - start);
                    finish -= start;
                    start = 0;
                    off = instream.read(buffer, finish,
                            buffer.length - finish);
                    if (off < 0) {
                        this.close();
                        throw new CharConversionException("Partial UTF-8 char");
                    }
                    finish += off;
                    continue;
                }

                //
                // check the format of the non-initial bytes
                //
                for (start++; start < off; start++) {
                    if ((buffer[start] & 0xC0) != 0x80) {
                        this.close();
                        throw new CharConversionException("Malformed UTF-8 char -- "
                                + "is an XML encoding declaration missing?");
                    }
                }

                //
                // If this needed a surrogate pair, consume ASAP
                //
                buf[offset + i++] = (char) c;
                if (nextChar != 0 && i < len) {
                    buf[offset + i++] = nextChar;
                    nextChar = 0;
                }
            }
            if (i > 0)
                return i;
            return (c == -1) ? -1 : 0;
        }
    }

    //
    // We want ASCII and ISO-8859 Readers since they're the most common
    // encodings in the US and Europe, and we don't want performance
    // regressions for them.  They're also easy to implement efficiently,
    // since they're bitmask subsets of UNICODE.
    //
    // XXX haven't benchmarked these readers vs what we get out of JDK.
    //
    static final class AsciiReader extends BaseReader {
        AsciiReader(InputStream in) {
            super(in);
        }

        public int read(char buf [], int offset, int len) throws IOException {
            int i, c;

            if (instream == null)
                return -1;

            for (i = 0; i < len; i++) {
                if (start >= finish) {
                    start = 0;
                    finish = instream.read(buffer, 0, buffer.length);
                    if (finish <= 0) {
                        if (finish <= 0)
                            this.close();
                        break;
                    }
                }
                c = buffer[start++];
                if ((c & 0x80) != 0)
                    throw new CharConversionException("Illegal ASCII character, 0x"
                            + Integer.toHexString(c & 0xff));
                buf[offset + i] = (char) c;
            }
            if (i == 0 && finish <= 0)
                return -1;
            return i;
        }
    }

    static final class Iso8859_1Reader extends BaseReader {
        Iso8859_1Reader(InputStream in) {
            super(in);
        }

        @Override
        public int read(char buf [], int offset, int len) throws IOException {
            int i;

            if (instream == null)
                return -1;

            for (i = 0; i < len; i++) {
                if (start >= finish) {
                    start = 0;
                    finish = instream.read(buffer, 0, buffer.length);
                    if (finish <= 0) {
                        if (finish <= 0)
                            this.close();
                        break;
                    }
                }
                buf[offset + i] = (char) (0x0ff & buffer[start++]);
            }
            if (i == 0 && finish <= 0)
                return -1;
            return i;
        }
    }
}
