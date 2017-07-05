/*
 * Copyright (c) 1997, 1999, Oracle and/or its affiliates. All rights reserved.
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
package org.omg.CORBA.portable;

import org.omg.CORBA.TypeCode;

/**
 * The base class for the Holder classess of all complex
 * IDL types. The ORB treats all generated Holders as Streamable to invoke
 * the methods for marshalling and unmarshalling.
 *
 * @since   JDK1.2
 */

public interface Streamable {
    /**
     * Reads data from <code>istream</code> and initalizes the
     * <code>value</code> field of the Holder with the unmarshalled data.
     *
     * @param     istream   the InputStream that represents the CDR data from the wire.
     */
    void _read(InputStream istream);
    /**
     * Marshals to <code>ostream</code> the value in the
     * <code>value</code> field of the Holder.
     *
     * @param     ostream   the CDR OutputStream
     */
    void _write(OutputStream ostream);

    /**
     * Retrieves the <code>TypeCode</code> object corresponding to the value
     * in the <code>value</code> field of the Holder.
     *
     * @return    the <code>TypeCode</code> object for the value held in the holder
     */
    TypeCode _type();
}
