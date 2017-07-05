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

package com.oracle.webservices.internal.api.databinding;

import java.util.Map;

/**
 * {@code DatabindingFactory} is the entry point of all the WebService
 * Databinding APIs. A DatabindingFactory instance can be used to create
 * <code>Databinding.Builder</code> instances, and <code>Databinding.Builder</code>
 * instances are used to configure and build <code>Databinding</code> instances.
 * <p>
 * </P>
 * <blockquote>
 * Following is an example that creates a {@code Databinding} which provides the
 * operations to serialize/deserialize a JavaCallInfo to/from a SOAP message:<br/>
 * <pre>
 * DatabindingFactory factory = DatabindingFactory.newInstance();
 * Databinding.Builder builder = factory.createBuilder(seiClass, endpointClass);
 * Databinding databinding = builder.build();
 * </pre>
 * </blockquote>
 *
 * @see com.oracle.webservices.internal.api.databinding.Databinding
 *
 * @author shih-chang.chen@oracle.com
 */
public abstract class DatabindingFactory {

  /**
   * Creates a new instance of a <code>Databinding.Builder</code> which is
   * initialized with the specified contractClass and endpointClass. The most
   * importance initial states of a Builder object is the contract class which
   * is also called "service endpoint interface" or "SEI" in JAX-WS and JAX-RPC,
   * and the implementation bean class (endpointClass). The the implementation
   * bean class (endpointClass) should be null if the Builder is to create
   * the client side proxy databinding.
   *
   * @param contractClass The service endpoint interface class
   * @param endpointClass The service implementation bean class
   *
   * @return New instance of a <code>Databinding.Builder</code>
   */
  abstract public Databinding.Builder createBuilder(Class<?> contractClass, Class<?> endpointClass);

  /**
   * Access properties on the <code>DatabindingFactory</code> instance.
   *
   * @return properties of this WsFactory
   */
   abstract public Map<String, Object> properties();

        /**
         * The default implementation class name.
         */
   static final String ImplClass = "com.sun.xml.internal.ws.db.DatabindingFactoryImpl";

  /**
   * Create a new instance of a <code>DatabindingFactory</code>. This static method
   * creates a new factory instance.
   *
   * Once an application has obtained a reference to a <code>DatabindingFactory</code>
   * it can use the factory to obtain and configure a <code>Databinding.Builder</code>
   * to build a <code>Databinding</code> instances.
   *
   * @return New instance of a <code>DatabindingFactory</code>
   */
        static public DatabindingFactory newInstance() {
                try {
                        Class<?> cls = Class.forName(ImplClass);
                        return convertIfNecessary(cls);
                } catch (Exception e) {
                        e.printStackTrace();
                }
                return null;
        }

    @SuppressWarnings("deprecation")
    private static DatabindingFactory convertIfNecessary(Class<?> cls) throws InstantiationException, IllegalAccessException {
        return (DatabindingFactory) cls.newInstance();
    }
}
