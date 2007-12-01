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
package com.sun.xml.internal.ws.spi.runtime;

import javax.xml.ws.WebServiceException;
import javax.xml.namespace.QName;
import static java.lang.Class.forName;
import static java.lang.Thread.currentThread;
import java.util.HashMap;

public abstract class SystemHandlerDelegateFactory {

    private final static String DEFAULT_FACTORY_NAME =
        "com.sun.xml.internal.xwss.SystemHandlerDelegateFactory";

    private static String factoryName;

    private static HashMap factoryMap;

    static {
        init();
    }

    private static synchronized void init() {
        factoryName = DEFAULT_FACTORY_NAME;
        factoryMap = new HashMap();
    };

    // foctory implementations that maintain a map of serviceName to
    // would override this method
    /**
    * Used by the Appserver on client and server sides
    * factory implementations that maintain a map of serviceName to
    * factory
    * @param serviceName when called by the SOAPBindingImpl to
    * create the SystemHandlerDelegate. serviceName must be
    * a QName
    * @return com.sun.xml.internal.ws.spi.runtime.SystemHandlerDelegate
    * @throws java.lang.Exception when the create failed.
    */
    public SystemHandlerDelegate getDelegate(QName serviceName) {
        return create();
    }

    /**
    * Used by the Appserver and xws-security on client and server sides
    * factory implementations that maintain a map of serviceName to
    * factory
    * @return com.sun.xml.internal.ws.spi.runtime.SystemHandlerDelegate
    * @throws java.lang.Exception when the create failed.
    */
    public abstract SystemHandlerDelegate create();

    //currently not used
    public abstract boolean isEnabled(MessageContext messageContext);

    // factory name can be set to null, in which case,
    // the default factory will be disabled.
    /**
    * Used by the Appserver on client and server sides
    * factoryName can be set to null, in which case the defaultFactory will be
    * disabled
    * @param name when called by the SOAPBindingImpl to
    * create the SystemHandlerDelegate. serviceName must be
    * a String
    */
    public static synchronized void setFactoryName(String name) {
        factoryName = name;
    }

    /**
    * Used by the Appserver on client and server sides
    * factoryName can be set to null, in which case the defaultFactory will be
    * disabled and will be null on return
    * @return java.lang.String - name of factory
    */
    public static synchronized String getFactoryName() {
        return factoryName;
    }
    /**
    * Used by the JAX-WS implementation on client and server sides
    * to load the SystemHandlerDelegateFactory
    * @return com.sun.xml.internal.ws.spi.runtime.SystemHandlerDelegateFactory
    * @throws javax.xml.ws.WebServiceException when the load fails.
    */
    public static synchronized SystemHandlerDelegateFactory getFactory() {

        SystemHandlerDelegateFactory factory =
            (SystemHandlerDelegateFactory) factoryMap.get(factoryName);

        if (factory != null || factoryMap.containsKey(factoryName)) {
            return factory;
        } else {

            Class clazz = null;
            try {
                ClassLoader loader = currentThread().getContextClassLoader();

                if (loader == null) {
                    clazz = forName(factoryName);
                } else {
                    clazz = loader.loadClass(factoryName);
                }

                if (clazz != null) {
                    factory = (SystemHandlerDelegateFactory) clazz.newInstance();
                }
            } catch (ClassNotFoundException e) {
                factory = null;
                // e.printStackTrace(); //todo:need to add log
            } catch (Exception x) {
                throw new WebServiceException(x);
            } finally {
                // stores null factory values in map to prevent
                // repeated class loading and instantiation errors.
                factoryMap.put(factoryName, factory);
            }
        }
        return factory;
    }
}
