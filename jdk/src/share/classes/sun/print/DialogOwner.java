/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.print;

import javax.print.attribute.PrintRequestAttribute;
import java.awt.Frame;

/**
 * Class DialogOwner is a printing attribute class that identifies
 * the window that owns the print dialog.
 *
 * <P>
 * <B>IPP Compatibility:</B> This is not an IPP attribute.
 * <P>
 *
 */
@SuppressWarnings("serial") // JDK-implementation class
public final class DialogOwner
    implements PrintRequestAttribute {

    private Frame dlgOwner;

    /**
     * Construct a new dialog type selection enumeration value with the
     * given integer value.
     *
     * @param  value  Integer value.
     */
    public DialogOwner(Frame frame) {
        dlgOwner = frame;
    }


    /**
     * Returns the string table for class DialogOwner.
     */
    public Frame getOwner() {
        return dlgOwner;
    }


    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class DialogOwner the category is class
     * DialogOwner itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class getCategory() {
        return DialogOwner.class;
    }


    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class DialogOwner the category name is
     * <CODE>"dialog-owner"</CODE>.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "dialog-owner";
    }

}
