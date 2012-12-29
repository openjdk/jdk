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

package com.sun.xml.internal.bind.v2.model.impl;

import com.sun.xml.internal.bind.v2.model.core.EnumConstant;
import com.sun.xml.internal.bind.v2.model.core.EnumLeafInfo;

/**
 * @author Kohsuke Kawaguchi
 */
class EnumConstantImpl<T,C,F,M> implements EnumConstant<T,C> {
    protected final String lexical;
    protected final EnumLeafInfoImpl<T,C,F,M> owner;
    protected final String name;

    /**
     * All the constants of the {@link EnumConstantImpl} is linked in one list.
     */
    protected final EnumConstantImpl<T,C,F,M> next;

    public EnumConstantImpl(EnumLeafInfoImpl<T,C,F,M> owner, String name, String lexical, EnumConstantImpl<T,C,F,M> next) {
        this.lexical = lexical;
        this.owner = owner;
        this.name = name;
        this.next = next;
    }

    public EnumLeafInfo<T,C> getEnclosingClass() {
        return owner;
    }

    public final String getLexicalValue() {
        return lexical;
    }

    public final String getName() {
        return name;
    }
}
