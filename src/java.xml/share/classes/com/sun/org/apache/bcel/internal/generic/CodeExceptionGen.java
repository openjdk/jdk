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

import com.sun.org.apache.bcel.internal.classfile.CodeException;

/**
 * This class represents an exception handler, i.e., specifies the region where a handler is active and an instruction
 * where the actual handling is done. pool as parameters. Opposed to the JVM specification the end of the handled region
 * is set to be inclusive, i.e. all instructions between start and end are protected including the start and end
 * instructions (handles) themselves. The end of the region is automatically mapped to be exclusive when calling
 * getCodeException(), i.e., there is no difference semantically.
 *
 * @see MethodGen
 * @see CodeException
 * @see InstructionHandle
 */
public final class CodeExceptionGen implements InstructionTargeter, Cloneable {

    static final CodeExceptionGen[] EMPTY_ARRAY = {};

    private InstructionHandle startPc;
    private InstructionHandle endPc;
    private InstructionHandle handlerPc;
    private ObjectType catchType;

    /**
     * Add an exception handler, i.e., specify region where a handler is active and an instruction where the actual handling
     * is done.
     *
     * @param startPc Start of handled region (inclusive)
     * @param endPc End of handled region (inclusive)
     * @param handlerPc Where handling is done
     * @param catchType which exception is handled, null for ANY
     */
    public CodeExceptionGen(final InstructionHandle startPc, final InstructionHandle endPc, final InstructionHandle handlerPc, final ObjectType catchType) {
        setStartPC(startPc);
        setEndPC(endPc);
        setHandlerPC(handlerPc);
        this.catchType = catchType;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (final CloneNotSupportedException e) {
            throw new Error("Clone Not Supported"); // never happens
        }
    }

    /**
     * @return true, if ih is target of this handler
     */
    @Override
    public boolean containsTarget(final InstructionHandle ih) {
        return startPc == ih || endPc == ih || handlerPc == ih;
    }

    /** Gets the type of the Exception to catch, 'null' for ANY. */
    public ObjectType getCatchType() {
        return catchType;
    }

    /**
     * Get CodeException object.<BR>
     *
     * This relies on that the instruction list has already been dumped to byte code or that the 'setPositions' methods
     * has been called for the instruction list.
     *
     * @param cp constant pool
     */
    public CodeException getCodeException(final ConstantPoolGen cp) {
        return new CodeException(startPc.getPosition(), endPc.getPosition() + endPc.getInstruction().getLength(), handlerPc.getPosition(),
            catchType == null ? 0 : cp.addClass(catchType));
    }

    /**
     * @return end of handled region (inclusive)
     */
    public InstructionHandle getEndPC() {
        return endPc;
    }

    /**
     * @return start of handler
     */
    public InstructionHandle getHandlerPC() {
        return handlerPc;
    }

    /**
     * @return start of handled region (inclusive)
     */
    public InstructionHandle getStartPC() {
        return startPc;
    }

    /** Sets the type of the Exception to catch. Set 'null' for ANY. */
    public void setCatchType(final ObjectType catchType) {
        this.catchType = catchType;
    }

    /*
     * Set end of handler
     *
     * @param endPc End of handled region (inclusive)
     */
    public void setEndPC(final InstructionHandle endPc) { // TODO could be package-protected?
        BranchInstruction.notifyTarget(this.endPc, endPc, this);
        this.endPc = endPc;
    }

    /*
     * Set handler code
     *
     * @param handlerPc Start of handler
     */
    public void setHandlerPC(final InstructionHandle handlerPc) { // TODO could be package-protected?
        BranchInstruction.notifyTarget(this.handlerPc, handlerPc, this);
        this.handlerPc = handlerPc;
    }

    /*
     * Set start of handler
     *
     * @param startPc Start of handled region (inclusive)
     */
    public void setStartPC(final InstructionHandle startPc) { // TODO could be package-protected?
        BranchInstruction.notifyTarget(this.startPc, startPc, this);
        this.startPc = startPc;
    }

    @Override
    public String toString() {
        return "CodeExceptionGen(" + startPc + ", " + endPc + ", " + handlerPc + ")";
    }

    /**
     * @param oldIh old target, either start or end
     * @param newIh new target
     */
    @Override
    public void updateTarget(final InstructionHandle oldIh, final InstructionHandle newIh) {
        boolean targeted = false;
        if (startPc == oldIh) {
            targeted = true;
            setStartPC(newIh);
        }
        if (endPc == oldIh) {
            targeted = true;
            setEndPC(newIh);
        }
        if (handlerPc == oldIh) {
            targeted = true;
            setHandlerPC(newIh);
        }
        if (!targeted) {
            throw new ClassGenException("Not targeting " + oldIh + ", but {" + startPc + ", " + endPc + ", " + handlerPc + "}");
        }
    }
}
