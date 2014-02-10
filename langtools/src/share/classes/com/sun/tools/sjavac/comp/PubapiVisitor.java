/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.sjavac.comp;

import java.util.Iterator;
import java.util.List;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner6;

/** Utility class that constructs a textual representation
 * of the public api of a class.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class PubapiVisitor extends ElementScanner6<Void, Void> {

    StringBuffer sb;
    // Important that it is 1! Part of protocol over wire, silly yes.
    // Fix please.
    int indent = 1;

    public PubapiVisitor(StringBuffer sb) {
        this.sb = sb;
    }

    String depth(int l) {
        return "                                              ".substring(0, l);
    }

    @Override
    public Void visitType(TypeElement e, Void p) {
        if (e.getModifiers().contains(Modifier.PUBLIC)
            || e.getModifiers().contains(Modifier.PROTECTED))
        {
            sb.append(depth(indent) + "TYPE " + e.getQualifiedName() + "\n");
            indent += 2;
            Void v = super.visitType(e, p);
            indent -= 2;
            return v;
        }
        return null;
    }

    @Override
    public Void visitVariable(VariableElement e, Void p) {
        if (e.getModifiers().contains(Modifier.PUBLIC)
            || e.getModifiers().contains(Modifier.PROTECTED)) {
            sb.append(depth(indent)).append("VAR ")
                    .append(makeVariableString(e)).append("\n");
        }
        // Safe to not recurse here, because the only thing
        // to visit here is the constructor of a variable declaration.
        // If it happens to contain an anonymous inner class (which it might)
        // then this class is never visible outside of the package anyway, so
        // we are allowed to ignore it here.
        return null;
    }

    @Override
    public Void visitExecutable(ExecutableElement e, Void p) {
        if (e.getModifiers().contains(Modifier.PUBLIC)
            || e.getModifiers().contains(Modifier.PROTECTED)) {
            sb.append(depth(indent)).append("METHOD ")
                    .append(makeMethodString(e)).append("\n");
        }
        return null;
    }

    /**
     * Creates a String representation of a method element with everything
     * necessary to track all public aspects of it in an API.
     * @param e Element to create String for.
     * @return String representation of element.
     */
    protected String makeMethodString(ExecutableElement e) {
        StringBuilder result = new StringBuilder();
        for (Modifier modifier : e.getModifiers()) {
            result.append(modifier.toString());
            result.append(" ");
        }
        result.append(e.getReturnType().toString());
        result.append(" ");
        result.append(e.toString());

        List<? extends TypeMirror> thrownTypes = e.getThrownTypes();
        if (!thrownTypes.isEmpty()) {
            result.append(" throws ");
            for (Iterator<? extends TypeMirror> iterator = thrownTypes
                    .iterator(); iterator.hasNext();) {
                TypeMirror typeMirror = iterator.next();
                result.append(typeMirror.toString());
                if (iterator.hasNext()) {
                    result.append(", ");
                }
            }
        }
        return result.toString();
    }

    /**
     * Creates a String representation of a variable element with everything
     * necessary to track all public aspects of it in an API.
     * @param e Element to create String for.
     * @return String representation of element.
     */
    protected String makeVariableString(VariableElement e) {
        StringBuilder result = new StringBuilder();
        for (Modifier modifier : e.getModifiers()) {
            result.append(modifier.toString());
            result.append(" ");
        }
        result.append(e.asType().toString());
        result.append(" ");
        result.append(e.toString());
        Object value = e.getConstantValue();
        if (value != null) {
            result.append(" = ");
            if (e.asType().toString().equals("char")) {
                int v = (int)value.toString().charAt(0);
                result.append("'\\u"+Integer.toString(v,16)+"'");
            } else {
                result.append(value.toString());
            }
        }
        return result.toString();
    }
}
