/*
 * Copyright (c) 2005, 2009, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2007 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.sun.xml.internal.ws.org.objectweb.asm;

import java.io.InputStream;
import java.io.IOException;

/**
 * A Java class parser to make a {@link ClassVisitor} visit an existing class.
 * This class parses a byte array conforming to the Java class file format and
 * calls the appropriate visit methods of a given class visitor for each field,
 * method and bytecode instruction encountered.
 *
 * @author Eric Bruneton
 * @author Eugene Kuleshov
 */
public class ClassReader {

    /**
     * True to enable signatures support.
     */
    static final boolean SIGNATURES = true;

    /**
     * True to enable annotations support.
     */
    static final boolean ANNOTATIONS = true;

    /**
     * True to enable stack map frames support.
     */
    static final boolean FRAMES = true;

    /**
     * True to enable bytecode writing support.
     */
    static final boolean WRITER = true;

    /**
     * True to enable JSR_W and GOTO_W support.
     */
    static final boolean RESIZE = true;

    /**
     * Flag to skip method code. If this class is set <code>CODE</code>
     * attribute won't be visited. This can be used, for example, to retrieve
     * annotations for methods and method parameters.
     */
    public static final int SKIP_CODE = 1;

    /**
     * Flag to skip the debug information in the class. If this flag is set the
     * debug information of the class is not visited, i.e. the
     * {@link MethodVisitor#visitLocalVariable visitLocalVariable} and
     * {@link MethodVisitor#visitLineNumber visitLineNumber} methods will not be
     * called.
     */
    public static final int SKIP_DEBUG = 2;

    /**
     * Flag to skip the stack map frames in the class. If this flag is set the
     * stack map frames of the class is not visited, i.e. the
     * {@link MethodVisitor#visitFrame visitFrame} method will not be called.
     * This flag is useful when the {@link ClassWriter#COMPUTE_FRAMES} option is
     * used: it avoids visiting frames that will be ignored and recomputed from
     * scratch in the class writer.
     */
    public static final int SKIP_FRAMES = 4;

    /**
     * Flag to expand the stack map frames. By default stack map frames are
     * visited in their original format (i.e. "expanded" for classes whose
     * version is less than V1_6, and "compressed" for the other classes). If
     * this flag is set, stack map frames are always visited in expanded format
     * (this option adds a decompression/recompression step in ClassReader and
     * ClassWriter which degrades performances quite a lot).
     */
    public static final int EXPAND_FRAMES = 8;

    /**
     * The class to be parsed. <i>The content of this array must not be
     * modified. This field is intended for {@link Attribute} sub classes, and
     * is normally not needed by class generators or adapters.</i>
     */
    public final byte[] b;

    /**
     * The start index of each constant pool item in {@link #b b}, plus one.
     * The one byte offset skips the constant pool item tag that indicates its
     * type.
     */
    private final int[] items;

    /**
     * The String objects corresponding to the CONSTANT_Utf8 items. This cache
     * avoids multiple parsing of a given CONSTANT_Utf8 constant pool item,
     * which GREATLY improves performances (by a factor 2 to 3). This caching
     * strategy could be extended to all constant pool items, but its benefit
     * would not be so great for these items (because they are much less
     * expensive to parse than CONSTANT_Utf8 items).
     */
    private final String[] strings;

    /**
     * Maximum length of the strings contained in the constant pool of the
     * class.
     */
    private final int maxStringLength;

    /**
     * Start index of the class header information (access, name...) in
     * {@link #b b}.
     */
    public final int header;

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    /**
     * Constructs a new {@link ClassReader} object.
     *
     * @param b the bytecode of the class to be read.
     */
    public ClassReader(final byte[] b) {
        this(b, 0, b.length);
    }

    /**
     * Constructs a new {@link ClassReader} object.
     *
     * @param b the bytecode of the class to be read.
     * @param off the start offset of the class data.
     * @param len the length of the class data.
     */
    public ClassReader(final byte[] b, final int off, final int len) {
        this.b = b;
        // parses the constant pool
        items = new int[readUnsignedShort(off + 8)];
        int n = items.length;
        strings = new String[n];
        int max = 0;
        int index = off + 10;
        for (int i = 1; i < n; ++i) {
            items[i] = index + 1;
            int size;
            switch (b[index]) {
                case ClassWriter.FIELD:
                case ClassWriter.METH:
                case ClassWriter.IMETH:
                case ClassWriter.INT:
                case ClassWriter.FLOAT:
                case ClassWriter.NAME_TYPE:
                    size = 5;
                    break;
                case ClassWriter.LONG:
                case ClassWriter.DOUBLE:
                    size = 9;
                    ++i;
                    break;
                case ClassWriter.UTF8:
                    size = 3 + readUnsignedShort(index + 1);
                    if (size > max) {
                        max = size;
                    }
                    break;
                // case ClassWriter.CLASS:
                // case ClassWriter.STR:
                default:
                    size = 3;
                    break;
            }
            index += size;
        }
        maxStringLength = max;
        // the class header information starts just after the constant pool
        header = index;
    }

    /**
     * Returns the class's access flags (see {@link Opcodes}). This value may
     * not reflect Deprecated and Synthetic flags when bytecode is before 1.5
     * and those flags are represented by attributes.
     *
     * @return the class access flags
     *
     * @see ClassVisitor#visit(int, int, String, String, String, String[])
     */
    public int getAccess() {
        return readUnsignedShort(header);
    }

    /**
     * Returns the internal name of the class (see
     * {@link Type#getInternalName() getInternalName}).
     *
     * @return the internal class name
     *
     * @see ClassVisitor#visit(int, int, String, String, String, String[])
     */
    public String getClassName() {
        return readClass(header + 2, new char[maxStringLength]);
    }

    /**
     * Returns the internal of name of the super class (see
     * {@link Type#getInternalName() getInternalName}). For interfaces, the
     * super class is {@link Object}.
     *
     * @return the internal name of super class, or <tt>null</tt> for
     *         {@link Object} class.
     *
     * @see ClassVisitor#visit(int, int, String, String, String, String[])
     */
    public String getSuperName() {
        int n = items[readUnsignedShort(header + 4)];
        return n == 0 ? null : readUTF8(n, new char[maxStringLength]);
    }

    /**
     * Returns the internal names of the class's interfaces (see
     * {@link Type#getInternalName() getInternalName}).
     *
     * @return the array of internal names for all implemented interfaces or
     *         <tt>null</tt>.
     *
     * @see ClassVisitor#visit(int, int, String, String, String, String[])
     */
    public String[] getInterfaces() {
        int index = header + 6;
        int n = readUnsignedShort(index);
        String[] interfaces = new String[n];
        if (n > 0) {
            char[] buf = new char[maxStringLength];
            for (int i = 0; i < n; ++i) {
                index += 2;
                interfaces[i] = readClass(index, buf);
            }
        }
        return interfaces;
    }

    /**
     * Copies the constant pool data into the given {@link ClassWriter}. Should
     * be called before the {@link #accept(ClassVisitor,int)} method.
     *
     * @param classWriter the {@link ClassWriter} to copy constant pool into.
     */
    void copyPool(final ClassWriter classWriter) {
        char[] buf = new char[maxStringLength];
        int ll = items.length;
        Item[] items2 = new Item[ll];
        for (int i = 1; i < ll; i++) {
            int index = items[i];
            int tag = b[index - 1];
            Item item = new Item(i);
            int nameType;
            switch (tag) {
                case ClassWriter.FIELD:
                case ClassWriter.METH:
                case ClassWriter.IMETH:
                    nameType = items[readUnsignedShort(index + 2)];
                    item.set(tag,
                            readClass(index, buf),
                            readUTF8(nameType, buf),
                            readUTF8(nameType + 2, buf));
                    break;

                case ClassWriter.INT:
                    item.set(readInt(index));
                    break;

                case ClassWriter.FLOAT:
                    item.set(Float.intBitsToFloat(readInt(index)));
                    break;

                case ClassWriter.NAME_TYPE:
                    item.set(tag,
                            readUTF8(index, buf),
                            readUTF8(index + 2, buf),
                            null);
                    break;

                case ClassWriter.LONG:
                    item.set(readLong(index));
                    ++i;
                    break;

                case ClassWriter.DOUBLE:
                    item.set(Double.longBitsToDouble(readLong(index)));
                    ++i;
                    break;

                case ClassWriter.UTF8: {
                    String s = strings[i];
                    if (s == null) {
                        index = items[i];
                        s = strings[i] = readUTF(index + 2,
                                readUnsignedShort(index),
                                buf);
                    }
                    item.set(tag, s, null, null);
                }
                    break;

                // case ClassWriter.STR:
                // case ClassWriter.CLASS:
                default:
                    item.set(tag, readUTF8(index, buf), null, null);
                    break;
            }

            int index2 = item.hashCode % items2.length;
            item.next = items2[index2];
            items2[index2] = item;
        }

        int off = items[1] - 1;
        classWriter.pool.putByteArray(b, off, header - off);
        classWriter.items = items2;
        classWriter.threshold = (int) (0.75d * ll);
        classWriter.index = ll;
    }

    /**
     * Constructs a new {@link ClassReader} object.
     *
     * @param is an input stream from which to read the class.
     * @throws IOException if a problem occurs during reading.
     */
    public ClassReader(final InputStream is) throws IOException {
        this(readClass(is));
    }

    /**
     * Constructs a new {@link ClassReader} object.
     *
     * @param name the fully qualified name of the class to be read.
     * @throws IOException if an exception occurs during reading.
     */
    public ClassReader(final String name) throws IOException {
        this(ClassLoader.getSystemResourceAsStream(name.replace('.', '/')
                + ".class"));
    }

    /**
     * Reads the bytecode of a class.
     *
     * @param is an input stream from which to read the class.
     * @return the bytecode read from the given input stream.
     * @throws IOException if a problem occurs during reading.
     */
    private static byte[] readClass(final InputStream is) throws IOException {
        if (is == null) {
            throw new IOException("Class not found");
        }
        byte[] b = new byte[is.available()];
        int len = 0;
        while (true) {
            int n = is.read(b, len, b.length - len);
            if (n == -1) {
                if (len < b.length) {
                    byte[] c = new byte[len];
                    System.arraycopy(b, 0, c, 0, len);
                    b = c;
                }
                return b;
            }
            len += n;
            if (len == b.length) {
                byte[] c = new byte[b.length + 1000];
                System.arraycopy(b, 0, c, 0, len);
                b = c;
            }
        }
    }

    // ------------------------------------------------------------------------
    // Public methods
    // ------------------------------------------------------------------------

    /**
     * Makes the given visitor visit the Java class of this {@link ClassReader}.
     * This class is the one specified in the constructor (see
     * {@link #ClassReader(byte[]) ClassReader}).
     *
     * @param classVisitor the visitor that must visit this class.
     * @param flags option flags that can be used to modify the default behavior
     *        of this class. See {@link #SKIP_DEBUG}, {@link #EXPAND_FRAMES},
     *        {@link #SKIP_FRAMES}, {@link #SKIP_CODE}.
     */
    public void accept(final ClassVisitor classVisitor, final int flags) {
        accept(classVisitor, new Attribute[0], flags);
    }

    /**
     * Makes the given visitor visit the Java class of this {@link ClassReader}.
     * This class is the one specified in the constructor (see
     * {@link #ClassReader(byte[]) ClassReader}).
     *
     * @param classVisitor the visitor that must visit this class.
     * @param attrs prototypes of the attributes that must be parsed during the
     *        visit of the class. Any attribute whose type is not equal to the
     *        type of one the prototypes will not be parsed: its byte array
     *        value will be passed unchanged to the ClassWriter. <i>This may
     *        corrupt it if this value contains references to the constant pool,
     *        or has syntactic or semantic links with a class element that has
     *        been transformed by a class adapter between the reader and the
     *        writer</i>.
     * @param flags option flags that can be used to modify the default behavior
     *        of this class. See {@link #SKIP_DEBUG}, {@link #EXPAND_FRAMES},
     *        {@link #SKIP_FRAMES}, {@link #SKIP_CODE}.
     */
    public void accept(
        final ClassVisitor classVisitor,
        final Attribute[] attrs,
        final int flags)
    {
        byte[] b = this.b; // the bytecode array
        char[] c = new char[maxStringLength]; // buffer used to read strings
        int i, j, k; // loop variables
        int u, v, w; // indexes in b
        Attribute attr;

        int access;
        String name;
        String desc;
        String attrName;
        String signature;
        int anns = 0;
        int ianns = 0;
        Attribute cattrs = null;

        // visits the header
        u = header;
        access = readUnsignedShort(u);
        name = readClass(u + 2, c);
        v = items[readUnsignedShort(u + 4)];
        String superClassName = v == 0 ? null : readUTF8(v, c);
        String[] implementedItfs = new String[readUnsignedShort(u + 6)];
        w = 0;
        u += 8;
        for (i = 0; i < implementedItfs.length; ++i) {
            implementedItfs[i] = readClass(u, c);
            u += 2;
        }

        boolean skipCode = (flags & SKIP_CODE) != 0;
        boolean skipDebug = (flags & SKIP_DEBUG) != 0;
        boolean unzip = (flags & EXPAND_FRAMES) != 0;

        // skips fields and methods
        v = u;
        i = readUnsignedShort(v);
        v += 2;
        for (; i > 0; --i) {
            j = readUnsignedShort(v + 6);
            v += 8;
            for (; j > 0; --j) {
                v += 6 + readInt(v + 2);
            }
        }
        i = readUnsignedShort(v);
        v += 2;
        for (; i > 0; --i) {
            j = readUnsignedShort(v + 6);
            v += 8;
            for (; j > 0; --j) {
                v += 6 + readInt(v + 2);
            }
        }
        // reads the class's attributes
        signature = null;
        String sourceFile = null;
        String sourceDebug = null;
        String enclosingOwner = null;
        String enclosingName = null;
        String enclosingDesc = null;

        i = readUnsignedShort(v);
        v += 2;
        for (; i > 0; --i) {
            attrName = readUTF8(v, c);
            // tests are sorted in decreasing frequency order
            // (based on frequencies observed on typical classes)
            if ("SourceFile".equals(attrName)) {
                sourceFile = readUTF8(v + 6, c);
            } else if ("InnerClasses".equals(attrName)) {
                w = v + 6;
            } else if ("EnclosingMethod".equals(attrName)) {
                enclosingOwner = readClass(v + 6, c);
                int item = readUnsignedShort(v + 8);
                if (item != 0) {
                    enclosingName = readUTF8(items[item], c);
                    enclosingDesc = readUTF8(items[item] + 2, c);
                }
            } else if (SIGNATURES && "Signature".equals(attrName)) {
                signature = readUTF8(v + 6, c);
            } else if (ANNOTATIONS && "RuntimeVisibleAnnotations".equals(attrName)) {
                anns = v + 6;
            } else if ("Deprecated".equals(attrName)) {
                access |= Opcodes.ACC_DEPRECATED;
            } else if ("Synthetic".equals(attrName)) {
                access |= Opcodes.ACC_SYNTHETIC;
            } else if ("SourceDebugExtension".equals(attrName)) {
                int len = readInt(v + 2);
                sourceDebug = readUTF(v + 6, len, new char[len]);
            } else if (ANNOTATIONS && "RuntimeInvisibleAnnotations".equals(attrName)) {
                ianns = v + 6;
            } else {
                attr = readAttribute(attrs,
                        attrName,
                        v + 6,
                        readInt(v + 2),
                        c,
                        -1,
                        null);
                if (attr != null) {
                    attr.next = cattrs;
                    cattrs = attr;
                }
            }
            v += 6 + readInt(v + 2);
        }
        // calls the visit method
        classVisitor.visit(readInt(4),
                access,
                name,
                signature,
                superClassName,
                implementedItfs);

        // calls the visitSource method
        if (!skipDebug && (sourceFile != null || sourceDebug != null)) {
            classVisitor.visitSource(sourceFile, sourceDebug);
        }

        // calls the visitOuterClass method
        if (enclosingOwner != null) {
            classVisitor.visitOuterClass(enclosingOwner,
                    enclosingName,
                    enclosingDesc);
        }

        // visits the class annotations
        if (ANNOTATIONS) {
            for (i = 1; i >= 0; --i) {
                v = i == 0 ? ianns : anns;
                if (v != 0) {
                    j = readUnsignedShort(v);
                    v += 2;
                    for (; j > 0; --j) {
                        v = readAnnotationValues(v + 2,
                                c,
                                true,
                                classVisitor.visitAnnotation(readUTF8(v, c), i != 0));
                    }
                }
            }
        }

        // visits the class attributes
        while (cattrs != null) {
            attr = cattrs.next;
            cattrs.next = null;
            classVisitor.visitAttribute(cattrs);
            cattrs = attr;
        }

        // calls the visitInnerClass method
        if (w != 0) {
            i = readUnsignedShort(w);
            w += 2;
            for (; i > 0; --i) {
                classVisitor.visitInnerClass(readUnsignedShort(w) == 0
                        ? null
                        : readClass(w, c), readUnsignedShort(w + 2) == 0
                        ? null
                        : readClass(w + 2, c), readUnsignedShort(w + 4) == 0
                        ? null
                        : readUTF8(w + 4, c), readUnsignedShort(w + 6));
                w += 8;
            }
        }

        // visits the fields
        i = readUnsignedShort(u);
        u += 2;
        for (; i > 0; --i) {
            access = readUnsignedShort(u);
            name = readUTF8(u + 2, c);
            desc = readUTF8(u + 4, c);
            // visits the field's attributes and looks for a ConstantValue
            // attribute
            int fieldValueItem = 0;
            signature = null;
            anns = 0;
            ianns = 0;
            cattrs = null;

            j = readUnsignedShort(u + 6);
            u += 8;
            for (; j > 0; --j) {
                attrName = readUTF8(u, c);
                // tests are sorted in decreasing frequency order
                // (based on frequencies observed on typical classes)
                if ("ConstantValue".equals(attrName)) {
                    fieldValueItem = readUnsignedShort(u + 6);
                } else if (SIGNATURES && "Signature".equals(attrName)) {
                    signature = readUTF8(u + 6, c);
                } else if ("Deprecated".equals(attrName)) {
                    access |= Opcodes.ACC_DEPRECATED;
                } else if ("Synthetic".equals(attrName)) {
                    access |= Opcodes.ACC_SYNTHETIC;
                } else if (ANNOTATIONS && "RuntimeVisibleAnnotations".equals(attrName)) {
                    anns = u + 6;
                } else if (ANNOTATIONS && "RuntimeInvisibleAnnotations".equals(attrName)) {
                    ianns = u + 6;
                } else {
                    attr = readAttribute(attrs,
                            attrName,
                            u + 6,
                            readInt(u + 2),
                            c,
                            -1,
                            null);
                    if (attr != null) {
                        attr.next = cattrs;
                        cattrs = attr;
                    }
                }
                u += 6 + readInt(u + 2);
            }
            // visits the field
            FieldVisitor fv = classVisitor.visitField(access,
                    name,
                    desc,
                    signature,
                    fieldValueItem == 0 ? null : readConst(fieldValueItem, c));
            // visits the field annotations and attributes
            if (fv != null) {
                if (ANNOTATIONS) {
                    for (j = 1; j >= 0; --j) {
                        v = j == 0 ? ianns : anns;
                        if (v != 0) {
                            k = readUnsignedShort(v);
                            v += 2;
                            for (; k > 0; --k) {
                                v = readAnnotationValues(v + 2,
                                        c,
                                        true,
                                        fv.visitAnnotation(readUTF8(v, c), j != 0));
                            }
                        }
                    }
                }
                while (cattrs != null) {
                    attr = cattrs.next;
                    cattrs.next = null;
                    fv.visitAttribute(cattrs);
                    cattrs = attr;
                }
                fv.visitEnd();
            }
        }

        // visits the methods
        i = readUnsignedShort(u);
        u += 2;
        for (; i > 0; --i) {
            int u0 = u + 6;
            access = readUnsignedShort(u);
            name = readUTF8(u + 2, c);
            desc = readUTF8(u + 4, c);
            signature = null;
            anns = 0;
            ianns = 0;
            int dann = 0;
            int mpanns = 0;
            int impanns = 0;
            cattrs = null;
            v = 0;
            w = 0;

            // looks for Code and Exceptions attributes
            j = readUnsignedShort(u + 6);
            u += 8;
            for (; j > 0; --j) {
                attrName = readUTF8(u, c);
                int attrSize = readInt(u + 2);
                u += 6;
                // tests are sorted in decreasing frequency order
                // (based on frequencies observed on typical classes)
                if ("Code".equals(attrName)) {
                    if (!skipCode) {
                        v = u;
                    }
                } else if ("Exceptions".equals(attrName)) {
                    w = u;
                } else if (SIGNATURES && "Signature".equals(attrName)) {
                    signature = readUTF8(u, c);
                } else if ("Deprecated".equals(attrName)) {
                    access |= Opcodes.ACC_DEPRECATED;
                } else if (ANNOTATIONS && "RuntimeVisibleAnnotations".equals(attrName)) {
                    anns = u;
                } else if (ANNOTATIONS && "AnnotationDefault".equals(attrName)) {
                    dann = u;
                } else if ("Synthetic".equals(attrName)) {
                    access |= Opcodes.ACC_SYNTHETIC;
                } else if (ANNOTATIONS && "RuntimeInvisibleAnnotations".equals(attrName)) {
                    ianns = u;
                } else if (ANNOTATIONS && "RuntimeVisibleParameterAnnotations".equals(attrName))
                {
                    mpanns = u;
                } else if (ANNOTATIONS && "RuntimeInvisibleParameterAnnotations".equals(attrName))
                {
                    impanns = u;
                } else {
                    attr = readAttribute(attrs,
                            attrName,
                            u,
                            attrSize,
                            c,
                            -1,
                            null);
                    if (attr != null) {
                        attr.next = cattrs;
                        cattrs = attr;
                    }
                }
                u += attrSize;
            }
            // reads declared exceptions
            String[] exceptions;
            if (w == 0) {
                exceptions = null;
            } else {
                exceptions = new String[readUnsignedShort(w)];
                w += 2;
                for (j = 0; j < exceptions.length; ++j) {
                    exceptions[j] = readClass(w, c);
                    w += 2;
                }
            }

            // visits the method's code, if any
            MethodVisitor mv = classVisitor.visitMethod(access,
                    name,
                    desc,
                    signature,
                    exceptions);

            if (mv != null) {
                /*
                 * if the returned MethodVisitor is in fact a MethodWriter, it
                 * means there is no method adapter between the reader and the
                 * writer. If, in addition, the writer's constant pool was
                 * copied from this reader (mw.cw.cr == this), and the signature
                 * and exceptions of the method have not been changed, then it
                 * is possible to skip all visit events and just copy the
                 * original code of the method to the writer (the access, name
                 * and descriptor can have been changed, this is not important
                 * since they are not copied as is from the reader).
                 */
                if (WRITER && mv instanceof MethodWriter) {
                    MethodWriter mw = (MethodWriter) mv;
                    if (mw.cw.cr == this) {
                        if (signature == mw.signature) {
                            boolean sameExceptions = false;
                            if (exceptions == null) {
                                sameExceptions = mw.exceptionCount == 0;
                            } else {
                                if (exceptions.length == mw.exceptionCount) {
                                    sameExceptions = true;
                                    for (j = exceptions.length - 1; j >= 0; --j)
                                    {
                                        w -= 2;
                                        if (mw.exceptions[j] != readUnsignedShort(w))
                                        {
                                            sameExceptions = false;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (sameExceptions) {
                                /*
                                 * we do not copy directly the code into
                                 * MethodWriter to save a byte array copy
                                 * operation. The real copy will be done in
                                 * ClassWriter.toByteArray().
                                 */
                                mw.classReaderOffset = u0;
                                mw.classReaderLength = u - u0;
                                continue;
                            }
                        }
                    }
                }

                if (ANNOTATIONS && dann != 0) {
                    AnnotationVisitor dv = mv.visitAnnotationDefault();
                    readAnnotationValue(dann, c, null, dv);
                    if (dv != null) {
                        dv.visitEnd();
                    }
                }
                if (ANNOTATIONS) {
                    for (j = 1; j >= 0; --j) {
                        w = j == 0 ? ianns : anns;
                        if (w != 0) {
                            k = readUnsignedShort(w);
                            w += 2;
                            for (; k > 0; --k) {
                                w = readAnnotationValues(w + 2,
                                        c,
                                        true,
                                        mv.visitAnnotation(readUTF8(w, c), j != 0));
                            }
                        }
                    }
                }
                if (ANNOTATIONS && mpanns != 0) {
                    readParameterAnnotations(mpanns, desc, c, true, mv);
                }
                if (ANNOTATIONS && impanns != 0) {
                    readParameterAnnotations(impanns, desc, c, false, mv);
                }
                while (cattrs != null) {
                    attr = cattrs.next;
                    cattrs.next = null;
                    mv.visitAttribute(cattrs);
                    cattrs = attr;
                }
            }

            if (mv != null && v != 0) {
                int maxStack = readUnsignedShort(v);
                int maxLocals = readUnsignedShort(v + 2);
                int codeLength = readInt(v + 4);
                v += 8;

                int codeStart = v;
                int codeEnd = v + codeLength;

                mv.visitCode();

                // 1st phase: finds the labels
                int label;
                Label[] labels = new Label[codeLength + 2];
                readLabel(codeLength + 1, labels);
                while (v < codeEnd) {
                    w = v - codeStart;
                    int opcode = b[v] & 0xFF;
                    switch (ClassWriter.TYPE[opcode]) {
                        case ClassWriter.NOARG_INSN:
                        case ClassWriter.IMPLVAR_INSN:
                            v += 1;
                            break;
                        case ClassWriter.LABEL_INSN:
                            readLabel(w + readShort(v + 1), labels);
                            v += 3;
                            break;
                        case ClassWriter.LABELW_INSN:
                            readLabel(w + readInt(v + 1), labels);
                            v += 5;
                            break;
                        case ClassWriter.WIDE_INSN:
                            opcode = b[v + 1] & 0xFF;
                            if (opcode == Opcodes.IINC) {
                                v += 6;
                            } else {
                                v += 4;
                            }
                            break;
                        case ClassWriter.TABL_INSN:
                            // skips 0 to 3 padding bytes*
                            v = v + 4 - (w & 3);
                            // reads instruction
                            readLabel(w + readInt(v), labels);
                            j = readInt(v + 8) - readInt(v + 4) + 1;
                            v += 12;
                            for (; j > 0; --j) {
                                readLabel(w + readInt(v), labels);
                                v += 4;
                            }
                            break;
                        case ClassWriter.LOOK_INSN:
                            // skips 0 to 3 padding bytes*
                            v = v + 4 - (w & 3);
                            // reads instruction
                            readLabel(w + readInt(v), labels);
                            j = readInt(v + 4);
                            v += 8;
                            for (; j > 0; --j) {
                                readLabel(w + readInt(v + 4), labels);
                                v += 8;
                            }
                            break;
                        case ClassWriter.VAR_INSN:
                        case ClassWriter.SBYTE_INSN:
                        case ClassWriter.LDC_INSN:
                            v += 2;
                            break;
                        case ClassWriter.SHORT_INSN:
                        case ClassWriter.LDCW_INSN:
                        case ClassWriter.FIELDORMETH_INSN:
                        case ClassWriter.TYPE_INSN:
                        case ClassWriter.IINC_INSN:
                            v += 3;
                            break;
                        case ClassWriter.ITFMETH_INSN:
                            v += 5;
                            break;
                        // case MANA_INSN:
                        default:
                            v += 4;
                            break;
                    }
                }
                // parses the try catch entries
                j = readUnsignedShort(v);
                v += 2;
                for (; j > 0; --j) {
                    Label start = readLabel(readUnsignedShort(v), labels);
                    Label end = readLabel(readUnsignedShort(v + 2), labels);
                    Label handler = readLabel(readUnsignedShort(v + 4), labels);
                    int type = readUnsignedShort(v + 6);
                    if (type == 0) {
                        mv.visitTryCatchBlock(start, end, handler, null);
                    } else {
                        mv.visitTryCatchBlock(start,
                                end,
                                handler,
                                readUTF8(items[type], c));
                    }
                    v += 8;
                }
                // parses the local variable, line number tables, and code
                // attributes
                int varTable = 0;
                int varTypeTable = 0;
                int stackMap = 0;
                int frameCount = 0;
                int frameMode = 0;
                int frameOffset = 0;
                int frameLocalCount = 0;
                int frameLocalDiff = 0;
                int frameStackCount = 0;
                Object[] frameLocal = null;
                Object[] frameStack = null;
                boolean zip = true;
                cattrs = null;
                j = readUnsignedShort(v);
                v += 2;
                for (; j > 0; --j) {
                    attrName = readUTF8(v, c);
                    if ("LocalVariableTable".equals(attrName)) {
                        if (!skipDebug) {
                            varTable = v + 6;
                            k = readUnsignedShort(v + 6);
                            w = v + 8;
                            for (; k > 0; --k) {
                                label = readUnsignedShort(w);
                                if (labels[label] == null) {
                                    readLabel(label, labels).status |= Label.DEBUG;
                                }
                                label += readUnsignedShort(w + 2);
                                if (labels[label] == null) {
                                    readLabel(label, labels).status |= Label.DEBUG;
                                }
                                w += 10;
                            }
                        }
                    } else if ("LocalVariableTypeTable".equals(attrName)) {
                        varTypeTable = v + 6;
                    } else if ("LineNumberTable".equals(attrName)) {
                        if (!skipDebug) {
                            k = readUnsignedShort(v + 6);
                            w = v + 8;
                            for (; k > 0; --k) {
                                label = readUnsignedShort(w);
                                if (labels[label] == null) {
                                    readLabel(label, labels).status |= Label.DEBUG;
                                }
                                labels[label].line = readUnsignedShort(w + 2);
                                w += 4;
                            }
                        }
                    } else if (FRAMES && "StackMapTable".equals(attrName)) {
                        if ((flags & SKIP_FRAMES) == 0) {
                            stackMap = v + 8;
                            frameCount = readUnsignedShort(v + 6);
                        }
                        /*
                         * here we do not extract the labels corresponding to
                         * the attribute content. This would require a full
                         * parsing of the attribute, which would need to be
                         * repeated in the second phase (see below). Instead the
                         * content of the attribute is read one frame at a time
                         * (i.e. after a frame has been visited, the next frame
                         * is read), and the labels it contains are also
                         * extracted one frame at a time. Thanks to the ordering
                         * of frames, having only a "one frame lookahead" is not
                         * a problem, i.e. it is not possible to see an offset
                         * smaller than the offset of the current insn and for
                         * which no Label exist.
                         */
                        // TODO true for frame offsets,
                        // but for UNINITIALIZED type offsets?
                    } else if (FRAMES && "StackMap".equals(attrName)) {
                        if ((flags & SKIP_FRAMES) == 0) {
                            stackMap = v + 8;
                            frameCount = readUnsignedShort(v + 6);
                            zip = false;
                        }
                        /*
                         * IMPORTANT! here we assume that the frames are
                         * ordered, as in the StackMapTable attribute, although
                         * this is not guaranteed by the attribute format.
                         */
                    } else {
                        for (k = 0; k < attrs.length; ++k) {
                            if (attrs[k].type.equals(attrName)) {
                                attr = attrs[k].read(this,
                                        v + 6,
                                        readInt(v + 2),
                                        c,
                                        codeStart - 8,
                                        labels);
                                if (attr != null) {
                                    attr.next = cattrs;
                                    cattrs = attr;
                                }
                            }
                        }
                    }
                    v += 6 + readInt(v + 2);
                }

                // 2nd phase: visits each instruction
                if (FRAMES && stackMap != 0) {
                    // creates the very first (implicit) frame from the method
                    // descriptor
                    frameLocal = new Object[maxLocals];
                    frameStack = new Object[maxStack];
                    if (unzip) {
                        int local = 0;
                        if ((access & Opcodes.ACC_STATIC) == 0) {
                            if ("<init>".equals(name)) {
                                frameLocal[local++] = Opcodes.UNINITIALIZED_THIS;
                            } else {
                                frameLocal[local++] = readClass(header + 2, c);
                            }
                        }
                        j = 1;
                        loop: while (true) {
                            k = j;
                            switch (desc.charAt(j++)) {
                                case 'Z':
                                case 'C':
                                case 'B':
                                case 'S':
                                case 'I':
                                    frameLocal[local++] = Opcodes.INTEGER;
                                    break;
                                case 'F':
                                    frameLocal[local++] = Opcodes.FLOAT;
                                    break;
                                case 'J':
                                    frameLocal[local++] = Opcodes.LONG;
                                    break;
                                case 'D':
                                    frameLocal[local++] = Opcodes.DOUBLE;
                                    break;
                                case '[':
                                    while (desc.charAt(j) == '[') {
                                        ++j;
                                    }
                                    if (desc.charAt(j) == 'L') {
                                        ++j;
                                        while (desc.charAt(j) != ';') {
                                            ++j;
                                        }
                                    }
                                    frameLocal[local++] = desc.substring(k, ++j);
                                    break;
                                case 'L':
                                    while (desc.charAt(j) != ';') {
                                        ++j;
                                    }
                                    frameLocal[local++] = desc.substring(k + 1,
                                            j++);
                                    break;
                                default:
                                    break loop;
                            }
                        }
                        frameLocalCount = local;
                    }
                    /*
                     * for the first explicit frame the offset is not
                     * offset_delta + 1 but only offset_delta; setting the
                     * implicit frame offset to -1 allow the use of the
                     * "offset_delta + 1" rule in all cases
                     */
                    frameOffset = -1;
                }
                v = codeStart;
                Label l;
                while (v < codeEnd) {
                    w = v - codeStart;

                    l = labels[w];
                    if (l != null) {
                        mv.visitLabel(l);
                        if (!skipDebug && l.line > 0) {
                            mv.visitLineNumber(l.line, l);
                        }
                    }

                    while (FRAMES && frameLocal != null
                            && (frameOffset == w || frameOffset == -1))
                    {
                        // if there is a frame for this offset,
                        // makes the visitor visit it,
                        // and reads the next frame if there is one.
                        if (!zip || unzip) {
                            mv.visitFrame(Opcodes.F_NEW,
                                    frameLocalCount,
                                    frameLocal,
                                    frameStackCount,
                                    frameStack);
                        } else if (frameOffset != -1) {
                            mv.visitFrame(frameMode,
                                    frameLocalDiff,
                                    frameLocal,
                                    frameStackCount,
                                    frameStack);
                        }

                        if (frameCount > 0) {
                            int tag, delta, n;
                            if (zip) {
                                tag = b[stackMap++] & 0xFF;
                            } else {
                                tag = MethodWriter.FULL_FRAME;
                                frameOffset = -1;
                            }
                            frameLocalDiff = 0;
                            if (tag < MethodWriter.SAME_LOCALS_1_STACK_ITEM_FRAME)
                            {
                                delta = tag;
                                frameMode = Opcodes.F_SAME;
                                frameStackCount = 0;
                            } else if (tag < MethodWriter.RESERVED) {
                                delta = tag
                                        - MethodWriter.SAME_LOCALS_1_STACK_ITEM_FRAME;
                                stackMap = readFrameType(frameStack,
                                        0,
                                        stackMap,
                                        c,
                                        labels);
                                frameMode = Opcodes.F_SAME1;
                                frameStackCount = 1;
                            } else {
                                delta = readUnsignedShort(stackMap);
                                stackMap += 2;
                                if (tag == MethodWriter.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED)
                                {
                                    stackMap = readFrameType(frameStack,
                                            0,
                                            stackMap,
                                            c,
                                            labels);
                                    frameMode = Opcodes.F_SAME1;
                                    frameStackCount = 1;
                                } else if (tag >= MethodWriter.CHOP_FRAME
                                        && tag < MethodWriter.SAME_FRAME_EXTENDED)
                                {
                                    frameMode = Opcodes.F_CHOP;
                                    frameLocalDiff = MethodWriter.SAME_FRAME_EXTENDED
                                            - tag;
                                    frameLocalCount -= frameLocalDiff;
                                    frameStackCount = 0;
                                } else if (tag == MethodWriter.SAME_FRAME_EXTENDED)
                                {
                                    frameMode = Opcodes.F_SAME;
                                    frameStackCount = 0;
                                } else if (tag < MethodWriter.FULL_FRAME) {
                                    j = unzip ? frameLocalCount : 0;
                                    for (k = tag
                                            - MethodWriter.SAME_FRAME_EXTENDED; k > 0; k--)
                                    {
                                        stackMap = readFrameType(frameLocal,
                                                j++,
                                                stackMap,
                                                c,
                                                labels);
                                    }
                                    frameMode = Opcodes.F_APPEND;
                                    frameLocalDiff = tag
                                            - MethodWriter.SAME_FRAME_EXTENDED;
                                    frameLocalCount += frameLocalDiff;
                                    frameStackCount = 0;
                                } else { // if (tag == FULL_FRAME) {
                                    frameMode = Opcodes.F_FULL;
                                    n = frameLocalDiff = frameLocalCount = readUnsignedShort(stackMap);
                                    stackMap += 2;
                                    for (j = 0; n > 0; n--) {
                                        stackMap = readFrameType(frameLocal,
                                                j++,
                                                stackMap,
                                                c,
                                                labels);
                                    }
                                    n = frameStackCount = readUnsignedShort(stackMap);
                                    stackMap += 2;
                                    for (j = 0; n > 0; n--) {
                                        stackMap = readFrameType(frameStack,
                                                j++,
                                                stackMap,
                                                c,
                                                labels);
                                    }
                                }
                            }
                            frameOffset += delta + 1;
                            readLabel(frameOffset, labels);

                            --frameCount;
                        } else {
                            frameLocal = null;
                        }
                    }

                    int opcode = b[v] & 0xFF;
                    switch (ClassWriter.TYPE[opcode]) {
                        case ClassWriter.NOARG_INSN:
                            mv.visitInsn(opcode);
                            v += 1;
                            break;
                        case ClassWriter.IMPLVAR_INSN:
                            if (opcode > Opcodes.ISTORE) {
                                opcode -= 59; // ISTORE_0
                                mv.visitVarInsn(Opcodes.ISTORE + (opcode >> 2),
                                        opcode & 0x3);
                            } else {
                                opcode -= 26; // ILOAD_0
                                mv.visitVarInsn(Opcodes.ILOAD + (opcode >> 2),
                                        opcode & 0x3);
                            }
                            v += 1;
                            break;
                        case ClassWriter.LABEL_INSN:
                            mv.visitJumpInsn(opcode, labels[w
                                    + readShort(v + 1)]);
                            v += 3;
                            break;
                        case ClassWriter.LABELW_INSN:
                            mv.visitJumpInsn(opcode - 33, labels[w
                                    + readInt(v + 1)]);
                            v += 5;
                            break;
                        case ClassWriter.WIDE_INSN:
                            opcode = b[v + 1] & 0xFF;
                            if (opcode == Opcodes.IINC) {
                                mv.visitIincInsn(readUnsignedShort(v + 2),
                                        readShort(v + 4));
                                v += 6;
                            } else {
                                mv.visitVarInsn(opcode,
                                        readUnsignedShort(v + 2));
                                v += 4;
                            }
                            break;
                        case ClassWriter.TABL_INSN:
                            // skips 0 to 3 padding bytes
                            v = v + 4 - (w & 3);
                            // reads instruction
                            label = w + readInt(v);
                            int min = readInt(v + 4);
                            int max = readInt(v + 8);
                            v += 12;
                            Label[] table = new Label[max - min + 1];
                            for (j = 0; j < table.length; ++j) {
                                table[j] = labels[w + readInt(v)];
                                v += 4;
                            }
                            mv.visitTableSwitchInsn(min,
                                    max,
                                    labels[label],
                                    table);
                            break;
                        case ClassWriter.LOOK_INSN:
                            // skips 0 to 3 padding bytes
                            v = v + 4 - (w & 3);
                            // reads instruction
                            label = w + readInt(v);
                            j = readInt(v + 4);
                            v += 8;
                            int[] keys = new int[j];
                            Label[] values = new Label[j];
                            for (j = 0; j < keys.length; ++j) {
                                keys[j] = readInt(v);
                                values[j] = labels[w + readInt(v + 4)];
                                v += 8;
                            }
                            mv.visitLookupSwitchInsn(labels[label],
                                    keys,
                                    values);
                            break;
                        case ClassWriter.VAR_INSN:
                            mv.visitVarInsn(opcode, b[v + 1] & 0xFF);
                            v += 2;
                            break;
                        case ClassWriter.SBYTE_INSN:
                            mv.visitIntInsn(opcode, b[v + 1]);
                            v += 2;
                            break;
                        case ClassWriter.SHORT_INSN:
                            mv.visitIntInsn(opcode, readShort(v + 1));
                            v += 3;
                            break;
                        case ClassWriter.LDC_INSN:
                            mv.visitLdcInsn(readConst(b[v + 1] & 0xFF, c));
                            v += 2;
                            break;
                        case ClassWriter.LDCW_INSN:
                            mv.visitLdcInsn(readConst(readUnsignedShort(v + 1),
                                    c));
                            v += 3;
                            break;
                        case ClassWriter.FIELDORMETH_INSN:
                        case ClassWriter.ITFMETH_INSN:
                            int cpIndex = items[readUnsignedShort(v + 1)];
                            String iowner = readClass(cpIndex, c);
                            cpIndex = items[readUnsignedShort(cpIndex + 2)];
                            String iname = readUTF8(cpIndex, c);
                            String idesc = readUTF8(cpIndex + 2, c);
                            if (opcode < Opcodes.INVOKEVIRTUAL) {
                                mv.visitFieldInsn(opcode, iowner, iname, idesc);
                            } else {
                                mv.visitMethodInsn(opcode, iowner, iname, idesc);
                            }
                            if (opcode == Opcodes.INVOKEINTERFACE) {
                                v += 5;
                            } else {
                                v += 3;
                            }
                            break;
                        case ClassWriter.TYPE_INSN:
                            mv.visitTypeInsn(opcode, readClass(v + 1, c));
                            v += 3;
                            break;
                        case ClassWriter.IINC_INSN:
                            mv.visitIincInsn(b[v + 1] & 0xFF, b[v + 2]);
                            v += 3;
                            break;
                        // case MANA_INSN:
                        default:
                            mv.visitMultiANewArrayInsn(readClass(v + 1, c),
                                    b[v + 3] & 0xFF);
                            v += 4;
                            break;
                    }
                }
                l = labels[codeEnd - codeStart];
                if (l != null) {
                    mv.visitLabel(l);
                }
                // visits the local variable tables
                if (!skipDebug && varTable != 0) {
                    int[] typeTable = null;
                    if (varTypeTable != 0) {
                        k = readUnsignedShort(varTypeTable) * 3;
                        w = varTypeTable + 2;
                        typeTable = new int[k];
                        while (k > 0) {
                            typeTable[--k] = w + 6; // signature
                            typeTable[--k] = readUnsignedShort(w + 8); // index
                            typeTable[--k] = readUnsignedShort(w); // start
                            w += 10;
                        }
                    }
                    k = readUnsignedShort(varTable);
                    w = varTable + 2;
                    for (; k > 0; --k) {
                        int start = readUnsignedShort(w);
                        int length = readUnsignedShort(w + 2);
                        int index = readUnsignedShort(w + 8);
                        String vsignature = null;
                        if (typeTable != null) {
                            for (int a = 0; a < typeTable.length; a += 3) {
                                if (typeTable[a] == start
                                        && typeTable[a + 1] == index)
                                {
                                    vsignature = readUTF8(typeTable[a + 2], c);
                                    break;
                                }
                            }
                        }
                        mv.visitLocalVariable(readUTF8(w + 4, c),
                                readUTF8(w + 6, c),
                                vsignature,
                                labels[start],
                                labels[start + length],
                                index);
                        w += 10;
                    }
                }
                // visits the other attributes
                while (cattrs != null) {
                    attr = cattrs.next;
                    cattrs.next = null;
                    mv.visitAttribute(cattrs);
                    cattrs = attr;
                }
                // visits the max stack and max locals values
                mv.visitMaxs(maxStack, maxLocals);
            }

            if (mv != null) {
                mv.visitEnd();
            }
        }

        // visits the end of the class
        classVisitor.visitEnd();
    }

    /**
     * Reads parameter annotations and makes the given visitor visit them.
     *
     * @param v start offset in {@link #b b} of the annotations to be read.
     * @param desc the method descriptor.
     * @param buf buffer to be used to call {@link #readUTF8 readUTF8},
     *        {@link #readClass(int,char[]) readClass} or
     *        {@link #readConst readConst}.
     * @param visible <tt>true</tt> if the annotations to be read are visible
     *        at runtime.
     * @param mv the visitor that must visit the annotations.
     */
    private void readParameterAnnotations(
        int v,
        final String desc,
        final char[] buf,
        final boolean visible,
        final MethodVisitor mv)
    {
        int i;
        int n = b[v++] & 0xFF;
        // workaround for a bug in javac (javac compiler generates a parameter
        // annotation array whose size is equal to the number of parameters in
        // the Java source file, while it should generate an array whose size is
        // equal to the number of parameters in the method descriptor - which
        // includes the synthetic parameters added by the compiler). This work-
        // around supposes that the synthetic parameters are the first ones.
        int synthetics = Type.getArgumentTypes(desc).length - n;
        AnnotationVisitor av;
        for (i = 0; i < synthetics; ++i) {
            // virtual annotation to detect synthetic parameters in MethodWriter
            av = mv.visitParameterAnnotation(i, "Ljava/lang/Synthetic;", false);
            if (av != null) {
                av.visitEnd();
            }
        }
        for (; i < n + synthetics; ++i) {
            int j = readUnsignedShort(v);
            v += 2;
            for (; j > 0; --j) {
                av = mv.visitParameterAnnotation(i, readUTF8(v, buf), visible);
                v = readAnnotationValues(v + 2, buf, true, av);
            }
        }
    }

    /**
     * Reads the values of an annotation and makes the given visitor visit them.
     *
     * @param v the start offset in {@link #b b} of the values to be read
     *        (including the unsigned short that gives the number of values).
     * @param buf buffer to be used to call {@link #readUTF8 readUTF8},
     *        {@link #readClass(int,char[]) readClass} or
     *        {@link #readConst readConst}.
     * @param named if the annotation values are named or not.
     * @param av the visitor that must visit the values.
     * @return the end offset of the annotation values.
     */
    private int readAnnotationValues(
        int v,
        final char[] buf,
        final boolean named,
        final AnnotationVisitor av)
    {
        int i = readUnsignedShort(v);
        v += 2;
        if (named) {
            for (; i > 0; --i) {
                v = readAnnotationValue(v + 2, buf, readUTF8(v, buf), av);
            }
        } else {
            for (; i > 0; --i) {
                v = readAnnotationValue(v, buf, null, av);
            }
        }
        if (av != null) {
            av.visitEnd();
        }
        return v;
    }

    /**
     * Reads a value of an annotation and makes the given visitor visit it.
     *
     * @param v the start offset in {@link #b b} of the value to be read (<i>not
     *        including the value name constant pool index</i>).
     * @param buf buffer to be used to call {@link #readUTF8 readUTF8},
     *        {@link #readClass(int,char[]) readClass} or
     *        {@link #readConst readConst}.
     * @param name the name of the value to be read.
     * @param av the visitor that must visit the value.
     * @return the end offset of the annotation value.
     */
    private int readAnnotationValue(
        int v,
        final char[] buf,
        final String name,
        final AnnotationVisitor av)
    {
        int i;
        if (av == null) {
            switch (b[v] & 0xFF) {
                case 'e': // enum_const_value
                    return v + 5;
                case '@': // annotation_value
                    return readAnnotationValues(v + 3, buf, true, null);
                case '[': // array_value
                    return readAnnotationValues(v + 1, buf, false, null);
                default:
                    return v + 3;
            }
        }
        switch (b[v++] & 0xFF) {
            case 'I': // pointer to CONSTANT_Integer
            case 'J': // pointer to CONSTANT_Long
            case 'F': // pointer to CONSTANT_Float
            case 'D': // pointer to CONSTANT_Double
                av.visit(name, readConst(readUnsignedShort(v), buf));
                v += 2;
                break;
            case 'B': // pointer to CONSTANT_Byte
                av.visit(name,
                        new Byte((byte) readInt(items[readUnsignedShort(v)])));
                v += 2;
                break;
            case 'Z': // pointer to CONSTANT_Boolean
                av.visit(name, readInt(items[readUnsignedShort(v)]) == 0
                        ? Boolean.FALSE
                        : Boolean.TRUE);
                v += 2;
                break;
            case 'S': // pointer to CONSTANT_Short
                av.visit(name,
                        new Short((short) readInt(items[readUnsignedShort(v)])));
                v += 2;
                break;
            case 'C': // pointer to CONSTANT_Char
                av.visit(name,
                        new Character((char) readInt(items[readUnsignedShort(v)])));
                v += 2;
                break;
            case 's': // pointer to CONSTANT_Utf8
                av.visit(name, readUTF8(v, buf));
                v += 2;
                break;
            case 'e': // enum_const_value
                av.visitEnum(name, readUTF8(v, buf), readUTF8(v + 2, buf));
                v += 4;
                break;
            case 'c': // class_info
                av.visit(name, Type.getType(readUTF8(v, buf)));
                v += 2;
                break;
            case '@': // annotation_value
                v = readAnnotationValues(v + 2,
                        buf,
                        true,
                        av.visitAnnotation(name, readUTF8(v, buf)));
                break;
            case '[': // array_value
                int size = readUnsignedShort(v);
                v += 2;
                if (size == 0) {
                    return readAnnotationValues(v - 2,
                            buf,
                            false,
                            av.visitArray(name));
                }
                switch (this.b[v++] & 0xFF) {
                    case 'B':
                        byte[] bv = new byte[size];
                        for (i = 0; i < size; i++) {
                            bv[i] = (byte) readInt(items[readUnsignedShort(v)]);
                            v += 3;
                        }
                        av.visit(name, bv);
                        --v;
                        break;
                    case 'Z':
                        boolean[] zv = new boolean[size];
                        for (i = 0; i < size; i++) {
                            zv[i] = readInt(items[readUnsignedShort(v)]) != 0;
                            v += 3;
                        }
                        av.visit(name, zv);
                        --v;
                        break;
                    case 'S':
                        short[] sv = new short[size];
                        for (i = 0; i < size; i++) {
                            sv[i] = (short) readInt(items[readUnsignedShort(v)]);
                            v += 3;
                        }
                        av.visit(name, sv);
                        --v;
                        break;
                    case 'C':
                        char[] cv = new char[size];
                        for (i = 0; i < size; i++) {
                            cv[i] = (char) readInt(items[readUnsignedShort(v)]);
                            v += 3;
                        }
                        av.visit(name, cv);
                        --v;
                        break;
                    case 'I':
                        int[] iv = new int[size];
                        for (i = 0; i < size; i++) {
                            iv[i] = readInt(items[readUnsignedShort(v)]);
                            v += 3;
                        }
                        av.visit(name, iv);
                        --v;
                        break;
                    case 'J':
                        long[] lv = new long[size];
                        for (i = 0; i < size; i++) {
                            lv[i] = readLong(items[readUnsignedShort(v)]);
                            v += 3;
                        }
                        av.visit(name, lv);
                        --v;
                        break;
                    case 'F':
                        float[] fv = new float[size];
                        for (i = 0; i < size; i++) {
                            fv[i] = Float.intBitsToFloat(readInt(items[readUnsignedShort(v)]));
                            v += 3;
                        }
                        av.visit(name, fv);
                        --v;
                        break;
                    case 'D':
                        double[] dv = new double[size];
                        for (i = 0; i < size; i++) {
                            dv[i] = Double.longBitsToDouble(readLong(items[readUnsignedShort(v)]));
                            v += 3;
                        }
                        av.visit(name, dv);
                        --v;
                        break;
                    default:
                        v = readAnnotationValues(v - 3,
                                buf,
                                false,
                                av.visitArray(name));
                }
        }
        return v;
    }

    private int readFrameType(
        final Object[] frame,
        final int index,
        int v,
        final char[] buf,
        final Label[] labels)
    {
        int type = b[v++] & 0xFF;
        switch (type) {
            case 0:
                frame[index] = Opcodes.TOP;
                break;
            case 1:
                frame[index] = Opcodes.INTEGER;
                break;
            case 2:
                frame[index] = Opcodes.FLOAT;
                break;
            case 3:
                frame[index] = Opcodes.DOUBLE;
                break;
            case 4:
                frame[index] = Opcodes.LONG;
                break;
            case 5:
                frame[index] = Opcodes.NULL;
                break;
            case 6:
                frame[index] = Opcodes.UNINITIALIZED_THIS;
                break;
            case 7: // Object
                frame[index] = readClass(v, buf);
                v += 2;
                break;
            default: // Uninitialized
                frame[index] = readLabel(readUnsignedShort(v), labels);
                v += 2;
        }
        return v;
    }

    /**
     * Returns the label corresponding to the given offset. The default
     * implementation of this method creates a label for the given offset if it
     * has not been already created.
     *
     * @param offset a bytecode offset in a method.
     * @param labels the already created labels, indexed by their offset. If a
     *        label already exists for offset this method must not create a new
     *        one. Otherwise it must store the new label in this array.
     * @return a non null Label, which must be equal to labels[offset].
     */
    protected Label readLabel(int offset, Label[] labels) {
        if (labels[offset] == null) {
            labels[offset] = new Label();
        }
        return labels[offset];
    }

    /**
     * Reads an attribute in {@link #b b}.
     *
     * @param attrs prototypes of the attributes that must be parsed during the
     *        visit of the class. Any attribute whose type is not equal to the
     *        type of one the prototypes is ignored (i.e. an empty
     *        {@link Attribute} instance is returned).
     * @param type the type of the attribute.
     * @param off index of the first byte of the attribute's content in
     *        {@link #b b}. The 6 attribute header bytes, containing the type
     *        and the length of the attribute, are not taken into account here
     *        (they have already been read).
     * @param len the length of the attribute's content.
     * @param buf buffer to be used to call {@link #readUTF8 readUTF8},
     *        {@link #readClass(int,char[]) readClass} or
     *        {@link #readConst readConst}.
     * @param codeOff index of the first byte of code's attribute content in
     *        {@link #b b}, or -1 if the attribute to be read is not a code
     *        attribute. The 6 attribute header bytes, containing the type and
     *        the length of the attribute, are not taken into account here.
     * @param labels the labels of the method's code, or <tt>null</tt> if the
     *        attribute to be read is not a code attribute.
     * @return the attribute that has been read, or <tt>null</tt> to skip this
     *         attribute.
     */
    private Attribute readAttribute(
        final Attribute[] attrs,
        final String type,
        final int off,
        final int len,
        final char[] buf,
        final int codeOff,
        final Label[] labels)
    {
        for (int i = 0; i < attrs.length; ++i) {
            if (attrs[i].type.equals(type)) {
                return attrs[i].read(this, off, len, buf, codeOff, labels);
            }
        }
        return new Attribute(type).read(this, off, len, null, -1, null);
    }

    // ------------------------------------------------------------------------
    // Utility methods: low level parsing
    // ------------------------------------------------------------------------

    /**
     * Returns the start index of the constant pool item in {@link #b b}, plus
     * one. <i>This method is intended for {@link Attribute} sub classes, and is
     * normally not needed by class generators or adapters.</i>
     *
     * @param item the index a constant pool item.
     * @return the start index of the constant pool item in {@link #b b}, plus
     *         one.
     */
    public int getItem(final int item) {
        return items[item];
    }

    /**
     * Reads a byte value in {@link #b b}. <i>This method is intended for
     * {@link Attribute} sub classes, and is normally not needed by class
     * generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public int readByte(final int index) {
        return b[index] & 0xFF;
    }

    /**
     * Reads an unsigned short value in {@link #b b}. <i>This method is
     * intended for {@link Attribute} sub classes, and is normally not needed by
     * class generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public int readUnsignedShort(final int index) {
        byte[] b = this.b;
        return ((b[index] & 0xFF) << 8) | (b[index + 1] & 0xFF);
    }

    /**
     * Reads a signed short value in {@link #b b}. <i>This method is intended
     * for {@link Attribute} sub classes, and is normally not needed by class
     * generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public short readShort(final int index) {
        byte[] b = this.b;
        return (short) (((b[index] & 0xFF) << 8) | (b[index + 1] & 0xFF));
    }

    /**
     * Reads a signed int value in {@link #b b}. <i>This method is intended for
     * {@link Attribute} sub classes, and is normally not needed by class
     * generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public int readInt(final int index) {
        byte[] b = this.b;
        return ((b[index] & 0xFF) << 24) | ((b[index + 1] & 0xFF) << 16)
                | ((b[index + 2] & 0xFF) << 8) | (b[index + 3] & 0xFF);
    }

    /**
     * Reads a signed long value in {@link #b b}. <i>This method is intended
     * for {@link Attribute} sub classes, and is normally not needed by class
     * generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public long readLong(final int index) {
        long l1 = readInt(index);
        long l0 = readInt(index + 4) & 0xFFFFFFFFL;
        return (l1 << 32) | l0;
    }

    /**
     * Reads an UTF8 string constant pool item in {@link #b b}. <i>This method
     * is intended for {@link Attribute} sub classes, and is normally not needed
     * by class generators or adapters.</i>
     *
     * @param index the start index of an unsigned short value in {@link #b b},
     *        whose value is the index of an UTF8 constant pool item.
     * @param buf buffer to be used to read the item. This buffer must be
     *        sufficiently large. It is not automatically resized.
     * @return the String corresponding to the specified UTF8 item.
     */
    public String readUTF8(int index, final char[] buf) {
        int item = readUnsignedShort(index);
        String s = strings[item];
        if (s != null) {
            return s;
        }
        index = items[item];
        return strings[item] = readUTF(index + 2, readUnsignedShort(index), buf);
    }

    /**
     * Reads UTF8 string in {@link #b b}.
     *
     * @param index start offset of the UTF8 string to be read.
     * @param utfLen length of the UTF8 string to be read.
     * @param buf buffer to be used to read the string. This buffer must be
     *        sufficiently large. It is not automatically resized.
     * @return the String corresponding to the specified UTF8 string.
     */
    private String readUTF(int index, final int utfLen, final char[] buf) {
        int endIndex = index + utfLen;
        byte[] b = this.b;
        int strLen = 0;
        int c, d, e;
        while (index < endIndex) {
            c = b[index++] & 0xFF;
            switch (c >> 4) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    // 0xxxxxxx
                    buf[strLen++] = (char) c;
                    break;
                case 12:
                case 13:
                    // 110x xxxx 10xx xxxx
                    d = b[index++];
                    buf[strLen++] = (char) (((c & 0x1F) << 6) | (d & 0x3F));
                    break;
                default:
                    // 1110 xxxx 10xx xxxx 10xx xxxx
                    d = b[index++];
                    e = b[index++];
                    buf[strLen++] = (char) (((c & 0x0F) << 12)
                            | ((d & 0x3F) << 6) | (e & 0x3F));
                    break;
            }
        }
        return new String(buf, 0, strLen);
    }

    /**
     * Reads a class constant pool item in {@link #b b}. <i>This method is
     * intended for {@link Attribute} sub classes, and is normally not needed by
     * class generators or adapters.</i>
     *
     * @param index the start index of an unsigned short value in {@link #b b},
     *        whose value is the index of a class constant pool item.
     * @param buf buffer to be used to read the item. This buffer must be
     *        sufficiently large. It is not automatically resized.
     * @return the String corresponding to the specified class item.
     */
    public String readClass(final int index, final char[] buf) {
        // computes the start index of the CONSTANT_Class item in b
        // and reads the CONSTANT_Utf8 item designated by
        // the first two bytes of this CONSTANT_Class item
        return readUTF8(items[readUnsignedShort(index)], buf);
    }

    /**
     * Reads a numeric or string constant pool item in {@link #b b}. <i>This
     * method is intended for {@link Attribute} sub classes, and is normally not
     * needed by class generators or adapters.</i>
     *
     * @param item the index of a constant pool item.
     * @param buf buffer to be used to read the item. This buffer must be
     *        sufficiently large. It is not automatically resized.
     * @return the {@link Integer}, {@link Float}, {@link Long},
     *         {@link Double}, {@link String} or {@link Type} corresponding to
     *         the given constant pool item.
     */
    public Object readConst(final int item, final char[] buf) {
        int index = items[item];
        switch (b[index - 1]) {
            case ClassWriter.INT:
                return new Integer(readInt(index));
            case ClassWriter.FLOAT:
                return new Float(Float.intBitsToFloat(readInt(index)));
            case ClassWriter.LONG:
                return new Long(readLong(index));
            case ClassWriter.DOUBLE:
                return new Double(Double.longBitsToDouble(readLong(index)));
            case ClassWriter.CLASS:
                return Type.getObjectType(readUTF8(index, buf));
                // case ClassWriter.STR:
            default:
                return readUTF8(index, buf);
        }
    }
}
