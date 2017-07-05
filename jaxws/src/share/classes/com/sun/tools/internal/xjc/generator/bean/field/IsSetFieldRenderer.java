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
package com.sun.tools.internal.xjc.generator.bean.field;

import com.sun.tools.internal.xjc.generator.bean.ClassOutlineImpl;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.outline.FieldOutline;

/**
 * FieldRenderer that wraps another field generator
 * and produces isSetXXX unsetXXX methods.
 *
 * <p>
 * This follows the decorator design pattern so that
 * the caller of FieldRenderer can forget about details
 * of the method generation.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class IsSetFieldRenderer implements FieldRenderer {
    private final FieldRenderer core;
    private final boolean generateUnSetMethod;
    private final boolean generateIsSetMethod;

    public IsSetFieldRenderer(
        FieldRenderer core,
        boolean generateUnSetMethod, boolean generateIsSetMethod ) {

        this.core = core;
        this.generateUnSetMethod = generateUnSetMethod;
        this.generateIsSetMethod = generateIsSetMethod;
    }

    public FieldOutline generate(ClassOutlineImpl context, CPropertyInfo prop) {
        return new IsSetField(context,prop,
            core.generate(context, prop),
            generateUnSetMethod,generateIsSetMethod);
    }
}
