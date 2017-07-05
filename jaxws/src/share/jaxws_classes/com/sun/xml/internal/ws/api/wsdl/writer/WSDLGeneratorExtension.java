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

package com.sun.xml.internal.ws.api.wsdl.writer;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.txw2.TypedXmlWriter;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.model.CheckedException;
import com.sun.xml.internal.ws.api.model.JavaMethod;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.server.WSEndpoint;

/**
 * This is a callback interface used to extend the WSDLGenerator.  Implementors
 * of this interface can add their own WSDL extensions to the generated WSDL.
 * There are a number of methods that will be invoked allowing the extensions
 * to be generated on various WSDL elements.
 * <p/>
 * The JAX-WS WSDLGenerator uses TXW to serialize the WSDL out to XML.
 * More information about TXW can be located at
 * <a href="http://txw.java.net">http://txw.java.net</a>.
 */
public abstract class WSDLGeneratorExtension {
    /**
     * Called at the very beginning of the process.
     * <p/>
     * This method is invoked so that the root element can be manipulated before
     * any tags have been written. This allows to set e.g. namespace prefixes.
     * <p/>
     * Another purpose of this method is to let extensions know what model
     * we are generating a WSDL for.
     *
     * @param root      This is the root element of the generated WSDL.
     * @param model     WSDL is being generated from this {@link SEIModel}.
     * @param binding   The binding for which we generate WSDL. the binding {@link WSBinding} represents a particular
     *                  configuration of JAXWS. This can be typically be overriden by
     * @param container The entry point to the external environment.
     *                  If this extension is used at the runtime to generate WSDL, you get a {@link Container}
     *                  that was given to {@link WSEndpoint#create}.
     *                  TODO: think about tool side
     * @deprecated
     */
    public void start(@NotNull TypedXmlWriter root, @NotNull SEIModel model, @NotNull WSBinding binding, @NotNull Container container) {
    }

    /**
     * Called before writing </wsdl:defintions>.
     *
     * @param ctxt
     */
    public void end(@NotNull WSDLGenExtnContext ctxt) {
    }

    /**
     * Called at the very beginning of the process.
     * <p/>
     * This method is invoked so that the root element can be manipulated before
     * any tags have been written. This allows to set e.g. namespace prefixes.
     * <p/>
     * Another purpose of this method is to let extensions know what model
     * we are generating a WSDL for.
     *
     * @param ctxt Provides the context for the generator extensions
     */
    public void start(WSDLGenExtnContext ctxt) {
    }

    /**
     * This method is invoked so that extensions to a <code>wsdl:definitions</code>
     * element can be generated.
     *
     * @param definitions This is the <code>wsdl:defintions</code> element that the extension can be added to.
     */
    public void addDefinitionsExtension(TypedXmlWriter definitions) {
    }

    /**
     * This method is invoked so that extensions to a <code>wsdl:service</code>
     * element can be generated.
     *
     * @param service This is the <code>wsdl:service</code> element that the extension can be added to.
     */
    public void addServiceExtension(TypedXmlWriter service) {
    }

    /**
     * This method is invoked so that extensions to a <code>wsdl:port</code>
     * element can be generated.
     *
     * @param port This is the wsdl:port element that the extension can be added to.
     */
    public void addPortExtension(TypedXmlWriter port) {
    }

    /**
     * This method is invoked so that extensions to a <code>wsdl:portType</code>
     * element can be generated.
     * <p/>
     *
     * @param portType This is the wsdl:portType element that the extension can be added to.
     */
    public void addPortTypeExtension(TypedXmlWriter portType) {
    }

    /**
     * This method is invoked so that extensions to a <code>wsdl:binding</code>
     * element can be generated.
     * <p/>
     * <p/>
     * TODO:  Some other information may need to be passed
     *
     * @param binding This is the wsdl:binding element that the extension can be added to.
     */
    public void addBindingExtension(TypedXmlWriter binding) {
    }

    /**
     * This method is invoked so that extensions to a <code>wsdl:portType/wsdl:operation</code>
     * element can be generated.
     *
     * @param operation This is the wsdl:portType/wsdl:operation  element that the
     *                  extension can be added to.
     * @param method    {@link JavaMethod} which captures all the information to generate wsdl:portType/wsdl:operation
     */
    public void addOperationExtension(TypedXmlWriter operation, JavaMethod method) {
    }


    /**
     * This method is invoked so that extensions to a <code>wsdl:binding/wsdl:operation</code>
     * element can be generated.
     *
     * @param operation This is the wsdl:binding/wsdl:operation  element that the
     *                  extension can be added to.
     * @param method    {@link JavaMethod} which captures all the information to generate wsdl:portType/wsdl:operation
     */
    public void addBindingOperationExtension(TypedXmlWriter operation, JavaMethod method) {
    }

    /**
     * This method is invoked so that extensions to an input <code>wsdl:message</code>
     * element can be generated.
     *
     * @param message This is the input wsdl:message element that the
     *                extension can be added to.
     * @param method  {@link JavaMethod} which captures all the information to generate wsdl:portType/wsdl:operation
     */
    public void addInputMessageExtension(TypedXmlWriter message, JavaMethod method) {
    }

    /**
     * This method is invoked so that extensions to an output <code>wsdl:message</code>
     * element can be generated.
     *
     * @param message This is the output wsdl:message element that the
     *                extension can be added to.
     * @param method  {@link JavaMethod} which captures all the information to generate wsdl:portType/wsdl:operation
     */
    public void addOutputMessageExtension(TypedXmlWriter message, JavaMethod method) {
    }


    /**
     * This method is invoked so that extensions to a
     * <code>wsdl:portType/wsdl:operation/wsdl:input</code>
     * element can be generated.
     *
     * @param input  This is the wsdl:portType/wsdl:operation/wsdl:input  element that the
     *               extension can be added to.
     * @param method {@link JavaMethod} which captures all the information to generate wsdl:portType/wsdl:operation
     */
    public void addOperationInputExtension(TypedXmlWriter input, JavaMethod method) {
    }


    /**
     * This method is invoked so that extensions to a <code>wsdl:portType/wsdl:operation/wsdl:output</code>
     * element can be generated.
     *
     * @param output This is the wsdl:portType/wsdl:operation/wsdl:output  element that the
     *               extension can be added to.
     * @param method {@link JavaMethod} which captures all the information to generate wsdl:portType/wsdl:operation
     */
    public void addOperationOutputExtension(TypedXmlWriter output, JavaMethod method) {
    }

    /**
     * This method is invoked so that extensions to a
     * <code>wsdl:binding/wsdl:operation/wsdl:input</code>
     * element can be generated.
     *
     * @param input  This is the wsdl:binding/wsdl:operation/wsdl:input  element that the
     *               extension can be added to.
     * @param method {@link JavaMethod} which captures all the information to generate wsdl:portType/wsdl:operation
     */
    public void addBindingOperationInputExtension(TypedXmlWriter input, JavaMethod method) {
    }


    /**
     * This method is invoked so that extensions to a  <code>wsdl:binding/wsdl:operation/wsdl:output</code>
     * element can be generated.
     *
     * @param output This is the wsdl:binding/wsdl:operation/wsdl:output  element that the
     *               extension can be added to.
     * @param method {@link JavaMethod} which captures all the information to generate wsdl:portType/wsdl:operation
     */
    public void addBindingOperationOutputExtension(TypedXmlWriter output, JavaMethod method) {
    }

    /**
     * This method is invoked so that extensions to a <code>wsdl:binding/wsdl:operation/wsdl:fault</code>
     * element can be generated.
     *
     * @param fault  This is the wsdl:binding/wsdl:operation/wsdl:fault or wsdl:portType/wsdl:output/wsdl:operation/wsdl:fault
     *               element that the extension can be added to.
     * @param method {@link JavaMethod} which captures all the information to generate wsdl:portType/wsdl:operation
     */
    public void addBindingOperationFaultExtension(TypedXmlWriter fault, JavaMethod method, CheckedException ce) {
    }

    /**
     * This method is invoked so that extensions to a <code>wsdl:portType/wsdl:operation/wsdl:fault</code>
     * element can be generated.
     *
     * @param message This is the fault wsdl:message element that the
     *                extension can be added to.
     * @param method  {@link JavaMethod} which captures all the information to generate wsdl:portType/wsdl:operation
     *
     * @param ce      {@link CheckedException} that abstracts wsdl:fault
     */
    public void addFaultMessageExtension(TypedXmlWriter message, JavaMethod method, CheckedException ce) {
    }

    /**
     * This method is invoked so that extensions to a <code>wsdl:portType/wsdl:operation/wsdl:fault</code>
     * element can be generated.
     *
     * @param fault  This is the wsdl:portType/wsdl:operation/wsdl:fault  element that the
     *               extension can be added to.
     * @param method {@link JavaMethod} which captures all the information to generate wsdl:portType/wsdl:operation
     * @param ce     {@link CheckedException} that abstracts wsdl:fault
     */
    public void addOperationFaultExtension(TypedXmlWriter fault, JavaMethod method, CheckedException ce) {
    }

}
