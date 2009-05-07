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
package com.sun.tools.internal.ws.processor.modeler.annotation;

import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.ParameterDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.tools.internal.ws.processor.model.Model;
import com.sun.tools.internal.ws.processor.model.Operation;
import com.sun.tools.internal.ws.processor.model.Port;
import com.sun.tools.internal.ws.processor.model.Service;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPUse;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


/**
 *
 * @author  dkohlert
 */
public class AnnotationProcessorContext {

    private Map<String, SEIContext> seiContextMap;
    private int round = 1;
    private boolean modelCompleted = false;

    /** Creates a new instance of AnnotationProcessorContext */
    public AnnotationProcessorContext() {
        seiContextMap = new HashMap<String, SEIContext>();
    }

    public void addSEIContext(String seiName, SEIContext seiContext) {
        seiContextMap.put(seiName, seiContext);
    }

    public SEIContext getSEIContext(String seiName) {
        SEIContext context =  seiContextMap.get(seiName);
        if (context == null) {
            context = new SEIContext(seiName);
            addSEIContext(seiName, context);
        }
        return context;
    }

    public SEIContext getSEIContext(TypeDeclaration d) {
        SEIContext context = getSEIContext(d.getQualifiedName());
        return context;
    }

    public Collection<SEIContext> getSEIContexts() {
        return seiContextMap.values();
    }

    public int getRound() {
        return round;
    }

    public void incrementRound() {
        round++;
    }

    public static boolean isEncoded(Model model) {
        if (model == null)
            return false;
        for (Service service : model.getServices()) {
            for (Port port : service.getPorts()) {
                for (Operation operation : port.getOperations()) {
                    if (operation.getUse() != null && operation.getUse().equals(SOAPUse.LITERAL))
                        return false;
                }
            }
        }
        return true;
    }

    public void setModelCompleted(boolean modelCompleted) {
        this.modelCompleted = modelCompleted;
    }

    public boolean isModelCompleted() {
        return modelCompleted;
    }

    public static class SEIContext {
        private Map<String, WrapperInfo> reqOperationWrapperMap;
        private Map<String, WrapperInfo> resOperationWrapperMap;
        private Map<String, FaultInfo> exceptionBeanMap;

        private String seiName;
        private String seiImplName;
        private boolean implementsSEI = false;
        private String namespaceURI = null;

        public SEIContext(String seiName) {
            reqOperationWrapperMap = new HashMap<String, WrapperInfo>();
            resOperationWrapperMap = new HashMap<String, WrapperInfo>();
            exceptionBeanMap = new HashMap<String,FaultInfo>();
            this.seiName = seiName;
        }

        public void setImplementsSEI(boolean implementsSEI) {
            this.implementsSEI = implementsSEI;
        }

        public boolean getImplementsSEI() {
            return implementsSEI;
        }

        public void setNamespaceURI(String namespaceURI) {
            this.namespaceURI = namespaceURI;
        }

        public String getNamespaceURI() {
            return namespaceURI;
        }

        public String getSEIImplName() {
            return seiImplName;
        }

        public void setSEIImplName(String implName) {
            seiImplName = implName;
        }

        public void setReqWrapperOperation(MethodDeclaration method, WrapperInfo wrapperInfo) {
            reqOperationWrapperMap.put(methodToString(method), wrapperInfo);
        }

        public WrapperInfo getReqOperationWrapper(MethodDeclaration method) {
            return reqOperationWrapperMap.get(methodToString(method));
        }

        public void setResWrapperOperation(MethodDeclaration method, WrapperInfo wrapperInfo) {
            resOperationWrapperMap.put(methodToString(method), wrapperInfo);
        }

        public WrapperInfo getResOperationWrapper(MethodDeclaration method) {
            return resOperationWrapperMap.get(methodToString(method));
        }

        public String methodToString(MethodDeclaration method) {
            StringBuffer buf = new StringBuffer(method.getSimpleName());
            for (ParameterDeclaration param : method.getParameters())
                buf.append(";"+param.getType().toString());
            return buf.toString();
        }

        public void clearExceptionMap() {
            exceptionBeanMap.clear();
        }

        public void addExceptionBeanEntry(String exception, FaultInfo faultInfo, ModelBuilder builder) {
            exceptionBeanMap.put(exception,faultInfo);
        }

        public FaultInfo getExceptionBeanName(String exception) {
            return exceptionBeanMap.get(exception);
        }
    }
}
