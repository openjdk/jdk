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

/*
 * Use is subject to the license terms.
 */
package com.sun.tools.internal.xjc.generator.bean;

import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JDocComment;
import com.sun.codemodel.internal.JMethod;
import com.sun.codemodel.internal.JType;
import com.sun.codemodel.internal.JVar;
import com.sun.tools.internal.xjc.outline.ClassOutline;

/**
 * The back-end may or may not generate the content interface
 * separately from the implementation class. If so, a method
 * needs to be declared on both the interface and the implementation class.
 * <p>
 * This class hides those details and allow callers to declare
 * methods just once.
 *
 * @author Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public abstract class MethodWriter {
    protected final JCodeModel codeModel;

    protected MethodWriter(ClassOutline context) {
        this.codeModel = context.parent().getCodeModel();
    }

    /**
     * Declares a method in both the interface and the implementation.
     *
     * @return
     *      JMethod object that represents a newly declared method
     *      on the implementation class.
     */
    public abstract JMethod declareMethod( JType returnType, String methodName );

    public final JMethod declareMethod( Class returnType, String methodName ) {
        return declareMethod( codeModel.ref(returnType), methodName );
    }

    /**
     * To generate javadoc for the previously declared method, use this method
     * to obtain a {@link JDocComment} object. This may return a value
     * different from declareMethod().javadoc().
     */
    public abstract JDocComment javadoc();


    /**
     * Adds a parameter to the previously declared method.
     *
     * @return
     *      JVar object that represents a newly added parameter
     *      on the implementation class.
     */
    public abstract JVar addParameter( JType type, String name );

    public final JVar addParameter( Class type, String name ) {
        return addParameter( codeModel.ref(type), name );
    }
}
