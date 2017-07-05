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
package com.sun.xml.internal.ws.spi.runtime;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;


/**
 * Captures many transports that are used to talk with WS endpoints.
 *
 * For endpoints deployed in light weight http server in J2SE, the implemenation
 * of this class uses HttpTransaction to read from or write to stream.
 *
 * For endpoints deployed in servlet container, the implementation of this
 * class uses HttpServletRequest to read a request, and uses HttpServletResponse
 * to write response.
 *
 * This also works for local transport, JMS transport.
 *
 * Runtime can access to the implementation of this interface using
 * messageInfo.getConnection()
 *
 */

public interface WSConnection {

    public static final int OK=200;
    public static final int ONEWAY=202;
    public static final int UNSUPPORTED_MEDIA=415;
    public static final int MALFORMED_XML=400;
    public static final int INTERNAL_ERR=500;

    /**
     * returns transport headers
     * @return transport headers
     */
    public Map<String,List<String>> getHeaders();

    /**
     * sets transport headers
     */
    public void setHeaders(Map<String,List<String>> headers);

    /**
     * sets the transport status code like <code>OK</code>
     */
    public void setStatus(int status);

    /**
     * @return return the status code
     */
    public int getStatus();

    /**
     * Transport's underlying input stream
     * @return Transport's underlying input stream
     */
    public InputStream getInput();

    /**
     * Closes transport's input stream
     */
    public void closeInput();

    /**
     * Transport's underlying output stream
     * @return Transport's underlying output stream
     */
    public OutputStream getOutput();

    /**
     * Closes transport's output stream
     */
    public void closeOutput();

    public OutputStream getDebug();

    /**
     * Closes transport connection
     */
    public void close();

}
