/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.webservices.internal.api.message;

import java.io.IOException;
import java.io.InputStream;

import com.oracle.webservices.internal.api.EnvelopeStyle;
import com.sun.xml.internal.ws.api.SOAPVersion; // TODO leaking RI APIs
import com.sun.xml.internal.ws.util.ServiceFinder;

import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.ws.WebServiceFeature;

public abstract class MessageContextFactory
{
    private static final MessageContextFactory DEFAULT = new com.sun.xml.internal.ws.api.message.MessageContextFactory(new WebServiceFeature[0]);

    protected abstract MessageContextFactory newFactory(WebServiceFeature ... f);

    public abstract MessageContext createContext();

    public abstract MessageContext createContext(SOAPMessage m);

    public abstract MessageContext createContext(Source m);

    public abstract MessageContext createContext(Source m, EnvelopeStyle.Style envelopeStyle);

    public abstract MessageContext createContext(InputStream in, String contentType) throws IOException;

    /**
     * @deprecated http://java.net/jira/browse/JAX_WS-1077
     */
    @Deprecated
    public abstract MessageContext createContext(InputStream in, MimeHeaders headers) throws IOException;

    static public MessageContextFactory createFactory(WebServiceFeature ... f) {
        return createFactory(null, f);
    }

    static public MessageContextFactory createFactory(ClassLoader cl, WebServiceFeature ...f) {
        for (MessageContextFactory factory : ServiceFinder.find(MessageContextFactory.class, cl)) {
            MessageContextFactory newfac = factory.newFactory(f);
            if (newfac != null) return newfac;
        }
        return new com.sun.xml.internal.ws.api.message.MessageContextFactory(f);
    }

    @Deprecated
    public abstract MessageContext doCreate();

    @Deprecated
    public abstract MessageContext doCreate(SOAPMessage m);

    //public abstract MessageContext doCreate(InputStream x);

    @Deprecated
    public abstract MessageContext doCreate(Source x, SOAPVersion soapVersion);

    @Deprecated
    public static MessageContext create(final ClassLoader... classLoader) {
        return serviceFinder(classLoader,
                             new Creator() {
                                 public MessageContext create(final MessageContextFactory f) {
                                     return f.doCreate();
                                 }
                             });
    }

    @Deprecated
    public static MessageContext create(final SOAPMessage m, final ClassLoader... classLoader) {
        return serviceFinder(classLoader,
                             new Creator() {
                                 public MessageContext create(final MessageContextFactory f) {
                                     return f.doCreate(m);
                                 }
                             });
    }

    @Deprecated
    public static MessageContext create(final Source m, final SOAPVersion v, final ClassLoader... classLoader) {
        return serviceFinder(classLoader,
                             new Creator() {
                                 public MessageContext create(final MessageContextFactory f) {
                                     return f.doCreate(m, v);
                                 }
                             });
    }

    @Deprecated
    private static MessageContext serviceFinder(final ClassLoader[] classLoader, final Creator creator) {
        final ClassLoader cl = classLoader.length == 0 ? null : classLoader[0];
        for (MessageContextFactory factory : ServiceFinder.find(MessageContextFactory.class, cl)) {
            final MessageContext messageContext = creator.create(factory);
            if (messageContext != null)
                return messageContext;
        }
        return creator.create(DEFAULT);
    }

    @Deprecated
    private static interface Creator {
        public MessageContext create(MessageContextFactory f);
    }
}
