/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.Map;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.Opcode;
import jdk.internal.classfile.TypeKind;
import sun.invoke.util.BytecodeDescriptor;
import sun.invoke.util.Wrapper;
import static sun.invoke.util.Wrapper.*;

class TypeConvertingMethodAdapter {

    private static final int NUM_WRAPPERS = Wrapper.COUNT;

    private static final ClassDesc NAME_OBJECT = ConstantDescs.CD_Object;
    private static final String WRAPPER_PREFIX = "Ljava/lang/";

    // Same for all primitives; name of the boxing method
    private static final String NAME_BOX_METHOD = "valueOf";

    // Table of opcodes for widening primitive conversions; NOP = no conversion
    private static final Opcode[][] wideningOpcodes = new Opcode[NUM_WRAPPERS][NUM_WRAPPERS];

    private static final Wrapper[] FROM_WRAPPER_NAME = new Wrapper[16];

    // Table of wrappers for primitives, indexed by ASM type sorts
    private static final Map<TypeKind, Wrapper> FROM_TYPE_SORT =
            Map.of(TypeKind.ByteType, Wrapper.BYTE,
                   TypeKind.ShortType, Wrapper.SHORT,
                   TypeKind.IntType, Wrapper.INT,
                   TypeKind.LongType, Wrapper.LONG,
                   TypeKind.CharType, Wrapper.CHAR,
                   TypeKind.FloatType, Wrapper.FLOAT,
                   TypeKind.DoubleType, Wrapper.DOUBLE,
                   TypeKind.BooleanType, Wrapper.BOOLEAN);

    static {
        for (Wrapper w : Wrapper.values()) {
            if (w.basicTypeChar() != 'L') {
                int wi = hashWrapperName(w.wrapperSimpleName());
                assert (FROM_WRAPPER_NAME[wi] == null);
                FROM_WRAPPER_NAME[wi] = w;
            }
        }

        // wideningOpcodes[][] will be NOP-initialized by default
        assert(Classfile.NOP == 0);

        initWidening(LONG,   Opcode.I2L, BYTE, SHORT, INT, CHAR);
        initWidening(LONG,   Opcode.F2L, FLOAT);
        initWidening(FLOAT,  Opcode.I2F, BYTE, SHORT, INT, CHAR);
        initWidening(FLOAT,  Opcode.L2F, LONG);
        initWidening(DOUBLE, Opcode.I2D, BYTE, SHORT, INT, CHAR);
        initWidening(DOUBLE, Opcode.F2D, FLOAT);
        initWidening(DOUBLE, Opcode.L2D, LONG);
    }

    private static void initWidening(Wrapper to, Opcode opcode, Wrapper... from) {
        for (Wrapper f : from) {
            wideningOpcodes[f.ordinal()][to.ordinal()] = opcode;
        }
    }

    /**
     * Class name to Wrapper hash, derived from Wrapper.hashWrap()
     * @param xn
     * @return The hash code 0-15
     */
    private static int hashWrapperName(String xn) {
        if (xn.length() < 3) {
            return 0;
        }
        return (3 * xn.charAt(1) + xn.charAt(2)) % 16;
    }

    static private Wrapper wrapperOrNullFromDescriptor(String desc) {
        if (!desc.startsWith(WRAPPER_PREFIX)) {
            // Not a class type (array or method), so not a boxed type
            // or not in the right package
            return null;
        }
        // Pare it down to the simple class name
        String cname = desc.substring(WRAPPER_PREFIX.length(), desc.length() - 1);
        // Hash to a Wrapper
        Wrapper w = FROM_WRAPPER_NAME[hashWrapperName(cname)];
        if (w == null || w.wrapperSimpleName().equals(cname)) {
            return w;
        } else {
            return null;
        }
    }

    private static String wrapperName(Wrapper w) {
        return "java/lang/" + w.wrapperSimpleName();
    }

    private static String unboxMethod(Wrapper w) {
        return w.primitiveSimpleName() + "Value";
    }

    private static MethodTypeDesc boxingDescriptor(Wrapper w) {
        return MethodTypeDesc.ofDescriptor("(" + w.basicTypeChar() + ")L" + wrapperName(w) + ";");
    }

    private static MethodTypeDesc unboxingDescriptor(Wrapper w) {
        return MethodTypeDesc.ofDescriptor("()" + w.basicTypeChar());
    }

    static void boxIfTypePrimitive(CodeBuilder cob, TypeKind tk) {
        Wrapper w = FROM_TYPE_SORT.get(tk);
        if (w != null) {
            box(cob, w);
        }
    }

    static void widen(CodeBuilder cob, Wrapper ws, Wrapper wt) {
        if (ws != wt) {
            var opcode = wideningOpcodes[ws.ordinal()][wt.ordinal()];
            if (opcode != null) {
                cob.convertInstruction(opcode.primaryTypeKind(), opcode.secondaryTypeKind());
            }
        }
    }

    static void box(CodeBuilder cob, Wrapper w) {
        cob.invokeInstruction(Opcode.INVOKESTATIC,
                ClassDesc.ofInternalName(wrapperName(w)),
                NAME_BOX_METHOD,
                boxingDescriptor(w), false);
    }

    /**
     * Convert types by unboxing. The source type is known to be a primitive wrapper.
     * @param sname A primitive wrapper corresponding to wrapped reference source type
     * @param wt A primitive wrapper being converted to
     */
    static void unbox(CodeBuilder cob, ClassDesc sname, Wrapper wt) {
        cob.invokeInstruction(Opcode.INVOKEVIRTUAL,
                sname,
                unboxMethod(wt),
                unboxingDescriptor(wt), false);
    }

    static private String descriptorToName(String desc) {
        int last = desc.length() - 1;
        if (desc.charAt(0) == 'L' && desc.charAt(last) == ';') {
            // In descriptor form
            return desc.substring(1, last);
        } else {
            // Already in internal name form
            return desc;
        }
    }

    static void cast(CodeBuilder cob, ClassDesc ds, ClassDesc dt) {
        if (!dt.equals(ds) && !dt.equals(NAME_OBJECT)) {
            cob.typeCheckInstruction(Opcode.CHECKCAST, dt);
        }
    }

    static private Wrapper toWrapper(String desc) {
        char first = desc.charAt(0);
        if (first == '[' || first == '(') {
            first = 'L';
        }
        return Wrapper.forBasicType(first);
    }

    /**
     * Convert an argument of type 'arg' to be passed to 'target' assuring that it is 'functional'.
     * Insert the needed conversion instructions in the method code.
     * @param arg
     * @param target
     * @param functional
     */
    static void convertType(CodeBuilder cob, Class<?> arg, Class<?> target, Class<?> functional) {
        if (arg.equals(target) && arg.equals(functional)) {
            return;
        }
        if (arg == Void.TYPE || target == Void.TYPE) {
            return;
        }
        if (arg.isPrimitive()) {
            Wrapper wArg = Wrapper.forPrimitiveType(arg);
            if (target.isPrimitive()) {
                // Both primitives: widening
                widen(cob, wArg, Wrapper.forPrimitiveType(target));
            } else {
                // Primitive argument to reference target
                String dTarget = BytecodeDescriptor.unparse(target);
                Wrapper wPrimTarget = wrapperOrNullFromDescriptor(dTarget);
                if (wPrimTarget != null) {
                    // The target is a boxed primitive type, widen to get there before boxing
                    widen(cob, wArg, wPrimTarget);
                    box(cob, wPrimTarget);
                } else {
                    // Otherwise, box and cast
                    box(cob, wArg);
                    cast(cob, ClassDesc.ofInternalName(wrapperName(wArg)), ClassDesc.ofDescriptor(dTarget));
                }
            }
        } else {
            String dArg = BytecodeDescriptor.unparse(arg);
            String dSrc;
            if (functional.isPrimitive()) {
                dSrc = dArg;
            } else {
                // Cast to convert to possibly more specific type, and generate CCE for invalid arg
                dSrc = BytecodeDescriptor.unparse(functional);
                cast(cob, ClassDesc.ofDescriptor(dArg), ClassDesc.ofDescriptor(dSrc));
            }
            String dTarget = BytecodeDescriptor.unparse(target);
            if (target.isPrimitive()) {
                Wrapper wTarget = toWrapper(dTarget);
                // Reference argument to primitive target
                Wrapper wps = wrapperOrNullFromDescriptor(dSrc);
                if (wps != null) {
                    if (wps.isSigned() || wps.isFloating()) {
                        // Boxed number to primitive
                        unbox(cob, ClassDesc.ofInternalName(wrapperName(wps)), wTarget);
                    } else {
                        // Character or Boolean
                        unbox(cob, ClassDesc.ofInternalName(wrapperName(wps)), wps);
                        widen(cob, wps, wTarget);
                    }
                } else {
                    // Source type is reference type, but not boxed type,
                    // assume it is super type of target type
                    ClassDesc intermediate;
                    if (wTarget.isSigned() || wTarget.isFloating()) {
                        // Boxed number to primitive
                        intermediate = ConstantDescs.CD_Number;
                    } else {
                        // Character or Boolean
                        intermediate = ClassDesc.ofInternalName(wrapperName(wTarget));
                    }
                    cast(cob, ClassDesc.ofDescriptor(dSrc), intermediate);
                    unbox(cob, intermediate, wTarget);
                }
            } else {
                // Both reference types: just case to target type
                cast(cob, ClassDesc.ofDescriptor(dSrc), ClassDesc.ofDescriptor(dTarget));
            }
        }
    }
}
