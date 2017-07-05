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

package com.sun.xml.internal.stream;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamReader;
import com.sun.org.apache.xerces.internal.xni.parser.XMLInputSource;

/**
 *
 * @author  Neeraj
 *
 * This class wraps XMLInputSource and is also capable of telling wether application
 * returned XMLStreamReader or not when XMLResolver.resolveEnity
 * was called.
 */
public class StaxXMLInputSource {

    XMLStreamReader fStreamReader ;
    XMLEventReader fEventReader ;
    XMLInputSource fInputSource ;

    /** Creates a new instance of StaxXMLInputSource */
    public StaxXMLInputSource(XMLStreamReader streamReader) {
        fStreamReader = streamReader ;
    }

    /** Creates a new instance of StaxXMLInputSource */
    public StaxXMLInputSource(XMLEventReader eventReader) {
        fEventReader = eventReader ;
    }

    public StaxXMLInputSource(XMLInputSource inputSource){
        fInputSource = inputSource ;

    }
    public XMLStreamReader getXMLStreamReader(){
        return fStreamReader ;
    }

    public XMLEventReader getXMLEventReader(){
        return fEventReader ;
    }

    public XMLInputSource getXMLInputSource(){
        return fInputSource ;
    }

    public boolean hasXMLStreamOrXMLEventReader(){
        return (fStreamReader == null) && (fEventReader == null) ? false : true ;
    }
}
