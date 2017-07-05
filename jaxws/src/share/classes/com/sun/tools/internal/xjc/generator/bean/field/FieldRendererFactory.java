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

import com.sun.tools.internal.xjc.Options;
import com.sun.codemodel.internal.JClass;

/**
 * Factory for {@link FieldRenderer}.
 *
 * <p>
 * This class can be overridden by a plugin to change the code generation
 * behavior of XJC. Note that such changes aren't composable; for a given
 * schema compilation, only one instance of {@link FieldRendererFactory} is
 * used.
 *
 * <p>
 * See {@link Options#fieldRendererFactory}
 *
 * <p>
 * To be more precise, since {@link FieldRenderer} is just a strategy pattern
 * and by itself is stateless, the "factory methods" don't necessarily need
 * to create new instances of {@link FieldRenderer} --- it can just return
 * a set of pre-created instances.
 *
 * @author Kohsuke Kawaguchi
 */
public class FieldRendererFactory {

    public FieldRenderer getDefault() {
        return DEFAULT;
    }
    public FieldRenderer getArray() {
        return ARRAY;
    }
    public FieldRenderer getRequiredUnboxed() {
        return REQUIRED_UNBOXED;
    }
    public FieldRenderer getSingle() {
        return SINGLE;
    }
    public FieldRenderer getSinglePrimitiveAccess() {
        return SINGLE_PRIMITIVE_ACCESS;
    }
    public FieldRenderer getList(JClass coreList) {
        return new UntypedListFieldRenderer(coreList);
    }
    public FieldRenderer getConst(FieldRenderer fallback) {
        return new ConstFieldRenderer(fallback);
    }

    private final FieldRenderer DEFAULT
        = new DefaultFieldRenderer(this);

    private static final FieldRenderer ARRAY
        = new GenericFieldRenderer(ArrayField.class);

    private static final FieldRenderer REQUIRED_UNBOXED
        = new GenericFieldRenderer(UnboxedField.class);

    private static final FieldRenderer SINGLE
        = new GenericFieldRenderer(SingleField.class);

    private static final FieldRenderer SINGLE_PRIMITIVE_ACCESS
        = new GenericFieldRenderer(SinglePrimitiveAccessField.class);
}
