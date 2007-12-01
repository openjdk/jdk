/*
 * Copyright 1996-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.awt.datatransfer;

import java.io.IOException;

/**
 * Defines the interface for classes that can be used to provide data
 * for a transfer operation.
 * <p>
 * For information on using data transfer with Swing, see
 * <a href="http://java.sun.com/docs/books/tutorial/uiswing/misc/dnd.html">
 * How to Use Drag and Drop and Data Transfer</a>,
 * a section in <em>The Java Tutorial</em>, for more information.
 *
 * @author      Amy Fowler
 */

public interface Transferable {

    /**
     * Returns an array of DataFlavor objects indicating the flavors the data
     * can be provided in.  The array should be ordered according to preference
     * for providing the data (from most richly descriptive to least descriptive).
     * @return an array of data flavors in which this data can be transferred
     */
    public DataFlavor[] getTransferDataFlavors();

    /**
     * Returns whether or not the specified data flavor is supported for
     * this object.
     * @param flavor the requested flavor for the data
     * @return boolean indicating whether or not the data flavor is supported
     */
    public boolean isDataFlavorSupported(DataFlavor flavor);

    /**
     * Returns an object which represents the data to be transferred.  The class
     * of the object returned is defined by the representation class of the flavor.
     *
     * @param flavor the requested flavor for the data
     * @see DataFlavor#getRepresentationClass
     * @exception IOException                if the data is no longer available
     *              in the requested flavor.
     * @exception UnsupportedFlavorException if the requested data flavor is
     *              not supported.
     */
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException;

}
