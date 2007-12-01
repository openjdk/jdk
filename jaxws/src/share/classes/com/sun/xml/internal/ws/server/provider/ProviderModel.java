/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.ws.server.provider;

import com.sun.xml.internal.bind.api.JAXBRIContext;
import java.lang.reflect.ParameterizedType;
import javax.activation.DataSource;
import javax.xml.ws.Binding;
import javax.xml.ws.Provider;
import com.sun.xml.internal.ws.server.PeptTie;
import java.lang.reflect.Type;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.ws.Service;
import javax.xml.ws.ServiceMode;
import javax.xml.ws.soap.SOAPBinding;


/**
 * Keeps the runtime information like Service.Mode and erasure of Provider class
 * about Provider endpoint. It proccess annotations to find about Service.Mode
 * It also finds about parameterized type(e.g. Source, SOAPMessage, DataSource)
 * of endpoint class.
 *
 */
public class ProviderModel {

    private final boolean isSource;
    private final Service.Mode mode;

    public ProviderModel(Class implementorClass, Binding binding) {
        assert implementorClass != null;
        assert binding != null;

        mode = getServiceMode(implementorClass);
        Class otherClass = (binding instanceof SOAPBinding)
            ? SOAPMessage.class : DataSource.class;
        isSource = isSource(implementorClass, otherClass);
        if (mode == Service.Mode.PAYLOAD && !isSource) {
            // Illegal to have PAYLOAD && SOAPMessage
            // Illegal to have PAYLOAD && DataSource
            throw new IllegalArgumentException(
                "Illeagal combination - Mode.PAYLOAD and Provider<"+otherClass.getName()+">");
        }
    }

    public boolean isSource() {
        return isSource;
    }

    public Service.Mode getServiceMode() {
        return mode;
    }

    /**
     * Is it PAYLOAD or MESSAGE ??
     */
    private static Service.Mode getServiceMode(Class c) {
        ServiceMode mode = (ServiceMode)c.getAnnotation(ServiceMode.class);
        if (mode == null) {
            return Service.Mode.PAYLOAD;
        }
        return mode.value();
    }

    /**
     * Is it Provider<Source> ? Finds whether the parameterized type is
     * Source.class or not.
     *
     * @param c provider endpoint class
     * @param otherClass Typically SOAPMessage.class or DataSource.class
     * @return true if c's parameterized type is Source
     *         false otherwise
     * @throws IllegalArgumentException if it is not
     *         Provider<Source> or Provider<otherClass>
     *
     */
    private static boolean isSource(Class c, Class otherClass) {
        Type base = JAXBRIContext.getBaseType(c, Provider.class);
        assert base != null;
        if (base instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType)base;
            Type[] types = pt.getActualTypeArguments();
            if (types[0] instanceof Class && Source.class.isAssignableFrom((Class)types[0])) {
                return true;
            }
            if (types[0] instanceof Class && otherClass.isAssignableFrom((Class)types[0])) {
                return false;
            }
        }
        throw new IllegalArgumentException(
            "Endpoint should implement Provider<"+Source.class.getName()+
                "> or Provider<"+otherClass.getName()+">");
    }


}
