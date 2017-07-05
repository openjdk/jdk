/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

package javax.print.event;

import javax.print.DocPrintJob;
import javax.print.attribute.AttributeSetUtilities;
import javax.print.attribute.PrintJobAttributeSet;

/**
 * Class PrintJobAttributeEvent encapsulates an event a PrintService
 * reports to let the client know that one or more printing attributes for a
 * PrintJob have changed.
 */

public class PrintJobAttributeEvent extends PrintEvent {

    private static final long serialVersionUID = -6534469883874742101L;

    private PrintJobAttributeSet attributes;

    /**
     * Constructs a PrintJobAttributeEvent object.
     * @param source the print job generating  this event
     * @param attributes the attribute changes being reported
     * @throws IllegalArgumentException if <code>source</code> is
     *         <code>null</code>.
     */
    public PrintJobAttributeEvent (DocPrintJob source,
                                   PrintJobAttributeSet attributes)  {
        super(source);

        this.attributes = AttributeSetUtilities.unmodifiableView(attributes);
    }


    /**
     * Determine the Print Job to which this print job event pertains.
     *
     * @return  Print Job object.
     */
    public DocPrintJob getPrintJob() {

        return (DocPrintJob) getSource();
    }


    /**
     * Determine the printing attributes that changed and their new values.
     *
     * @return  Attributes containing the new values for the print job
     * attributes that changed. The returned set may not be modifiable.
     */
    public PrintJobAttributeSet getAttributes() {

        return attributes;

    }

}
