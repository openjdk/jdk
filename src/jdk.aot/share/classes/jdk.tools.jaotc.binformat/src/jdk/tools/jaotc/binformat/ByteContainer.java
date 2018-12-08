/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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



package jdk.tools.jaotc.binformat;

import jdk.tools.jaotc.binformat.Symbol.Binding;
import jdk.tools.jaotc.binformat.Symbol.Kind;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Base class that represents content of all sections with byte-level granularity. The ByteContainer
 * class is backed by a ByteArrayOutputStream. This class supports writing all desired byte content
 * to the container using the method {@code appendBytes} and accessing the byte array using the
 * method {@code getByteArray}.
 *
 * The method {@code putIntAt} updates the content of {@code contentBytes}. Changes are not
 * reflected in {@code contentStream}.
 */
public class ByteContainer implements Container {
    /**
     * {@code ByteBuffer} representation of {@code BinaryContainer}.
     */
    private ByteBuffer contentBytes;

    /**
     * {@code ByteArrayoutputStream} to which all appends are done.
     */
    private ByteArrayOutputStream contentStream;

    /**
     * Boolean to indicate if contentBytes was modified.
     */
    private boolean bufferModified;

    /**
     * Boolean to indicate if this section contains any relocations.
     */
    private boolean hasRelocations;

    /**
     * Name of this container, used as section name.
     */
    private String containerName;
    private final SymbolTable symbolTable;

    /**
     * Contains a unique id.
     */
    private int sectionId = -1;

    /**
     * Construct a {@code ByteContainer} object.
     */
    public ByteContainer(String containerName, SymbolTable symbolTable) {
        this.containerName = containerName;
        this.symbolTable = symbolTable;
        this.contentBytes = null;
        this.bufferModified = false;
        this.hasRelocations = false;
        this.contentStream = new ByteArrayOutputStream();
    }

    /**
     * Update byte buffer to reflect the current contents of byte stream.
     *
     * @throws InternalError throws {@code InternalError} if buffer byte array was modified
     */
    private void updateByteBuffer() {
        if (!bufferModified) {
            contentBytes = ByteBuffer.wrap(contentStream.toByteArray());
            // Default byte order of ByteBuffer is BIG_ENDIAN.
            // Set it appropriately
            this.contentBytes.order(ByteOrder.nativeOrder());
        } else {
            throw new InternalError("Backing byte buffer no longer in sync with byte stream");
        }
    }

    /**
     * Get the byte array of {@code ByteContainer}.
     *
     * @return byte array
     * @throws InternalError throws {@code InternalError} if buffer byte array was modified
     */
    public byte[] getByteArray() {
        if (!bufferModified) {
            updateByteBuffer();
        }
        return contentBytes.array();
    }

    /**
     * Append to byte stream. It is an error to append to stream if the byte buffer version is
     * changed.
     *
     * @param newBytes new content
     * @param off offset start offset in {@code newBytes}
     * @param len length of data to write
     * @throws InternalError throws {@code InternalError} if buffer byte array was modified
     */
    public ByteContainer appendBytes(byte[] newBytes, int off, int len) {
        if (bufferModified) {
            throw new InternalError("Backing byte buffer no longer in sync with byte stream");
        }
        contentStream.write(newBytes, off, len);
        return this;
    }

    public ByteContainer appendBytes(byte[] newBytes) {
        appendBytes(newBytes, 0, newBytes.length);
        return this;
    }

    public ByteContainer appendInt(int i) {
        if (bufferModified) {
            throw new InternalError("Backing byte buffer no longer in sync with byte stream");
        }
        ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
        b.order(ByteOrder.nativeOrder());
        b.putInt(i);
        byte[] result = b.array();
        contentStream.write(result, 0, result.length);
        return this;
    }

    public ByteContainer appendInts(int[] newInts) {
        if (bufferModified) {
            throw new InternalError("Backing byte buffer no longer in sync with byte stream");
        }
        ByteBuffer b = ByteBuffer.allocate(Integer.BYTES * newInts.length).order(ByteOrder.nativeOrder());
        Arrays.stream(newInts).forEach(i -> b.putInt(i));
        byte[] result = b.array();
        contentStream.write(result, 0, result.length);
        return this;
    }

    public void appendLong(long l) {
        if (bufferModified) {
            throw new InternalError("Backing byte buffer no longer in sync with byte stream");
        }
        ByteBuffer b = ByteBuffer.allocate(8);
        b.order(ByteOrder.nativeOrder());
        b.putLong(l);
        byte[] result = b.array();
        contentStream.write(result, 0, result.length);
    }

    /**
     * Return the current size of byte stream backing the BinaryContainer.
     *
     * @return size of buffer stream
     */
    public int getByteStreamSize() {
        return contentStream.size();
    }

    /**
     * Return the name of this container.
     *
     * @return string containing name
     */
    @Override
    public String getContainerName() {
        return containerName;
    }

    /**
     * Modify the byte buffer version of the byte output stream. Note that after calling this method
     * all further updates to BinaryContainer will be out of sync with byte buffer content.
     *
     * @param index index of byte to be changed
     * @param value new value
     */
    public void putIntAt(int index, int value) {
        if (!bufferModified) {
            updateByteBuffer();
        }
        contentBytes.putInt(index, value);
        bufferModified = true;
    }

    public void putLongAt(int index, long value) {
        if (!bufferModified) {
            updateByteBuffer();
        }
        contentBytes.putLong(index, value);
        bufferModified = true;
    }

    public void setSectionId(int id) {
        if (sectionId != -1) {
            throw new InternalError("Assigning new sectionId (old: " + sectionId + ", new: " + id + ")");
        }
        sectionId = id;
    }

    @Override
    public int getSectionId() {
        if (sectionId == -1) {
            throw new InternalError("Using sectionId before assigned");
        }
        return sectionId;
    }

    public Symbol createSymbol(int offset, Kind kind, Binding binding, int size, String name) {
        Symbol symbol = new Symbol(offset, kind, binding, this, size, name);
        symbolTable.addSymbol(symbol);
        return symbol;
    }

    public GotSymbol createGotSymbol(String name) {
        GotSymbol symbol = new GotSymbol(Kind.OBJECT, Binding.LOCAL, this, name);
        symbolTable.addSymbol(symbol);
        return symbol;
    }

    public GotSymbol createGotSymbol(int offset, String name) {
        GotSymbol symbol = new GotSymbol(offset, Kind.OBJECT, Binding.LOCAL, this, name);
        symbolTable.addSymbol(symbol);
        return symbol;
    }

    public void clear() {
        this.contentBytes = null;
        this.contentStream = null;
    }

    public void setHasRelocations() {
        this.hasRelocations = true;
    }

    public boolean hasRelocations() {
        return this.hasRelocations;
    }
}
