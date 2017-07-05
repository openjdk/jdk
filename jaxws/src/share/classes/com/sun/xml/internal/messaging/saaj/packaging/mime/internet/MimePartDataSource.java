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
/*
 * @(#)MimePartDataSource.java        1.9 02/03/27
 */



package com.sun.xml.internal.messaging.saaj.packaging.mime.internet;

import java.io.*;
import java.net.UnknownServiceException;

import javax.activation.DataSource;

import com.sun.xml.internal.messaging.saaj.packaging.mime.MessagingException;

/**
 * A utility class that implements a DataSource out of
 * a MimeBodyPart. This class is primarily meant for service providers.
 *
 * @author      John Mani
 */

public final class MimePartDataSource implements DataSource {
    private final MimeBodyPart part;

    /**
     * Constructor, that constructs a DataSource from a MimeBodyPart.
     */
    public MimePartDataSource(MimeBodyPart part) {
        this.part = part;
    }

    /**
     * Returns an input stream from this  MimeBodyPart. <p>
     *
     * This method applies the appropriate transfer-decoding, based
     * on the Content-Transfer-Encoding attribute of this MimeBodyPart.
     * Thus the returned input stream is a decoded stream of bytes.<p>
     *
     * This implementation obtains the raw content from the MimeBodyPart
     * using the <code>getContentStream()</code> method and decodes
     * it using the <code>MimeUtility.decode()</code> method.
     *
     * @return  decoded input stream
     */
    public InputStream getInputStream() throws IOException {

        try {
        InputStream is = part.getContentStream();

            String encoding = part.getEncoding();
            if (encoding != null)
                return MimeUtility.decode(is, encoding);
            else
                return is;
        } catch (MessagingException mex) {
            throw new IOException(mex.getMessage());
        }
    }

    /**
     * DataSource method to return an output stream. <p>
     *
     * This implementation throws the UnknownServiceException.
     */
    public OutputStream getOutputStream() throws IOException {
        throw new UnknownServiceException();
    }

    /**
     * Returns the content-type of this DataSource. <p>
     *
     * This implementation just invokes the <code>getContentType</code>
     * method on the MimeBodyPart.
     */
    public String getContentType() {
        return part.getContentType();
    }

    /**
     * DataSource method to return a name.  <p>
     *
     * This implementation just returns an empty string.
     */
    public String getName() {
        try {
                return part.getFileName();
        } catch (MessagingException mex) {
        return "";
        }
    }
}
