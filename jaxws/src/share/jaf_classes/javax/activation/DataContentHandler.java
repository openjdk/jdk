/*
 * Copyright (c) 1997, 2005, Oracle and/or its affiliates. All rights reserved.
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

package javax.activation;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.activation.DataSource;

/**
 * The DataContentHandler interface is implemented by objects that can
 * be used to extend the capabilities of the DataHandler's implementation
 * of the Transferable interface. Through <code>DataContentHandlers</code>
 * the framework can be extended to convert streams in to objects, and
 * to write objects to streams. <p>
 *
 * Applications don't generally call the methods in DataContentHandlers
 * directly. Instead, an application calls the equivalent methods in
 * DataHandler. The DataHandler will attempt to find an appropriate
 * DataContentHandler that corresponds to its MIME type using the
 * current DataContentHandlerFactory. The DataHandler then calls
 * through to the methods in the DataContentHandler.
 *
 * @since 1.6
 */

public interface DataContentHandler {
    /**
     * Returns an array of DataFlavor objects indicating the flavors the
     * data can be provided in. The array should be ordered according to
     * preference for providing the data (from most richly descriptive to
     * least descriptive).
     *
     * @return The DataFlavors.
     */
    public DataFlavor[] getTransferDataFlavors();

    /**
     * Returns an object which represents the data to be transferred.
     * The class of the object returned is defined by the representation class
     * of the flavor.
     *
     * @param df The DataFlavor representing the requested type.
     * @param ds The DataSource representing the data to be converted.
     * @return The constructed Object.
     * @exception UnsupportedFlavorException    if the handler doesn't
     *                                          support the requested flavor
     * @exception IOException   if the data can't be accessed
     */
    public Object getTransferData(DataFlavor df, DataSource ds)
                                throws UnsupportedFlavorException, IOException;

    /**
     * Return an object representing the data in its most preferred form.
     * Generally this will be the form described by the first DataFlavor
     * returned by the <code>getTransferDataFlavors</code> method.
     *
     * @param ds The DataSource representing the data to be converted.
     * @return The constructed Object.
     * @exception IOException   if the data can't be accessed
     */
    public Object getContent(DataSource ds) throws IOException;

    /**
     * Convert the object to a byte stream of the specified MIME type
     * and write it to the output stream.
     *
     * @param obj       The object to be converted.
     * @param mimeType  The requested MIME type of the resulting byte stream.
     * @param os        The output stream into which to write the converted
     *                  byte stream.
     * @exception IOException   errors writing to the stream
     */
    public void writeTo(Object obj, String mimeType, OutputStream os)
                                                       throws IOException;
}
