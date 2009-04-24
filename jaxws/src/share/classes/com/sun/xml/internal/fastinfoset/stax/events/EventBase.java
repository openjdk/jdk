/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */



package com.sun.xml.internal.fastinfoset.stax.events ;

import javax.xml.stream.Location;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.namespace.QName;
import java.io.Writer;
import com.sun.xml.internal.fastinfoset.CommonResourceBundle;


public abstract class EventBase implements XMLEvent {

    /* Event type this event corresponds to */
    protected int _eventType;
    protected Location _location = null;

    public EventBase() {

    }

    public EventBase(int eventType) {
        _eventType = eventType;
    }

    /**
    * Returns an integer code for this event.
    */
    public int getEventType() {
        return _eventType;
    }

    protected void setEventType(int eventType){
        _eventType = eventType;
    }


    public boolean isStartElement() {
        return _eventType == START_ELEMENT;
    }

    public boolean isEndElement() {
        return _eventType == END_ELEMENT;
    }

    public boolean isEntityReference() {
        return _eventType == ENTITY_REFERENCE;
    }

    public boolean isProcessingInstruction() {
        return _eventType == PROCESSING_INSTRUCTION;
    }

    public boolean isStartDocument() {
        return _eventType == START_DOCUMENT;
    }

    public boolean isEndDocument() {
        return _eventType == END_DOCUMENT;
    }

  /**
   * Return the location of this event.  The Location
   * returned from this method is non-volatile and
   * will retain its information.
   * @see javax.xml.stream.Location
   */
    public Location getLocation(){
        return _location;
    }

    public void setLocation(Location loc){
        _location = loc;
    }
    public String getSystemId() {
        if(_location == null )
            return "";
        else
            return _location.getSystemId();
    }

    /** Returns this event as Characters, may result in
     * a class cast exception if this event is not Characters.
     */
    public Characters asCharacters() {
        if (isCharacters()) {
            return (Characters)this;
        } else
            throw new ClassCastException(CommonResourceBundle.getInstance().getString("message.charactersCast", new Object[]{getEventTypeString()}));
    }

    /** Returns this event as an end  element event, may result in
     * a class cast exception if this event is not a end element.
     */
    public EndElement asEndElement() {
        if (isEndElement()) {
            return (EndElement)this;
        } else
            throw new ClassCastException(CommonResourceBundle.getInstance().getString("message.endElementCase", new Object[]{getEventTypeString()}));
    }

  /**
   * Returns this event as a start element event, may result in
   * a class cast exception if this event is not a start element.
   */
    public StartElement asStartElement() {
        if (isStartElement()) {
            return (StartElement)this;
        } else
            throw new ClassCastException(CommonResourceBundle.getInstance().getString("message.startElementCase", new Object[]{getEventTypeString()}));
    }

    /**
    * This method is provided for implementations to provide
    * optional type information about the associated event.
    * It is optional and will return null if no information
    * is available.
    */
    public QName getSchemaType() {
        return null;
    }

    /** A utility function to check if this event is an Attribute.
     * @see javax.xml.stream.events.Attribute
     */
    public boolean isAttribute() {
        return _eventType == ATTRIBUTE;
    }

    /** A utility function to check if this event is Characters.
     * @see javax.xml.stream.events.Characters
     */
    public boolean isCharacters() {
        return _eventType == CHARACTERS;
    }

    /** A utility function to check if this event is a Namespace.
     * @see javax.xml.stream.events.Namespace
     */
    public boolean isNamespace() {
        return _eventType == NAMESPACE;
    }


    /**
    * This method will write the XMLEvent as per the XML 1.0 specification as Unicode characters.
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

    private String getEventTypeString() {
        switch (_eventType){
            case START_ELEMENT:
                return "StartElementEvent";
            case END_ELEMENT:
                return "EndElementEvent";
            case PROCESSING_INSTRUCTION:
                return "ProcessingInstructionEvent";
            case CHARACTERS:
                return "CharacterEvent";
            case COMMENT:
                return "CommentEvent";
            case START_DOCUMENT:
                return "StartDocumentEvent";
            case END_DOCUMENT:
                return "EndDocumentEvent";
            case ENTITY_REFERENCE:
                return "EntityReferenceEvent";
            case ATTRIBUTE:
                return "AttributeBase";
            case DTD:
                return "DTDEvent";
            case CDATA:
                return "CDATA";
        }
        return "UNKNOWN_EVENT_TYPE";
    }

}
