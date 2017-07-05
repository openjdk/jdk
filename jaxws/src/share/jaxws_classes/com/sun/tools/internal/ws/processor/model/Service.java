/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.internal.ws.processor.model.java.JavaInterface;
import com.sun.tools.internal.ws.wsdl.framework.Entity;

import javax.xml.namespace.QName;
import java.util.*;

/**
 *
 * @author WS Development Team
 */
public class Service extends ModelObject {

    public Service(Entity entity) {
        super(entity);
    }

    public Service(QName name, JavaInterface javaInterface, Entity entity) {
        super(entity);
        this.name = name;
        this.javaInterface = javaInterface;
    }

    public QName getName() {
        return name;
    }

    public void setName(QName n) {
        name = n;
    }

    public void addPort(Port port) {
        if (portsByName.containsKey(port.getName())) {
            throw new ModelException("model.uniqueness");
        }
        ports.add(port);
        portsByName.put(port.getName(), port);
    }


    public Port getPortByName(QName n) {
        if (portsByName.size() != ports.size()) {
            initializePortsByName();
        }
        return (Port) portsByName.get(n);
    }

    /* serialization */
    public List<Port> getPorts() {
        return ports;
    }

    /* serialization */
    public void setPorts(List<Port> m) {
        ports = m;
//        initializePortsByName();
    }

    private void initializePortsByName() {
        portsByName = new HashMap();
        if (ports != null) {
            for (Iterator iter = ports.iterator(); iter.hasNext();) {
                Port port = (Port) iter.next();
                if (port.getName() != null &&
                    portsByName.containsKey(port.getName())) {

                    throw new ModelException("model.uniqueness");
                }
                portsByName.put(port.getName(), port);
            }
        }
    }

    public JavaInterface getJavaIntf() {
        return getJavaInterface();
    }

    public JavaInterface getJavaInterface() {
        return javaInterface;
    }

    public void setJavaInterface(JavaInterface i) {
        javaInterface = i;
    }

    public void accept(ModelVisitor visitor) throws Exception {
        visitor.visit(this);
    }

    private QName name;
    private List<Port> ports = new ArrayList();
    private Map<QName, Port> portsByName = new HashMap<QName, Port>();
    private JavaInterface javaInterface;
}
