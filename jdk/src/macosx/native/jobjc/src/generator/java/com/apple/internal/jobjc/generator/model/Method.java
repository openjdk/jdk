/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator.model;

import java.util.Set;
import org.w3c.dom.Node;
import com.apple.jobjc.SEL;
import com.apple.internal.jobjc.generator.Utils;

public class Method extends Function {
    public final boolean isClassMethod;

    public String javaName;

    public boolean ignore;
    public String suggestion;

    public Method(final Node node, final Framework fw) {
        super(node, getAttr(node, "selector"), fw);
        this.javaName = SEL.jMethodName(name);
        this.isClassMethod = "true".equals(getAttr(node, "class_method"));
        this.ignore = "true".equals(getAttr(node, "ignore"));
        this.suggestion = getAttr(node, "suggestion");
    }

    @Override public String getJavaName(){ return javaName; }

    @Override public String toString() {
        return returnValue + " " + super.toString() + args;
    }

    public boolean returnTypeEquals(final ReturnValue returnValueIn) {
        return returnValue.type.getJType().getJavaReturnTypeName().equals(returnValueIn.type.getJType().getJavaReturnTypeName());
    }

    public void disambiguateNameAndArgs(final Clazz parentClazz, final Set<String> existingMethodNames) {
        javaName = getDisambiguatedNameFor(parentClazz, javaName, existingMethodNames);
        disambiguateArgs();
    }

    private String getDisambiguatedNameFor(final Clazz parentClazz, final String proposedName, final Set<String> existingNames) {
        // Does this method override a parent class method and change the return type? Example: IOBlueToothSDPUUID length
        {
            final Method superClassMethod = parentClazz.getParentMethodMatchingName(proposedName);
            if (superClassMethod != null && !superClassMethod.returnValue.equals(returnValue)) {
                final String usingReturnType = createMethodNameAppendingReturnType(proposedName);
                if(existingNames.add(usingReturnType))
                    return usingReturnType;
            }
        }

        if(existingNames.add(proposedName))
            return proposedName;

        final String usingReturnType = createMethodNameAppendingReturnType(proposedName);
        if(existingNames.add(usingReturnType))
            return usingReturnType;

        throw new RuntimeException("Unable to disambiguate method: " + this);
    }

    private String createMethodNameAppendingReturnType(final String proposedName) {
        return proposedName + Utils.capitalize(returnValue.type.getJType().getAppendableDescription().replaceAll(".+\\.", ""));
    }
}
