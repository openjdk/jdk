/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.reflect.generics.tree;

import sun.reflect.generics.visitor.TypeTreeVisitor;

public class SimpleClassTypeSignature implements FieldTypeSignature {
    private boolean dollar;
    private String name;
    private TypeArgument[] typeArgs;

    private SimpleClassTypeSignature(String n, boolean dollar, TypeArgument[] tas) {
        name = n;
        this.dollar = dollar;
        typeArgs = tas;
    }

    public static SimpleClassTypeSignature make(String n,
                                                boolean dollar,
                                                TypeArgument[] tas){
        return new SimpleClassTypeSignature(n, dollar, tas);
    }

    /*
     * Should a '$' be used instead of '.' to separate this component
     * of the name from the previous one when composing a string to
     * pass to Class.forName; in other words, is this a transition to
     * a nested class.
     */
    public boolean getDollar(){return dollar;}
    public String getName(){return name;}
    public TypeArgument[] getTypeArguments(){return typeArgs;}

    public void accept(TypeTreeVisitor<?> v){
        v.visitSimpleClassTypeSignature(this);
    }
}
