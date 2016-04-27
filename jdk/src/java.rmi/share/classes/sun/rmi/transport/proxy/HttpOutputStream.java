/*
 * Copyright (c) 1996, Oracle and/or its affiliates. All rights reserved.
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
package sun.rmi.transport.proxy;

import java.io.*;

/**
 * The HttpOutputStream class assists the HttpSendSocket and HttpReceiveSocket
 * classes by providing an output stream that buffers its entire input until
 * closed, and then it sends the complete transmission prefixed by the end of
 * an HTTP header that specifies the content length.
 */
class HttpOutputStream extends ByteArrayOutputStream {

    /** the output stream to send response to */
    protected OutputStream out;

    /** true if HTTP response has been sent */
    boolean responseSent = false;

    /**
     * Begin buffering new HTTP response to be sent to a given stream.
     * @param out the OutputStream to send response to
     */
    public HttpOutputStream(OutputStream out) {
        super();
        this.out = out;
    }

    /**
     * On close, send HTTP-packaged response.
     */
    public synchronized void close() throws IOException {
        if (!responseSent) {
            /*
             * If response would have zero content length, then make it
             * have some arbitrary data so that certain clients will not
             * fail because the "document contains no data".
             */
            if (size() == 0)
                write(emptyData);

            DataOutputStream dos = new DataOutputStream(out);
            dos.writeBytes("Content-type: application/octet-stream\r\n");
            dos.writeBytes("Content-length: " + size() + "\r\n");
            dos.writeBytes("\r\n");
            writeTo(dos);
            dos.flush();
            // Do not close the underlying stream here, because that would
            // close the underlying socket and prevent reading a response.
            reset(); // reset byte array
            responseSent = true;
        }
    }

    /** data to send if the response would otherwise be empty */
    private static byte[] emptyData = { 0 };
}
