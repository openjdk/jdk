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

package com.sun.tools.internal.ws.processor.util;

import com.sun.tools.internal.ws.processor.model.*;
import com.sun.tools.internal.ws.processor.model.java.JavaInterface;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBType;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBTypeVisitor;
import com.sun.tools.internal.ws.processor.model.jaxb.RpcLitStructure;

import javax.xml.namespace.QName;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * This class writes out a Model as an XML document.
 *
 * @author WS Development Team
 */
public class ClassNameCollector extends ExtendedModelVisitor
    implements JAXBTypeVisitor {

    public ClassNameCollector() {
    }

    public void process(Model model) {
        try {
            _allClassNames = new HashSet();
            _exceptions = new HashSet();
            _wsdlBindingNames = new HashSet();
            _conflictingClassNames = new HashSet();
            _seiClassNames = new HashSet<String>();
            _jaxbGeneratedClassNames = new HashSet<String>();
            _exceptionClassNames = new HashSet<String>();
            _portTypeNames = new HashSet<QName>();
            visit(model);
        } catch (Exception e) {
            e.printStackTrace();
            // fail silently
        } finally {
            _allClassNames = null;
            _exceptions = null;
        }
    }

    public Set getConflictingClassNames() {
        return _conflictingClassNames;
    }

    protected void postVisit(Model model) throws Exception {
        for (Iterator iter = model.getExtraTypes(); iter.hasNext();) {
            visitType((AbstractType)iter.next());
        }
    }

    protected void preVisit(Service service) throws Exception {
        registerClassName(
            ((JavaInterface)service.getJavaInterface()).getName());
        // We don't generate Impl classes, commenting it out.
        // Otherwise, it would cause naming conflicts
        //registerClassName(
        //    ((JavaInterface)service.getJavaInterface()).getImpl());
    }

    protected void processPort11x(Port port){
        QName wsdlBindingName = (QName) port.getProperty(
            ModelProperties.PROPERTY_WSDL_BINDING_NAME);
        if (!_wsdlBindingNames.contains(wsdlBindingName)) {

            // multiple ports can share a binding without causing a conflict
            registerClassName(port.getJavaInterface().getName());
        }
        registerClassName((String) port.getProperty(
            ModelProperties.PROPERTY_STUB_CLASS_NAME));
        registerClassName((String) port.getProperty(
            ModelProperties.PROPERTY_TIE_CLASS_NAME));
    }

    protected void preVisit(Port port) throws Exception {
        QName portTypeName = (QName)port.getProperty(ModelProperties.PROPERTY_WSDL_PORT_TYPE_NAME);
        if(_portTypeNames.contains(portTypeName))
            return;

        //in 2.0, stub/tie class are binding agnostic so they should be per port, that is multiple
        // bindings can share the same port

        addSEIClassName(port.getJavaInterface().getName());
    }

    private void addSEIClassName(String s) {
        _seiClassNames.add(s);
        registerClassName(s);
    }

    protected void postVisit(Port port) throws Exception {
        QName wsdlBindingName = (QName) port.getProperty(
            ModelProperties.PROPERTY_WSDL_BINDING_NAME);
        if (!_wsdlBindingNames.contains(wsdlBindingName)) {
            _wsdlBindingNames.add(wsdlBindingName);
        }

        QName portTypeName = (QName)port.getProperty(ModelProperties.PROPERTY_WSDL_PORT_TYPE_NAME);
        if(!_portTypeNames.contains(portTypeName)){
            _portTypeNames.add(portTypeName);
        }
    }

    protected boolean shouldVisit(Port port) {
        QName wsdlBindingName = (QName) port.getProperty(
            ModelProperties.PROPERTY_WSDL_BINDING_NAME);
        return !_wsdlBindingNames.contains(wsdlBindingName);
    }

    protected void preVisit(Fault fault) throws Exception {
        if (!_exceptions.contains(fault.getJavaException())) {

            /* the same exception can be used in several faults, but that
             * doesn't mean that there is a conflict
             */
            _exceptions.add(fault.getJavaException());
            addExceptionClassName(fault.getJavaException().getName());

            for (Iterator iter = fault.getSubfaults();
                iter != null && iter.hasNext();) {

                Fault subfault = (Fault) iter.next();
                preVisit(subfault);
            }
        }
    }

    private void addExceptionClassName(String name) {
        if(_allClassNames.contains(name))
            _exceptionClassNames.add(name);
        registerClassName(name);
        //To change body of created methods use File | Settings | File Templates.
    }

    protected void visitBodyBlock(Block block) throws Exception {
        visitBlock(block);
    }

    protected void visitHeaderBlock(Block block) throws Exception {
        visitBlock(block);
    }

    protected void visitFaultBlock(Block block) throws Exception {
    }

    protected void visitBlock(Block block) throws Exception {
        visitType(block.getType());
    }

    protected void visit(Parameter parameter) throws Exception {
        visitType(parameter.getType());
    }

    private void visitType(AbstractType type) throws Exception {
        if (type != null) {
            if (type instanceof JAXBType)
                visitType((JAXBType)type);
            else if (type instanceof RpcLitStructure)
                visitType((RpcLitStructure)type);
        }
    }


    private void visitType(JAXBType type) throws Exception {
        type.accept(this);
    }

    private void visitType(RpcLitStructure type) throws Exception {
        type.accept(this);
    }
    private void registerClassName(String name) {
        if (name == null || name.equals("")) {
            return;
        }
        if (_allClassNames.contains(name)) {
            _conflictingClassNames.add(name);
        } else {
            _allClassNames.add(name);
        }
    }

    public Set<String> getSeiClassNames() {
        return _seiClassNames;
    }

    private Set<String> _seiClassNames;

    public Set<String> getJaxbGeneratedClassNames() {
        return _jaxbGeneratedClassNames;
    }

    private Set<String> _jaxbGeneratedClassNames;


    public Set<String> getExceptionClassNames() {
        return _exceptionClassNames;
    }

    private Set<String> _exceptionClassNames;
    boolean doneVisitingJAXBModel = false;
    public void visit(JAXBType type) throws Exception {
        if(!doneVisitingJAXBModel && type.getJaxbModel() != null){
            Set<String> classNames = type.getJaxbModel().getGeneratedClassNames();
            for(String className : classNames){
                addJAXBGeneratedClassName(className);
            }
            doneVisitingJAXBModel = true;
        }
    }

    public void visit(RpcLitStructure type) throws Exception {
        if(!doneVisitingJAXBModel){
            Set<String> classNames = type.getJaxbModel().getGeneratedClassNames();
            for(String className : classNames){
                addJAXBGeneratedClassName(className);
            }
            doneVisitingJAXBModel = true;
        }
    }


    private void addJAXBGeneratedClassName(String name) {
        _jaxbGeneratedClassNames.add(name);
        registerClassName(name);
    }

    private Set _allClassNames;
    private Set _exceptions;
    private Set _wsdlBindingNames;
    private Set _conflictingClassNames;
    private Set<QName> _portTypeNames;
}
