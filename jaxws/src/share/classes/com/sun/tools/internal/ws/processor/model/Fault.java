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
package com.sun.tools.internal.ws.processor.model;

import com.sun.codemodel.internal.JClass;
import com.sun.tools.internal.ws.processor.generator.GeneratorUtil;
import com.sun.tools.internal.ws.processor.model.java.JavaException;
import com.sun.tools.internal.ws.wsdl.framework.Entity;

import javax.xml.namespace.QName;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author WS Development Team
 */
public class Fault extends ModelObject {

    public Fault(Entity entity) {
        super(entity);
    }

    public Fault(String name, Entity entity) {
        super(entity);
        this.name = name;
        parentFault = null;
    }

    public String getName() {
        return name;
    }

    public void setName(String s) {
        name = s;
    }

    public Block getBlock() {
        return block;
    }

    public void setBlock(Block b) {
        block = b;
    }

    public JavaException getJavaException() {
        return javaException;
    }

    public void setJavaException(JavaException e) {
        javaException = e;
    }

    public void accept(ModelVisitor visitor) throws Exception {
        visitor.visit(this);
    }

    public Fault getParentFault() {
        return parentFault;
    }

    public Iterator getSubfaults() {
        if (subfaults.size() == 0) {
            return null;
        }
        return subfaults.iterator();
    }

    /* serialization */
    public Set getSubfaultsSet() {
        return subfaults;
    }

    /* serialization */
    public void setSubfaultsSet(Set s) {
        subfaults = s;
    }

    public Iterator getAllFaults() {
        Set allFaults = getAllFaultsSet();
        if (allFaults.size() == 0) {
            return null;
        }
        return allFaults.iterator();
    }

    public Set getAllFaultsSet() {
        Set transSet = new HashSet();
        Iterator iter = subfaults.iterator();
        while (iter.hasNext()) {
            transSet.addAll(((Fault)iter.next()).getAllFaultsSet());
        }
        transSet.addAll(subfaults);
        return transSet;
    }

    public QName getElementName() {
        return elementName;
    }

    public void setElementName(QName elementName) {
        this.elementName = elementName;
    }

    public String getJavaMemberName() {
        return javaMemberName;
    }

    public void setJavaMemberName(String javaMemberName) {
        this.javaMemberName = javaMemberName;
    }

    /**
     * @return Returns the wsdlFault.
     */
    public boolean isWsdlException() {
            return wsdlException;
    }
    /**
     * @param wsdlFault The wsdlFault to set.
     */
    public void setWsdlException(boolean wsdlFault) {
            this.wsdlException = wsdlFault;
    }

    public void setExceptionClass(JClass ex){
        exceptionClass = ex;
    }

    public JClass getExceptionClass(){
        return exceptionClass;
    }

    private boolean wsdlException = true;
    private String name;
    private Block block;
    private JavaException javaException;
    private Fault parentFault;
    private Set subfaults = new HashSet();
    private QName elementName = null;
    private String javaMemberName = null;
    private JClass exceptionClass;

    public String getWsdlFaultName() {
        return wsdlFaultName;
    }

    public void setWsdlFaultName(String wsdlFaultName) {
        this.wsdlFaultName = wsdlFaultName;
    }

    private String wsdlFaultName;
}
