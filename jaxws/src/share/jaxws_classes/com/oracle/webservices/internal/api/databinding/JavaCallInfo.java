/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
