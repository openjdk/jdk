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

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author  dkohlert
 */
public class FaultInfo {
    public String beanName;
    public TypeMoniker beanTypeMoniker;
    public boolean isWSDLException;
    public QName elementName;
    public List<MemberInfo> members;

    /** Creates a new instance of FaultInfo */
    public FaultInfo() {
    }
    public FaultInfo(String beanName) {
        this.beanName = beanName;
    }
    public FaultInfo(String beanName, boolean isWSDLException) {
        this.beanName = beanName;
        this.isWSDLException = isWSDLException;
    }
    public FaultInfo(TypeMoniker typeMoniker, boolean isWSDLException) {
        this.beanTypeMoniker = typeMoniker;
        this.isWSDLException = isWSDLException;
    }

    public void setIsWSDLException(boolean isWSDLException) {
        this.isWSDLException = isWSDLException;
    }

    public boolean isWSDLException() {
        return isWSDLException;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public String getBeanName() {
        return beanName;
    }

    public void setElementName(QName elementName) {
        this.elementName =  elementName;
    }

    public QName getElementName() {
        return elementName;
    }
    public void setBeanTypeMoniker(TypeMoniker typeMoniker) {
        this.beanTypeMoniker = typeMoniker;
    }
    public TypeMoniker getBeanTypeMoniker() {
        return beanTypeMoniker;
    }
    public List<MemberInfo> getMembers() {
        return members;
    }
    public void setMembers(List<MemberInfo> members) {
        this.members = members;
    }
    public void addMember(MemberInfo member) {
        if (members == null)
            members = new ArrayList<MemberInfo>();
        members.add(member);
    }
}
