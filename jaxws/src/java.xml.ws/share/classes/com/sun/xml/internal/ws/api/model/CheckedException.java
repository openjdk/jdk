/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.model;

import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.ws.spi.db.TypeInfo;

import javax.xml.ws.WebFault;

/**
 * This class provides abstractio to the  the exception class
 * corresponding to the wsdl:fault, such as class MUST have
 * {@link WebFault} annotation defined on it.
 *
 * Also the exception class must have
 *
 * <code>public WrapperException()String message, FaultBean){}</code>
 *
 * and method
 *
 * <code>public FaultBean getFaultInfo();</code>
 *
 * @author Vivek Pandey
 */
public interface CheckedException {
    /**
     * Gets the root {@link SEIModel} that owns this model.
     */
    SEIModel getOwner();

    /**
     * Gets the parent {@link JavaMethod} to which this checked exception belongs.
     */
    JavaMethod getParent();

    /**
     * The returned exception class would be userdefined or WSDL exception class.
     *
     * @return
     *      always non-null same object.
     */
    Class getExceptionClass();

    /**
     * The detail bean is serialized inside the detail entry in the SOAP message.
     * This must be known to the {@link javax.xml.bind.JAXBContext} inorder to get
     * marshalled/unmarshalled.
     *
     * @return the detail bean
     */
    Class getDetailBean();

    /**
     * Gives the {@link com.sun.xml.internal.bind.api.Bridge} associated with the detail
     * @deprecated Why do you need this?
     */
    Bridge getBridge();

    /**
     * Tells whether the exception class is a userdefined or a WSDL exception.
     * A WSDL exception class follows the pattern defined in JSR 224. According to that
     * a WSDL exception class must have:
     *
     * <code>public WrapperException()String message, FaultBean){}</code>
     *
     * and accessor method
     *
     * <code>public FaultBean getFaultInfo();</code>
     */
    ExceptionType getExceptionType();

    /**
     * Gives the wsdl:portType/wsdl:operation/wsdl:fault@message value - that is the wsdl:message
     * referenced by wsdl:fault
     */
    String getMessageName();

    /**
     * Gives the {@link com.sun.xml.internal.ws.spi.db.TypeInfo} of the detail
     */
    TypeInfo getDetailType();
}
