/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.org.apache.bcel.internal.classfile;

import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.org.apache.bcel.internal.Const;

/**
 * This class represents an entry in the exception table of the <em>Code</em>
 * attribute and is used only there. It contains a range in which a
 * particular exception handler is active.
 *
 * @see     Code
 * @LastModified: Jan 2020
 */
public final class CodeException implements Cloneable, Node {

    private int start_pc; // Range in the code the exception handler is
    private int end_pc; // active. start_pc is inclusive, end_pc exclusive
    private int handler_pc; /* Starting address of exception handler, i.e.,
     * an offset from start of code.
     */
    private int catch_type; /* If this is zero the handler catches any
     * exception, otherwise it points to the
     * exception class which is to be caught.
     */


    /**
     * Initialize from another object.
     */
    public CodeException(final CodeException c) {
        this(c.getStartPC(), c.getEndPC(), c.getHandlerPC(), c.getCatchType());
    }


    /**
     * Construct object from file stream.
     * @param file Input stream
     * @throws IOException
     */
    CodeException(final DataInput file) throws IOException {
        this(file.readUnsignedShort(), file.readUnsignedShort(), file.readUnsignedShort(), file
                .readUnsignedShort());
    }


    /**
     * @param start_pc Range in the code the exception handler is active,
     * start_pc is inclusive while
     * @param end_pc is exclusive
     * @param handler_pc Starting address of exception handler, i.e.,
     * an offset from start of code.
     * @param catch_type If zero the handler catches any
     * exception, otherwise it points to the exception class which is
     * to be caught.
     */
    public CodeException(final int start_pc, final int end_pc, final int handler_pc, final int catch_type) {
        this.start_pc = start_pc;
        this.end_pc = end_pc;
        this.handler_pc = handler_pc;
        this.catch_type = catch_type;
    }


    /**
     * Called by objects that are traversing the nodes of the tree implicitely
     * defined by the contents of a Java class. I.e., the hierarchy of methods,
     * fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept( final Visitor v ) {
        v.visitCodeException(this);
    }


    /**
     * Dump code exception to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    public void dump( final DataOutputStream file ) throws IOException {
        file.writeShort(start_pc);
        file.writeShort(end_pc);
        file.writeShort(handler_pc);
        file.writeShort(catch_type);
    }


    /**
     * @return 0, if the handler catches any exception, otherwise it points to
     * the exception class which is to be caught.
     */
    public int getCatchType() {
        return catch_type;
    }


    /**
     * @return Exclusive end index of the region where the handler is active.
     */
    public int getEndPC() {
        return end_pc;
    }


    /**
     * @return Starting address of exception handler, relative to the code.
     */
    public int getHandlerPC() {
        return handler_pc;
    }


    /**
     * @return Inclusive start index of the region where the handler is active.
     */
    public int getStartPC() {
        return start_pc;
    }


    /**
     * @param catch_type the type of exception that is caught
     */
    public void setCatchType( final int catch_type ) {
        this.catch_type = catch_type;
    }


    /**
     * @param end_pc end of handled block
     */
    public void setEndPC( final int end_pc ) {
        this.end_pc = end_pc;
    }


    /**
     * @param handler_pc where the actual code is
     */
    public void setHandlerPC( final int handler_pc ) { // TODO unused
        this.handler_pc = handler_pc;
    }


    /**
     * @param start_pc start of handled block
     */
    public void setStartPC( final int start_pc ) { // TODO unused
        this.start_pc = start_pc;
    }


    /**
     * @return String representation.
     */
    @Override
    public String toString() {
        return "CodeException(start_pc = " + start_pc + ", end_pc = " + end_pc + ", handler_pc = "
                + handler_pc + ", catch_type = " + catch_type + ")";
    }


    /**
     * @return String representation.
     */
    public String toString( final ConstantPool cp, final boolean verbose ) {
        String str;
        if (catch_type == 0) {
            str = "<Any exception>(0)";
        } else {
            str = Utility.compactClassName(cp.getConstantString(catch_type, Const.CONSTANT_Class), false)
                    + (verbose ? "(" + catch_type + ")" : "");
        }
        return start_pc + "\t" + end_pc + "\t" + handler_pc + "\t" + str;
    }


    public String toString( final ConstantPool cp ) {
        return toString(cp, true);
    }


    /**
     * @return deep copy of this object
     */
    public CodeException copy() {
        try {
            return (CodeException) clone();
        } catch (final CloneNotSupportedException e) {
            // TODO should this throw?
        }
        return null;
    }
}
