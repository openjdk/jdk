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

package com.sun.tools.internal.ws.processor.model;

import java.util.Iterator;

/**
 *
 * A model visitor incorporating all the logic required to walk through the model.
 *
 * @author WS Development Team
 */
public class ExtendedModelVisitor {

    public ExtendedModelVisitor() {}

    public void visit(Model model) throws Exception {
        preVisit(model);
        for (Service service : model.getServices()) {
            preVisit(service);
            for (Port port : service.getPorts()) {
                preVisit(port);
                if (shouldVisit(port)) {
                    for (Operation operation : port.getOperations()) {
                        preVisit(operation);
                        Request request = operation.getRequest();
                        if (request != null) {
                            preVisit(request);
                            for (Iterator iter4 = request.getHeaderBlocks();
                                iter4.hasNext();) {

                                Block block = (Block) iter4.next();
                                visitHeaderBlock(block);
                            }
                            for (Iterator iter4 = request.getBodyBlocks();
                                iter4.hasNext();) {

                                Block block = (Block) iter4.next();
                                visitBodyBlock(block);
                            }
                            for (Iterator iter4 = request.getParameters();
                                iter4.hasNext();) {

                                Parameter parameter = (Parameter) iter4.next();
                                visit(parameter);
                            }
                            postVisit(request);
                        }

                        Response response = operation.getResponse();
                        if (response != null) {
                            preVisit(response);
                            for (Iterator iter4 = response.getHeaderBlocks();
                                iter4.hasNext();) {

                                Block block = (Block) iter4.next();
                                visitHeaderBlock(block);
                            }
                            for (Iterator iter4 = response.getBodyBlocks();
                                iter4.hasNext();) {

                                Block block = (Block) iter4.next();
                                visitBodyBlock(block);
                            }
                            for (Iterator iter4 = response.getParameters();
                                iter4.hasNext();) {

                                Parameter parameter = (Parameter) iter4.next();
                                visit(parameter);
                            }
                            postVisit(response);
                        }

                        for (Iterator iter4 = operation.getFaults();
                            iter4.hasNext();) {

                            Fault fault = (Fault) iter4.next();
                            preVisit(fault);
                            visitFaultBlock(fault.getBlock());
                            postVisit(fault);
                        }
                        postVisit(operation);
                    }
                }
                postVisit(port);
            }
            postVisit(service);
        }
        postVisit(model);
    }

    protected boolean shouldVisit(Port port) {
        return true;
    }

    // these methods are intended for subclasses
    protected void preVisit(Model model) throws Exception {}
    protected void postVisit(Model model) throws Exception {}
    protected void preVisit(Service service) throws Exception {}
    protected void postVisit(Service service) throws Exception {}
    protected void preVisit(Port port) throws Exception {}
    protected void postVisit(Port port) throws Exception {}
    protected void preVisit(Operation operation) throws Exception {}
    protected void postVisit(Operation operation) throws Exception {}
    protected void preVisit(Request request) throws Exception {}
    protected void postVisit(Request request) throws Exception {}
    protected void preVisit(Response response) throws Exception {}
    protected void postVisit(Response response) throws Exception {}
    protected void preVisit(Fault fault) throws Exception {}
    protected void postVisit(Fault fault) throws Exception {}
    protected void visitBodyBlock(Block block) throws Exception {}
    protected void visitHeaderBlock(Block block) throws Exception {}
    protected void visitFaultBlock(Block block) throws Exception {}
    protected void visit(Parameter parameter) throws Exception {}
}
