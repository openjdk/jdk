/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
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
