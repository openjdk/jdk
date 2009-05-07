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

package com.sun.tools.internal.ws.processor.model.java;

import com.sun.tools.internal.ws.processor.model.ModelException;
import com.sun.tools.internal.ws.util.ClassNameInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author WS Development Team
 */
public class JavaInterface {

    public JavaInterface() {}

    public JavaInterface(String name) {
        this(name, null);
    }

    public JavaInterface(String name, String impl) {
        this.realName = name;
        this.name = name.replace('$', '.');
        this.impl = impl;
    }

    public String getName() {
        return name;
    }

    public String getFormalName() {
        return name;
    }

    public void setFormalName(String s) {
        name = s;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String s) {
        realName = s;
    }

    public String getImpl() {
        return impl;
    }

    public void setImpl(String s) {
        impl = s;
    }

    public Iterator getMethods() {
        return methods.iterator();
    }

    public boolean hasMethod(JavaMethod method) {
        for (int i=0; i<methods.size();i++) {
            if (method.equals(((JavaMethod)methods.get(i)))) {
                return true;
            }
        }
        return false;
    }

    public void addMethod(JavaMethod method) {

        if (hasMethod(method)) {
            throw new ModelException("model.uniqueness");
        }
        methods.add(method);
    }

    /* serialization */
    public List getMethodsList() {
        return methods;
    }

    /* serialization */
    public void setMethodsList(List l) {
        methods = l;
    }

    public boolean hasInterface(String interfaceName) {
        for (int i=0; i<interfaces.size();i++) {
            if (interfaceName.equals((String)interfaces.get(i))) {
                return true;
            }
        }
        return false;
    }

    public void addInterface(String interfaceName) {

        // verify that an exception with this name does not already exist
        if (hasInterface(interfaceName)) {
            return;
        }
        interfaces.add(interfaceName);
    }

    public Iterator getInterfaces() {
        return interfaces.iterator();
    }

    /* serialization */
    public List getInterfacesList() {
        return interfaces;
    }

    /* serialization */
    public void setInterfacesList(List l) {
        interfaces = l;
    }

    public String getSimpleName() {
        return ClassNameInfo.getName(name);
    }

    /* NOTE - all these fields (except "interfaces") were final, but had to
     * remove this modifier to enable serialization
     */
    private String javadoc;

    public String getJavaDoc() {
        return javadoc;
    }

    public void setJavaDoc(String javadoc) {
        this.javadoc = javadoc;
    }

    private String name;
    private String realName;
    private String impl;
    private List methods = new ArrayList();
    private List interfaces = new ArrayList();
}
