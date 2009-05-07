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

import java.util.ArrayList;
import java.util.List;
import java.lang.annotation.Annotation;

/**
 * Enum Constant.
 *
 * When used as an {@link JExpression}, this object represents a reference to the enum constant.
 *
 * @author
 *     Bhakti Mehta (Bhakti.Mehta@sun.com)
 */
public final class JEnumConstant extends JExpressionImpl implements JDeclaration, JAnnotatable {

    /**
     * The constant.
     */
    private final String name;
    /**
     * The enum class.
     */
    private final JDefinedClass type;
    /**
     * javadoc comments, if any.
     */
    private JDocComment jdoc = null;

    /**
     * Annotations on this variable. Lazily created.
     */
    private List<JAnnotationUse> annotations = null;


    /**
     * List of the constructor argument expressions.
     * Lazily constructed.
     */
    private List<JExpression> args = null;

    JEnumConstant(JDefinedClass type,String name) {
        this.name = name;
        this.type = type;
    }

    /**
     *  Add an expression to this constructor's argument list
     *
     * @param arg
     *        Argument to add to argument list
     */
    public JEnumConstant arg(JExpression arg) {
        if(arg==null)   throw new IllegalArgumentException();
        if(args==null)
            args = new ArrayList<JExpression>();
        args.add(arg);
        return this;
    }

    /**
     * Returns the name of this constant.
     *
     * @return never null.
     */
    public String getName() {
        return this.type.fullName().concat(".").concat(this.name);
    }

    /**
     * Creates, if necessary, and returns the enum constant javadoc.
     *
     * @return JDocComment containing javadocs for this constant.
     */
    public JDocComment javadoc() {
        if (jdoc == null)
            jdoc = new JDocComment(type.owner());
        return jdoc;
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

    public void declare(JFormatter f) {
        if( jdoc != null )
            f.nl().g( jdoc );
        if (annotations != null) {
            for( int i=0; i<annotations.size(); i++ )
                f.g(annotations.get(i)).nl();
        }
        f.id(name);
        if(args!=null) {
            f.p('(').g(args).p(')');
        }
    }

    public void generate(JFormatter f) {
        f.t(type).p('.').p(name);
    }
}
