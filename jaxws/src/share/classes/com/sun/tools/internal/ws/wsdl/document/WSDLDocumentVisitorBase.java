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

package com.sun.tools.internal.ws.wsdl.document;

import com.sun.tools.internal.ws.wsdl.framework.ExtensionVisitorBase;


/**
 *
 * @author WS Development Team
 */
public class WSDLDocumentVisitorBase extends ExtensionVisitorBase {
    public WSDLDocumentVisitorBase() {
    }

    public void preVisit(Definitions definitions) throws Exception {
    }
    public void postVisit(Definitions definitions) throws Exception {
    }
    public void visit(Import i) throws Exception {
    }
    public void preVisit(Types types) throws Exception {
    }
    public void postVisit(Types types) throws Exception {
    }
    public void preVisit(Message message) throws Exception {
    }
    public void postVisit(Message message) throws Exception {
    }
    public void visit(MessagePart part) throws Exception {
    }
    public void preVisit(PortType portType) throws Exception {
    }
    public void postVisit(PortType portType) throws Exception {
    }
    public void preVisit(Operation operation) throws Exception {
    }
    public void postVisit(Operation operation) throws Exception {
    }
    public void preVisit(Input input) throws Exception {
    }
    public void postVisit(Input input) throws Exception {
    }
    public void preVisit(Output output) throws Exception {
    }
    public void postVisit(Output output) throws Exception {
    }
    public void preVisit(Fault fault) throws Exception {
    }
    public void postVisit(Fault fault) throws Exception {
    }
    public void preVisit(Binding binding) throws Exception {
    }
    public void postVisit(Binding binding) throws Exception {
    }
    public void preVisit(BindingOperation operation) throws Exception {
    }
    public void postVisit(BindingOperation operation) throws Exception {
    }
    public void preVisit(BindingInput input) throws Exception {
    }
    public void postVisit(BindingInput input) throws Exception {
    }
    public void preVisit(BindingOutput output) throws Exception {
    }
    public void postVisit(BindingOutput output) throws Exception {
    }
    public void preVisit(BindingFault fault) throws Exception {
    }
    public void postVisit(BindingFault fault) throws Exception {
    }
    public void preVisit(Service service) throws Exception {
    }
    public void postVisit(Service service) throws Exception {
    }
    public void preVisit(Port port) throws Exception {
    }
    public void postVisit(Port port) throws Exception {
    }
    public void visit(Documentation documentation) throws Exception {
    }
}
