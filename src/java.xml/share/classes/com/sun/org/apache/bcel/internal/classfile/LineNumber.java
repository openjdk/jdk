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

/**
 * This class represents a (PC offset, line number) pair, i.e., a line number in
 * the source that corresponds to a relative address in the byte code. This
 * is used for debugging purposes.
 *
 * @version $Id: LineNumber.java 1749603 2016-06-21 20:50:19Z ggregory $
 * @see     LineNumberTable
 */
public final class LineNumber implements Cloneable, Node {

    /** Program Counter (PC) corresponds to line */
    private short start_pc;

    /** number in source file */
    private short line_number;

    /**
     * Initialize from another object.
     *
     * @param c the object to copy
     */
    public LineNumber(final LineNumber c) {
        this(c.getStartPC(), c.getLineNumber());
    }


    /**
     * Construct object from file stream.
     *
     * @param file Input stream
     * @throws IOEXception if an I/O Exception occurs in readUnsignedShort
     */
    LineNumber(final DataInput file) throws IOException {
        this(file.readUnsignedShort(), file.readUnsignedShort());
    }


    /**
     * @param start_pc Program Counter (PC) corresponds to
     * @param line_number line number in source file
     */
    public LineNumber(final int start_pc, final int line_number) {
        this.start_pc = (short) start_pc;
        this.line_number = (short)line_number;
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
        v.visitLineNumber(this);
    }


    /**
     * Dump line number/pc pair to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOEXception if an I/O Exception occurs in writeShort
     */
    public final void dump( final DataOutputStream file ) throws IOException {
        file.writeShort(start_pc);
        file.writeShort(line_number);
    }


    /**
     * @return Corresponding source line
     */
    public final int getLineNumber() {
        return 0xffff & line_number;
    }


    /**
     * @return PC in code
     */
    public final int getStartPC() {
        return  0xffff & start_pc;
    }


    /**
     * @param line_number the source line number
     */
    public final void setLineNumber( final int line_number ) {
        this.line_number = (short) line_number;
    }


    /**
     * @param start_pc the pc for this line number
     */
    public final void setStartPC( final int start_pc ) {
        this.start_pc = (short) start_pc;
    }


    /**
     * @return String representation
     */
    @Override
    public final String toString() {
        return "LineNumber(" + start_pc + ", " + line_number + ")";
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
}
