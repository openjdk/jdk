/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.net.httpserver;

import java.util.*;
import java.nio.*;
import java.net.*;
import java.io.*;
import java.nio.channels.*;
import com.sun.net.httpserver.*;
import com.sun.net.httpserver.spi.*;

/**
 */
class Request {

    final static int BUF_LEN = 2048;
    final static byte CR = 13;
    final static byte LF = 10;

    private String startLine;
    private SocketChannel chan;
    private InputStream is;
    private OutputStream os;

    Request (InputStream rawInputStream, OutputStream rawout) throws IOException {
        this.chan = chan;
        is = rawInputStream;
        os = rawout;
        do {
            startLine = readLine();
            /* skip blank lines */
        } while (startLine == null ? false : startLine.equals (""));
    }


    char[] buf = new char [BUF_LEN];
    int pos;
    StringBuffer lineBuf;

    public InputStream inputStream () {
        return is;
    }

    public OutputStream outputStream () {
        return os;
    }

    /**
     * read a line from the stream returning as a String.
     * Not used for reading headers.
     */

    public String readLine () throws IOException {
        boolean gotCR = false, gotLF = false;
        pos = 0; lineBuf = new StringBuffer();
        while (!gotLF) {
            int c = is.read();
            if (c == -1) {
                return null;
            }
            if (gotCR) {
                if (c == LF) {
                    gotLF = true;
                } else {
                    gotCR = false;
                    consume (CR);
                    consume (c);
                }
            } else {
                if (c == CR) {
                    gotCR = true;
                } else {
                    consume (c);
                }
            }
        }
        lineBuf.append (buf, 0, pos);
        return new String (lineBuf);
    }

    private void consume (int c) {
        if (pos == BUF_LEN) {
            lineBuf.append (buf);
            pos = 0;
        }
        buf[pos++] = (char)c;
    }

    /**
     * returns the request line (first line of a request)
     */
    public String requestLine () {
        return startLine;
    }

    Headers hdrs = null;

    Headers headers () throws IOException {
        if (hdrs != null) {
            return hdrs;
        }
        hdrs = new Headers();

        char s[] = new char[10];
        int firstc = is.read();
        while (firstc != LF && firstc != CR && firstc >= 0) {
            int len = 0;
            int keyend = -1;
            int c;
            boolean inKey = firstc > ' ';
            s[len++] = (char) firstc;
    parseloop:{
                while ((c = is.read()) >= 0) {
                    switch (c) {
                      case ':':
                        if (inKey && len > 0)
                            keyend = len;
                        inKey = false;
                        break;
                      case '\t':
                        c = ' ';
                      case ' ':
                        inKey = false;
                        break;
                      case CR:
                      case LF:
                        firstc = is.read();
                        if (c == CR && firstc == LF) {
                            firstc = is.read();
                            if (firstc == CR)
                                firstc = is.read();
                        }
                        if (firstc == LF || firstc == CR || firstc > ' ')
                            break parseloop;
                        /* continuation */
                        c = ' ';
                        break;
                    }
                    if (len >= s.length) {
                        char ns[] = new char[s.length * 2];
                        System.arraycopy(s, 0, ns, 0, len);
                        s = ns;
                    }
                    s[len++] = (char) c;
                }
                firstc = -1;
            }
            while (len > 0 && s[len - 1] <= ' ')
                len--;
            String k;
            if (keyend <= 0) {
                k = null;
                keyend = 0;
            } else {
                k = String.copyValueOf(s, 0, keyend);
                if (keyend < len && s[keyend] == ':')
                    keyend++;
                while (keyend < len && s[keyend] <= ' ')
                    keyend++;
            }
            String v;
            if (keyend >= len)
                v = new String();
            else
                v = String.copyValueOf(s, keyend, len - keyend);
            hdrs.add (k,v);
        }
        return hdrs;
    }

    /**
     * Implements blocking reading semantics on top of a non-blocking channel
     */

    static class ReadStream extends InputStream {
        SocketChannel channel;
        SelectorCache sc;
        Selector selector;
        ByteBuffer chanbuf;
        SelectionKey key;
        int available;
        byte[] one;
        boolean closed = false, eof = false;
        ByteBuffer markBuf; /* reads may be satisifed from this buffer */
        boolean marked;
        boolean reset;
        int readlimit;
        static long readTimeout;
        ServerImpl server;

        static {
            readTimeout = ServerConfig.getReadTimeout();
        }

        public ReadStream (ServerImpl server, SocketChannel chan) throws IOException {
            this.channel = chan;
            this.server = server;
            sc = SelectorCache.getSelectorCache();
            selector = sc.getSelector();
            chanbuf = ByteBuffer.allocate (8* 1024);
            key = chan.register (selector, SelectionKey.OP_READ);
            available = 0;
            one = new byte[1];
            closed = marked = reset = false;
        }

        public synchronized int read (byte[] b) throws IOException {
            return read (b, 0, b.length);
        }

        public synchronized int read () throws IOException {
            int result = read (one, 0, 1);
            if (result == 1) {
                return one[0] & 0xFF;
            } else {
                return -1;
            }
        }

        public synchronized int read (byte[] b, int off, int srclen) throws IOException {

            int canreturn, willreturn;

            if (closed)
                throw new IOException ("Stream closed");

            if (eof) {
                return -1;
            }

            if (reset) { /* satisfy from markBuf */
                canreturn = markBuf.remaining ();
                willreturn = canreturn>srclen ? srclen : canreturn;
                markBuf.get(b, off, willreturn);
                if (canreturn == willreturn) {
                    reset = false;
                }
            } else { /* satisfy from channel */
                canreturn = available();
                while (canreturn == 0 && !eof) {
                    block ();
                    canreturn = available();
                }
                if (eof) {
                    return -1;
                }
                willreturn = canreturn>srclen ? srclen : canreturn;
                chanbuf.get(b, off, willreturn);
                available -= willreturn;

                if (marked) { /* copy into markBuf */
                    try {
                        markBuf.put (b, off, willreturn);
                    } catch (BufferOverflowException e) {
                        marked = false;
                    }
                }
            }
            return willreturn;
        }

        public synchronized int available () throws IOException {
            if (closed)
                throw new IOException ("Stream is closed");

            if (eof)
                return -1;

            if (reset)
                return markBuf.remaining();

            if (available > 0)
                return available;

            chanbuf.clear ();
            available = channel.read (chanbuf);
            if (available > 0) {
                chanbuf.flip();
            } else if (available == -1) {
                eof = true;
                available = 0;
            }
            return available;
        }

        /**
         * block() only called when available==0 and buf is empty
         */
        private synchronized void block () throws IOException {
            long currtime = server.getTime();
            long maxtime = currtime + readTimeout;

            while (currtime < maxtime) {
                if (selector.select (readTimeout) == 1) {
                    selector.selectedKeys().clear();
                    available ();
                    return;
                }
                currtime = server.getTime();
            }
            throw new SocketTimeoutException ("no data received");
        }

        public void close () throws IOException {
            if (closed) {
                return;
            }
            channel.close ();
            selector.selectNow();
            sc.freeSelector(selector);
            closed = true;
        }

        public synchronized void mark (int readlimit) {
            if (closed)
                return;
            this.readlimit = readlimit;
            markBuf = ByteBuffer.allocate (readlimit);
            marked = true;
            reset = false;
        }

        public synchronized void reset () throws IOException {
            if (closed )
                return;
            if (!marked)
                throw new IOException ("Stream not marked");
            marked = false;
            reset = true;
            markBuf.flip ();
        }
    }

    static class WriteStream extends java.io.OutputStream {
        SocketChannel channel;
        ByteBuffer buf;
        SelectionKey key;
        SelectorCache sc;
        Selector selector;
        boolean closed;
        byte[] one;
        ServerImpl server;
        static long writeTimeout;

        static {
            writeTimeout = ServerConfig.getWriteTimeout();
        }

        public WriteStream (ServerImpl server, SocketChannel channel) throws IOException {
            this.channel = channel;
            this.server = server;
            sc = SelectorCache.getSelectorCache();
            selector = sc.getSelector();
            key = channel.register (selector, SelectionKey.OP_WRITE);
            closed = false;
            one = new byte [1];
            buf = ByteBuffer.allocate (4096);
        }

        public synchronized void write (int b) throws IOException {
            one[0] = (byte)b;
            write (one, 0, 1);
        }

        public synchronized void write (byte[] b) throws IOException {
            write (b, 0, b.length);
        }

        public synchronized void write (byte[] b, int off, int len) throws IOException {
            int l = len;
            if (closed)
                throw new IOException ("stream is closed");

            int cap = buf.capacity();
            if (cap < len) {
                int diff = len - cap;
                buf = ByteBuffer.allocate (2*(cap+diff));
            }
            buf.clear();
            buf.put (b, off, len);
            buf.flip ();
            int n;
            while ((n = channel.write (buf)) < l) {
                l -= n;
                if (l == 0)
                    return;
                block();
            }
        }

        void block () throws IOException {
            long currtime = server.getTime();
            long maxtime = currtime + writeTimeout;

            while (currtime < maxtime) {
                if (selector.select (writeTimeout) == 1) {
                    selector.selectedKeys().clear ();
                    return;
                }
                currtime = server.getTime();
            }
            throw new SocketTimeoutException ("write blocked too long");
        }


        public void close () throws IOException {
            if (closed)
                return;
            channel.close ();
            selector.selectNow();
            sc.freeSelector(selector);
            closed = true;
        }
    }
}
