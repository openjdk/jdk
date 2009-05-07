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



package com.sun.xml.internal.fastinfoset.stax.factory;

import com.sun.xml.internal.fastinfoset.stax.*;
import com.sun.xml.internal.fastinfoset.stax.events.StAXEventWriter;
import javax.xml.transform.Result;
import javax.xml.stream.XMLOutputFactory ;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import com.sun.xml.internal.fastinfoset.CommonResourceBundle;

public class StAXOutputFactory extends XMLOutputFactory {

    //List of supported properties and default values.
    private StAXManager _manager = null ;

    /** Creates a new instance of StAXOutputFactory */
    public StAXOutputFactory() {
        _manager = new StAXManager(StAXManager.CONTEXT_WRITER);
    }

    public XMLEventWriter createXMLEventWriter(Result result) throws XMLStreamException {
        return new StAXEventWriter(createXMLStreamWriter(result));
    }

    public XMLEventWriter createXMLEventWriter(Writer writer) throws XMLStreamException {
        return new StAXEventWriter(createXMLStreamWriter(writer));
    }

    public XMLEventWriter createXMLEventWriter(OutputStream outputStream) throws XMLStreamException {
        return new StAXEventWriter(createXMLStreamWriter(outputStream));
    }

    public XMLEventWriter createXMLEventWriter(OutputStream outputStream, String encoding) throws XMLStreamException {
        return new StAXEventWriter(createXMLStreamWriter(outputStream, encoding));
    }

    public XMLStreamWriter createXMLStreamWriter(Result result) throws XMLStreamException {
        if(result instanceof StreamResult){
            StreamResult streamResult = (StreamResult)result;
            if( streamResult.getWriter() != null){
                return createXMLStreamWriter(streamResult.getWriter());
            }else if(streamResult.getOutputStream() != null ){
                return createXMLStreamWriter(streamResult.getOutputStream());
            }else if(streamResult.getSystemId()!= null){
                try{
                    FileWriter writer = new FileWriter(new File(streamResult.getSystemId()));
                    return createXMLStreamWriter(writer);
                }catch(IOException ie){
                    throw new XMLStreamException(ie);
                }
            }
        }
        else {
            try{
                //xxx: should we be using FileOutputStream - nb.
                FileWriter writer = new FileWriter(new File(result.getSystemId()));
                return createXMLStreamWriter(writer);
            }catch(IOException ie){
                throw new XMLStreamException(ie);
            }
        }
        throw new java.lang.UnsupportedOperationException();
    }

    /** this is assumed that user wants to write the file in xml format
     *
     */
    public XMLStreamWriter createXMLStreamWriter(Writer writer) throws XMLStreamException {
        throw new java.lang.UnsupportedOperationException();
    }

    public XMLStreamWriter createXMLStreamWriter(OutputStream outputStream) throws XMLStreamException {
        return new StAXDocumentSerializer(outputStream, new StAXManager(_manager));
    }

    public XMLStreamWriter createXMLStreamWriter(OutputStream outputStream, String encoding) throws XMLStreamException {
        StAXDocumentSerializer serializer = new StAXDocumentSerializer(outputStream, new StAXManager(_manager));
        serializer.setEncoding(encoding);
        return serializer;
    }

    public Object getProperty(String name) throws java.lang.IllegalArgumentException {
        if(name == null){
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().getString("message.propertyNotSupported", new Object[]{null}));
        }
        if(_manager.containsProperty(name))
            return _manager.getProperty(name);
        throw new IllegalArgumentException(CommonResourceBundle.getInstance().getString("message.propertyNotSupported", new Object[]{name}));
    }

    public boolean isPropertySupported(String name) {
        if(name == null)
            return false ;
        else
            return _manager.containsProperty(name);
    }

    public void setProperty(String name, Object value) throws java.lang.IllegalArgumentException {
        _manager.setProperty(name,value);

    }

}
