/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.stream.events ;

import java.io.Writer;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.ProcessingInstruction;

/** Implements Processing Instruction Event
 *
 *@author Neeraj Bajaj, Sun Microsystems.
 *
 */


public class ProcessingInstructionEvent extends DummyEvent
implements ProcessingInstruction {

    /** Processing Instruction Name */
    private String fName;
    /** Processsing instruction content */
    private String fContent;

    public ProcessingInstructionEvent() {
        init();
    }

    public ProcessingInstructionEvent(String targetName, String data) {
        this(targetName,data,null);
    }

    public ProcessingInstructionEvent(String targetName, String data,Location loc) {
        init();
        this.fName = targetName;
        fContent = data;
        setLocation(loc);
    }

    protected void init() {
        setEventType(XMLStreamConstants.PROCESSING_INSTRUCTION);
    }

    public String getTarget() {
        return fName;
    }

    public void setTarget(String targetName) {
        fName = targetName;
    }

    public void setData(String data) {
        fContent = data;
    }

    public String getData() {
        return fContent;
    }

    public String toString() {
        if(fContent != null && fName != null)
            return "<?" + fName + fContent + "?>";
        if(fName != null)
            return "<?" + fName + "?>";
        if(fContent != null)
            return "<?" + fContent + "?>";
        else
            return "<??>";
    }

    /** This method will write the XMLEvent as per the XML 1.0 specification as Unicode characters.
     * No indentation or whitespace should be outputted.
     *
     * Any user defined event type SHALL have this method
     * called when being written to on an output stream.
     * Built in Event types MUST implement this method,
     * but implementations MAY choose not call these methods
     * for optimizations reasons when writing out built in
     * Events to an output stream.
     * The output generated MUST be equivalent in terms of the
     * infoset expressed.
     *
     * @param writer The writer that will output the data
     * @throws XMLStreamException if there is a fatal error writing the event
     */
    public void writeAsEncodedUnicode(Writer writer) throws XMLStreamException {
    }

}
