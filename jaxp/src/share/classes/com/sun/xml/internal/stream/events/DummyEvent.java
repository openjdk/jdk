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


import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.namespace.QName;
import java.io.Writer;
import javax.xml.stream.Location;

/** DummyEvent is an abstract class. It provides functionality for most of the
 * function of XMLEvent.
 *
 * @author Neeraj Bajaj Sun Microsystems,Inc.
 * @author K.Venugopal Sun Microsystems,Inc.
 *
 */

public abstract class DummyEvent implements XMLEvent {
    // Make sure that getLocation() never returns null. Instead, return this dummy location
    // that indicates "nowhere" as effectively as possible.
    private static DummyLocation nowhere = new DummyLocation();

    /* Event type this event corresponds to */
    private int fEventType;
    protected Location fLocation = (Location) nowhere;

    public DummyEvent() {
    }

    public DummyEvent(int i) {
        fEventType = i;
    }

    public int getEventType() {
        return fEventType;
    }

    protected void setEventType(int eventType){
        fEventType = eventType;
    }


    public boolean isStartElement() {
        return fEventType == XMLEvent.START_ELEMENT;
    }

    public boolean isEndElement() {
        return fEventType == XMLEvent.END_ELEMENT;
    }

    public boolean isEntityReference() {
        return fEventType == XMLEvent.ENTITY_REFERENCE;
    }

    public boolean isProcessingInstruction() {
        return fEventType == XMLEvent.PROCESSING_INSTRUCTION;
    }

    public boolean isCharacterData() {
        return fEventType == XMLEvent.CHARACTERS;
    }

    public boolean isStartDocument() {
        return fEventType == XMLEvent.START_DOCUMENT;
    }

    public boolean isEndDocument() {
        return fEventType == XMLEvent.END_DOCUMENT;
    }

    public Location getLocation(){
        return fLocation;
    }

    void setLocation(Location loc){
        if (loc == null) {
            fLocation = nowhere;
        } else {
            fLocation = loc;
        }
    }

    /** Returns this event as Characters, may result in
     * a class cast exception if this event is not Characters.
     */
    public Characters asCharacters() {
        return (Characters)this;
    }

    /** Returns this event as an end  element event, may result in
     * a class cast exception if this event is not a end element.
     */
    public EndElement asEndElement() {
        return (EndElement)this;
    }

    /** Returns this event as a start element event, may result in
     * a class cast exception if this event is not a start element.
     */
    public StartElement asStartElement() {
        return (StartElement)this;
    }

    /** This method is provided for implementations to provide
     * optional type information about the associated event.
     * It is optional and will return null if no information
     * is available.
     */
    public QName getSchemaType() {
        //Base class will take care of providing extra information about this event.
        return null;
    }

    /** A utility function to check if this event is an Attribute.
     * @see Attribute
     */
    public boolean isAttribute() {
        return fEventType == XMLEvent.ATTRIBUTE;
    }

    /** A utility function to check if this event is Characters.
     * @see Characters
     */
    public boolean isCharacters() {
        return fEventType == XMLEvent.CHARACTERS;
    }

    /** A utility function to check if this event is a Namespace.
     * @see Namespace
     */
    public boolean isNamespace() {
        return fEventType == XMLEvent.NAMESPACE;
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
    public void writeAsEncodedUnicode(Writer writer) throws javax.xml.stream.XMLStreamException {
    }

    static class DummyLocation implements Location {
        public DummyLocation() {
        }

        public int getCharacterOffset() {
            return -1;
        }

        public int getColumnNumber() {
            return -1;
        }

        public int getLineNumber() {
            return -1;
        }

        public String getPublicId() {
            return null;
        }

        public String getSystemId() {
            return null;
        }
    }
}
