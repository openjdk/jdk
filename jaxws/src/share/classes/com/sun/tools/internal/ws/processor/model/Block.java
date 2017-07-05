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


import com.sun.tools.internal.ws.wsdl.framework.Entity;
import com.sun.tools.internal.ws.wsdl.document.*;

import javax.xml.namespace.QName;

/**
 *
 * @author WS Development Team
 */
public class Block extends ModelObject {

    public static final int UNBOUND = 0;
    public static final int BODY   = 1;
    public static final int HEADER = 2;
    public static final int ATTACHMENT = 3;

    public Block(QName name, AbstractType type, Entity entity) {
        super(entity);
        this.name = name;
        this.type = type;
    }

    public QName getName() {
        return name;
    }

    public AbstractType getType() {
        return type;
    }

    public void setType(AbstractType type) {
        this.type = type;
    }

    public int getLocation() {
        return location;
    }

    public void setLocation(int i) {
        location = i;
    }

    public void accept(ModelVisitor visitor) throws Exception {
        visitor.visit(this);
    }

    private final QName name;
    private AbstractType type;
    private int location;
}
