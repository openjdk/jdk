/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
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

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.ExceptionConst;
import com.sun.org.apache.bcel.internal.classfile.ConstantPool;
import com.sun.org.apache.bcel.internal.util.ByteSequence;

/**
 * INVOKEINTERFACE - Invoke interface method
 *
 * <pre>
 * Stack: ..., objectref, [arg1, [arg2 ...]] -&gt; ...
 * </pre>
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-6.html#jvms-6.5.invokeinterface"> The
 *      invokeinterface instruction in The Java Virtual Machine Specification</a>
 */
public final class INVOKEINTERFACE extends InvokeInstruction {

    private int nargs; // Number of arguments on stack (number of stack slots), called "count" in vmspec2

    /**
     * Empty constructor needed for Instruction.readInstruction. Not to be used otherwise.
     */
    INVOKEINTERFACE() {
    }

    /**
     * Constructs an INVOKEINTERFACE instruction.
     *
     * @param index index into constant pool.
     * @param nargs number of arguments.
     */
    public INVOKEINTERFACE(final int index, final int nargs) {
        super(Const.INVOKEINTERFACE, index);
        super.setLength(5);
        if (nargs < 1 || nargs > Const.MAX_BYTE) {
            throw new ClassGenException("Number of arguments out of range (1 - " + Const.MAX_BYTE + "): " + nargs);
        }
        this.nargs = nargs;
    }

    /**
     * Call corresponding visitor method(s). The order is: Call visitor methods of implemented interfaces first, then call
     * methods according to the class hierarchy in descending order, that is, the most specific visitXXX() call comes last.
     *
     * @param v Visitor object.
     */
    @Override
    public void accept(final Visitor v) {
        v.visitExceptionThrower(this);
        v.visitTypedInstruction(this);
        v.visitStackConsumer(this);
        v.visitStackProducer(this);
        v.visitLoadClass(this);
        v.visitCPInstruction(this);
        v.visitFieldOrMethod(this);
        v.visitInvokeInstruction(this);
        v.visitINVOKEINTERFACE(this);
    }

    @Override
    public int consumeStack(final ConstantPoolGen cpg) { // nargs is given in byte-code
        return nargs; // nargs includes this reference
    }

    /**
     * Dumps instruction as byte code to stream out.
     *
     * @param out Output stream.
     */
    @Override
    public void dump(final DataOutputStream out) throws IOException {
        out.writeByte(super.getOpcode());
        out.writeShort(super.getIndex());
        out.writeByte(nargs);
        out.writeByte(0);
    }

    /**
     * The <strong>count</strong> argument according to the Java Language Specification, Second Edition.
     *
     * @return The count argument.
     */
    public int getCount() {
        return nargs;
    }

    @Override
    public Class<?>[] getExceptions() {
        return ExceptionConst.createExceptions(ExceptionConst.EXCS.EXCS_INTERFACE_METHOD_RESOLUTION, ExceptionConst.UNSATISFIED_LINK_ERROR,
            ExceptionConst.ABSTRACT_METHOD_ERROR, ExceptionConst.ILLEGAL_ACCESS_ERROR, ExceptionConst.INCOMPATIBLE_CLASS_CHANGE_ERROR);
    }

    /**
     * Reads needed data (that is, index) from file.
     */
    @Override
    protected void initFromFile(final ByteSequence bytes, final boolean wide) throws IOException {
        super.initFromFile(bytes, wide);
        super.setLength(5);
        nargs = bytes.readUnsignedByte();
        bytes.readByte(); // Skip 0 byte
    }

    /**
     * @return mnemonic for instruction with symbolic references resolved.
     */
    @Override
    public String toString(final ConstantPool cp) {
        return super.toString(cp) + " " + nargs;
    }
}
