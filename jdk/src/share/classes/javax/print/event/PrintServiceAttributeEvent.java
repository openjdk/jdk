/*
 * Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.print.event;

import javax.print.PrintService;
import javax.print.attribute.AttributeSetUtilities;
import javax.print.attribute.PrintServiceAttributeSet;

/**
 *
 * Class PrintServiceAttributeEvent encapsulates an event a
 * Print Service instance reports to let the client know of
 * changes in the print service state.
 */

public class PrintServiceAttributeEvent extends PrintEvent {

    private static final long serialVersionUID = -7565987018140326600L;

    private PrintServiceAttributeSet attributes;

    /**
     * Constructs a PrintServiceAttributeEvent object.
     *
     * @param source the print job generating  this event
     * @param attributes the attribute changes being reported
     * @throws IllegalArgumentException if <code>source</code> is
     *         <code>null</code>.
     */
    public PrintServiceAttributeEvent(PrintService source,
                                      PrintServiceAttributeSet attributes) {

        super(source);
        this.attributes = AttributeSetUtilities.unmodifiableView(attributes);
    }


    /**
     * Returns the print service.

     * @return  Print Service object.
     */
    public PrintService getPrintService() {

        return (PrintService) getSource();
    }


    /**
     * Determine the printing service attributes that changed and their new
     * values.
     *
     * @return  Attributes containing the new values for the service
     * attributes that changed. The returned set may be unmodifiable.
     */
    public PrintServiceAttributeSet getAttributes() {

        return attributes;
    }

}
