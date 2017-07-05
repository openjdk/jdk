/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.transport.http.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import com.sun.xml.internal.ws.pept.ept.EPTFactory;
import com.sun.xml.internal.ws.transport.WSConnectionImpl;
import com.sun.net.httpserver.HttpExchange;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * <code>com.sun.xml.internal.ws.spi.runtime.WSConnection</code> used with Java SE endpoints
 *
 * @author WS Development Team
 */
public class ServerConnectionImpl extends WSConnectionImpl {

    private HttpExchange httpExchange;
    private int status;
    private Map<String,List<String>> requestHeaders;
    private Map<String,List<String>> responseHeaders;
    private NoCloseInputStream is;
    private NoCloseOutputStream out;
    private boolean closedInput;
    private boolean closedOutput;

    public ServerConnectionImpl(HttpExchange httpTransaction) {
        this.httpExchange = httpTransaction;
    }

    public Map<String,List<String>> getHeaders() {
        return httpExchange.getRequestHeaders();
    }

    /**
     * sets response headers.
     */
    public void setHeaders(Map<String,List<String>> headers) {
        responseHeaders = headers;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * sets HTTP status code
     */
    public int getStatus() {
        if (status == 0) {
            status = HttpURLConnection.HTTP_INTERNAL_ERROR;
        }
        return status;
    }

    public InputStream getInput() {
        if (is == null) {
            is = new NoCloseInputStream(httpExchange.getRequestBody());
        }
        return is;
    }

    public OutputStream getOutput() {
        if (out == null) {
            try {
                closeInput();
                int len = 0;
                if (responseHeaders != null) {
                    for(Map.Entry <String, List<String>> entry : responseHeaders.entrySet()) {
                        String name = entry.getKey();
                        List<String> values = entry.getValue();
                        if (name.equals("Content-Length")) {
                            // No need to add this header
                            len = Integer.valueOf(values.get(0));
                        } else {
                            for(String value : values) {
                                httpExchange.getResponseHeaders().add(name, value);
                            }
                        }
                    }
                }

                // write HTTP status code, and headers
                httpExchange.sendResponseHeaders(getStatus(), len);
                out = new NoCloseOutputStream(httpExchange.getResponseBody());
            } catch(IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return out;
    }

    public void closeOutput() {
        if (out != null) {
            try {
                out.getOutputStream().close();
                closedOutput = true;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        out = null;
    }

    public void closeInput() {
        if (is != null) {
            try {
                // Read everything from request and close it
                byte[] buf = new byte[1024];
                while (is.read(buf) != -1) {
                }
                is.getInputStream().close();
                closedInput = true;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        is = null;
    }

    public void close() {
        httpExchange.close();
    }

    private static class NoCloseInputStream extends InputStream {
        private InputStream is;

        public NoCloseInputStream(InputStream is) {
            this.is = is;
        }

        @Override
        public int read() throws IOException {
            return is.read();
        }

        @Override
        public void close() throws IOException {
            // Intentionally left empty. use closeInput() to close
        }

        public InputStream getInputStream() {
            return is;
        }

        @Override
        public int read(byte b[]) throws IOException {
            return is.read(b);
        }

        @Override
        public int read(byte b[], int off, int len) throws IOException {
            return is.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return is.skip(n);
        }

        @Override
        public int available() throws IOException {
            return is.available();
        }

        @Override
        public void mark(int readlimit) {
            is.mark(readlimit);
        }


        @Override
        public void reset() throws IOException {
            is.reset();
        }

        @Override
        public boolean markSupported() {
            return is.markSupported();
        }
    }

    private static class NoCloseOutputStream extends OutputStream {
        private OutputStream out;

        public NoCloseOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void write(int ch) throws IOException {
            out.write(ch);
        }

        @Override
        public void close() throws IOException {
            // Intentionally left empty. use closeOutput() to close
        }

        @Override
        public void write(byte b[]) throws IOException {
            out.write(b);
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        public OutputStream getOutputStream() {
            return out;
        }
    }

}
