/*
 * Copyright 2007-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.nio.file.attribute;

import java.util.*;
import java.io.IOException;

/**
 * An object that provides a read-only or updatable <em>view</em> of non-opaque
 * values associated with an object in a filesystem. This interface is extended
 * or implemented by specific attribute views that define the attributes
 * supported by the view. A specific attribute view will typically define
 * type-safe methods to read or update the attributes that it supports. It also
 * provides <em>dynamic access</em> where the {@link #readAttributes
 * readAttributes}, {@link #getAttribute getAttribute} and {@link #setAttribute
 * setAttributs} methods are used to access the attributes by names defined
 * by the attribute view. Implementations must ensure that the attribute names
 * do not contain the colon (':') or comma (',') characters.
 *
 * @since 1.7
 */

public interface AttributeView {
    /**
     * Returns the name of the attribute view.
     */
    String name();

    /**
     * Reads the value of an attribute.
     *
     * @param   attribute
     *          the attribute name (case sensitive)
     *
     * @return  the value of the attribute, or {@code null} if the attribute is
     *          not supported
     *
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          if a security manager is set and it denies access
     */
    Object getAttribute(String attribute) throws IOException;

    /**
     * Sets/updates the value of an attribute.
     *
     * @param   attribute
     *          the attribute name (case sensitive)
     * @param   value
     *          the attribute value
     *
     * @throws  UnsupportedOperationException
     *          if the attribute is not supported or this attribute view does
     *          not support updating the value of the attribute
     * @throws  IllegalArgumentException
     *          if the attribute value is of the correct type but has an
     *          inappropriate value
     * @throws  ClassCastException
     *          if the attribute value is not of the expected type or is a
     *          collection containing elements that are not of the expected
     *          type
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          if a security manager is set and it denies access
     */
    void setAttribute(String attribute, Object value) throws IOException;

    /**
     * Reads all, or a subset, of the attributes supported by this file attribute
     * view.
     *
     * <p> The {@code first} and {@code rest} parameters are the names of the
     * attributes to read. If any of the parameters has the value {@code "*"}
     * then all attributes are read. Attributes that are not supported are
     * ignored and will not be present in the returned map. It is implementation
     * specific if all attributes are read as an atomic operation with respect
     * to other file system operations.
     *
     * @param   first
     *          the name of an attribute to read (case sensitive)
     * @param   rest
     *          the names of other attributes to read (case sensitive)
     *
     * @return  an unmodifiable map of the attributes; may be empty. Its keys are
     *          the attribute names, its values are the attribute values
     *
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          if a security manager is set and it denies access
     */
    Map<String,?> readAttributes(String first, String... rest) throws IOException;
}
