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

import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Property;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * ECMA 15.11.6.3 ReferenceError
 *
 */
@ScriptClass("Error")
public final class NativeReferenceError extends ScriptObject {

    /** message property in instance */
    @Property(name = NativeError.MESSAGE)
    public Object instMessage;

    /** error name property */
    @Property(attributes = Attribute.NOT_ENUMERABLE, where = Where.PROTOTYPE)
    public Object name;

    /** ECMA 15.1.1.1 message property */
    @Property(attributes = Attribute.NOT_ENUMERABLE, where = Where.PROTOTYPE)
    public Object message;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    static PropertyMap getInitialMap() {
        return $nasgenmap$;
    }

    private NativeReferenceError(final Object msg, final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
        if (msg != UNDEFINED) {
            this.instMessage = JSType.toString(msg);
        } else {
            this.delete(NativeError.MESSAGE, false);
        }
    }

    NativeReferenceError(final Object msg, final Global global) {
        this(msg, global.getReferenceErrorPrototype(), global.getReferenceErrorMap());
    }

    private NativeReferenceError(final Object msg) {
        this(msg, Global.instance());
    }

    @Override
    public String getClassName() {
        return "Error";
    }

    /**
     * ECMA 15.11.6.3 ReferenceError
     *
     * Constructor
     *
     * @param newObj was this error instantiated with the new operator
     * @param self   self reference
     * @param msg    error message
     *
     * @return new ReferenceError
     */
    @Constructor(name = "ReferenceError")
    public static Object constructor(final boolean newObj, final Object self, final Object msg) {
        return new NativeReferenceError(msg);
    }
}
