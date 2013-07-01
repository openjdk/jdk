/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.objects;

import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Getter;
import jdk.nashorn.internal.objects.annotations.Property;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.Setter;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.regexp.RegExpResult;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.arrays.ArrayData;

/**
 * Objects of this class are used to represent return values from
 * RegExp.prototype.exec method.
 */
@ScriptClass("RegExpExecResult")
public final class NativeRegExpExecResult extends ScriptObject {
    /** index property */
    @Property
    public Object index;

    /** input property */
    @Property
    public Object input;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    NativeRegExpExecResult(final RegExpResult result) {
        super(Global.instance().getArrayPrototype(), $nasgenmap$);
        setIsArray();
        this.setArray(ArrayData.allocate(result.getGroups().clone()));
        this.index = result.getIndex();
        this.input = result.getInput();
    }

    @Override
    public String getClassName() {
        return "Array";
    }

    /**
     * Length getter
     * @param self self reference
     * @return length property value
     */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE)
    public static Object length(final Object self) {
        if (self instanceof ScriptObject) {
            return ((ScriptObject)self).getArray().length() & JSType.MAX_UINT;
        }

        return 0;
    }

    /**
     * Length setter
     * @param self self reference
     * @param length property value
     */
    @Setter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE)
    public static void length(final Object self, final Object length) {
        if (self instanceof ScriptObject) {
            ((ScriptObject)self).setLength(NativeArray.validLength(length, true));
        }
    }
}
