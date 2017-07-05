/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.io.*;
import java.nio.*;
import java.nio.charset.*;

/**
 * An object used for sending Content to the requestor.
 *
 * @author Mark Reinhold
 * @author Brad R. Wetmore
 */
class Reply implements Sendable {

    /**
     * A helper class which define the HTTP response codes
     */
    static class Code {

        private int number;
        private String reason;
        private Code(int i, String r) { number = i; reason = r; }
        public String toString() { return number + " " + reason; }

        static Code OK = new Code(200, "OK");
        static Code BAD_REQUEST = new Code(400, "Bad Request");
        static Code NOT_FOUND = new Code(404, "Not Found");
        static Code METHOD_NOT_ALLOWED = new Code(405, "Method Not Allowed");

    }

    private Code code;
    private Content content;
    private boolean headersOnly;

    Reply(Code rc, Content c) {
        this(rc, c, null);
    }

    Reply(Code rc, Content c, Request.Action head) {
        code = rc;
        content = c;
        headersOnly = (head == Request.Action.HEAD);
    }

    private static String CRLF = "\r\n";
    private static Charset ascii = Charset.forName("US-ASCII");

    private ByteBuffer hbb = null;

    private ByteBuffer headers() {
        CharBuffer cb = CharBuffer.allocate(1024);
        for (;;) {
            try {
                cb.put("HTTP/1.0 ").put(code.toString()).put(CRLF);
                cb.put("Server: niossl/0.1").put(CRLF);
                cb.put("Content-type: ").put(content.type()).put(CRLF);
                cb.put("Content-length: ")
                    .put(Long.toString(content.length())).put(CRLF);
                cb.put(CRLF);
                break;
            } catch (BufferOverflowException x) {
                assert(cb.capacity() < (1 << 16));
                cb = CharBuffer.allocate(cb.capacity() * 2);
                continue;
            }
        }
        cb.flip();
        return ascii.encode(cb);
    }

    public void prepare() throws IOException {
        content.prepare();
        hbb = headers();
    }

    public boolean send(ChannelIO cio) throws IOException {

        if (hbb == null)
            throw new IllegalStateException();

        if (hbb.hasRemaining()) {
            if (cio.write(hbb) <= 0)
                return true;
        }

        if (!headersOnly) {
            if (content.send(cio))
                return true;
        }

        if (!cio.dataFlush())
            return true;

        return false;
    }

    public void release() throws IOException {
        content.release();
    }
}
