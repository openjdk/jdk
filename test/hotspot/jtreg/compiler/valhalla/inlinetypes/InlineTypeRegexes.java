/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes;

public class InlineTypeRegexes {
    public static final String MYVALUE_KLASS = "compiler/valhalla/inlinetypes/.*MyValue\\w*";
    public static final String ANY_KLASS = "compiler/valhalla/inlinetypes/[\\w/]*";
    public static final String STORE_INLINE_TYPE_FIELDS = "store_inline_type_fields";
    public static final String JDK_INTERNAL_MISC_UNSAFE = "# Static  jdk.internal.misc.Unsafe::";
    public static final String LOAD_UNKNOWN_INLINE = "load_unknown_inline_blob \\(C2 runtime\\)";
    public static final String STORE_UNKNOWN_INLINE = "store_unknown_inline_blob \\(C2 runtime\\)";
    public static final String INLINE_ARRAY_NULL_GUARD = "null_check' action='none'";
    public static final String JLONG_DISJOINT_ARRAYCOPY = "jlong_disjoint_arraycopy";
    public static final String CHECKCAST_ARRAYCOPY = "checkcast_arraycopy";
    public static final String JAVA_LANG_OBJECT_CLONE = "java.lang.Object::clone";
}
