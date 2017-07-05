/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @(#)MultipartDataSource.java       1.6 02/03/27
 */



package com.sun.xml.internal.messaging.saaj.packaging.mime;

import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.MimeBodyPart;

import javax.activation.DataSource;

/**
 * MultipartDataSource is a <code>DataSource</code> that contains body
 * parts.  This allows "mail aware" <code>DataContentHandlers</code> to
 * be implemented more efficiently by being aware of such
 * <code>DataSources</code> and using the appropriate methods to access
 * <code>BodyParts</code>. <p>
 *
 * Note that the data of a MultipartDataSource is also available as
 * an input stream. <p>
 *
 * This interface will typically be implemented by providers that
 * preparse multipart bodies, for example an IMAP provider.
 *
 * @version     1.6, 02/03/27
 * @author      John Mani
 * @see         javax.activation.DataSource
 */

public interface MultipartDataSource extends DataSource {

    /**
     * Return the number of enclosed MimeBodyPart objects.
     *
     * @return          number of parts
     */
    public int getCount();

    /**
     * Get the specified MimeBodyPart.  Parts are numbered starting at 0.
     *
     * @param index     the index of the desired MimeBodyPart
     * @return          the MimeBodyPart
     * @exception       IndexOutOfBoundsException if the given index
     *                  is out of range.
     * @exception       MessagingException thrown in case of error
     */
    public MimeBodyPart getBodyPart(int index) throws MessagingException;

}
