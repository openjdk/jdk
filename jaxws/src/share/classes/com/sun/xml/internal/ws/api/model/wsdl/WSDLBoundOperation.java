/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.api.model.wsdl;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import javax.jws.WebParam.Mode;
import javax.xml.namespace.QName;
import java.util.Map;

/**
 * Abstracts wsdl:binding/wsdl:operation. It can be used to determine the parts and their binding.
 *
 * @author Vivek Pandey
 */
public interface WSDLBoundOperation extends WSDLObject, WSDLExtensible {
    /**
     * Short-cut for {@code getOperation().getName()}
     */
    @NotNull QName getName();

    /**
     * Gives soapbinding:operation@soapAction value. soapbinding:operation@soapAction is optional attribute.
     * If not present an empty String is returned as per BP 1.1 R2745.
     */
    @NotNull String getSOAPAction();

    /**
     * Gets the wsdl:portType/wsdl:operation model - {@link WSDLOperation},
     * associated with this binding operation.
     *
     * @return always same {@link WSDLOperation}
     */
    @NotNull WSDLOperation getOperation();

    /**
     * Gives the owner {@link WSDLBoundPortType}
     */
    @NotNull WSDLBoundPortType getBoundPortType();

    /**
     * Gets the soapbinding:binding/operation/wsaw:Anonymous. A default value of OPTIONAL is returned.
     *
     * @return Anonymous value of the operation
     */
    ANONYMOUS getAnonymous();

    enum ANONYMOUS { optional, required, prohibited }

    /**
     * Gets {@link WSDLPart} for the given wsdl:input or wsdl:output part
     *
     * @return null if no part is found
     */
    @Nullable WSDLPart getPart(@NotNull String partName, @NotNull Mode mode);

    /**
     * Gets all inbound {@link WSDLPart} by its {@link WSDLPart#getName() name}.
     */
    @NotNull Map<String,WSDLPart> getInParts();

    /**
     * Gets all outbound {@link WSDLPart} by its {@link WSDLPart#getName() name}.
     */
    @NotNull Map<String,WSDLPart> getOutParts();

    /**
     * Gets all the {@link WSDLFault} bound to this operation.
     */
    @NotNull Iterable<? extends WSDLBoundFault> getFaults();

    /**
     * Gets the payload QName of the request message.
     *
     * <p>
     * It's possible for an operation to define no body part, in which case
     * this method returns null.
     */
    @Nullable QName getReqPayloadName();

}
