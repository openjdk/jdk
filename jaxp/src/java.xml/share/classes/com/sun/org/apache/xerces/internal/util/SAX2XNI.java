/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 2000-2002 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Xerces" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation and was
 * originally based on software copyright (c) 1999, International
 * Business Machines, Inc., http://www.apache.org.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */
package com.sun.org.apache.xerces.internal.util;

import com.sun.org.apache.xerces.internal.impl.xs.util.SimpleLocator;
import com.sun.org.apache.xerces.internal.jaxp.validation.WrappedSAXException;
import com.sun.org.apache.xerces.internal.xni.QName;
import com.sun.org.apache.xerces.internal.xni.XMLAttributes;
import com.sun.org.apache.xerces.internal.xni.XMLDocumentHandler;
import com.sun.org.apache.xerces.internal.xni.XMLLocator;
import com.sun.org.apache.xerces.internal.xni.XMLString;
import com.sun.org.apache.xerces.internal.xni.parser.XMLDocumentSource;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * Receves SAX {@link ContentHandler} events
 * and produces the equivalent {@link XMLDocumentHandler} events.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class SAX2XNI implements ContentHandler, XMLDocumentSource {
    public SAX2XNI( XMLDocumentHandler core ) {
        this.fCore = core;
    }

    private XMLDocumentHandler fCore;

    private final NamespaceSupport nsContext = new NamespaceSupport();
    private final SymbolTable symbolTable = new SymbolTable();


    public void setDocumentHandler(XMLDocumentHandler handler) {
        fCore = handler;
    }

    public XMLDocumentHandler getDocumentHandler() {
        return fCore;
    }


    //
    //
    // ContentHandler implementation
    //
    //
    public void startDocument() throws SAXException {
        try {
            nsContext.reset();

            XMLLocator xmlLocator;
            if(locator==null)
                // some SAX source doesn't provide a locator,
                // in which case we assume no line information is available
                // and use a dummy locator. With this, downstream components
                // can always assume that they will get a non-null Locator.
                xmlLocator=new SimpleLocator(null,null,-1,-1);
            else
                xmlLocator=new LocatorWrapper(locator);

            fCore.startDocument(
                    xmlLocator,
                    null,
                    nsContext,
                    null);
        } catch( WrappedSAXException e ) {
            throw e.exception;
        }
    }

    public void endDocument() throws SAXException {
        try {
            fCore.endDocument(null);
        } catch( WrappedSAXException e ) {
            throw e.exception;
        }
    }

    public void startElement( String uri, String local, String qname, Attributes att ) throws SAXException {
        try {
            fCore.startElement(createQName(uri,local,qname),createAttributes(att),null);
        } catch( WrappedSAXException e ) {
            throw e.exception;
        }
    }

    public void endElement( String uri, String local, String qname ) throws SAXException {
        try {
            fCore.endElement(createQName(uri,local,qname),null);
        } catch( WrappedSAXException e ) {
            throw e.exception;
        }
    }

    public void characters( char[] buf, int offset, int len ) throws SAXException {
        try {
            fCore.characters(new XMLString(buf,offset,len),null);
        } catch( WrappedSAXException e ) {
            throw e.exception;
        }
    }

    public void ignorableWhitespace( char[] buf, int offset, int len ) throws SAXException {
        try {
            fCore.ignorableWhitespace(new XMLString(buf,offset,len),null);
        } catch( WrappedSAXException e ) {
            throw e.exception;
        }
    }

    public void startPrefixMapping( String prefix, String uri ) {
        nsContext.pushContext();
        nsContext.declarePrefix(prefix,uri);
    }

    public void endPrefixMapping( String prefix ) {
        nsContext.popContext();
    }

    public void processingInstruction( String target, String data ) throws SAXException {
        try {
            fCore.processingInstruction(
                    symbolize(target),createXMLString(data),null);
        } catch( WrappedSAXException e ) {
            throw e.exception;
        }
    }

    public void skippedEntity( String name ) {
    }

    private Locator locator;
    public void setDocumentLocator( Locator _loc ) {
        this.locator = _loc;
    }

    /** Creates a QName object. */
    private QName createQName(String uri, String local, String raw) {

        int idx = raw.indexOf(':');

        if( local.length()==0 ) {
            // if naemspace processing is turned off, local could be "".
            // in that case, treat everything to be in the no namespace.
            uri = "";
            if(idx<0)
                local = raw;
            else
                local = raw.substring(idx+1);
        }

        String prefix;
        if (idx < 0)
            prefix = null;
        else
            prefix = raw.substring(0, idx);

        if (uri != null && uri.length() == 0)
            uri = null; // XNI uses null whereas SAX uses the empty string

        return new QName(symbolize(prefix), symbolize(local), symbolize(raw), symbolize(uri));
    }

    /** Symbolizes the specified string. */
    private String symbolize(String s) {
        if (s == null)
            return null;
        else
            return symbolTable.addSymbol(s);
    }

    private XMLString createXMLString(String str) {
        // with my patch
        // return new XMLString(str);

        // for now
        return new XMLString(str.toCharArray(), 0, str.length());
    }


    /** only one instance of XMLAttributes is used. */
    private final XMLAttributes xa = new XMLAttributesImpl();

    /** Creates an XMLAttributes object. */
    private XMLAttributes createAttributes(Attributes att) {
        xa.removeAllAttributes();
        int len = att.getLength();
        for (int i = 0; i < len; i++)
            xa.addAttribute(
                    createQName(att.getURI(i), att.getLocalName(i), att.getQName(i)),
                    att.getType(i),
                    att.getValue(i));
        return xa;
    }
}
