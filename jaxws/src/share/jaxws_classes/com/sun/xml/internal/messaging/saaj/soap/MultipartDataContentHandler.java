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

package com.sun.xml.internal.messaging.saaj.soap;

import java.io.*;
import java.awt.datatransfer.DataFlavor;
import javax.activation.*;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.MimeMultipart;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.ContentType;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;

public class MultipartDataContentHandler implements DataContentHandler {
    private ActivationDataFlavor myDF = new ActivationDataFlavor(
            com.sun.xml.internal.messaging.saaj.packaging.mime.internet.MimeMultipart.class,
            "multipart/mixed",
            "Multipart");

    /**
     * Return the DataFlavors for this <code>DataContentHandler</code>.
     *
     * @return The DataFlavors
     */
    public DataFlavor[] getTransferDataFlavors() { // throws Exception;
        return new DataFlavor[] { myDF };
    }

    /**
     * Return the Transfer Data of type DataFlavor from InputStream.
     *
     * @param df The DataFlavor
     * @param ins The InputStream corresponding to the data
     * @return String object
     */
    public Object getTransferData(DataFlavor df, DataSource ds) {
        // use myDF.equals to be sure to get ActivationDataFlavor.equals,
        // which properly ignores Content-Type parameters in comparison
        if (myDF.equals(df))
            return getContent(ds);
        else
            return null;
    }

    /**
     * Return the content.
     */
    public Object getContent(DataSource ds) {
        try {
            return new MimeMultipart(
                ds, new ContentType(ds.getContentType()));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Write the object to the output stream, using the specific MIME type.
     */
    public void writeTo(Object obj, String mimeType, OutputStream os)
                        throws IOException {
        if (obj instanceof MimeMultipart) {
            try {
                //TODO: temporarily allow only ByteOutputStream
                // Need to add writeTo(OutputStream) on MimeMultipart
                ByteOutputStream baos = null;
                if (os instanceof ByteOutputStream) {
                    baos = (ByteOutputStream)os;
                } else {
                    throw new IOException("Input Stream expected to be a com.sun.xml.internal.messaging.saaj.util.ByteOutputStream, but found " +
                        os.getClass().getName());
                }
                ((MimeMultipart)obj).writeTo(baos);
            } catch (Exception e) {
                throw new IOException(e.toString());
            }
        }
    }
}
