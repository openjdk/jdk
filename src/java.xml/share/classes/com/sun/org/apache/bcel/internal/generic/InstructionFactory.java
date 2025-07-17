/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * Instances of this class may be used, e.g., to generate typed versions of instructions. Its main purpose is to be used
 * as the byte code generating backend of a compiler. You can subclass it to add your own create methods.
 * <p>
 * Note: The static createXXX methods return singleton instances from the {@link InstructionConst} class.
 * </p>
 *
 * @see Const
 * @see InstructionConst
 * @LastModified: Feb 2023
 */
public class InstructionFactory {

    private static class MethodObject {

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

    // N.N. These must agree with the order of Constants.T_CHAR through T_LONG
    private static final String[] shortNames = {"C", "F", "D", "B", "S", "I", "L"};

    private static final MethodObject[] appendMethodObjects = {
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
     * @param type type of elements of array, i.e., array.getElementType()
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
     * @param type type of elements of array, i.e., array.getElementType()
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
     * @param bootstrap_index index into the bootstrap_methods array
     * @param name name of the called method
     * @param ret_type return type of method
     * @param argTypes argument types of method
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
     * @param size size of operand, either 1 (int, e.g.) or 2 (double)
     */
    public static StackInstruction createDup(final int size) {
        return size == 2 ? InstructionConst.DUP2 : InstructionConst.DUP;
    }

    /**
     * @param size size of operand, either 1 (int, e.g.) or 2 (double)
     */
    public static StackInstruction createDup_1(final int size) {
        return size == 2 ? InstructionConst.DUP2_X1 : InstructionConst.DUP_X1;
    }

    /**
     * @param size size of operand, either 1 (int, e.g.) or 2 (double)
     */
    public static StackInstruction createDup_2(final int size) {
        return size == 2 ? InstructionConst.DUP2_X2 : InstructionConst.DUP_X2;
    }

    /**
     * @param index index of local variable
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
     * Create "null" value for reference types, 0 for basic types like int
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
     * @param size size of operand, either 1 (int, e.g.) or 2 (double)
     */
    public static StackInstruction createPop(final int size) {
        return size == 2 ? InstructionConst.POP2 : InstructionConst.POP;
    }

    /**
     * Create typed return
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
     * @param index index of local variable
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
     * Create reference to 'this'
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
     * Initialize with ClassGen object
     */
    public InstructionFactory(final ClassGen cg) {
        this(cg, cg.getConstantPool());
    }

    public InstructionFactory(final ClassGen cg, final ConstantPoolGen cp) {
        this.cg = cg;
        this.cp = cp;
    }

    /**
     * Initialize just with ConstantPoolGen object
     */
    public InstructionFactory(final ConstantPoolGen cp) {
        this(null, cp);
    }

    public Instruction createAppend(final Type type) {
        final byte t = type.getType();
        if (isString(type)) {
            return createInvoke(appendMethodObjects[0], Const.INVOKEVIRTUAL);
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
            return createInvoke(appendMethodObjects[t], Const.INVOKEVIRTUAL);
        case Const.T_ARRAY:
        case Const.T_OBJECT:
            return createInvoke(appendMethodObjects[1], Const.INVOKEVIRTUAL);
        default:
            throw new IllegalArgumentException("No append for this type? " + type);
        }
    }

    /**
     * Create conversion operation for two stack operands, this may be an I2C, instruction, e.g., if the operands are basic
     * types and CHECKCAST if they are reference types.
     */
    public Instruction createCast(final Type srcType, final Type destType) {
        if (srcType instanceof BasicType && destType instanceof BasicType) {
            final byte dest = destType.getType();
            byte src = srcType.getType();
            if (dest == Const.T_LONG && (src == Const.T_CHAR || src == Const.T_BYTE || src == Const.T_SHORT)) {
                src = Const.T_INT;
            }
            final String name = "com.sun.org.apache.bcel.internal.generic." + shortNames[src - Const.T_CHAR] + "2" + shortNames[dest - Const.T_CHAR];
            Instruction i = null;
            try {
                i = (Instruction) Class.forName(name).getDeclaredConstructor().newInstance();;
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

    public CHECKCAST createCheckCast(final ReferenceType t) {
        if (t instanceof ArrayType) {
            return new CHECKCAST(cp.addArrayClass((ArrayType) t));
        }
        return new CHECKCAST(cp.addClass((ObjectType) t));
    }

    /**
     * Uses PUSH to push a constant value onto the stack.
     *
     * @param value must be of type Number, Boolean, Character or String
     */
    public Instruction createConstant(final Object value) {
        PUSH push;
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
     * @param className name of the accessed class
     * @param name name of the referenced field
     * @param type type of field
     * @param kind how to access, i.e., GETFIELD, PUTFIELD, GETSTATIC, PUTSTATIC
     * @see Const
     */
    public FieldInstruction createFieldAccess(final String className, final String name, final Type type, final short kind) {
        int index;
        final String signature = type.getSignature();
        index = cp.addFieldref(className, name, signature);
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

    public GETFIELD createGetField(final String className, final String name, final Type t) {
        return new GETFIELD(cp.addFieldref(className, name, t.getSignature()));
    }

    public GETSTATIC createGetStatic(final String className, final String name, final Type t) {
        return new GETSTATIC(cp.addFieldref(className, name, t.getSignature()));
    }

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
     * @param className name of the called class
     * @param name name of the called method
     * @param retType return type of method
     * @param argTypes argument types of method
     * @param kind how to invoke, i.e., INVOKEINTERFACE, INVOKESTATIC, INVOKEVIRTUAL, or INVOKESPECIAL
     * @see Const
     */
    public InvokeInstruction createInvoke(final String className, final String name, final Type retType, final Type[] argTypes, final short kind) {
        return createInvoke(className, name, retType, argTypes, kind, kind == Const.INVOKEINTERFACE);
    }

    /**
     * Create an invoke instruction. (Except for invokedynamic.)
     *
     * @param className name of the called class
     * @param name name of the called method
     * @param retType return type of method
     * @param argTypes argument types of method
     * @param kind how to invoke: INVOKEINTERFACE, INVOKESTATIC, INVOKEVIRTUAL, or INVOKESPECIAL
     * @param useInterface force use of InterfaceMethodref
     * @return A new InvokeInstruction.
     * @since 6.5.0
     */
    public InvokeInstruction createInvoke(final String className, final String name, final Type retType, final Type[] argTypes, final short kind,
        final boolean useInterface) {
        if (kind != Const.INVOKESPECIAL && kind != Const.INVOKEVIRTUAL && kind != Const.INVOKESTATIC && kind != Const.INVOKEINTERFACE
            && kind != Const.INVOKEDYNAMIC) {
            throw new IllegalArgumentException("Unknown invoke kind: " + kind);
        }
        int index;
        int nargs = 0;
        final String signature = Type.getMethodSignature(retType, argTypes);
        for (final Type argType : argTypes) {
            nargs += argType.getSize();
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

    public NEW createNew(final ObjectType t) {
        return new NEW(cp.addClass(t));
    }

    public NEW createNew(final String s) {
        return createNew(ObjectType.getInstance(s));
    }

    /**
     * Create new array of given size and type.
     *
     * @return an instruction that creates the corresponding array at runtime, i.e. is an AllocationInstruction
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
        ArrayType at;
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
     * @param s the string to print
     */
    public InstructionList createPrintln(final String s) {
        final InstructionList il = new InstructionList();
        il.append(createGetStatic("java.lang.System", "out", Type.getType("Ljava/io/PrintStream;")));
        il.append(new PUSH(cp, s));
        final MethodObject methodObject = new MethodObject("java.io.PrintStream", "println", Type.VOID, new Type[] { Type.getType("Ljava/lang/String;") });
        il.append(createInvoke(methodObject, Const.INVOKEVIRTUAL));
        return il;
    }

    public PUTFIELD createPutField(final String className, final String name, final Type t) {
        return new PUTFIELD(cp.addFieldref(className, name, t.getSignature()));
    }

    public PUTSTATIC createPutStatic(final String className, final String name, final Type t) {
        return new PUTSTATIC(cp.addFieldref(className, name, t.getSignature()));
    }

    public ClassGen getClassGen() {
        return cg;
    }

    public ConstantPoolGen getConstantPool() {
        return cp;
    }

    public void setClassGen(final ClassGen c) {
        cg = c;
    }

    public void setConstantPool(final ConstantPoolGen c) {
        cp = c;
    }
}
