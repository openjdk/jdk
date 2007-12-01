/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.sql.rowset.spi;

import java.sql.SQLException;

/**
 * Indicates an error with <code>SyncFactory</code> mechanism. A disconnected
 * RowSet implementation cannot be used  without a <code>SyncProvider</code>
 * being successfully instantiated
 *
 * @author Jonathan Bruce
 * @see javax.sql.rowset.spi.SyncFactory
 * @see javax.sql.rowset.spi.SyncFactoryException
 */
public class SyncFactoryException extends java.sql.SQLException {

    /**
     * Creates new <code>SyncFactoryException</code> without detail message.
     */
    public SyncFactoryException() {
    }

    /**
     * Constructs an <code>SyncFactoryException</code> with the specified
     * detail message.
     *
     * @param msg the detail message.
     */
    public SyncFactoryException(String msg) {
        super(msg);
    }

    static final long serialVersionUID = -4354595476433200352L;
}
