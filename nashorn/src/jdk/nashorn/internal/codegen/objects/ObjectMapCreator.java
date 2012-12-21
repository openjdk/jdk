/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.codegen.objects;

import java.util.List;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.runtime.Property;

/**
 * This map creator is used to guarantee that all properties start out as
 * object types. Only semantically significant in the -Dnashorn.fields.dual=true world,
 * where we want to avoid invalidation upon initialization e.g. for var x = {a:"str"};
 */

public class ObjectMapCreator extends MapCreator {
    /**
     * Constructor
     *
     * @param structure structure for object class
     * @param keys      keys in object
     * @param symbols   symbols in object corresponding to keys
     */
    public ObjectMapCreator(final Class<?> structure, final List<String> keys, final List<Symbol> symbols) {
        super(structure, keys, symbols);
    }

    @Override
    protected int getPropertyFlags(final Symbol symbol, final boolean isVarArg) {
        return super.getPropertyFlags(symbol, isVarArg) | Property.IS_ALWAYS_OBJECT;
    }
}
