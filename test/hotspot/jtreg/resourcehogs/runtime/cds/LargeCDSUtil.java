/*
 * Copyright (c) 2026 salesforce.com, inc. All Rights Reserved
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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

class LargeCDSUtil {
    static final long TWO_G = 2L * 1024 * 1024 * 1024;

    static void deleteIfExists(String... files) throws IOException {
        for (String f : files) {
            Files.deleteIfExists(Paths.get(f));
        }
    }

    static void createGeneratedJar(Path jar, int simpleClasses, int megaClasses, int megaFields) throws Exception {
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
            for (int i = 0; i < simpleClasses; i++) {
                String internalName = classInternalName("gen", "C", i);
                writeClassToJar(jos, internalName, makeSimpleClass(internalName));
            }
            for (int i = 0; i < megaClasses; i++) {
                String internalName = classInternalName("gen", "M", i);
                writeClassToJar(jos, internalName, makeMegaClass(internalName, megaFields));
            }
        }
    }

    static void writeClassToJar(JarOutputStream jos, String internalName, byte[] bytes) throws IOException {
        JarEntry je = new JarEntry(internalName + ".class");
        jos.putNextEntry(je);
        jos.write(bytes);
        jos.closeEntry();
    }

    static String classInternalName(String pkg, String prefix, int i) {
        return pkg + "/" + prefix + toFixedWidth(i, 6);
    }

    static String toFixedWidth(int value, int width) {
        String s = Integer.toString(value);
        if (s.length() >= width) {
            return s;
        }
        StringBuilder sb = new StringBuilder(width);
        for (int i = s.length(); i < width; i++) {
            sb.append('0');
        }
        sb.append(s);
        return sb.toString();
    }

    private static byte[] makeSimpleClass(String thisClassInternalName) throws IOException {
        return makeClass(thisClassInternalName, 0);
    }

    private static byte[] makeMegaClass(String thisClassInternalName, int fieldCount) throws IOException {
        return makeClass(thisClassInternalName, fieldCount);
    }

    private static byte[] makeClass(String thisClassInternalName, int fieldCount) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        try (DataOutputStream out = new DataOutputStream(baos)) {
            CP cp = new CP(out);
            out.writeInt(0xCAFEBABE);
            out.writeShort(0);
            out.writeShort(52);

            int utf8_java_lang_Object = cp.utf8("java/lang/Object");
            int class_java_lang_Object = cp.clazz(utf8_java_lang_Object);

            int utf8_init = cp.utf8("<init>");
            int utf8_void_sig = cp.utf8("()V");
            int utf8_Code = cp.utf8("Code");
            int nat_init = cp.nameAndType(utf8_init, utf8_void_sig);
            int mr_Object_init = cp.methodRef(class_java_lang_Object, nat_init);

            int utf8_this_class = cp.utf8(thisClassInternalName);
            int class_this = cp.clazz(utf8_this_class);

            int utf8_int_sig = 0;
            if (fieldCount > 0) {
                utf8_int_sig = cp.utf8("I");
                for (int i = 0; i < fieldCount; i++) {
                    cp.utf8("f" + toFixedWidth(i, 5));
                }
            }

            cp.finish();

            out.writeShort(0x0021);
            out.writeShort(class_this);
            out.writeShort(class_java_lang_Object);

            out.writeShort(0);

            if (fieldCount > 0) {
                out.writeShort(fieldCount);
                int first_field_name_index = cp.firstFieldNameIndex();
                for (int i = 0; i < fieldCount; i++) {
                    out.writeShort(0x0001);
                    out.writeShort(first_field_name_index + i);
                    out.writeShort(utf8_int_sig);
                    out.writeShort(0);
                }
            } else {
                out.writeShort(0);
            }

            out.writeShort(1);
            out.writeShort(0x0001);
            out.writeShort(utf8_init);
            out.writeShort(utf8_void_sig);
            out.writeShort(1);
            out.writeShort(utf8_Code);

            byte[] code = new byte[] {
                    (byte)0x2A,
                    (byte)0xB7, (byte)((mr_Object_init >> 8) & 0xFF), (byte)(mr_Object_init & 0xFF),
                    (byte)0xB1
            };
            int codeAttrLen = 12 + code.length;
            out.writeInt(codeAttrLen);
            out.writeShort(1);
            out.writeShort(1);
            out.writeInt(code.length);
            out.write(code);
            out.writeShort(0);
            out.writeShort(0);

            out.writeShort(0);
        }
        return baos.toByteArray();
    }

    private static class CP {
        private final DataOutputStream _out;
        private final ByteArrayOutputStream _cpBytes;
        private final DataOutputStream _cpOut;
        private int _count;
        private int _firstFieldNameIndex;

        CP(DataOutputStream out) {
            _out = out;
            _cpBytes = new ByteArrayOutputStream(4096);
            _cpOut = new DataOutputStream(_cpBytes);
            _count = 1;
            _firstFieldNameIndex = 0;
        }

        int utf8(String s) throws IOException {
            if (_firstFieldNameIndex == 0 && s.startsWith("f")) {
                _firstFieldNameIndex = _count;
            }
            _cpOut.writeByte(1);
            _cpOut.writeUTF(s);
            return _count++;
        }

        int clazz(int utf8Index) throws IOException {
            _cpOut.writeByte(7);
            _cpOut.writeShort(utf8Index);
            return _count++;
        }

        int nameAndType(int nameIndex, int typeIndex) throws IOException {
            _cpOut.writeByte(12);
            _cpOut.writeShort(nameIndex);
            _cpOut.writeShort(typeIndex);
            return _count++;
        }

        int methodRef(int classIndex, int nameAndTypeIndex) throws IOException {
            _cpOut.writeByte(10);
            _cpOut.writeShort(classIndex);
            _cpOut.writeShort(nameAndTypeIndex);
            return _count++;
        }

        int firstFieldNameIndex() {
            return _firstFieldNameIndex;
        }

        void finish() throws IOException {
            _out.writeShort(_count);
            _out.write(_cpBytes.toByteArray());
        }
    }
}
