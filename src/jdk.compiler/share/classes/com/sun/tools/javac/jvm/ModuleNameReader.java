/*
 * Copyright (c) 1999, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.javac.jvm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.tools.JavaFileObject;

import static com.sun.tools.javac.jvm.ClassFile.*;


/**
 * Stripped down ClassReader, just sufficient to read module names from module-info.class files
 * while analyzing the module path.
 *
 * <p>
 * <b>This is NOT part of any supported API. If you write code that depends on this, you do so at
 * your own risk. This code and its internal interfaces are subject to change or deletion without
 * notice.</b>
 */
public class ModuleNameReader {
    public static class BadClassFile extends Exception {
        private static final long serialVersionUID = 0;
        BadClassFile(String msg) {
            super(msg);
        }
    }

    private static final int INITIAL_BUFFER_SIZE = 0x0fff0;

    /** The buffer containing the currently read class file.
     */
    private byte[] buf = new byte[INITIAL_BUFFER_SIZE];

    /** The current input pointer.
     */
    private int bp;

    /** For every constant pool entry, an index into buf where the
     *  defining section of the entry is found.
     */
    private int[] poolIdx;

    public ModuleNameReader() {
    }

    public String readModuleName(Path p) throws IOException, BadClassFile {
        try (InputStream in = Files.newInputStream(p)) {
            return readModuleName(in);
        }
    }

    public String readModuleName(JavaFileObject jfo) throws IOException, BadClassFile {
        try (InputStream in = jfo.openInputStream()) {
            return readModuleName(in);
        }
    }

    public String readModuleName(InputStream in) throws IOException, BadClassFile {
        bp = 0;
        buf = readInputStream(buf, in);

        int magic = nextInt();
        if (magic != JAVA_MAGIC)
            throw new BadClassFile("illegal.start.of.class.file");

        int minorVersion = nextChar();
        int majorVersion = nextChar();
        if (majorVersion < 53)
            throw new BadClassFile("bad major version number for module: " + majorVersion);

        indexPool();

        int access_flags = nextChar();
        if (access_flags != 0x8000)
            throw new BadClassFile("invalid access flags for module: 0x" + Integer.toHexString(access_flags));

        int this_class = nextChar();
        // could, should, check this_class == CONSTANT_Class("mdoule-info")
        checkZero(nextChar(), "super_class");
        checkZero(nextChar(), "interface_count");
        checkZero(nextChar(), "fields_count");
        checkZero(nextChar(), "methods_count");
        int attributes_count = nextChar();
        for (int i = 0; i < attributes_count; i++) {
            int attr_name = nextChar();
            int attr_length = nextInt();
            if (getUtf8Value(attr_name, false).equals("Module") && attr_length > 2) {
                return getModuleName(nextChar());
            } else {
                // skip over unknown attributes
                bp += attr_length;
            }
        }
        throw new BadClassFile("no Module attribute");
    }

    void checkZero(int count, String name) throws BadClassFile {
        if (count != 0)
            throw new BadClassFile("invalid " + name + " for module: " + count);
    }

    /** Extract a character at position bp from buf.
     */
    char getChar(int bp) {
        return
            (char)(((buf[bp] & 0xFF) << 8) + (buf[bp+1] & 0xFF));
    }

    /** Read a character.
     */
    char nextChar() {
        return (char)(((buf[bp++] & 0xFF) << 8) + (buf[bp++] & 0xFF));
    }

    /** Read an integer.
     */
    int nextInt() {
        return
            ((buf[bp++] & 0xFF) << 24) +
            ((buf[bp++] & 0xFF) << 16) +
            ((buf[bp++] & 0xFF) << 8) +
            (buf[bp++] & 0xFF);
    }

    /** Index all constant pool entries, writing their start addresses into
     *  poolIdx.
     */
    void indexPool() throws BadClassFile {
        poolIdx = new int[nextChar()];
        int i = 1;
        while (i < poolIdx.length) {
            poolIdx[i++] = bp;
            byte tag = buf[bp++];
            switch (tag) {
            case CONSTANT_Utf8: case CONSTANT_Unicode: {
                int len = nextChar();
                bp = bp + len;
                break;
            }
            case CONSTANT_Class:
            case CONSTANT_String:
            case CONSTANT_MethodType:
            case CONSTANT_Module:
            case CONSTANT_Package:
                bp = bp + 2;
                break;
            case CONSTANT_MethodHandle:
                bp = bp + 3;
                break;
            case CONSTANT_Fieldref:
            case CONSTANT_Methodref:
            case CONSTANT_InterfaceMethodref:
            case CONSTANT_NameandType:
            case CONSTANT_Integer:
            case CONSTANT_Float:
            case CONSTANT_InvokeDynamic:
                bp = bp + 4;
                break;
            case CONSTANT_Long:
            case CONSTANT_Double:
                bp = bp + 8;
                i++;
                break;
            default:
                throw new BadClassFile("malformed constant pool");
            }
        }
    }

    String getUtf8Value(int index, boolean internalize) throws BadClassFile {
        int utf8Index = poolIdx[index];
        if (buf[utf8Index] == CONSTANT_Utf8) {
            int len = getChar(utf8Index + 1);
            int start = utf8Index + 3;
            if (internalize) {
                return new String(ClassFile.internalize(buf, start, len));
            } else {
                return new String(buf, start, len);
            }
        }
        throw new BadClassFile("bad name at index " + index);
    }

    String getModuleName(int index) throws BadClassFile {
        int infoIndex = poolIdx[index];
        if (buf[infoIndex] == CONSTANT_Module) {
            return getUtf8Value(getChar(infoIndex + 1), true);
        } else {
            throw new BadClassFile("bad module name at index " + index);
        }
    }

    private static byte[] readInputStream(byte[] buf, InputStream s) throws IOException {
        try {
            buf = ensureCapacity(buf, s.available());
            int r = s.read(buf);
            int bp = 0;
            while (r != -1) {
                bp += r;
                buf = ensureCapacity(buf, bp);
                r = s.read(buf, bp, buf.length - bp);
            }
            return buf;
        } finally {
            try {
                s.close();
            } catch (IOException e) {
                /* Ignore any errors, as this stream may have already
                 * thrown a related exception which is the one that
                 * should be reported.
                 */
            }
        }
    }

    /*
     * ensureCapacity will increase the buffer as needed, taking note that
     * the new buffer will always be greater than the needed and never
     * exactly equal to the needed size or bp. If equal then the read (above)
     * will infinitely loop as buf.length - bp == 0.
     */
    private static byte[] ensureCapacity(byte[] buf, int needed) {
        if (buf.length <= needed) {
            byte[] old = buf;
            buf = new byte[Integer.highestOneBit(needed) << 1];
            System.arraycopy(old, 0, buf, 0, old.length);
        }
        return buf;
    }
}
