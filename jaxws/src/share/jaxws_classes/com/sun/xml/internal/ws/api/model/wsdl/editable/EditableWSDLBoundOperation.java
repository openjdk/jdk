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

package com.sun.xml.internal.ws.api.model.wsdl.editable;

import java.util.Map;

import javax.jws.WebParam.Mode;
import javax.jws.soap.SOAPBinding.Style;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;

public interface EditableWSDLBoundOperation extends WSDLBoundOperation {

        @Override
    @NotNull EditableWSDLOperation getOperation();

        @Override
    @NotNull EditableWSDLBoundPortType getBoundPortType();

        @Override
    @Nullable EditableWSDLPart getPart(@NotNull String partName, @NotNull Mode mode);

        @Override
    @NotNull Map<String,? extends EditableWSDLPart> getInParts();

        @Override
    @NotNull Map<String,? extends EditableWSDLPart> getOutParts();

        @Override
    @NotNull Iterable<? extends EditableWSDLBoundFault> getFaults();

        /**
         * Add Part
         * @param part Part
         * @param mode Mode
         */
    public void addPart(EditableWSDLPart part, Mode mode);

    /**
     * Add Fault
     * @param fault Fault
     */
    public void addFault(@NotNull EditableWSDLBoundFault fault);

    /**
     * Sets the soapbinding:binding/operation/wsaw:Anonymous.
     *
     * @param anonymous Anonymous value of the operation
     */
        public void setAnonymous(ANONYMOUS anonymous);

        /**
         * Sets input explicit body parts
         * @param b True, if input body part is explicit
         */
        public void setInputExplicitBodyParts(boolean b);

        /**
         * Sets output explicit body parts
         * @param b True, if output body part is explicit
         */
        public void setOutputExplicitBodyParts(boolean b);

        /**
         * Sets fault explicit body parts
         * @param b True, if fault body part is explicit
         */
        public void setFaultExplicitBodyParts(boolean b);

        /**
         * Set request namespace
         * @param ns Namespace
         */
        public void setRequestNamespace(String ns);

        /**
         * Set response namespace
         * @param ns Namespace
         */
        public void setResponseNamespace(String ns);

        /**
         * Set SOAP action
         * @param soapAction SOAP action
         */
        public void setSoapAction(String soapAction);

        /**
         * Set parameter style
         * @param style Style
         */
        public void setStyle(Style style);

        /**
         * Freezes WSDL model to prevent further modification
         * @param root WSDL Model
         */
        public void freeze(EditableWSDLModel root);
}
