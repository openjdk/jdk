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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * The DataSource interface provides the JavaBeans Activation Framework
 * with an abstraction of an arbitrary collection of data.  It
 * provides a type for that data as well as access
 * to it in the form of <code>InputStreams</code> and
 * <code>OutputStreams</code> where appropriate.
 *
 * @since 1.6
 */

public interface DataSource {

    /**
     * This method returns an <code>InputStream</code> representing
     * the data and throws the appropriate exception if it can
     * not do so.  Note that a new <code>InputStream</code> object must be
     * returned each time this method is called, and the stream must be
     * positioned at the beginning of the data.
     *
     * @return an InputStream
     */
    public InputStream getInputStream() throws IOException;

    /**
     * This method returns an <code>OutputStream</code> where the
     * data can be written and throws the appropriate exception if it can
     * not do so.  Note that a new <code>OutputStream</code> object must
     * be returned each time this method is called, and the stream must
     * be positioned at the location the data is to be written.
     *
     * @return an OutputStream
     */
    public OutputStream getOutputStream() throws IOException;

    /**
     * This method returns the MIME type of the data in the form of a
     * string. It should always return a valid type. It is suggested
     * that getContentType return "application/octet-stream" if the
     * DataSource implementation can not determine the data type.
     *
     * @return the MIME Type
     */
    public String getContentType();

    /**
     * Return the <i>name</i> of this object where the name of the object
     * is dependant on the nature of the underlying objects. DataSources
     * encapsulating files may choose to return the filename of the object.
     * (Typically this would be the last component of the filename, not an
     * entire pathname.)
     *
     * @return the name of the object.
     */
    public String getName();
}
