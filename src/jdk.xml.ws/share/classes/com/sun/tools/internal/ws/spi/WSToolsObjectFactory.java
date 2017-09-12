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

package com.sun.tools.internal.ws.spi;

import com.sun.tools.internal.ws.util.WSToolsObjectFactoryImpl;
import com.sun.xml.internal.ws.api.server.Container;

import java.io.OutputStream;


/**
 * Singleton abstract factory used to produce JAX-WS tools related objects.
 *
 * @author JAX-WS Development Team
 */
public abstract class WSToolsObjectFactory {

    private static final WSToolsObjectFactory factory = new WSToolsObjectFactoryImpl();

    /**
     * Obtain an instance of a factory. Don't worry about synchronization(at the
     * most, one more factory is created).
     */
    public static WSToolsObjectFactory newInstance() {
        return factory;
    }

    /**
     * Invokes wsimport on the wsdl URL argument, and generates the necessary
     * portable artifacts like SEI, Service, Bean classes etc.
     *
     * @param logStream Stream used for reporting log messages like errors, warnings etc
     * @param container gives an environment for tool if it is run during appserver
     *                  deployment
     * @param args arguments with various options and wsdl url
     *
     * @return true if there is no error, otherwise false
     */
    public abstract boolean wsimport(OutputStream logStream, Container container, String[] args);

    /**
     * Invokes wsimport on the wsdl URL argument, and generates the necessary
     * portable artifacts like SEI, Service, Bean classes etc.
     *
     * @return true if there is no error, otherwise false
     *
     * @see #wsimport(OutputStream, Container, String[])
     */
    public boolean wsimport(OutputStream logStream, String[] args) {
        return wsimport(logStream, Container.NONE, args);
    }

    /**
     * Invokes wsgen on the endpoint implementation, and generates the necessary
     * artifacts like wrapper, exception bean classes etc.
     *
     * @param logStream Stream used for reporting log messages like errors, warnings etc
     * @param container gives an environment for tool if it is run during appserver
     *                  deployment
     * @param args arguments with various options and endpoint class
     *
     * @return true if there is no error, otherwise false
     */
    public abstract boolean wsgen(OutputStream logStream, Container container, String[] args);

    /**
     * Invokes wsgen on the endpoint implementation, and generates the necessary
     * artifacts like wrapper, exception bean classes etc.
     *
     * @return true if there is no error, otherwise false
     * @see #wsgen(OutputStream, Container, String[])
     */
    public boolean wsgen(OutputStream logStream, String[] args) {
        return wsgen(logStream, Container.NONE, args);
    }

}
