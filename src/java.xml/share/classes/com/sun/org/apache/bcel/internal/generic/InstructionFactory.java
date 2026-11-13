/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sun.org.apache.bcel.internal.generic;

import com.sun.org.apache.bcel.internal.Const;

/**
 * Instances of this class may be used, for example, to generate typed versions of instructions. Its main purpose is to be used
 * as the byte code generating backend of a compiler. You can subclass it to add your own create methods.
 * <p>
 * Note: The static createXXX methods return singleton instances from the {@link InstructionConst} class.
 * </p>
 *
 * @see Const
 * @see InstructionConst
 * @LastModified: Sept 2025
 */
public class InstructionFactory {

    private static final class MethodObject {

        final Type[] argTypes;
        final Type resultType;
        final String className;
        final String name;

        MethodObject(final String c, final String n, final Type r, final Type[] a) {
            this.className = c;
            this.name = n;
            this.resultType = r;
            this.argTypes = a;
        }
    }

    private static final String APPEND = "append";

    private static final String FQCN_STRING_BUFFER = "java.lang.StringBuffer";

    /**
     * These must agree with the order of Constants.T_CHAR through T_LONG.
     */
    private static final String[] SHORT_NAMES = {"C", "F", "D", "B", "S", "I", "L"};

    private static final MethodObject[] APPEND_METHOD_OBJECTS = {
            new MethodObject(FQCN_STRING_BUFFER, APPEND, Type.STRINGBUFFER, new Type[] { Type.STRING }),
            new MethodObject(FQCN_STRING_BUFFER, APPEND, Type.STRINGBUFFER, new Type[] { Type.OBJECT }), null, null, // indices 2, 3
            new MethodObject(FQCN_STRING_BUFFER, APPEND, Type.STRINGBUFFER, new Type[] { Type.BOOLEAN }),
            new MethodObject(FQCN_STRING_BUFFER, APPEND, Type.STRINGBUFFER, new Type[] { Type.CHAR }),
            new MethodObject(FQCN_STRING_BUFFER, APPEND, Type.STRINGBUFFER, new Type[] { Type.FLOAT }),
            new MethodObject(FQCN_STRING_BUFFER, APPEND, Type.STRINGBUFFER, new Type[] { Type.DOUBLE }),
            new MethodObject(FQCN_STRING_BUFFER, APPEND, Type.STRINGBUFFER, new Type[] { Type.INT }),
            new MethodObject(FQCN_STRING_BUFFER, APPEND, Type.STRINGBUFFER, new Type[] { Type.INT }), // No append(byte)
            new MethodObject(FQCN_STRING_BUFFER, APPEND, Type.STRINGBUFFER, new Type[] { Type.INT }), // No append(short)
            new MethodObject(FQCN_STRING_BUFFER, APPEND, Type.STRINGBUFFER, new Type[] { Type.LONG })};

    /**
     * Creates an array load instruction for the given type.
     *
     * @param type type of elements of array, that is, array.getElementType().
     * @return The array load instruction.
     */
    public static ArrayInstruction createArrayLoad(final Type type) {
        switch (type.getType()) {
        case Const.T_BOOLEAN:
        case Const.T_BYTE:
            return InstructionConst.BALOAD;
        case Const.T_CHAR:
            return InstructionConst.CALOAD;
        case Const.T_SHORT:
            return InstructionConst.SALOAD;
        case Const.T_INT:
            return InstructionConst.IALOAD;
        case Const.T_FLOAT:
            return InstructionConst.FALOAD;
        case Const.T_DOUBLE:
            return InstructionConst.DALOAD;
        case Const.T_LONG:
            return InstructionConst.LALOAD;
        case Const.T_ARRAY:
        case Const.T_OBJECT:
            return InstructionConst.AALOAD;
        default:
            throw new IllegalArgumentException("Invalid type " + type);
        }
    }

    /**
     * Creates an array store instruction for the given type.
     *
     * @param type type of elements of array, that is, array.getElementType().
     * @return The array store instruction.
     */
    public static ArrayInstruction createArrayStore(final Type type) {
        switch (type.getType()) {
        case Const.T_BOOLEAN:
        case Const.T_BYTE:
            return InstructionConst.BASTORE;
        case Const.T_CHAR:
            return InstructionConst.CASTORE;
        case Const.T_SHORT:
            return InstructionConst.SASTORE;
        case Const.T_INT:
            return InstructionConst.IASTORE;
        case Const.T_FLOAT:
            return InstructionConst.FASTORE;
        case Const.T_DOUBLE:
            return InstructionConst.DASTORE;
        case Const.T_LONG:
            return InstructionConst.LASTORE;
        case Const.T_ARRAY:
        case Const.T_OBJECT:
            return InstructionConst.AASTORE;
        default:
            throw new IllegalArgumentException("Invalid type " + type);
        }
    }

    private static ArithmeticInstruction createBinaryDoubleOp(final char op) {
        switch (op) {
        case '-':
            return InstructionConst.DSUB;
        case '+':
            return InstructionConst.DADD;
        case '*':
            return InstructionConst.DMUL;
        case '/':
            return InstructionConst.DDIV;
        case '%':
            return InstructionConst.DREM;
        default:
            throw new IllegalArgumentException("Invalid operand " + op);
        }
    }

    private static ArithmeticInstruction createBinaryFloatOp(final char op) {
        switch (op) {
        case '-':
            return InstructionConst.FSUB;
        case '+':
            return InstructionConst.FADD;
        case '*':
            return InstructionConst.FMUL;
        case '/':
            return InstructionConst.FDIV;
        case '%':
            return InstructionConst.FREM;
        default:
            throw new IllegalArgumentException("Invalid operand " + op);
        }
    }

    private static ArithmeticInstruction createBinaryIntOp(final char first, final String op) {
        switch (first) {
        case '-':
            return InstructionConst.ISUB;
        case '+':
            return InstructionConst.IADD;
        case '%':
            return InstructionConst.IREM;
        case '*':
            return InstructionConst.IMUL;
        case '/':
            return InstructionConst.IDIV;
        case '&':
            return InstructionConst.IAND;
        case '|':
            return InstructionConst.IOR;
        case '^':
            return InstructionConst.IXOR;
        case '<':
            return InstructionConst.ISHL;
        case '>':
            return op.equals(">>>") ? InstructionConst.IUSHR : InstructionConst.ISHR;
        default:
            throw new IllegalArgumentException("Invalid operand " + op);
        }
    }

    /**
     * Create an invokedynamic instruction.
     *
     * @param bootstrap_index index into the bootstrap_methods array.
     * @param name name of the called method.
     * @param ret_type return type of method.
     * @param argTypes argument types of method.
     * @see Const
     */

    /*
     * createInvokeDynamic only needed if instrumentation code wants to generate a new invokedynamic instruction. I don't
     * think we need.
     *
     * public InvokeInstruction createInvokeDynamic( int bootstrap_index, String name, Type ret_type, Type[] argTypes) {
     * int index; int nargs = 0; String signature = Type.getMethodSignature(ret_type, argTypes); for (int i = 0; i <
     * argTypes.length; i++) { nargs += argTypes[i].getSize(); } // UNDONE - needs to be added to ConstantPoolGen //index
     * = cp.addInvokeDynamic(bootstrap_index, name, signature); index = 0; return new INVOKEDYNAMIC(index); }
     */

    private static ArithmeticInstruction createBinaryLongOp(final char first, final String op) {
        switch (first) {
        case '-':
            return InstructionConst.LSUB;
        case '+':
            return InstructionConst.LADD;
        case '%':
            return InstructionConst.LREM;
        case '*':
            return InstructionConst.LMUL;
        case '/':
            return InstructionConst.LDIV;
        case '&':
            return InstructionConst.LAND;
        case '|':
            return InstructionConst.LOR;
        case '^':
            return InstructionConst.LXOR;
        case '<':
            return InstructionConst.LSHL;
        case '>':
            return op.equals(">>>") ? InstructionConst.LUSHR : InstructionConst.LSHR;
        default:
            throw new IllegalArgumentException("Invalid operand " + op);
        }
    }

    /**
     * Create binary operation for simple basic types, such as int and float.
     *
     * @param op operation, such as "+", "*", "&lt;&lt;", etc.
     * @param type The operand type.
     * @return The binary arithmetic instruction.
     */
    public static ArithmeticInstruction createBinaryOperation(final String op, final Type type) {
        final char first = op.charAt(0);
        switch (type.getType()) {
        case Const.T_BYTE:
        case Const.T_SHORT:
        case Const.T_INT:
        case Const.T_CHAR:
            return createBinaryIntOp(first, op);
        case Const.T_LONG:
            return createBinaryLongOp(first, op);
        case Const.T_FLOAT:
            return createBinaryFloatOp(first);
        case Const.T_DOUBLE:
            return createBinaryDoubleOp(first);
        default:
            throw new IllegalArgumentException("Invalid type " + type);
        }
    }

    /**
     * Create branch instruction by given opcode, except LOOKUPSWITCH and TABLESWITCH. For those you should use the SWITCH
     * compound instruction.
     *
     * @param opcode The branch opcode.
     * @param target The target instruction handle.
     * @return The branch instruction.
     */
    public static BranchInstruction createBranchInstruction(final short opcode, final InstructionHandle target) {
        switch (opcode) {
        case Const.IFEQ:
            return new IFEQ(target);
        case Const.IFNE:
            return new IFNE(target);
        case Const.IFLT:
            return new IFLT(target);
        case Const.IFGE:
            return new IFGE(target);
        case Const.IFGT:
            return new IFGT(target);
        case Const.IFLE:
            return new IFLE(target);
        case Const.IF_ICMPEQ:
            return new IF_ICMPEQ(target);
        case Const.IF_ICMPNE:
            return new IF_ICMPNE(target);
        case Const.IF_ICMPLT:
            return new IF_ICMPLT(target);
        case Const.IF_ICMPGE:
            return new IF_ICMPGE(target);
        case Const.IF_ICMPGT:
            return new IF_ICMPGT(target);
        case Const.IF_ICMPLE:
            return new IF_ICMPLE(target);
        case Const.IF_ACMPEQ:
            return new IF_ACMPEQ(target);
        case Const.IF_ACMPNE:
            return new IF_ACMPNE(target);
        case Const.GOTO:
            return new GOTO(target);
        case Const.JSR:
            return new JSR(target);
        case Const.IFNULL:
            return new IFNULL(target);
        case Const.IFNONNULL:
            return new IFNONNULL(target);
        case Const.GOTO_W:
            return new GOTO_W(target);
        case Const.JSR_W:
            return new JSR_W(target);
        default:
            throw new IllegalArgumentException("Invalid opcode: " + opcode);
        }
    }

    /**
     * Creates a DUP instruction.
     *
     * @param size size of operand, either 1 (int, for example) or 2 (double).
     * @return The DUP instruction.
     */
    public static StackInstruction createDup(final int size) {
        return size == 2 ? InstructionConst.DUP2 : InstructionConst.DUP;
    }

    /**
     * Creates a DUP_X1 instruction.
     *
     * @param size size of operand, either 1 (int, for example) or 2 (double).
     * @return The DUP instruction.
     */
    public static StackInstruction createDup_1(final int size) {
        return size == 2 ? InstructionConst.DUP2_X1 : InstructionConst.DUP_X1;
    }

    /**
     * Creates a DUP_X2 instruction.
     *
     * @param size size of operand, either 1 (int, for example) or 2 (double).
     * @return The DUP instruction.
     */
    public static StackInstruction createDup_2(final int size) {
        return size == 2 ? InstructionConst.DUP2_X2 : InstructionConst.DUP_X2;
    }

    /**
     * Creates a load instruction for the given type.
     *
     * @param type The variable type.
     * @param index index of local variable.
     * @return The load instruction.
     */
    public static LocalVariableInstruction createLoad(final Type type, final int index) {
        switch (type.getType()) {
        case Const.T_BOOLEAN:
        case Const.T_CHAR:
        case Const.T_BYTE:
        case Const.T_SHORT:
        case Const.T_INT:
            return new ILOAD(index);
        case Const.T_FLOAT:
            return new FLOAD(index);
        case Const.T_DOUBLE:
            return new DLOAD(index);
        case Const.T_LONG:
            return new LLOAD(index);
        case Const.T_ARRAY:
        case Const.T_OBJECT:
            return new ALOAD(index);
        default:
            throw new IllegalArgumentException("Invalid type " + type);
        }
    }

    /**
     * Create "null" value for reference types, 0 for basic types like int.
     *
     * @param type The type.
     * @return The null or zero instruction.
     */
    public static Instruction createNull(final Type type) {
        switch (type.getType()) {
        case Const.T_ARRAY:
        case Const.T_OBJECT:
            return InstructionConst.ACONST_NULL;
        case Const.T_INT:
        case Const.T_SHORT:
        case Const.T_BOOLEAN:
        case Const.T_CHAR:
        case Const.T_BYTE:
            return InstructionConst.ICONST_0;
        case Const.T_FLOAT:
            return InstructionConst.FCONST_0;
        case Const.T_DOUBLE:
            return InstructionConst.DCONST_0;
        case Const.T_LONG:
            return InstructionConst.LCONST_0;
        case Const.T_VOID:
            return InstructionConst.NOP;
        default:
            throw new IllegalArgumentException("Invalid type: " + type);
        }
    }

    /**
     * Creates a pop instruction.
     *
     * @param size size of operand, either 1 (int, for example) or 2 (double).
     * @return The pop instruction.
     */
    public static StackInstruction createPop(final int size) {
        return size == 2 ? InstructionConst.POP2 : InstructionConst.POP;
    }

    /**
     * Create typed return.
     *
     * @param type The return type.
     * @return The return instruction.
     */
    public static ReturnInstruction createReturn(final Type type) {
        switch (type.getType()) {
        case Const.T_ARRAY:
        case Const.T_OBJECT:
            return InstructionConst.ARETURN;
        case Const.T_INT:
        case Const.T_SHORT:
        case Const.T_BOOLEAN:
        case Const.T_CHAR:
        case Const.T_BYTE:
            return InstructionConst.IRETURN;
        case Const.T_FLOAT:
            return InstructionConst.FRETURN;
        case Const.T_DOUBLE:
            return InstructionConst.DRETURN;
        case Const.T_LONG:
            return InstructionConst.LRETURN;
        case Const.T_VOID:
            return InstructionConst.RETURN;
        default:
            throw new IllegalArgumentException("Invalid type: " + type);
        }
    }

    /**
     * Creates a store instruction.
     *
     * @param type The variable type.
     * @param index index of local variable.
     * @return The store instruction.
     */
    public static LocalVariableInstruction createStore(final Type type, final int index) {
        switch (type.getType()) {
        case Const.T_BOOLEAN:
        case Const.T_CHAR:
        case Const.T_BYTE:
        case Const.T_SHORT:
        case Const.T_INT:
            return new ISTORE(index);
        case Const.T_FLOAT:
            return new FSTORE(index);
        case Const.T_DOUBLE:
            return new DSTORE(index);
        case Const.T_LONG:
            return new LSTORE(index);
        case Const.T_ARRAY:
        case Const.T_OBJECT:
            return new ASTORE(index);
        default:
            throw new IllegalArgumentException("Invalid type " + type);
        }
    }

    /**
     * Create reference to 'this'.
     *
     * @return reference to 'this'.
     */
    public static Instruction createThis() {
        return new ALOAD(0);
    }

    private static boolean isString(final Type type) {
        return type instanceof ObjectType && ((ObjectType) type).getClassName().equals("java.lang.String");
    }

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @Deprecated
    protected ClassGen cg;

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @Deprecated
    protected ConstantPoolGen cp;

    /**
     * Initialize with ClassGen object.
     *
     * @param cg The class generator.
     */
    public InstructionFactory(final ClassGen cg) {
        this(cg, cg.getConstantPool());
    }

    /**
     * Constructs an InstructionFactory.
     *
     * @param cg The class generator.
     * @param cp The constant pool generator.
     */
    public InstructionFactory(final ClassGen cg, final ConstantPoolGen cp) {
        this.cg = cg;
        this.cp = cp;
    }

    /**
     * Initialize just with ConstantPoolGen object.
     *
     * @param cp The constant pool generator.
     */
    public InstructionFactory(final ConstantPoolGen cp) {
        this(null, cp);
    }

    /**
     * Creates an append instruction for the given type.
     *
     * @param type The type to append.
     * @return The append instruction.
     */
    public Instruction createAppend(final Type type) {
        final byte t = type.getType();
        if (isString(type)) {
            return createInvoke(APPEND_METHOD_OBJECTS[0], Const.INVOKEVIRTUAL);
        }
        switch (t) {
        case Const.T_BOOLEAN:
        case Const.T_CHAR:
        case Const.T_FLOAT:
        case Const.T_DOUBLE:
        case Const.T_BYTE:
        case Const.T_SHORT:
        case Const.T_INT:
        case Const.T_LONG:
            return createInvoke(APPEND_METHOD_OBJECTS[t], Const.INVOKEVIRTUAL);
        case Const.T_ARRAY:
        case Const.T_OBJECT:
            return createInvoke(APPEND_METHOD_OBJECTS[1], Const.INVOKEVIRTUAL);
        default:
            throw new IllegalArgumentException("No append for this type? " + type);
        }
    }

    /**
     * Create conversion operation for two stack operands, this may be an I2C, instruction, for example, if the operands are basic
     * types and CHECKCAST if they are reference types.
     *
     * @param srcType The source type.
     * @param destType The destination type.
     * @return The conversion instruction.
     */
    public Instruction createCast(final Type srcType, final Type destType) {
        if (srcType instanceof BasicType && destType instanceof BasicType) {
            final byte dest = destType.getType();
            byte src = srcType.getType();
            if (dest == Const.T_LONG && (src == Const.T_CHAR || src == Const.T_BYTE || src == Const.T_SHORT)) {
                src = Const.T_INT;
            }
            final String name = "com.sun.org.apache.bcel.internal.generic." + SHORT_NAMES[src - Const.T_CHAR] + "2" + SHORT_NAMES[dest - Const.T_CHAR];
            Instruction i = null;
            try {
                i = (Instruction) Class.forName(name).getConstructor().newInstance();
            } catch (final Exception e) {
                throw new IllegalArgumentException("Could not find instruction: " + name, e);
            }
            return i;
        }
        if (!(srcType instanceof ReferenceType) || !(destType instanceof ReferenceType)) {
            throw new IllegalArgumentException("Cannot cast " + srcType + " to " + destType);
        }
        if (destType instanceof ArrayType) {
            return new CHECKCAST(cp.addArrayClass((ArrayType) destType));
        }
        return new CHECKCAST(cp.addClass(((ObjectType) destType).getClassName()));
    }

    /**
     * Creates a CHECKCAST instruction.
     *
     * @param t The reference type.
     * @return The CHECKCAST instruction.
     */
    public CHECKCAST createCheckCast(final ReferenceType t) {
        if (t instanceof ArrayType) {
            return new CHECKCAST(cp.addArrayClass((ArrayType) t));
        }
        return new CHECKCAST(cp.addClass((ObjectType) t));
    }

    /**
     * Uses PUSH to push a constant value onto the stack.
     *
     * @param value must be of type Number, Boolean, Character or String.
     * @return The instruction.
     */
    public Instruction createConstant(final Object value) {
        final PUSH push;
        if (value instanceof Number) {
            push = new PUSH(cp, (Number) value);
        } else if (value instanceof String) {
            push = new PUSH(cp, (String) value);
        } else if (value instanceof Boolean) {
            push = new PUSH(cp, (Boolean) value);
        } else if (value instanceof Character) {
            push = new PUSH(cp, (Character) value);
        } else {
            throw new ClassGenException("Illegal type: " + value.getClass());
        }
        return push.getInstruction();
    }

    /**
     * Create a field instruction.
     *
     * @param className class name containing the field.
     * @param name name of the referenced field.
     * @param type type of field.
     * @param kind how to access, that is, GETFIELD, PUTFIELD, GETSTATIC, PUTSTATIC.
     * @return A field instruction.
     * @see Const
     */
    public FieldInstruction createFieldAccess(final String className, final String name, final Type type, final short kind) {
        final String signature = type.getSignature();
        final int index = cp.addFieldref(className, name, signature);
        switch (kind) {
        case Const.GETFIELD:
            return new GETFIELD(index);
        case Const.PUTFIELD:
            return new PUTFIELD(index);
        case Const.GETSTATIC:
            return new GETSTATIC(index);
        case Const.PUTSTATIC:
            return new PUTSTATIC(index);
        default:
            throw new IllegalArgumentException("Unknown getfield kind:" + kind);
        }
    }

    /**
     * Creates a GETFIELD instruction.
     *
     * @param className The class name.
     * @param name The field name.
     * @param t The field type.
     * @return The GETFIELD instruction.
     */
    public GETFIELD createGetField(final String className, final String name, final Type t) {
        return new GETFIELD(cp.addFieldref(className, name, t.getSignature()));
    }

    /**
     * Creates a GETSTATIC instruction.
     *
     * @param className The class name.
     * @param name The field name.
     * @param t The field type.
     * @return The GETSTATIC instruction.
     */
    public GETSTATIC createGetStatic(final String className, final String name, final Type t) {
        return new GETSTATIC(cp.addFieldref(className, name, t.getSignature()));
    }

    /**
     * Creates an INSTANCEOF instruction.
     *
     * @param t The reference type.
     * @return The INSTANCEOF instruction.
     */
    public INSTANCEOF createInstanceOf(final ReferenceType t) {
        if (t instanceof ArrayType) {
            return new INSTANCEOF(cp.addArrayClass((ArrayType) t));
        }
        return new INSTANCEOF(cp.addClass((ObjectType) t));
    }

    private InvokeInstruction createInvoke(final MethodObject m, final short kind) {
        return createInvoke(m.className, m.name, m.resultType, m.argTypes, kind);
    }

    /**
     * Create an invoke instruction. (Except for invokedynamic.)
     *
     * @param className name of the called class.
     * @param name name of the called method.
     * @param retType return type of method.
     * @param argTypes argument types of method.
     * @param kind how to invoke, that is, INVOKEINTERFACE, INVOKESTATIC, INVOKEVIRTUAL, or INVOKESPECIAL.
     * @return The invoke instruction.
     * @see Const
     */
    public InvokeInstruction createInvoke(final String className, final String name, final Type retType, final Type[] argTypes, final short kind) {
        return createInvoke(className, name, retType, argTypes, kind, kind == Const.INVOKEINTERFACE);
    }

    /**
     * Create an invoke instruction. (Except for invokedynamic.)
     *
     * @param className name of the called class.
     * @param name name of the called method.
     * @param retType return type of method.
     * @param argTypes argument types of method.
     * @param kind how to invoke: INVOKEINTERFACE, INVOKESTATIC, INVOKEVIRTUAL, or INVOKESPECIAL.
     * @param useInterface force use of InterfaceMethodref.
     * @return A new InvokeInstruction.
     * @since 6.5.0
     */
    public InvokeInstruction createInvoke(final String className, final String name, final Type retType, final Type[] argTypes, final short kind,
        final boolean useInterface) {
        if (kind != Const.INVOKESPECIAL && kind != Const.INVOKEVIRTUAL && kind != Const.INVOKESTATIC && kind != Const.INVOKEINTERFACE
            && kind != Const.INVOKEDYNAMIC) {
            throw new IllegalArgumentException("Unknown invoke kind: " + kind);
        }
        final int index;
        int nargs = 0;
        final String signature = Type.getMethodSignature(retType, argTypes);
        if (argTypes != null) {
            for (final Type argType : argTypes) {
                nargs += argType.getSize();
            }
        }
        if (useInterface) {
            index = cp.addInterfaceMethodref(className, name, signature);
        } else {
            index = cp.addMethodref(className, name, signature);
        }
        switch (kind) {
        case Const.INVOKESPECIAL:
            return new INVOKESPECIAL(index);
        case Const.INVOKEVIRTUAL:
            return new INVOKEVIRTUAL(index);
        case Const.INVOKESTATIC:
            return new INVOKESTATIC(index);
        case Const.INVOKEINTERFACE:
            return new INVOKEINTERFACE(index, nargs + 1);
        case Const.INVOKEDYNAMIC:
            return new INVOKEDYNAMIC(index);
        default:
            // Can't happen
            throw new IllegalStateException("Unknown invoke kind: " + kind);
        }
    }

    /**
     * Creates a NEW instruction.
     *
     * @param t The object type.
     * @return The NEW instruction.
     */
    public NEW createNew(final ObjectType t) {
        return new NEW(cp.addClass(t));
    }

    /**
     * Creates a NEW instruction.
     *
     * @param s The class name.
     * @return The NEW instruction.
     */
    public NEW createNew(final String s) {
        return createNew(ObjectType.getInstance(s));
    }

    /**
     * Create new array of given size and type.
     *
     * @param t The array element type.
     * @param dim The array dimensions.
     * @return An instruction that creates the corresponding array at runtime, for example is an AllocationInstruction.
     */
    public Instruction createNewArray(final Type t, final short dim) {
        if (dim == 1) {
            if (t instanceof ObjectType) {
                return new ANEWARRAY(cp.addClass((ObjectType) t));
            }
            if (t instanceof ArrayType) {
                return new ANEWARRAY(cp.addArrayClass((ArrayType) t));
            }
            return new NEWARRAY(t.getType());
        }
        final ArrayType at;
        if (t instanceof ArrayType) {
            at = (ArrayType) t;
        } else {
            at = new ArrayType(t, dim);
        }
        return new MULTIANEWARRAY(cp.addArrayClass(at), dim);
    }

    /**
     * Create a call to the most popular System.out.println() method.
     *
     * @param s The string to print.
     * @return The instruction list.
     */
    public InstructionList createPrintln(final String s) {
        final InstructionList il = new InstructionList();
        il.append(createGetStatic("java.lang.System", "out", Type.getType("Ljava/io/PrintStream;")));
        il.append(new PUSH(cp, s));
        final MethodObject methodObject = new MethodObject("java.io.PrintStream", "println", Type.VOID, new Type[] { Type.getType("Ljava/lang/String;") });
        il.append(createInvoke(methodObject, Const.INVOKEVIRTUAL));
        return il;
    }

    /**
     * Creates a PUTFIELD instruction.
     *
     * @param className The class name.
     * @param name The field name.
     * @param t The field type.
     * @return The PUTFIELD instruction.
     */
    public PUTFIELD createPutField(final String className, final String name, final Type t) {
        return new PUTFIELD(cp.addFieldref(className, name, t.getSignature()));
    }

    /**
     * Creates a PUTSTATIC instruction.
     *
     * @param className The class name.
     * @param name The field name.
     * @param t The field type.
     * @return The PUTSTATIC instruction.
     */
    public PUTSTATIC createPutStatic(final String className, final String name, final Type t) {
        return new PUTSTATIC(cp.addFieldref(className, name, t.getSignature()));
    }

    /**
     * Gets the class generator.
     *
     * @return The class generator.
     */
    public ClassGen getClassGen() {
        return cg;
    }

    /**
     * Gets the constant pool generator.
     *
     * @return The constant pool generator.
     */
    public ConstantPoolGen getConstantPool() {
        return cp;
    }

    /**
     * Sets the class generator.
     *
     * @param c The class generator.
     */
    public void setClassGen(final ClassGen c) {
        cg = c;
    }

    /**
     * Sets the constant pool generator.
     *
     * @param c The constant pool generator.
     */
    public void setConstantPool(final ConstantPoolGen c) {
        cp = c;
    }
}
