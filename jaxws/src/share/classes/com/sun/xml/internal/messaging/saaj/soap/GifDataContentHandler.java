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


package com.sun.xml.internal.messaging.saaj.soap;

import java.awt.datatransfer.DataFlavor;
import java.io.*;
import java.awt.*;

import javax.activation.*;

/**
 * DataContentHandler for image/gif.
 *
 * @author Ana Lindstrom-Tamer
 */
public class GifDataContentHandler extends Component implements DataContentHandler {
    private static ActivationDataFlavor myDF =
        new ActivationDataFlavor(
            java.awt.Image.class,
            "image/gif",
            "GIF Image");

    protected ActivationDataFlavor getDF() {
        return myDF;
    }

    /**
     * Return the DataFlavors for this <code>DataContentHandler</code>.
     *
     * @return The DataFlavors
     */
    public DataFlavor[] getTransferDataFlavors() { // throws Exception;
        return new DataFlavor[] { getDF()};
    }

    /**
     * Return the Transfer Data of type DataFlavor from InputStream.
     *
     * @param df The DataFlavor
     * @param ins The InputStream corresponding to the data
     * @return String object
     */
    public Object getTransferData(DataFlavor df, DataSource ds)
        throws IOException {
        // use myDF.equals to be sure to get ActivationDataFlavor.equals,
        // which properly ignores Content-Type parameters in comparison
        if (getDF().equals(df))
            return getContent(ds);
        else
            return null;
    }

    public Object getContent(DataSource ds) throws IOException {
        InputStream is = ds.getInputStream();
        int pos = 0;
        int count;
        byte buf[] = new byte[1024];

        while ((count = is.read(buf, pos, buf.length - pos)) != -1) {
            pos += count;
            if (pos >= buf.length) {
                int size = buf.length;
                if (size < 256*1024)
                    size += size;
                else
                    size += 256*1024;
                byte tbuf[] = new byte[size];
                System.arraycopy(buf, 0, tbuf, 0, pos);
                buf = tbuf;
            }
        }
        Toolkit tk = Toolkit.getDefaultToolkit();
        return tk.createImage(buf, 0, pos);
    }

    /**
     * Write the object to the output stream, using the specified MIME type.
     */
    public void writeTo(Object obj, String type, OutputStream os)
                        throws IOException {
        if (obj != null && !(obj instanceof Image))
            throw new IOException("\"" + getDF().getMimeType() +
                "\" DataContentHandler requires Image object, " +
                "was given object of type " + obj.getClass().toString());

        throw new IOException(getDF().getMimeType() + " encoding not supported");
    }


}
