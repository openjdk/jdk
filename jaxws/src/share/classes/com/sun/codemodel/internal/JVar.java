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

package com.sun.codemodel.internal;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;


/**
 * Variables and fields.
 */

public class JVar extends JExpressionImpl implements JDeclaration, JAssignmentTarget, JAnnotatable {

    /**
     * Modifiers.
     */
    private JMods mods;

    /**
     * JType of the variable
     */
    private JType type;

    /**
     * Name of the variable
     */
    private String name;

    /**
     * Initialization of the variable in its declaration
     */
    private JExpression init;

    /**
     * Annotations on this variable. Lazily created.
     */
    private List<JAnnotationUse> annotations = null;



    /**
     * JVar constructor
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
    JVar(JMods mods, JType type, String name, JExpression init) {
        this.mods = mods;
        this.type = type;
        this.name = name;
        this.init = init;
    }


    /**
     * Initialize this variable
     *
     * @param init
     *        JExpression to be used to initialize this field
     */
    public JVar init(JExpression init) {
        this.init = init;
        return this;
    }

    /**
     * Get the name of this variable
     *
     * @return Name of the variable
     */
    public String name() {
        return name;
    }

    /**
     * Changes the name of this variable.
     */
    public void name(String name) {
        if(!JJavaName.isJavaIdentifier(name))
            throw new IllegalArgumentException();
        this.name = name;
    }

    /**
     * Return the type of this variable.
     * @return
     *      always non-null.
     */
    public JType type() {
        return type;
    }

    /**
     * @return
     *      the current modifiers of this method.
     *      Always return non-null valid object.
     */
    public JMods mods() {
        return mods;
    }

    /**
     * Sets the type of this variable.
     *
     * @param newType
     *      must not be null.
     *
     * @return
     *      the old type value. always non-null.
     */
    public JType type(JType newType) {
        JType r = type;
        if(newType==null)
            throw new IllegalArgumentException();
        type = newType;
        return r;
    }


    /**
     * Adds an annotation to this variable.
     * @param clazz
     *          The annotation class to annotate the field with
     */
    public JAnnotationUse annotate(JClass clazz){
        if(annotations==null)
           annotations = new ArrayList<JAnnotationUse>();
        JAnnotationUse a = new JAnnotationUse(clazz);
        annotations.add(a);
        return a;
    }

    /**
     * Adds an annotation to this variable.
     *
     * @param clazz
     *          The annotation class to annotate the field with
     */
    public JAnnotationUse annotate(Class <? extends Annotation> clazz){
        return annotate(type.owner().ref(clazz));
    }

    public <W extends JAnnotationWriter> W annotate2(Class<W> clazz) {
        return TypedAnnotationWriter.create(clazz,this);
    }

    protected boolean isAnnotated() {
        return annotations!=null;
    }

    public void bind(JFormatter f) {
        if (annotations != null){
            for( int i=0; i<annotations.size(); i++ )
                f.g(annotations.get(i)).nl();
        }
        f.g(mods).g(type).id(name);
        if (init != null)
            f.p('=').g(init);
    }

    public void declare(JFormatter f) {
        f.b(this).p(';').nl();
    }

    public void generate(JFormatter f) {
        f.id(name);
    }


    public JExpression assign(JExpression rhs) {
                return JExpr.assign(this,rhs);
    }
    public JExpression assignPlus(JExpression rhs) {
                return JExpr.assignPlus(this,rhs);
    }

}
