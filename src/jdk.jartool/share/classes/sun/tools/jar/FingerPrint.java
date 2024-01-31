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

package sun.tools.jar;

import java.io.IOException;
import java.lang.reflect.AccessFlag;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.lang.classfile.AccessFlags;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.EnclosingMethodAttribute;
import java.lang.classfile.attribute.InnerClassesAttribute;

/**
 * A FingerPrint is an abstract representation of a JarFile entry that contains
 * information to determine if the entry represents a class or a
 * resource, and whether two entries are identical.  If the FingerPrint represents
 * a class, it also contains information to (1) describe the public API;
 * (2) compare the public API of this class with another class;  (3) determine
 * whether or not it's a nested class and, if so, the name of the associated
 * outer class; and (4) for an canonically ordered set of classes determine
 * if the class versions are compatible.  A set of classes is canonically
 * ordered if the classes in the set have the same name, and the base class
 * precedes the versioned classes and if each versioned class with version
 * {@code n} precedes classes with versions {@code > n} for all versions
 * {@code n}.
 */
final class FingerPrint {
    private static final MessageDigest MD;

    private final String basename;
    private final String entryName;
    private final int mrversion;

    private final byte[] sha1;
    private final ClassAttributes attrs;
    private final boolean isClassEntry;

    static {
        try {
            MD = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException x) {
            // log big problem?
            throw new RuntimeException(x);
        }
    }

    public FingerPrint(String basename, String entryName, int mrversion, byte[] bytes)
            throws IOException {
        this.basename = basename;
        this.entryName = entryName;
        this.mrversion = mrversion;
        if (isCafeBabe(bytes)) {
            isClassEntry = true;
            sha1 = sha1(bytes, 8);  // skip magic number and major/minor version
            attrs = getClassAttributes(bytes);
        } else {
            isClassEntry = false;
            sha1 = null;
            attrs = null;
        }
    }

    public boolean isClass() {
        return isClassEntry;
    }

    public boolean isNestedClass() {
        return attrs.maybeNestedClass && attrs.outerClassName != null;
    }

    public boolean isPublicClass() {
        return attrs.publicClass;
    }

    public boolean isIdentical(FingerPrint that) {
        if (that == null) return false;
        if (this == that) return true;
        return isEqual(this.sha1, that.sha1);
    }

    public boolean isCompatibleVersion(FingerPrint that) {
        return attrs.majorVersion >= that.attrs.majorVersion;
    }

    public boolean isSameAPI(FingerPrint that) {
        if (that == null) return false;
        return attrs.equals(that.attrs);
    }

    public String basename() {
        return basename;
    }

    public String entryName() {
        return entryName;
    }

    public String className() {
        return attrs.name;
    }

    public int mrversion() {
        return mrversion;
    }

    public String outerClassName() {
        return attrs.outerClassName;
    }

    private byte[] sha1(byte[] entry) {
        MD.update(entry);
        return MD.digest();
    }

    private byte[] sha1(byte[] entry, int offset) {
        MD.update(entry, offset, entry.length - offset);
        return MD.digest();
    }

    private boolean isEqual(byte[] sha1_1, byte[] sha1_2) {
        return MessageDigest.isEqual(sha1_1, sha1_2);
    }

    private static final byte[] cafeBabe = {(byte)0xca, (byte)0xfe, (byte)0xba, (byte)0xbe};

    private boolean isCafeBabe(byte[] bytes) {
        if (bytes.length < 4) return false;
        for (int i = 0; i < 4; i++) {
            if (bytes[i] != cafeBabe[i]) {
                return false;
            }
        }
        return true;
    }

    private static ClassAttributes getClassAttributes(byte[] bytes) {
        var cm = ClassFile.of().parse(bytes);
        ClassAttributes attrs = new ClassAttributes(
                cm.flags(),
                cm.thisClass().asInternalName(),
                cm.superclass().map(ClassEntry::asInternalName).orElse(null),
                cm.majorVersion());
        cm.forEachElement(attrs);
        return attrs;
    }

    private static final class Field {
        private final int access;
        private final String name;
        private final String desc;

        Field(int access, String name, String desc) {
            this.access = access;
            this.name = name;
            this.desc = desc;
        }

        @Override
        public boolean equals(Object that) {
            if (that == null) return false;
            if (this == that) return true;
            if (!(that instanceof Field)) return false;
            Field field = (Field)that;
            return (access == field.access) && name.equals(field.name)
                    && desc.equals(field.desc);
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 37 * result + access;
            result = 37 * result + name.hashCode();
            result = 37 * result + desc.hashCode();
            return result;
        }
    }

    private static final class Method {
        private final int access;
        private final String name;
        private final String desc;
        private final Set<String> exceptions;

        Method(int access, String name, String desc, Set<String> exceptions) {
            this.access = access;
            this.name = name;
            this.desc = desc;
            this.exceptions = exceptions;
        }

        @Override
        public boolean equals(Object that) {
            if (that == null) return false;
            if (this == that) return true;
            if (!(that instanceof Method)) return false;
            Method method = (Method)that;
            return (access == method.access) && name.equals(method.name)
                    && desc.equals(method.desc)
                    && exceptions.equals(method.exceptions);
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 37 * result + access;
            result = 37 * result + name.hashCode();
            result = 37 * result + desc.hashCode();
            result = 37 * result + exceptions.hashCode();
            return result;
        }
    }

    private static final class ClassAttributes implements Consumer<ClassElement> {
        private final String name;
        private String outerClassName;
        private final String superName;
        private final int majorVersion;
        private final int access;
        private final boolean publicClass;
        private final boolean maybeNestedClass;
        private final Set<Field> fields = new HashSet<>();
        private final Set<Method> methods = new HashSet<>();

        public ClassAttributes(AccessFlags access, String name, String superName, int majorVersion) {
            this.majorVersion = majorVersion; // JDK-8296329: extract major version only
            this.access = access.flagsMask();
            this.name = name;
            this.maybeNestedClass = name.contains("$");
            this.superName = superName;
            this.publicClass = isPublic(access);
        }

        @Override
        public void accept(ClassElement cle) {
            switch (cle) {
                case InnerClassesAttribute ica -> {
                    for (var icm : ica.classes()) {
                        if (this.maybeNestedClass && icm.outerClass().isPresent()
                                && this.name.equals(icm.innerClass().asInternalName())
                                && this.outerClassName == null) {
                            this.outerClassName = icm.outerClass().get().asInternalName();
                        }
                    }
                }
                case FieldModel fm -> {
                    if (isPublic(fm.flags())) {
                        fields.add(new Field(fm.flags().flagsMask(),
                                             fm.fieldName().stringValue(),
                                             fm.fieldType().stringValue()));
                    }
                }
                case MethodModel mm -> {
                    if (isPublic(mm.flags())) {
                        Set<String> exceptionSet = new HashSet<>();
                        mm.findAttribute(Attributes.EXCEPTIONS).ifPresent(ea ->
                                ea.exceptions().forEach(e ->
                                        exceptionSet.add(e.asInternalName())));
                        // treat type descriptor as a proxy for signature because signature
                        // is usually null, need to strip off the return type though
                        int n;
                        var desc = mm.methodType().stringValue();
                        if (desc != null && (n = desc.lastIndexOf(')')) != -1) {
                            desc = desc.substring(0, n + 1);
                            methods.add(new Method(mm.flags().flagsMask(),
                                    mm.methodName().stringValue(), desc, exceptionSet));
                        }
                    }
                }
                case EnclosingMethodAttribute ema -> {
                    if (this.maybeNestedClass) {
                        this.outerClassName = ema.enclosingClass().asInternalName();
                    }
                }
                default -> {}
            }
        }

        private static boolean isPublic(AccessFlags access) {
            return access.has(AccessFlag.PUBLIC) || access.has(AccessFlag.PROTECTED);
        }

        @Override
        public boolean equals(Object that) {
            if (that == null) return false;
            if (this == that) return true;
            if (!(that instanceof ClassAttributes)) return false;
            ClassAttributes clsAttrs = (ClassAttributes)that;
            boolean superNameOkay = superName != null
                    ? superName.equals(clsAttrs.superName) : true;
            return access == clsAttrs.access
                    && superNameOkay
                    && fields.equals(clsAttrs.fields)
                    && methods.equals(clsAttrs.methods);
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 37 * result + access;
            result = 37 * result + superName != null ? superName.hashCode() : 0;
            result = 37 * result + fields.hashCode();
            result = 37 * result + methods.hashCode();
            return result;
        }
    }
}
