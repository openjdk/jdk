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
 * {@link FieldRenderer} for possibly constant field.
 *
 * <p>
 * Since we don't know if the constant can be actually generated until
 * we get to the codemodel building phase, this renderer lazily
 * determines if it wants to generate a constant field or a normal property.
 *
 * @author Kohsuke Kawaguchi
 */
final class ConstFieldRenderer implements FieldRenderer {

    private final FieldRenderer fallback;

    protected ConstFieldRenderer(FieldRenderer fallback) {
        this.fallback = fallback;
    }

    public FieldOutline generate(ClassOutlineImpl outline, CPropertyInfo prop) {
        if(prop.defaultValue.compute(outline.parent())==null)
            return fallback.generate(outline, prop);
        else
            return new ConstField(outline,prop);
    }
}
