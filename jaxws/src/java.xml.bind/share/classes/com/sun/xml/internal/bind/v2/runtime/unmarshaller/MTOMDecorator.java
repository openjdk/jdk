/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import javax.activation.DataHandler;
import javax.xml.bind.attachment.AttachmentUnmarshaller;
import javax.xml.namespace.NamespaceContext;

import com.sun.xml.internal.bind.v2.WellKnownNamespace;

import org.xml.sax.SAXException;

/**
 * Decorator of {@link XmlVisitor} that performs XOP processing.
 * Used to support MTOM.
 *
 * @author Kohsuke Kawaguchi
 */
final class MTOMDecorator implements XmlVisitor {

    private final XmlVisitor next;

    private final AttachmentUnmarshaller au;

    private UnmarshallerImpl parent;

    private final Base64Data base64data = new Base64Data();

    /**
     * True if we are between the start and the end of xop:Include
     */
    private boolean inXopInclude;

    /**
     * UGLY HACK: we need to ignore the whitespace that follows
     * the attached base64 image.
     *
     * This happens twice; once before {@code </xop:Include>}, another
     * after {@code </xop:Include>}. The spec guarantees that
     * no valid pcdata can follow {@code </xop:Include>}.
     */
    private boolean followXop;

    public MTOMDecorator(UnmarshallerImpl parent,XmlVisitor next, AttachmentUnmarshaller au) {
        this.parent = parent;
        this.next = next;
        this.au = au;
    }

    public void startDocument(LocatorEx loc, NamespaceContext nsContext) throws SAXException {
        next.startDocument(loc,nsContext);
    }

    public void endDocument() throws SAXException {
        next.endDocument();
    }

    public void startElement(TagName tagName) throws SAXException {
        if(tagName.local.equals("Include") && tagName.uri.equals(WellKnownNamespace.XOP)) {
            // found xop:Include
            String href = tagName.atts.getValue("href");
            DataHandler attachment = au.getAttachmentAsDataHandler(href);
            if(attachment==null) {
                // report an error and ignore
                parent.getEventHandler().handleEvent(null);
                // TODO
            }
            base64data.set(attachment);
            next.text(base64data);
            inXopInclude = true;
            followXop = true;
        } else
            next.startElement(tagName);
    }

    public void endElement(TagName tagName) throws SAXException {
        if(inXopInclude) {
            // consume </xop:Include> by ourselves.
            inXopInclude = false;
            followXop = true;
            return;
        }
        next.endElement(tagName);
    }

    public void startPrefixMapping(String prefix, String nsUri) throws SAXException {
        next.startPrefixMapping(prefix,nsUri);
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        next.endPrefixMapping(prefix);
    }

    public void text( CharSequence pcdata ) throws SAXException {
        if(!followXop)
            next.text(pcdata);
        else
            followXop = false;
    }

    public UnmarshallingContext getContext() {
        return next.getContext();
    }

    public TextPredictor getPredictor() {
        return next.getPredictor();
    }
}
