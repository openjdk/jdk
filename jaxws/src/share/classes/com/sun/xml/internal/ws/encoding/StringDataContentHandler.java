/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.encoding;

import javax.activation.ActivationDataFlavor;
import javax.activation.DataSource;
import javax.activation.DataContentHandler;
import java.awt.datatransfer.DataFlavor;
import java.io.*;

/**
 * JAF data content handler for text/plain --> String
 *
 * @author Anil Vijendran
 */
public class StringDataContentHandler implements DataContentHandler {

    /**
     * return the DataFlavors for this <code>DataContentHandler</code>
     * @return The DataFlavors.
     */
    public DataFlavor[] getTransferDataFlavors() { // throws Exception;
        DataFlavor flavors[] = new DataFlavor[2];
        flavors[0] =  new ActivationDataFlavor(String.class, "text/plain", "text string");
        flavors[1] = new DataFlavor("text/plain", "Plain Text");
        return flavors;
    }

    /**
     * return the Transfer Data of type DataFlavor from InputStream
     * @param df The DataFlavor.
     * @param ds The InputStream corresponding to the data.
     * @return The constructed Object.
     */
    public Object getTransferData(DataFlavor df, DataSource ds) {

        // this is sort of hacky, but will work for the
        // sake of testing...
        if (df.getMimeType().startsWith("text/plain")) {
            if (df
                .getRepresentationClass()
                .getName()
                .equals("java.lang.String")) {
                // spit out String
                StringBuffer buf = new StringBuffer();
                char data[] = new char[1024];
                // InputStream is = null;
                InputStreamReader isr = null;
                int bytes_read = 0;
                int total_bytes = 0;

                try {
                    isr = new InputStreamReader(ds.getInputStream());

                    while (true) {
                        bytes_read = isr.read(data);
                        if (bytes_read > 0)
                            buf.append(data, 0, bytes_read);
                        else
                            break;
                        total_bytes += bytes_read;
                    }
                } catch (Exception e) {
                }

                return buf.toString();

            } else if (
                df.getRepresentationClass().getName().equals(
                    "java.io.InputStream")) {
                // spit out InputStream
                try {
                    return ds.getInputStream();
                } catch (Exception e) {
                }
            }

        }
        return null;
    }

    /**
     *
     */
    public Object getContent(DataSource ds) { // throws Exception;
        StringBuffer buf = new StringBuffer();
        char data[] = new char[1024];
        // InputStream is = null;
        InputStreamReader isr = null;
        int bytes_read = 0;
        int total_bytes = 0;

        try {
            isr = new InputStreamReader(ds.getInputStream());

            while (true) {
                bytes_read = isr.read(data);
                if (bytes_read > 0)
                    buf.append(data, 0, bytes_read);
                else
                    break;
                total_bytes += bytes_read;
            }
        } catch (Exception e) {
        }

        return buf.toString();
    }
    /**
     * construct an object from a byte stream
     * (similar semantically to previous method, we are deciding
     *  which one to support)
     */
    public void writeTo(Object obj, String mimeType, OutputStream os)
        throws IOException {
        if (!mimeType.startsWith("text/plain"))
            throw new IOException(
                "Invalid type \"" + mimeType + "\" on StringDCH");

        Writer out = new OutputStreamWriter(os);
        out.write((String) obj);
        out.flush();
    }

}
