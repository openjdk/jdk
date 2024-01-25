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

package com.sun.org.apache.bcel.internal.classfile;

import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.org.apache.bcel.internal.util.Args;

/**
 * This class represents a (PC offset, line number) pair, i.e., a line number in the source that corresponds to a
 * relative address in the byte code. This is used for debugging purposes.
 *
 * @see LineNumberTable
 */
public final class LineNumber implements Cloneable, Node {

    static final LineNumber[] EMPTY_ARRAY = {};

    /** Program Counter (PC) corresponds to line */
    private int startPc;

    /** number in source file */
    private int lineNumber;

    /**
     * Construct object from file stream.
     *
     * @param file Input stream
     * @throws IOException if an I/O Exception occurs in readUnsignedShort
     */
    LineNumber(final DataInput file) throws IOException {
        this(file.readUnsignedShort(), file.readUnsignedShort());
    }

    /**
     * @param startPc Program Counter (PC) corresponds to
     * @param lineNumber line number in source file
     */
    public LineNumber(final int startPc, final int lineNumber) {
        this.startPc = Args.requireU2(startPc, "startPc");
        this.lineNumber = Args.requireU2(lineNumber, "lineNumber");
    }

    /**
     * Initialize from another object.
     *
     * @param c the object to copy
     */
    public LineNumber(final LineNumber c) {
        this(c.getStartPC(), c.getLineNumber());
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitLineNumber(this);
    }

    /**
     * @return deep copy of this object
     */
    public LineNumber copy() {
        try {
            return (LineNumber) clone();
        } catch (final CloneNotSupportedException e) {
            // TODO should this throw?
        }
        return null;
    }

    /**
     * Dump line number/pc pair to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O Exception occurs in writeShort
     */
    public void dump(final DataOutputStream file) throws IOException {
        file.writeShort(startPc);
        file.writeShort(lineNumber);
    }

    /**
     * @return Corresponding source line
     */
    public int getLineNumber() {
        return lineNumber & 0xffff;
    }

    /**
     * @return PC in code
     */
    public int getStartPC() {
        return startPc & 0xffff;
    }

    /**
     * @param lineNumber the source line number
     */
    public void setLineNumber(final int lineNumber) {
        this.lineNumber = (short) lineNumber;
    }

    /**
     * @param startPc the pc for this line number
     */
    public void setStartPC(final int startPc) {
        this.startPc = (short) startPc;
    }

    /**
     * @return String representation
     */
    @Override
    public String toString() {
        return "LineNumber(" + getStartPC() + ", " + getLineNumber() + ")";
    }
}
