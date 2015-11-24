/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.test.lib.jittester;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeBoolean;
import jdk.test.lib.jittester.types.TypeByte;
import jdk.test.lib.jittester.types.TypeChar;
import jdk.test.lib.jittester.types.TypeDouble;
import jdk.test.lib.jittester.types.TypeFloat;
import jdk.test.lib.jittester.types.TypeInt;
import jdk.test.lib.jittester.types.TypeLong;
import jdk.test.lib.jittester.types.TypeShort;
import jdk.test.lib.jittester.types.TypeVoid;

public class TypeList {
    private static final TypeVoid TYPE_VOID = new TypeVoid();
    private static final List<Type> TYPES = new ArrayList<>();
    private static final List<Type> BUILTIN_TYPES = new ArrayList<>();
    private static final List<Type> BUILTIN_INT_TYPES = new ArrayList<>();
    private static final List<Type> BUILTIN_FP_TYPES = new ArrayList<>();
    private static final List<Type> REFERENCE_TYPES = new ArrayList<>();

    static {
        BUILTIN_INT_TYPES.add(new TypeBoolean());
        BUILTIN_INT_TYPES.add(new TypeByte());
        BUILTIN_INT_TYPES.add(new TypeChar());
        BUILTIN_INT_TYPES.add(new TypeShort());
        BUILTIN_INT_TYPES.add(new TypeInt());
        BUILTIN_INT_TYPES.add(new TypeLong());
        BUILTIN_FP_TYPES.add(new TypeFloat());
        BUILTIN_FP_TYPES.add(new TypeDouble());

        BUILTIN_TYPES.addAll(BUILTIN_INT_TYPES);
        BUILTIN_TYPES.addAll(BUILTIN_FP_TYPES);

        TYPES.addAll(BUILTIN_TYPES);

        if (!ProductionParams.disableArrays.value()) {
            REFERENCE_TYPES.add(new TypeArray().produce());
            TYPES.addAll(REFERENCE_TYPES);
        }
    }

    public static TypeVoid getVoid() {
        return TYPE_VOID;
    }

    public static Collection<Type> getAll() {
        return TYPES;
    }

    public static Collection<Type> getBuiltIn() {
        return BUILTIN_TYPES;
    }

    public static Collection<Type> getBuiltInInt() {
        return BUILTIN_INT_TYPES;
    }

    protected static Collection<Type> getBuiltInFP() {
        return BUILTIN_FP_TYPES;
    }

    protected static Collection<Type> getReferenceTypes() {
        return REFERENCE_TYPES;
    }

    protected static boolean isBuiltInFP(Type t) {
        return BUILTIN_FP_TYPES.contains(t);
    }

    public static boolean isBuiltInInt(Type t) {
        return BUILTIN_INT_TYPES.contains(t);
    }

    public static boolean isBuiltIn(Type t) {
        return isBuiltInInt(t) || isBuiltInFP(t);
    }

    protected static boolean isIn(Type t) {
        return TYPES.contains(t);
    }

    protected static boolean isReferenceType(Type t) {
        return REFERENCE_TYPES.contains(t);
    }

    public static Type find(Type t) {
        int i = TYPES.indexOf(t);
        if (i != -1) {
            return TYPES.get(i);
        }
        return null;
    }

    protected static Type findReferenceType(Type t) {
        int i = REFERENCE_TYPES.indexOf(t);
        if (i != -1) {
            return REFERENCE_TYPES.get(i);
        }
        return null;
    }

    public static Type find(String name) {
        for (Type t : TYPES) {
            if (t.getName().equals(name)) {
                return t;
            }
        }
        return null;
    }

    public static void add(Type t) {
        REFERENCE_TYPES.add(t);
        TYPES.add(t);
    }

    protected static void remove(Type t) {
        REFERENCE_TYPES.remove(t);
        TYPES.remove(t);
    }

    public static void removeAll() {
        Predicate<? super Type> isNotBasic = t -> t.getName().startsWith("Test_");
        TYPES.removeIf(isNotBasic);
        REFERENCE_TYPES.removeIf(isNotBasic);
    }
}
