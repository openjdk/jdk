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

package com.sun.xml.internal.ws.server.provider;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.server.AsyncProvider;
import com.sun.xml.internal.ws.resources.ServerMessages;
import com.sun.xml.internal.ws.spi.db.BindingHelper;

import javax.activation.DataSource;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.ws.Provider;
import javax.xml.ws.Service;
import javax.xml.ws.ServiceMode;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPBinding;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;


/**
 * Keeps the runtime information like Service.Mode and erasure of Provider class
 * about Provider endpoint. It proccess annotations to find about Service.Mode
 * It also finds about parameterized type(e.g. Source, SOAPMessage, DataSource)
 * of endpoint class.
 *
 * @author Jitendra Kotamraju
 * @author Kohsuke Kawaguchi
 */
final class ProviderEndpointModel<T> {
    /**
     * True if this is {@link AsyncProvider}.
     */
    final boolean isAsync;

    /**
     * In which mode does this provider operate?
     */
    @NotNull final Service.Mode mode;
    /**
     * T of {@link Provider}&lt;T>.
     */
    @NotNull final Class datatype;
    /**
     * User class that extends {@link Provider}.
     */
    @NotNull final Class implClass;

    ProviderEndpointModel(Class<T> implementorClass, WSBinding binding) {
        assert implementorClass != null;
        assert binding != null;

        implClass = implementorClass;
        mode = getServiceMode(implementorClass);
        Class otherClass = (binding instanceof SOAPBinding)
            ? SOAPMessage.class : DataSource.class;
        isAsync = AsyncProvider.class.isAssignableFrom(implementorClass);


        Class<? extends Object> baseType = isAsync ? AsyncProvider.class : Provider.class;
        Type baseParam = BindingHelper.getBaseType(implementorClass, baseType);
        if (baseParam==null)
            throw new WebServiceException(ServerMessages.NOT_IMPLEMENT_PROVIDER(implementorClass.getName()));
        if (!(baseParam instanceof ParameterizedType))
            throw new WebServiceException(ServerMessages.PROVIDER_NOT_PARAMETERIZED(implementorClass.getName()));

        ParameterizedType pt = (ParameterizedType)baseParam;
        Type[] types = pt.getActualTypeArguments();
        if(!(types[0] instanceof Class))
            throw new WebServiceException(ServerMessages.PROVIDER_INVALID_PARAMETER_TYPE(implementorClass.getName(),types[0]));
        datatype = (Class)types[0];

        if (mode == Service.Mode.PAYLOAD && datatype!=Source.class) {
            // Illegal to have PAYLOAD && SOAPMessage
            // Illegal to have PAYLOAD && DataSource
            throw new IllegalArgumentException(
                "Illeagal combination - Mode.PAYLOAD and Provider<"+otherClass.getName()+">");
        }
    }

    /**
     * Is it PAYLOAD or MESSAGE ??
     *
     * @param c endpoint class
     * @return Service.Mode.PAYLOAD or Service.Mode.MESSAGE
     */
    private static Service.Mode getServiceMode(Class<?> c) {
        ServiceMode mode = c.getAnnotation(ServiceMode.class);
        return (mode == null) ? Service.Mode.PAYLOAD : mode.value();
    }
}
