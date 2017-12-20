/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.classfile;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.replacements.classfile.ClassfileConstant.Utf8;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Container for objects representing the {@code Code} attributes parsed from a class file.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.3">Code
 *      attributes</a>
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4">Constant
 *      Pool</a>
 */
public class Classfile {

    private final ResolvedJavaType type;
    private final List<ClassfileBytecode> codeAttributes;

    private static final int MAJOR_VERSION_JAVA7 = 51;
    private static final int MAJOR_VERSION_JAVA10 = 54;
    private static final int MAGIC = 0xCAFEBABE;

    /**
     * Creates a {@link Classfile} by parsing the class file bytes for {@code type} loadable from
     * {@code context}.
     *
     * @throws NoClassDefFoundError if there is an IO error while parsing the class file
     */
    public Classfile(ResolvedJavaType type, DataInputStream stream, ClassfileBytecodeProvider context) throws IOException {
        this.type = type;

        // magic
        int magic = stream.readInt();
        assert magic == MAGIC;

        int minor = stream.readUnsignedShort();
        int major = stream.readUnsignedShort();
        if (major < MAJOR_VERSION_JAVA7 || major > MAJOR_VERSION_JAVA10) {
            throw new UnsupportedClassVersionError("Unsupported class file version: " + major + "." + minor);
        }

        ClassfileConstantPool cp = new ClassfileConstantPool(stream, context);

        // access_flags, this_class, super_class
        skipFully(stream, 6);

        // interfaces
        skipFully(stream, stream.readUnsignedShort() * 2);

        // fields
        skipFields(stream);

        // methods
        codeAttributes = readMethods(stream, cp);

        // attributes
        skipAttributes(stream);
    }

    public ClassfileBytecode getCode(String name, String descriptor) {
        for (ClassfileBytecode code : codeAttributes) {
            ResolvedJavaMethod method = code.getMethod();
            if (method.getName().equals(name) && method.getSignature().toMethodDescriptor().equals(descriptor)) {
                return code;
            }
        }
        throw new NoSuchMethodError(type.toJavaName() + "." + name + descriptor);
    }

    private static void skipAttributes(DataInputStream stream) throws IOException {
        int attributesCount;
        attributesCount = stream.readUnsignedShort();
        for (int i = 0; i < attributesCount; i++) {
            skipFully(stream, 2); // name_index
            int attributeLength = stream.readInt();
            skipFully(stream, attributeLength);
        }
    }

    static void skipFully(DataInputStream stream, int n) throws IOException {
        long skipped = 0;
        do {
            long s = stream.skip(n - skipped);
            skipped += s;
            if (s == 0 && skipped != n) {
                // Check for EOF (i.e., truncated class file)
                if (stream.read() == -1) {
                    throw new IOException("truncated stream");
                }
                skipped++;
            }
        } while (skipped != n);
    }

    private ClassfileBytecode findCodeAttribute(DataInputStream stream, ClassfileConstantPool cp, String name, String descriptor, boolean isStatic) throws IOException {
        int attributesCount;
        attributesCount = stream.readUnsignedShort();
        ClassfileBytecode code = null;
        for (int i = 0; i < attributesCount; i++) {
            String attributeName = cp.get(Utf8.class, stream.readUnsignedShort()).value;
            int attributeLength = stream.readInt();
            if (code == null && attributeName.equals("Code")) {
                ResolvedJavaMethod method = cp.context.findMethod(type, name, descriptor, isStatic);
                // Even if we will discard the Code attribute (see below), we still
                // need to parse it to reach the following class file content.
                code = new ClassfileBytecode(method, stream, cp);
                if (method == null) {
                    // This is a method hidden from reflection
                    // (see sun.reflect.Reflection.filterMethods)
                    code = null;
                }
            } else {
                skipFully(stream, attributeLength);
            }
        }
        return code;
    }

    private static void skipFields(DataInputStream stream) throws IOException {
        int count = stream.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            skipFully(stream, 6); // access_flags, name_index, descriptor_index
            skipAttributes(stream);
        }
    }

    private List<ClassfileBytecode> readMethods(DataInputStream stream, ClassfileConstantPool cp) throws IOException {
        int count = stream.readUnsignedShort();
        List<ClassfileBytecode> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int accessFlags = stream.readUnsignedShort();
            boolean isStatic = Modifier.isStatic(accessFlags);
            String name = cp.get(Utf8.class, stream.readUnsignedShort()).value;
            String descriptor = cp.get(Utf8.class, stream.readUnsignedShort()).value;
            ClassfileBytecode code = findCodeAttribute(stream, cp, name, descriptor, isStatic);
            if (code != null) {
                result.add(code);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "<" + type.toJavaName() + ">";
    }
}
