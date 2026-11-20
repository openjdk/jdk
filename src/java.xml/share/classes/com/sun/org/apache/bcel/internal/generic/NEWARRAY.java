/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.org.apache.bcel.internal.ExceptionConst;
import com.sun.org.apache.bcel.internal.util.ByteSequence;

/**
 * NEWARRAY - Create new array of basic type (int, short, ...)
 *
 * <pre>
 * Stack: ..., count -&gt; ..., arrayref
 * </pre>
 *
 * type must be one of T_INT, T_SHORT, ...
 * @LastModified: Nov 2026
 */
public class NEWARRAY extends Instruction implements AllocationInstruction, ExceptionThrower, StackProducer {

    private byte type;

    /**
     * Empty constructor needed for Instruction.readInstruction. Not to be used otherwise.
     */
    NEWARRAY() {
    }

    /**
     * Constructs a NEWARRAY instruction.
     *
     * @param type element type of array.
     */
    public NEWARRAY(final BasicType type) {
        this(type.getType());
    }

    /**
     * Constructs a NEWARRAY instruction.
     *
     * @param type element type code.
     */
    public NEWARRAY(final byte type) {
        super(com.sun.org.apache.bcel.internal.Const.NEWARRAY, (short) 2);
        this.type = type;
    }

    /**
     * Call corresponding visitor method(s). The order is: Call visitor methods of implemented interfaces first, then call
     * methods according to the class hierarchy in descending order, that is, the most specific visitXXX() call comes last.
     *
     * @param v Visitor object.
     */
    @Override
    public void accept(final Visitor v) {
        v.visitAllocationInstruction(this);
        v.visitExceptionThrower(this);
        v.visitStackProducer(this);
        v.visitNEWARRAY(this);
    }

    /**
     * Dumps instruction as byte code to stream out.
     *
     * @param out Output stream.
     */
    @Override
    public void dump(final DataOutputStream out) throws IOException {
        out.writeByte(super.getOpcode());
        out.writeByte(type);
    }

    @Override
    public Class<?>[] getExceptions() {
        return new Class<?>[] {ExceptionConst.NEGATIVE_ARRAY_SIZE_EXCEPTION};
    }

    /**
     * Gets the type of the constructed array.
     *
     * @return type of constructed array.
     */
    public final Type getType() {
        return new ArrayType(BasicType.getType(type), 1);
    }

    /**
     * Gets the type code.
     *
     * @return numeric code for basic element type.
     */
    public final byte getTypecode() {
        return type;
    }

    /**
     * Reads needed data (for example index) from file.
     */
    @Override
    protected void initFromFile(final ByteSequence bytes, final boolean wide) throws IOException {
        type = bytes.readByte();
        super.setLength(2);
    }

    /**
     * @return mnemonic for instruction.
     */
    @Override
    public String toString(final boolean verbose) {
        return super.toString(verbose) + " " + com.sun.org.apache.bcel.internal.Const.getTypeName(type);
    }
}
