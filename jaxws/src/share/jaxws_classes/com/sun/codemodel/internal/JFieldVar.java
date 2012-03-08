/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.codemodel.internal;




/**
 * A field that can have a {@link JDocComment} associated with it
 */
public class JFieldVar extends JVar implements JDocCommentable {

    /**
     * javadoc comments for this JFieldVar
     */
    private JDocComment jdoc = null;

    private final JDefinedClass owner;


    /**
     * JFieldVar constructor
     *
     * @param type
     *        Datatype of this variable
     *
     * @param name
     *        Name of this variable
     *
     * @param init
     *        Value to initialize this variable to
     */
    JFieldVar(JDefinedClass owner, JMods mods, JType type, String name, JExpression init) {
        super( mods, type, name, init );
        this.owner = owner;
    }

    @Override
    public void name(String name) {
        // make sure that the new name is available
        if(owner.fields.containsKey(name))
            throw new IllegalArgumentException("name "+name+" is already in use");
        String oldName = name();
        super.name(name);
        owner.fields.remove(oldName);
        owner.fields.put(name,this);
    }

    /**
     * Creates, if necessary, and returns the class javadoc for this
     * JDefinedClass
     *
     * @return JDocComment containing javadocs for this class
     */
    public JDocComment javadoc() {
        if( jdoc == null )
            jdoc = new JDocComment(owner.owner());
        return jdoc;
    }

    public void declare(JFormatter f) {
        if( jdoc != null )
            f.g( jdoc );
        super.declare( f );
    }


}
