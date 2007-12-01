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

package com.sun.xml.internal.ws.transport;

import com.sun.xml.internal.ws.pept.ept.EPTFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import com.sun.xml.internal.ws.client.ClientTransportException;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;

/**
 * Abstract class for WSConnection. All client-side and server-side
 * transports should extend this class and override appropriate methods.
 *
 * @author WS Development Team
 */
public abstract class WSConnectionImpl implements WSConnection {
    Map<String, List<String>> headers = null;
    public OutputStream debugStream = null;
    public OutputStream outputStream = null;
    public InputStream inputStream = null;
    int statusCode;

    /** Creates a new instance of WSConnectionImpl */
    public WSConnectionImpl () {
    }

    public int getStatus () {
        return statusCode;
    }

    public void setStatus (int statusCode) {
        this.statusCode = statusCode;
    }

    public OutputStream getDebug () {
        return debugStream;
    }

    /**
     * @return outputStream
     *
     * Returns the OutputStream on which the outbound message is written.
     * Any stream or connection initialization, pre-processing is done here.
     */
    public OutputStream getOutput() {
        return outputStream;
    }

    /**
     * @return inputStream
     *
     * Returns the InputStream on which the inbound message is received.
     * Any post-processing of message is done here.
     */
    public InputStream getInput() {
        return inputStream;
    }

    public Map<String, List<String>> getHeaders () {
        return headers;
    }

    public void setHeaders (Map<String, List<String>> headers) {
        this.headers = headers;
    }

    /**
     * Write connection headers in HTTP syntax using \r\n as a
     * separator.
     */
    public void writeHeaders(OutputStream os) {
        try {
            byte[] newLine = "\r\n".getBytes("us-ascii");

            // Write all headers ala HTTP (only first list entry serialized)
            Map<String, List<String>> headers = getHeaders();
            for (String header : headers.keySet()) {
                os.write((header + ":" +
                    headers.get(header).get(0)).getBytes("us-ascii"));
                os.write(newLine);
            }

            // Write empty line as in HTTP
            os.write(newLine);
        }
        catch (Exception ex) {
            throw new ClientTransportException("local.client.failed",ex);
        }
    }

    /**
     * Read and consume connection headers in HTTP syntax using
     * \r\n as a separator.
     */
    public void readHeaders(InputStream is) {
        try {
            int c1, c2;
            StringBuffer line = new StringBuffer();

            if (headers == null) {
                headers = new HashMap<String, List<String>>();
            }
            else {
                headers.clear();
            }

            // Read headers until finding a \r\n line
            while ((c1 = is.read()) != -1) {
                if (c1 == '\r') {
                    c2 = is.read();
                    assert c2 != -1;

                    if (c2 == '\n') {
                        String s = line.toString();
                        if (s.length() == 0) {
                            break;  // found \r\n line
                        }
                        else {
                            int k  = s.indexOf(':');
                            assert k > 0;
                            ArrayList<String> value = new ArrayList<String>();
                            value.add(s.substring(k + 1));
                            headers.put(s.substring(0, k), value);
                            line.setLength(0);      // clear line buffer
                        }
                    }
                    else {
                        line.append((char) c1).append((char) c2);
                    }
                }
                else {
                    line.append((char) c1);
                }
            }
        }
        catch (Exception ex) {
            throw new ClientTransportException("local.client.failed",ex);
        }
    }

    public void closeOutput() {
        try {
            if (outputStream != null) {
                outputStream.flush();
                outputStream.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void closeInput() {
    }

    public void close() {

    }
}
