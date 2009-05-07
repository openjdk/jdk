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
package com.sun.tools.internal.xjc.reader.xmlschema.bindinfo;

import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.annotation.DomHandler;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;

import com.sun.xml.internal.bind.marshaller.SAX2DOMEx;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Locator;
import org.xml.sax.helpers.LocatorImpl;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * {@link DomHandler} that produces a W3C DOM but with a location information.
 *
 * @author Kohsuke Kawaguchi
 */
final class DomHandlerEx implements DomHandler<DomHandlerEx.DomAndLocation,DomHandlerEx.ResultImpl> {

    public static final class DomAndLocation {
        public final Element element;
        public final Locator loc;

        public DomAndLocation(Element element, Locator loc) {
            this.element = element;
            this.loc = loc;
        }
    }

    public ResultImpl createUnmarshaller(ValidationEventHandler errorHandler) {
        return new ResultImpl();
    }

    public DomAndLocation getElement(ResultImpl r) {
        return new DomAndLocation( ((Document)r.s2d.getDOM()).getDocumentElement(), r.location );
    }

    public Source marshal(DomAndLocation domAndLocation, ValidationEventHandler errorHandler) {
        return new DOMSource(domAndLocation.element);
    }

    public static final class ResultImpl extends SAXResult {
        final SAX2DOMEx s2d;

        Locator location = null;

        ResultImpl() {
            try {
                s2d = new SAX2DOMEx();
            } catch (ParserConfigurationException e) {
                throw new AssertionError(e);    // impossible
            }

            XMLFilterImpl f = new XMLFilterImpl() {
                public void setDocumentLocator(Locator locator) {
                    super.setDocumentLocator(locator);
                    location = new LocatorImpl(locator);
                }
            };
            f.setContentHandler(s2d);

            setHandler(f);
        }

    }
}
