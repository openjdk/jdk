/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.consumer;

import java.io.DataInput;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;

public final class RecordingInput implements DataInput, AutoCloseable {

    public static final byte STRING_ENCODING_NULL = 0;
    public static final byte STRING_ENCODING_EMPTY_STRING = 1;
    public static final byte STRING_ENCODING_CONSTANT_POOL = 2;
    public static final byte STRING_ENCODING_UTF8_BYTE_ARRAY = 3;
    public static final byte STRING_ENCODING_CHAR_ARRAY = 4;
    public static final byte STRING_ENCODING_LATIN1_BYTE_ARRAY = 5;

    private final static int DEFAULT_BLOCK_SIZE = 16 * 1024 * 1024;
    private final static Charset UTF8 = Charset.forName("UTF-8");
    private final static Charset LATIN1 = Charset.forName("ISO-8859-1");

    private static final class Block {
        private byte[] bytes = new byte[0];
        private long blockPosition;

        boolean contains(long position) {
            return position >= blockPosition && position < blockPosition + bytes.length;
        }

        public void read(RandomAccessFile file, int amount) throws IOException {
            blockPosition = file.getFilePointer();
            // reuse byte array, if possible
            if (amount != bytes.length) {
                bytes = new byte[amount];
            }
            file.readFully(bytes);
        }

        public byte get(long position) {
            return bytes[(int) (position - blockPosition)];
        }
    }

    private final RandomAccessFile file;
    private final long size;
    private Block currentBlock = new Block();
    private Block previousBlock = new Block();
    private long position;
    private final int blockSize;

    private RecordingInput(File f, int blockSize) throws IOException {
        this.size = f.length();
        this.blockSize = blockSize;
        this.file = new RandomAccessFile(f, "r");
        if (size < 8) {
            throw new IOException("Not a valid Flight Recorder file. File length is only " + size + " bytes.");
        }
    }

    public RecordingInput(File f) throws IOException {
        this(f, DEFAULT_BLOCK_SIZE);
    }

    @Override
    public final byte readByte() throws IOException {
        if (!currentBlock.contains(position)) {
            position(position);
        }
        return currentBlock.get(position++);
    }

    @Override
    public final void readFully(byte[] dest, int offset, int length) throws IOException {
        // TODO: Optimize, use Arrays.copy if all bytes are in current block
        // array
        for (int i = 0; i < length; i++) {
            dest[i + offset] = readByte();
        }
    }

    @Override
    public final void readFully(byte[] dst) throws IOException {
        readFully(dst, 0, dst.length);
    }

    public final short readRawShort() throws IOException {
        // copied from java.io.Bits
        byte b0 = readByte();
        byte b1 = readByte();
        return (short) ((b1 & 0xFF) + (b0 << 8));
    }

    @Override
    public final double readDouble() throws IOException {
        // copied from java.io.Bits
        return Double.longBitsToDouble(readRawLong());
    }

    @Override
    public final float readFloat() throws IOException {
        // copied from java.io.Bits
        return Float.intBitsToFloat(readRawInt());
    }

    public final int readRawInt() throws IOException {
        // copied from java.io.Bits
        byte b0 = readByte();
        byte b1 = readByte();
        byte b2 = readByte();
        byte b3 = readByte();
        return ((b3 & 0xFF)) + ((b2 & 0xFF) << 8) + ((b1 & 0xFF) << 16) + ((b0) << 24);
    }

    public final long readRawLong() throws IOException {
        // copied from java.io.Bits
        byte b0 = readByte();
        byte b1 = readByte();
        byte b2 = readByte();
        byte b3 = readByte();
        byte b4 = readByte();
        byte b5 = readByte();
        byte b6 = readByte();
        byte b7 = readByte();
        return ((b7 & 0xFFL)) + ((b6 & 0xFFL) << 8) + ((b5 & 0xFFL) << 16) + ((b4 & 0xFFL) << 24) + ((b3 & 0xFFL) << 32) + ((b2 & 0xFFL) << 40) + ((b1 & 0xFFL) << 48) + (((long) b0) << 56);
    }

    public final long position() throws IOException {
        return position;
    }

    public final void position(long newPosition) throws IOException {
        if (!currentBlock.contains(newPosition)) {
            if (!previousBlock.contains(newPosition)) {
                if (newPosition > size()) {
                    throw new EOFException("Trying to read at " + newPosition + ", but file is only " + size() + " bytes.");
                }
                long blockStart = trimToFileSize(calculateBlockStart(newPosition));
                file.seek(blockStart);
                // trim amount to file size
                long amount = Math.min(size() - blockStart, blockSize);
                previousBlock.read(file, (int) amount);
            }
            // swap previous and current
            Block tmp = currentBlock;
            currentBlock = previousBlock;
            previousBlock = tmp;
        }
        position = newPosition;
    }

    private final long trimToFileSize(long position) throws IOException {
        return Math.min(size(), Math.max(0, position));
    }

    private final long calculateBlockStart(long newPosition) {
        // align to end of current block
        if (currentBlock.contains(newPosition - blockSize)) {
            return currentBlock.blockPosition + currentBlock.bytes.length;
        }
        // align before current block
        if (currentBlock.contains(newPosition + blockSize)) {
            return currentBlock.blockPosition - blockSize;
        }
        // not near current block, pick middle
        return newPosition - blockSize / 2;
    }

    public final long size() throws IOException {
        return size;
    }

    public final void close() throws IOException {
        file.close();
    }

    @Override
    public final int skipBytes(int n) throws IOException {
        long position = position();
        position(position + n);
        return (int) (position() - position);
    }

    @Override
    public final boolean readBoolean() throws IOException {
        return readByte() != 0;
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return readByte() & 0x00FF;
    }

    @Override
    public int readUnsignedShort() throws IOException {
        return readShort() & 0xFFFF;
    }

    @Override
    public final String readLine() throws IOException {
        throw new UnsupportedOperationException();
    }

    // NOTE, this method should really be called readString
    // but can't be renamed without making RecordingInput a
    // public class.
    //
    // This method DOES Not read as expected (s2 + utf8 encoded character)
    // instead it read:
    // byte encoding
    // int size
    // data (byte or char)
    //
    // where encoding
    //
    // 0, means null
    // 1, means UTF8 encoded byte array
    // 2, means char array
    // 3, means latin-1 (ISO-8859-1) encoded byte array
    // 4, means ""
    @Override
    public String readUTF() throws IOException {
        return readEncodedString(readByte());
    }

    public String readEncodedString(byte encoding) throws IOException {
        if (encoding == STRING_ENCODING_NULL) {
            return null;
        }
        if (encoding == STRING_ENCODING_EMPTY_STRING) {
            return "";
        }
        int size = readInt();
        if (encoding == STRING_ENCODING_CHAR_ARRAY) {
            char[] c = new char[size];
            for (int i = 0; i < size; i++) {
                c[i] = readChar();
            }
            return new String(c);
        }
        byte[] bytes = new byte[size];
        readFully(bytes); // TODO: optimize, check size, and copy only if needed
        if (encoding == STRING_ENCODING_UTF8_BYTE_ARRAY) {
            return new String(bytes, UTF8);
        }

        if (encoding == STRING_ENCODING_LATIN1_BYTE_ARRAY) {
            return new String(bytes, LATIN1);
        }
        throw new IOException("Unknown string encoding " + encoding);
    }

    @Override
    public char readChar() throws IOException {
        return (char) readLong();
    }

    @Override
    public short readShort() throws IOException {
        return (short) readLong();
    }

    @Override
    public int readInt() throws IOException {
        return (int) readLong();
    }

    @Override
    public long readLong() throws IOException {
        // can be optimized by branching checks, but will do for now
        byte b0 = readByte();
        long ret = (b0 & 0x7FL);
        if (b0 >= 0) {
            return ret;
        }
        int b1 = readByte();
        ret += (b1 & 0x7FL) << 7;
        if (b1 >= 0) {
            return ret;
        }
        int b2 = readByte();
        ret += (b2 & 0x7FL) << 14;
        if (b2 >= 0) {
            return ret;
        }
        int b3 = readByte();
        ret += (b3 & 0x7FL) << 21;
        if (b3 >= 0) {
            return ret;
        }
        int b4 = readByte();
        ret += (b4 & 0x7FL) << 28;
        if (b4 >= 0) {
            return ret;
        }
        int b5 = readByte();
        ret += (b5 & 0x7FL) << 35;
        if (b5 >= 0) {
            return ret;
        }
        int b6 = readByte();
        ret += (b6 & 0x7FL) << 42;
        if (b6 >= 0) {
            return ret;
        }
        int b7 = readByte();
        ret += (b7 & 0x7FL) << 49;
        if (b7 >= 0) {
            return ret;
        }
        int b8 = readByte(); // read last byte raw
        return ret + (((long) (b8 & 0XFF)) << 56);
    }
}
