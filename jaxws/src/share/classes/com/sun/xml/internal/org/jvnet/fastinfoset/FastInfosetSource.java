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



package com.sun.xml.internal.org.jvnet.fastinfoset;

import java.io.InputStream;

import javax.xml.transform.sax.SAXSource;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.sun.xml.internal.fastinfoset.sax.SAXDocumentParser;

/**
 *  A JAXP Source implementation that supports the parsing fast
 *  infoset document for use by applications that expect a Source.
 *
 *  <P>The derivation of FISource from SAXSource is an implementation
 *  detail.<P>
 *
 *  <P>This implementation is designed for interoperation with JAXP and is not
 *  not designed with performance in mind. It is recommended that for performant
 *  interoperation alternative parser specific solutions be used.<P>
 *
 *  <P>Applications shall obey the following restrictions:
 *   <UL>
 *     <LI>The setXMLReader and setInputSource shall not be called.</LI>
 *     <LI>The XMLReader object obtained by the getXMLReader method shall
 *        be used only for parsing the InputSource object returned by
 *        the getInputSource method.</LI>
 *     <LI>The InputSource object obtained by the getInputSource method shall
 *        be used only for being parsed by the XMLReader object returned by
 *        the getXMLReader method.</LI>
 *   </UL>
 *  </P>
 */
public class FastInfosetSource extends SAXSource {

    public FastInfosetSource(InputStream inputStream) {
        super(new InputSource(inputStream));
    }

    public XMLReader getXMLReader() {
        XMLReader reader = super.getXMLReader();
        if (reader == null) {
            reader = new SAXDocumentParser();
            setXMLReader(reader);
        }
        ((SAXDocumentParser) reader).setInputStream(getInputStream());
        return reader;
    }

    public InputStream getInputStream() {
        return getInputSource().getByteStream();
    }

    public void setInputStream(InputStream inputStream) {
        setInputSource(new InputSource(inputStream));
    }
}
