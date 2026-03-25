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

import compiler.lib.ir_framework.IRNode;
import static compiler.lib.ir_framework.IRNode.PREFIX;

public class InlineTypeIRNode {
    private static final String POSTFIX = "#I_";

    public static final String CALL_UNSAFE = PREFIX + "CALL_UNSAFE" + POSTFIX;
    static {
        IRNode.staticCallOfMethodNodes(CALL_UNSAFE, InlineTypeRegexes.JDK_INTERNAL_MISC_UNSAFE);
    }

    public static final String STORE_INLINE_FIELDS = PREFIX + "STORE_INLINE_FIELDS" + POSTFIX;
    static {
        IRNode.staticCallOfMethodNodes(STORE_INLINE_FIELDS, InlineTypeRegexes.STORE_INLINE_TYPE_FIELDS);
    }

    public static final String LOAD_UNKNOWN_INLINE = PREFIX + "LOAD_UNKNOWN_INLINE" + POSTFIX;
    static {
        IRNode.staticCallOfMethodNodes(LOAD_UNKNOWN_INLINE, InlineTypeRegexes.LOAD_UNKNOWN_INLINE);
    }

    public static final String STORE_UNKNOWN_INLINE = PREFIX + "STORE_UNKNOWN_INLINE" + POSTFIX;
    static {
        IRNode.staticCallOfMethodNodes(STORE_UNKNOWN_INLINE, InlineTypeRegexes.STORE_UNKNOWN_INLINE);
    }

    public static final String INLINE_ARRAY_NULL_GUARD = PREFIX + "INLINE_ARRAY_NULL_GUARD" + POSTFIX;
    static {
        IRNode.staticCallOfMethodNodes(INLINE_ARRAY_NULL_GUARD, InlineTypeRegexes.INLINE_ARRAY_NULL_GUARD);
    }

    public static final String CLONE_INTRINSIC_SLOW_PATH = PREFIX + "CLONE_INTRINSIC_SLOW_PATH" + POSTFIX;
    static {
        IRNode.staticCallOfMethodNodes(CLONE_INTRINSIC_SLOW_PATH, InlineTypeRegexes.JAVA_LANG_OBJECT_CLONE);
    }

    public static final String CHECKCAST_ARRAYCOPY = PREFIX + "CHECKCAST_ARRAYCOPY" + POSTFIX;
    static {
        IRNode.callLeafNoFpOfMethodNodes(CHECKCAST_ARRAYCOPY, InlineTypeRegexes.CHECKCAST_ARRAYCOPY);
    }

    public static final String JLONG_ARRAYCOPY = PREFIX + "JLONG_ARRAYCOPY" + POSTFIX;
    static {
        IRNode.callLeafNoFpOfMethodNodes(JLONG_ARRAYCOPY, InlineTypeRegexes.JLONG_DISJOINT_ARRAYCOPY);
    }

    public static final String ALLOC_OF_MYVALUE_KLASS = PREFIX + "ALLOC_OF_MYVALUE_KLASS" + POSTFIX;
    static {
        IRNode.allocateOfNodes(ALLOC_OF_MYVALUE_KLASS, InlineTypeRegexes.MYVALUE_KLASS);
    }

    public static final String ALLOC_ARRAY_OF_MYVALUE_KLASS = PREFIX + "ALLOC_ARRAY_OF_MYVALUE_KLASS" + POSTFIX;
    static {
        IRNode.allocateArrayOfNodes(ALLOC_ARRAY_OF_MYVALUE_KLASS, InlineTypeRegexes.MYVALUE_KLASS);
    }

    public static final String LOAD_OF_ANY_KLASS = PREFIX + "LOAD_OF_ANY_KLASS" + POSTFIX;
    static {
        IRNode.anyLoadOfNodes(LOAD_OF_ANY_KLASS, InlineTypeRegexes.ANY_KLASS);
    }

    public static final String STORE_OF_ANY_KLASS = PREFIX + "STORE_OF_ANY_KLASS" + POSTFIX;
    static {
        IRNode.anyStoreOfNodes(STORE_OF_ANY_KLASS, InlineTypeRegexes.ANY_KLASS);
    }

    // Dummy method to call to force the static initializer blocks to be run before starting the IR framework.
    public static void forceStaticInitialization() {}
}
