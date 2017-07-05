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

/**
 *
 * @author WS Development Team
 */
public class JavaStructureMember {

    public JavaStructureMember() {}

    public JavaStructureMember(String name, JavaType type, Object owner) {
        this(name, type, owner, false);
    }
    public JavaStructureMember(String name, JavaType type,
        Object owner, boolean isPublic) {

        this.name = name;
        this.type = type;
        this.owner = owner;
        this.isPublic = isPublic;
        constructorPos = -1;
    }

    public String getName() {
        return name;
    }

    public void setName(String s) {
        name = s;
    }

    public JavaType getType() {
        return type;
    }

    public void setType(JavaType t) {
        type = t;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean b) {
        isPublic = b;
    }

    public boolean isInherited() {
        return isInherited;
    }

    public void setInherited(boolean b) {
        isInherited = b;
    }

    public String getReadMethod() {
        return readMethod;
    }

    public void setReadMethod(String readMethod) {
        this.readMethod = readMethod;
    }

    public String getWriteMethod() {
        return writeMethod;
    }

    public void setWriteMethod(String writeMethod) {
        this.writeMethod = writeMethod;
    }

    public String getDeclaringClass() {
        return declaringClass;
    }
    public void setDeclaringClass(String declaringClass) {
        this.declaringClass = declaringClass;
    }

    public Object getOwner() {
        return owner;
    }

    public void setOwner(Object owner) {
        this.owner = owner;
    }

    public int getConstructorPos() {
        return constructorPos;
    }

    public void setConstructorPos(int idx) {
        constructorPos = idx;
    }

    private String name;
    private JavaType type;
    private boolean isPublic = false;
    private boolean isInherited = false;
    private String readMethod;
    private String writeMethod;
    private String declaringClass;
    private Object owner;
    private int constructorPos;
}
