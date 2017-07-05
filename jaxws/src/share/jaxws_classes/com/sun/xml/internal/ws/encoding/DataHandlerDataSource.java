/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.encoding;

import javax.activation.DataSource;
import javax.activation.DataHandler;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * {@link DataSource} impl using a DataHandler
 *
 * @author Jitendra Kotamraju
 */
public class DataHandlerDataSource implements DataSource {
    private final DataHandler dataHandler;

    public DataHandlerDataSource(DataHandler dh) {
        this.dataHandler = dh;
    }

    /**
     * Returns an <code>InputStream</code> representing this object.
     *
     * @return the <code>InputStream</code>
     */
    public InputStream getInputStream() throws IOException {
        return dataHandler.getInputStream();
    }

    /**
     * Returns the <code>OutputStream</code> for this object.
     *
     * @return the <code>OutputStream</code>
     */
    public OutputStream getOutputStream() throws IOException {
        return dataHandler.getOutputStream();
    }

    /**
     * Returns the MIME type of the data represented by this object.
     *
     * @return the MIME type
     */
    public String getContentType() {
        return dataHandler.getContentType();
    }

    /**
     * Returns the name of this object.
     *
     * @return the name of this object
     */
    public String getName() {
        return dataHandler.getName();
    }
}
