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
 */
package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import javax.xml.namespace.NamespaceContext;
import javax.xml.validation.Schema;
import javax.xml.validation.ValidatorHandler;

import com.sun.xml.internal.bind.v2.util.FatalAdapter;

import org.xml.sax.SAXException;

/**
 * {@link XmlVisitor} decorator that validates the events by using JAXP validation API.
 *
 * @author Kohsuke Kawaguchi
 */
final class ValidatingUnmarshaller implements XmlVisitor, XmlVisitor.TextPredictor {

    private final XmlVisitor next;
    private final ValidatorHandler validator;

    /**
     * {@link TextPredictor} of the next {@link XmlVisitor}.
     */
    private final TextPredictor predictor;

    private char[] buf = new char[256];

    /**
     * Creates a new instance of ValidatingUnmarshaller.
     */
    public ValidatingUnmarshaller( Schema schema, XmlVisitor next ) {
        this.validator = schema.newValidatorHandler();
        this.next = next;
        this.predictor = next.getPredictor();
        // if the user bothers to use a validator, make validation errors fatal
        // so that it will abort unmarshalling.
        validator.setErrorHandler(new FatalAdapter(getContext()));
    }

    public void startDocument(LocatorEx locator, NamespaceContext nsContext) throws SAXException {
        // when nsContext is non-null, validator won't probably work correctly.
        // should we warn?
        validator.setDocumentLocator(locator);
        validator.startDocument();
        next.startDocument(locator,nsContext);
    }

    public void endDocument() throws SAXException {
        validator.endDocument();
        next.endDocument();
    }

    public void startElement(TagName tagName) throws SAXException {
        validator.startElement(tagName.uri,tagName.local,tagName.getQname(),tagName.atts);
        next.startElement(tagName);
    }

    public void endElement(TagName tagName ) throws SAXException {
        validator.endElement(tagName.uri,tagName.local,tagName.getQname());
        next.endElement(tagName);
    }

    public void startPrefixMapping(String prefix, String nsUri) throws SAXException {
        validator.startPrefixMapping(prefix,nsUri);
        next.startPrefixMapping(prefix,nsUri);
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        validator.endPrefixMapping(prefix);
        next.endPrefixMapping(prefix);
    }

    public void text( CharSequence pcdata ) throws SAXException {
        int len = pcdata.length();
        if(buf.length<len) {
            buf = new char[len];
        }
        for( int i=0;i<len; i++ )
            buf[i] = pcdata.charAt(i);  // isn't this kinda slow?

        validator.characters(buf,0,len);
        if(predictor.expectText())
            next.text(pcdata);
    }

    public UnmarshallingContext getContext() {
        return next.getContext();
    }

    public TextPredictor getPredictor() {
        return this;
    }

    // should be always invoked through TextPredictor
    @Deprecated
    public boolean expectText() {
        // validator needs to make sure that there's no text
        // even when it's not expected. So always have them
        // send text, ignoring optimization hints from the unmarshaller
        return true;
    }
}
