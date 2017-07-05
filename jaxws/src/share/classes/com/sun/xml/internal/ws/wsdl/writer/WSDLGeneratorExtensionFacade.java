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

package com.sun.xml.internal.ws.wsdl.writer;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.txw2.TypedXmlWriter;
import com.sun.xml.internal.ws.api.model.CheckedException;
import com.sun.xml.internal.ws.api.model.JavaMethod;
import com.sun.xml.internal.ws.api.wsdl.writer.WSDLGenExtnContext;
import com.sun.xml.internal.ws.api.wsdl.writer.WSDLGeneratorExtension;

/**
 * {@link WSDLGeneratorExtension} that delegates to
 * multiple {@link WSDLGeneratorExtension}s.
 *
 * <p>
 * This simplifies {@link WSDLGenerator} since it now
 * only needs to work with one {@link WSDLGeneratorExtension}.
 *
 *
 * @author Doug Kohlert
 */
final class WSDLGeneratorExtensionFacade extends WSDLGeneratorExtension {
    private final WSDLGeneratorExtension[] extensions;

    WSDLGeneratorExtensionFacade(WSDLGeneratorExtension... extensions) {
        assert extensions!=null;
        this.extensions = extensions;
    }

    public void start(WSDLGenExtnContext ctxt) {
        for (WSDLGeneratorExtension e : extensions)
            e.start(ctxt);
    }

    public void end(@NotNull WSDLGenExtnContext ctxt) {
        for (WSDLGeneratorExtension e : extensions)
            e.end(ctxt);
    }

    public void addDefinitionsExtension(TypedXmlWriter definitions) {
        for (WSDLGeneratorExtension e : extensions)
            e.addDefinitionsExtension(definitions);
    }

    public void addServiceExtension(TypedXmlWriter service) {
        for (WSDLGeneratorExtension e : extensions)
            e.addServiceExtension(service);
    }

    public void addPortExtension(TypedXmlWriter port) {
        for (WSDLGeneratorExtension e : extensions)
            e.addPortExtension(port);
    }

    public void addPortTypeExtension(TypedXmlWriter portType) {
        for (WSDLGeneratorExtension e : extensions)
            e.addPortTypeExtension(portType);
    }

    public void addBindingExtension(TypedXmlWriter binding) {
        for (WSDLGeneratorExtension e : extensions)
            e.addBindingExtension(binding);
    }

    public void addOperationExtension(TypedXmlWriter operation, JavaMethod method) {
        for (WSDLGeneratorExtension e : extensions)
            e.addOperationExtension(operation, method);
    }

    public void addBindingOperationExtension(TypedXmlWriter operation, JavaMethod method) {
        for (WSDLGeneratorExtension e : extensions)
            e.addBindingOperationExtension(operation, method);
    }

    public void addInputMessageExtension(TypedXmlWriter message, JavaMethod method) {
        for (WSDLGeneratorExtension e : extensions)
            e.addInputMessageExtension(message, method);
    }

    public void addOutputMessageExtension(TypedXmlWriter message, JavaMethod method) {
        for (WSDLGeneratorExtension e : extensions)
            e.addOutputMessageExtension(message, method);
    }

    public void addOperationInputExtension(TypedXmlWriter input, JavaMethod method) {
        for (WSDLGeneratorExtension e : extensions)
            e.addOperationInputExtension(input, method);
    }

    public void addOperationOutputExtension(TypedXmlWriter output, JavaMethod method) {
        for (WSDLGeneratorExtension e : extensions)
            e.addOperationOutputExtension(output, method);
    }

    public void addBindingOperationInputExtension(TypedXmlWriter input, JavaMethod method) {
        for (WSDLGeneratorExtension e : extensions)
            e.addBindingOperationInputExtension(input, method);
    }

    public void addBindingOperationOutputExtension(TypedXmlWriter output, JavaMethod method) {
        for (WSDLGeneratorExtension e : extensions)
            e.addBindingOperationOutputExtension(output, method);
    }

    public void addBindingOperationFaultExtension(TypedXmlWriter fault, JavaMethod method, CheckedException ce) {
        for (WSDLGeneratorExtension e : extensions)
            e.addBindingOperationFaultExtension(fault, method, ce);
    }

    public void addFaultMessageExtension(TypedXmlWriter message, JavaMethod method, CheckedException ce) {
        for (WSDLGeneratorExtension e : extensions)
            e.addFaultMessageExtension(message, method, ce);
    }

    public void addOperationFaultExtension(TypedXmlWriter fault, JavaMethod method, CheckedException ce) {
        for (WSDLGeneratorExtension e : extensions)
            e.addOperationFaultExtension(fault, method, ce);
    }
}
