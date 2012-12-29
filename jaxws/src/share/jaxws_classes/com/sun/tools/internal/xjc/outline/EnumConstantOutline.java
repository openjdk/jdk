/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.outline;

import com.sun.codemodel.internal.JEnumConstant;
import com.sun.tools.internal.xjc.generator.bean.BeanGenerator;
import com.sun.tools.internal.xjc.model.CEnumConstant;
import com.sun.tools.internal.xjc.model.CEnumLeafInfo;

/**
 * Outline object that provides per-{@link CEnumConstant} information.
 *
 * This object can be obtained from {@link EnumOutline}
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class EnumConstantOutline {
    /**
     * This {@link EnumOutline} holds information about this {@link CEnumLeafInfo}.
     */
    public final CEnumConstant target;

    /**
     * The generated enum constant.
     */
    public final JEnumConstant constRef;

    /**
     * Reserved for {@link BeanGenerator}.
     */
    protected EnumConstantOutline(CEnumConstant target, JEnumConstant constRef) {
        this.target = target;
        this.constRef = constRef;
    }
}
