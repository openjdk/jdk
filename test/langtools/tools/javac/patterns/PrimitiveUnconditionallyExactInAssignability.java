/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Check assignability of narrowing p.c. with constant expressions vs exact conversion methods
 * @library /tools/lib/types
 * @modules jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.comp
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.util
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.tree
 * @build TypeHarness
 * @compile PrimitiveUnconditionallyExactInAssignability.java
 * @run main PrimitiveUnconditionallyExactInAssignability
 */

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;

import java.lang.runtime.ExactConversionsSupport;

import static com.sun.tools.javac.code.TypeTag.ERROR;
import static com.sun.tools.javac.code.TypeTag.INT;

public class PrimitiveUnconditionallyExactInAssignability extends TypeHarness {
    PrimitiveUnconditionallyExactInAssignability() {
    }

    void assertOriginalAssignmentNarrowingAndUnconditionality() {
        // byte b = <constant short> vs ExactConversionsSupport::isIntToByteExact
        assertOriginaAndUpdatedAssignable(fac.Constant(Short.MIN_VALUE),                     predef.byteType, ExactConversionsSupport.isIntToByteExact(Short.MIN_VALUE));
        assertOriginaAndUpdatedAssignable(fac.Constant((short) (Byte.MIN_VALUE - 1)),        predef.byteType, ExactConversionsSupport.isIntToByteExact((short) (Byte.MIN_VALUE - 1)));
        assertOriginaAndUpdatedAssignable(fac.Constant((short) (Byte.MAX_VALUE + 1)),        predef.byteType, ExactConversionsSupport.isIntToByteExact((short) (Byte.MAX_VALUE + 1)));
        assertOriginaAndUpdatedAssignable(fac.Constant(Short.MAX_VALUE),                     predef.byteType, ExactConversionsSupport.isIntToByteExact(Short.MAX_VALUE));

        // byte b = <constant char> vs ExactConversionsSupport::isIntToByteExact
        assertOriginaAndUpdatedAssignable(fac.Constant(Character.MIN_VALUE),                 predef.byteType, ExactConversionsSupport.isIntToByteExact(Character.MIN_VALUE));
        assertOriginaAndUpdatedAssignable(fac.Constant((char) (Byte.MAX_VALUE + 1)),         predef.byteType, ExactConversionsSupport.isIntToByteExact((char) (Byte.MAX_VALUE + 1)));
        assertOriginaAndUpdatedAssignable(fac.Constant(Character.MAX_VALUE),                 predef.byteType, ExactConversionsSupport.isIntToByteExact(Character.MAX_VALUE));

        // byte b = <constant int>  vs ExactConversionsSupport::isIntToByteExact
        assertOriginaAndUpdatedAssignable(fac.Constant(Integer.MIN_VALUE),                   predef.byteType, ExactConversionsSupport.isIntToByteExact(Integer.MIN_VALUE));
        assertOriginaAndUpdatedAssignable(fac.Constant((int) (Byte.MIN_VALUE - 1)),          predef.byteType, ExactConversionsSupport.isIntToByteExact((int) (Byte.MIN_VALUE - 1)));
        assertOriginaAndUpdatedAssignable(fac.Constant((int) (Byte.MAX_VALUE + 1)),          predef.byteType, ExactConversionsSupport.isIntToByteExact((int) (Byte.MAX_VALUE + 1)));
        assertOriginaAndUpdatedAssignable(fac.Constant(Integer.MAX_VALUE),                   predef.byteType, ExactConversionsSupport.isIntToByteExact(Integer.MAX_VALUE));

        // char c = <constant short> vs ExactConversionsSupport::isIntToCharExact
        assertOriginaAndUpdatedAssignable(fac.Constant(Short.MIN_VALUE),                     predef.charType, ExactConversionsSupport.isIntToCharExact(Short.MIN_VALUE));
        assertOriginaAndUpdatedAssignable(fac.Constant((short) (Character.MIN_VALUE - 1)),   predef.charType, ExactConversionsSupport.isIntToCharExact((short) (Character.MIN_VALUE - 1)));
        assertOriginaAndUpdatedAssignable(fac.Constant((short) (Character.MAX_VALUE + 1)),   predef.charType, ExactConversionsSupport.isIntToCharExact((short) (Character.MIN_VALUE + 1)));
        assertOriginaAndUpdatedAssignable(fac.Constant(Short.MAX_VALUE),                     predef.charType, ExactConversionsSupport.isIntToCharExact(Short.MAX_VALUE));

        // char c = <constant int>   vs ExactConversionsSupport::isIntToCharExact
        assertOriginaAndUpdatedAssignable(fac.Constant(Integer.MIN_VALUE),                   predef.charType, ExactConversionsSupport.isIntToCharExact(Integer.MIN_VALUE));
        assertOriginaAndUpdatedAssignable(fac.Constant((int) (Character.MIN_VALUE - 1)),     predef.charType, ExactConversionsSupport.isIntToCharExact((int) (Character.MIN_VALUE - 1)));
        assertOriginaAndUpdatedAssignable(fac.Constant((int) (Character.MAX_VALUE + 1)),     predef.charType, ExactConversionsSupport.isIntToCharExact((int) (Character.MAX_VALUE + 1)));
        assertOriginaAndUpdatedAssignable(fac.Constant(Integer.MAX_VALUE),                   predef.charType, ExactConversionsSupport.isIntToCharExact(Integer.MAX_VALUE));

        // short b = <constant char> vs ExactConversionsSupport::isIntToShortExact
        assertOriginaAndUpdatedAssignable(fac.Constant(Character.MIN_VALUE),                 predef.shortType, ExactConversionsSupport.isIntToShortExact(Character.MIN_VALUE));
        assertOriginaAndUpdatedAssignable(fac.Constant((char) (Character.MAX_VALUE + 1)),    predef.shortType, ExactConversionsSupport.isIntToShortExact((char) (Character.MAX_VALUE + 1)));
        assertOriginaAndUpdatedAssignable(fac.Constant(Character.MAX_VALUE),                 predef.shortType, ExactConversionsSupport.isIntToShortExact(Character.MAX_VALUE));

        // short b = <constant int>  vs ExactConversionsSupport::isIntToShortExact
        assertOriginaAndUpdatedAssignable(fac.Constant(Integer.MIN_VALUE),                   predef.shortType, ExactConversionsSupport.isIntToShortExact(Integer.MIN_VALUE));
        assertOriginaAndUpdatedAssignable(fac.Constant((int) (Short.MIN_VALUE - 1)),         predef.shortType, ExactConversionsSupport.isIntToShortExact((int) (Short.MIN_VALUE - 1)));
        assertOriginaAndUpdatedAssignable(fac.Constant((int) (Short.MAX_VALUE + 1)),         predef.shortType, ExactConversionsSupport.isIntToShortExact((int) (Short.MAX_VALUE + 1)));
        assertOriginaAndUpdatedAssignable(fac.Constant(Integer.MAX_VALUE),                   predef.shortType, ExactConversionsSupport.isIntToShortExact(Integer.MAX_VALUE));
    }
    // where
    public void assertOriginaAndUpdatedAssignable(Type s, Type t, boolean expected) {
        assertAssignable(s, t, originalIsAssignable(s, t));
    }
    public boolean originalIsAssignable(Type t, Type s) {
        if (t.hasTag(ERROR))
            return true;
        if (t.getTag().isSubRangeOf(INT) && t.constValue() != null) {
            int value = ((Number)t.constValue()).intValue();
            switch (s.getTag()) {
                case BYTE:
                case CHAR:
                case SHORT:
                case INT:
                    if (originalCheckRange(s.getTag(), value))
                        return true;
                    break;
            }
        }
        return types.isConvertible(t, s);
    }
    public boolean originalCheckRange(TypeTag that, int value) {
        switch (that) {
            case BOOLEAN:
                return 0 <= value && value <= 1;
            case BYTE:
                return Byte.MIN_VALUE <= value && value <= Byte.MAX_VALUE;
            case CHAR:
                return Character.MIN_VALUE <= value && value <= Character.MAX_VALUE;
            case SHORT:
                return Short.MIN_VALUE <= value && value <= Short.MAX_VALUE;
            case INT:
                return true;
            default:
                throw new AssertionError();
        }
    }

    private void error(String msg) {
        throw new AssertionError("Unexpected result in original isAssignable: " + msg);
    }

    public static void main(String[] args) {
        PrimitiveUnconditionallyExactInAssignability harness = new PrimitiveUnconditionallyExactInAssignability();
        harness.assertOriginalAssignmentNarrowingAndUnconditionality();
    }
}
