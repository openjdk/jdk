/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2012 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;

/**
 * On the client or service-requestor side, a JavaCallInfo object represents a
 * method call on the service proxy to be serialized as a SOAP request message
 * to be sent to the service. A SOAP response message returned to the service
 * client is deserialized as an update to the JavaCallInfo object which is used
 * to generated the request.
 * <p>
 * </p>
 * On the server or service provider side, a SOAP request message is
 * deserialized to a JavaCallInfo object which can be used to determine which
 * method to call, and get the parameter values to call the back-end service
 * implementation object. The return value or exception returned from the
 * service implementation should be set to the JavaCallInfo object which then
 * can be used to serialize to a A SOAP response or fault message to be sent
 * back to the service client.
 *
 * @author shih-chang.chen@oracle.com
 */
public interface JavaCallInfo {

        /**
         * Gets the method of this JavaCallInfo
         *
         * @return the method
         */
        public Method getMethod();

//      /**
//       * Sets the method of this JavaCallInfo
//       *
//       * @param method The method to set
//       */
//      public void setMethod(Method method);

        /**
         * Gets the parameters of this JavaCallInfo
         *
         * @return The parameters
         */
        public Object[] getParameters();

//      /**
//       * Sets the parameters of this JavaCallInfo
//       *
//       * @param parameters
//       *            the parameters to set
//       */
//      public void setParameters(Object[] parameters);

        /**
         * Gets the returnValue of this JavaCallInfo
         *
         * @return the returnValue
         */
        public Object getReturnValue();

        /**
         * Sets the returnValue of this JavaCallInfo
         *
         * @param returnValue
         *            the returnValue to set
         */
        public void setReturnValue(Object returnValue);

        /**
         * Gets the exception of this JavaCallInfo
         *
         * @return the exception
         */
        public Throwable getException();

        /**
         * Sets the exception of this JavaCallInfo
         *
         * @param exception
         *            the exception to set
         */
        public void setException(Throwable exception);
}
