/*
 * Copyright 1998-1999 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.awt.print;

/**
 * The <code>PrinterGraphics</code> interface is implemented by
 * {@link java.awt.Graphics} objects that are passed to
 * {@link Printable} objects to render a page. It allows an
 * application to find the {@link PrinterJob} object that is
 * controlling the printing.
 */

public interface PrinterGraphics {

    /**
     * Returns the <code>PrinterJob</code> that is controlling the
     * current rendering request.
     * @return the <code>PrinterJob</code> controlling the current
     * rendering request.
     * @see java.awt.print.Printable
     */
    PrinterJob getPrinterJob();

}
