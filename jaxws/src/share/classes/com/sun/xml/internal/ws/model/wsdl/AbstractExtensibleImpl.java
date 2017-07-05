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

package com.sun.xml.internal.ws.model.wsdl;

import com.sun.xml.internal.ws.api.model.wsdl.WSDLExtensible;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLExtension;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLObject;
import com.sun.xml.internal.ws.resources.UtilMessages;
import com.sun.xml.internal.ws.wsdl.parser.WSDLConstants;
import com.sun.istack.internal.NotNull;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.Location;
import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.xml.sax.Locator;
import org.xml.sax.helpers.LocatorImpl;

/**
 * All the WSDL 1.1 elements that are extensible should subclass from this abstract implementation of
 * {@link WSDLExtensible} interface.
 *
 * @author Vivek Pandey
 * @author Kohsuke Kawaguchi
 */
abstract class AbstractExtensibleImpl extends AbstractObjectImpl implements WSDLExtensible {
    protected final Set<WSDLExtension> extensions = new HashSet<WSDLExtension>();
    // this captures any wsdl extensions that are not understood by WSDLExtensionParsers
    // and have wsdl:required=true
    protected List<UnknownWSDLExtension> notUnderstoodExtensions =
            new ArrayList<UnknownWSDLExtension>();

    protected AbstractExtensibleImpl(XMLStreamReader xsr) {
        super(xsr);
    }

    protected AbstractExtensibleImpl(String systemId, int lineNumber) {
        super(systemId, lineNumber);
    }

    public final Iterable<WSDLExtension> getExtensions() {
        return extensions;
    }

    public final <T extends WSDLExtension> Iterable<T> getExtensions(Class<T> type) {
        // TODO: this is a rather stupid implementation
        List<T> r = new ArrayList<T>(extensions.size());
        for (WSDLExtension e : extensions) {
            if(type.isInstance(e))
                r.add(type.cast(e));
        }
        return r;
    }

    public <T extends WSDLExtension> T getExtension(Class<T> type) {
        for (WSDLExtension e : extensions) {
            if(type.isInstance(e))
                return type.cast(e);
        }
        return null;
    }

    public void addExtension(WSDLExtension ex) {
        if(ex==null)
            // I don't trust plugins. So let's always check it, instead of making this an assertion
            throw new IllegalArgumentException();
        extensions.add(ex);
    }

    /**
     * This can be used if a WSDL extension element that has wsdl:required=true
     * is not understood
     * @param extnEl
     * @param locator
     */
    public void addNotUnderstoodExtension(QName extnEl, Locator locator) {
        notUnderstoodExtensions.add(new UnknownWSDLExtension(extnEl, locator));
    }

    protected class UnknownWSDLExtension implements WSDLExtension, WSDLObject {
        private final QName extnEl;
        private final Locator locator;
        public UnknownWSDLExtension(QName extnEl, Locator locator) {
            this.extnEl = extnEl;
            this.locator = locator;
        }
        public QName getName() {
            return extnEl;
        }
        @NotNull public Locator getLocation() {
            return locator;
        }
        public String toString(){
           return extnEl + " "+ UtilMessages.UTIL_LOCATION( locator.getLineNumber(), locator.getSystemId());
       }
    }

    /**
     * This method should be called after freezing the WSDLModel
     * @return true if all wsdl required extensions on Port and Binding are understood
     */
    public boolean areRequiredExtensionsUnderstood() {
        if (notUnderstoodExtensions.size() != 0) {
            StringBuilder buf = new StringBuilder("Unknown WSDL extensibility elements:");
            for (UnknownWSDLExtension extn : notUnderstoodExtensions)
                buf.append('\n').append(extn.toString());
            throw new WebServiceException(buf.toString());
        }
        return true;
    }
}
