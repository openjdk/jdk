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



package com.sun.xml.internal.fastinfoset.stax.events;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.util.XMLEventAllocator;
import javax.xml.stream.util.XMLEventConsumer;

import com.sun.xml.internal.fastinfoset.CommonResourceBundle;

/**
 * This class provides the same functionality as StAXEventAllocatorBase, but without
 * using EventFactory and creating a new object for each call.
 *
 * It seems to be good idea using static components. Unfortunately, EventReader's peek
 * and next methods require that multiple instances being created.
 *
 */
public class StAXEventAllocator implements XMLEventAllocator {
    StartElementEvent startElement = new StartElementEvent();
    EndElementEvent endElement = new EndElementEvent();
    CharactersEvent characters = new CharactersEvent();
    CharactersEvent cData = new CharactersEvent("",true);
    CharactersEvent space = new CharactersEvent();
    CommentEvent comment = new CommentEvent();
    EntityReferenceEvent entity = new EntityReferenceEvent();
    ProcessingInstructionEvent pi = new ProcessingInstructionEvent();
    StartDocumentEvent startDoc = new StartDocumentEvent();
    EndDocumentEvent endDoc = new EndDocumentEvent();
    DTDEvent dtd = new DTDEvent();

    /** Creates a new instance of StAXEventAllocator */
    public StAXEventAllocator() {
    }
    public XMLEventAllocator newInstance() {
        return new StAXEventAllocator();
    }

  /**
   * This method allocates an event given the current state of the XMLStreamReader.
   * If this XMLEventAllocator does not have a one-to-one mapping between reader state
   * and events this method will return null.
   * @param streamReader The XMLStreamReader to allocate from
   * @return the event corresponding to the current reader state
   */
    public XMLEvent allocate(XMLStreamReader streamReader) throws XMLStreamException {
        if(streamReader == null )
            throw new XMLStreamException(CommonResourceBundle.getInstance().getString("message.nullReader"));
        return getXMLEvent(streamReader);
    }

  /**
   * This method allocates an event or set of events given the current state of
   * the XMLStreamReader and adds the event or set of events to the consumer that
   * was passed in.
   * @param streamReader The XMLStreamReader to allocate from
   * @param consumer The XMLEventConsumer to add to.
   */
    public void allocate(XMLStreamReader streamReader, XMLEventConsumer consumer) throws XMLStreamException {
        consumer.add(getXMLEvent(streamReader));

    }
    // ---------------------end of methods defined by XMLEventAllocator-----------------//


    XMLEvent getXMLEvent(XMLStreamReader reader){
        EventBase event = null;
        int eventType = reader.getEventType();

        switch(eventType){

            case XMLEvent.START_ELEMENT:
            {
                startElement.reset();
                startElement.setName(new QName(reader.getNamespaceURI(),
                                   reader.getLocalName(), reader.getPrefix()));

                addAttributes(startElement,reader);
                addNamespaces(startElement, reader);
                //need to fix it along with the Reader
                //setNamespaceContext(startElement,reader);
                event = startElement;
                break;
            }
            case XMLEvent.END_ELEMENT:
            {
                endElement.reset();
                endElement.setName(new QName(reader.getNamespaceURI(),
                                 reader.getLocalName(),reader.getPrefix()));
                addNamespaces(endElement,reader);
                event = endElement ;
                break;
            }
            case XMLEvent.PROCESSING_INSTRUCTION:
            {
                pi.setTarget(reader.getPITarget());
                pi.setData(reader.getPIData());
                event = pi;
                break;
            }
            case XMLEvent.CHARACTERS:
            {
                characters.setData(reader.getText());
                event = characters;
                /**
                if (reader.isWhiteSpace()) {
                    space.setData(reader.getText());
                    space.setSpace(true);
                    event = space;
                }
                else {
                    characters.setData(reader.getText());
                    event = characters;
                }
                 */
                break;
            }
            case XMLEvent.COMMENT:
            {
                comment.setText(reader.getText());
                event = comment;
                break;
            }
            case XMLEvent.START_DOCUMENT:
            {
                startDoc.reset();
                String encoding = reader.getEncoding();
                String version = reader.getVersion();
                if (encoding != null)
                    startDoc.setEncoding(encoding);
                if (version != null)
                    startDoc.setVersion(version);
                startDoc.setStandalone(reader.isStandalone());
                if(reader.getCharacterEncodingScheme() != null){
                    startDoc.setDeclaredEncoding(true);
                }else{
                    startDoc.setDeclaredEncoding(false);
                }
                event = startDoc ;
                break;
            }
            case XMLEvent.END_DOCUMENT:{
                event = endDoc;
                break;
            }
            case XMLEvent.ENTITY_REFERENCE:{
                entity.setName(reader.getLocalName());
                entity.setDeclaration(new EntityDeclarationImpl(reader.getLocalName(),reader.getText()));
                event = entity;
                break;

            }
            case XMLEvent.ATTRIBUTE:{
                event = null ;
                break;
            }
            case XMLEvent.DTD:{
                dtd.setDTD(reader.getText());
                event = dtd;
                break;
            }
            case XMLEvent.CDATA:{
                cData.setData(reader.getText());
                event = cData;
                break;
            }
            case XMLEvent.SPACE:{
                space.setData(reader.getText());
                space.setSpace(true);
                event = space;
                break;
            }
        }
        event.setLocation(reader.getLocation());
        return event ;
    }

    //use event.addAttribute instead of addAttributes to avoid creating another list
    protected void addAttributes(StartElementEvent event,XMLStreamReader reader){
        AttributeBase attr = null;
        for(int i=0; i<reader.getAttributeCount() ;i++){
            attr =  new AttributeBase(reader.getAttributeName(i), reader.getAttributeValue(i));
            attr.setAttributeType(reader.getAttributeType(i));
            attr.setSpecified(reader.isAttributeSpecified(i));
            event.addAttribute(attr);
        }
    }

    //add namespaces to StartElement/EndElement
    protected void addNamespaces(StartElementEvent event,XMLStreamReader reader){
        Namespace namespace = null;
        for(int i=0; i<reader.getNamespaceCount(); i++){
            namespace =  new NamespaceBase(reader.getNamespacePrefix(i), reader.getNamespaceURI(i));
            event.addNamespace(namespace);
        }
    }

    protected void addNamespaces(EndElementEvent event,XMLStreamReader reader){
        Namespace namespace = null;
        for(int i=0; i<reader.getNamespaceCount(); i++){
            namespace =  new NamespaceBase(reader.getNamespacePrefix(i), reader.getNamespaceURI(i));
            event.addNamespace(namespace);
        }
    }

}
