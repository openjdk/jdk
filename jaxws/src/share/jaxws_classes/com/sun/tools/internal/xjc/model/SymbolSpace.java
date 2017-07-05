/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.model;

import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JType;

/**
 * Symbol space for ID/IDREF.
 *
 * In XJC, the whole ID space is considered to be splitted into
 * one or more "symbol space". For an IDREF to match an ID, we impose
 * additional restriction to the one stated in the XML rec.
 *
 * <p>
 * That is, XJC'll require that the IDREF belongs to the same symbol
 * space as the ID. Having this concept allows us to assign more
 * specific type to IDREF.
 *
 * <p>
 * See the design document for detail.
 *
 * @author
 *    <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public class SymbolSpace
{
    private JType type;
    private final JCodeModel codeModel;

    public SymbolSpace( JCodeModel _codeModel ) {
        this.codeModel = _codeModel;
    }

    /**
     * Gets the Java type of this symbol space.
     *
     * <p>
     * A symbol space is said to have a Java type X if all classes
     * pointed by IDs belonging to this symbol space are assignable
     * to X.
     */
    public JType getType() {
        if(type==null)  return codeModel.ref(Object.class);
        return type;
    }

    public void setType( JType _type ) {
        if( this.type==null )
            this.type = _type;
    }

    public String toString() {
        if(type==null)  return "undetermined";
        else            return type.name();
    }
}
