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

import com.sun.tools.internal.ws.processor.model.Parameter;

/**
 *
 * @author WS Development Team
 */
public class JavaParameter {

    public JavaParameter() {}

    public JavaParameter(String name, JavaType type, Parameter parameter) {
        this(name, type, parameter, false);
    }

    public JavaParameter(String name, JavaType type, Parameter parameter,
        boolean holder) {

        this.name = name;
        this.type = type;
        this.parameter = parameter;
        this.holder = holder;
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

    public Parameter getParameter() {
        return parameter;
    }

    public void setParameter(Parameter p) {
        parameter = p;
    }

    public boolean isHolder() {
        return holder;
    }

    public void setHolder(boolean b) {
        holder = b;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    private String name;
    private JavaType type;
    private Parameter parameter;
    private boolean holder;
    private String holderName;
}
