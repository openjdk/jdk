/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */
package com.sun.classanalyzer;

import com.sun.tools.classfile.*;
import com.sun.tools.classfile.Type.*;
import com.sun.tools.classfile.Descriptor.InvalidDescriptor;
import static com.sun.tools.classfile.AccessFlags.*;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author Mandy Chung
 */
public class ClassFileParser {

    final Klass this_klass;
    final ClassFile classfile;
    final ConstantPoolParser constantPoolParser;
    final AnnotationParser annotationParser;
    final CodeAttributeParser codeAttributeParser;
    private final boolean buildDeps;

    protected ClassFileParser(InputStream in, long size, boolean buildDeps) throws IOException {
        try {
            this.classfile = ClassFile.read(in);
            this.this_klass = getKlass(this.classfile);
            this.buildDeps = buildDeps;
            this.constantPoolParser = new ConstantPoolParser(this);
            this.annotationParser = new AnnotationParser(this);
            this.codeAttributeParser = new CodeAttributeParser(this);
        } catch (ConstantPoolException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Klass getKlass(ClassFile cf) throws ConstantPoolException {
        Klass k = Klass.getKlass(cf.getName());
        k.setAccessFlags(cf.access_flags.flags);
        k.setFileSize(cf.byteLength());
        return k;
    }

    public static ClassFileParser newParser(InputStream in, long size, boolean buildDeps) throws IOException {
        return new ClassFileParser(in, size, buildDeps);
    }

    public static ClassFileParser newParser(String classPathname, boolean buildDeps) throws IOException {
        return newParser(new File(classPathname), buildDeps);
    }

    public static ClassFileParser newParser(File f, boolean buildDeps) throws IOException {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(f));
        try {
            return newParser(in, f.length(), buildDeps);
        } finally {
            in.close();
        }
    }

    public void parseDependency(boolean publicAPIs) throws IOException {
        if (publicAPIs && !classfile.access_flags.is(ACC_PUBLIC)) {
            // process public APIs only
            return;
        }

        parseClassInfo();
        if (!publicAPIs) {
            // parse all references in the classfile
            constantPoolParser.parseDependency();
        }
        parseMethods(publicAPIs);
        parseFields(publicAPIs);
    }

    void parseClassInfo() throws IOException {
        ConstantPool cpool = classfile.constant_pool;
        try {
            Signature_attribute sigAttr = (Signature_attribute) classfile.attributes.get(Attribute.Signature);
            if (sigAttr == null) {
                // use info from class file header
                if (classfile.isClass() && classfile.super_class != 0) {
                    String sn = classfile.getSuperclassName();
                    addExtends(sn);
                }
                for (int i = 0; i < classfile.interfaces.length; i++) {
                    String interf = classfile.getInterfaceName(i);
                    if (classfile.isClass()) {
                        addImplements(interf);
                    } else {
                        addExtends(interf);
                    }
                }
            } else {
                Type t = sigAttr.getParsedSignature().getType(cpool);
                // The signature parser cannot disambiguate between a
                // FieldType and a ClassSignatureType that only contains a superclass type.
                if (t instanceof Type.ClassSigType) {
                    Type.ClassSigType cst = Type.ClassSigType.class.cast(t);
                    if (cst.superclassType != null) {
                        for (Klass k : getKlass(cst.superclassType)) {
                            addExtends(k);
                        }
                    }
                    if (cst.superinterfaceTypes != null) {
                        for (Type t1 : cst.superinterfaceTypes) {
                            for (Klass k : getKlass(t1)) {
                                addImplements(k);
                            }
                        }
                    }
                } else {
                    for (Klass k : getKlass(t)) {
                        addExtends(k);
                    }
                }
            }
            // parse attributes
            annotationParser.parseAttributes(classfile.attributes);
        } catch (ConstantPoolException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void parseFields(boolean publicAPIs) throws IOException {
        ConstantPool cpool = classfile.constant_pool;
        for (Field f : classfile.fields) {
            try {
                AccessFlags flags = f.access_flags;
                if (publicAPIs && !flags.is(ACC_PUBLIC) && !flags.is(ACC_PROTECTED)) {
                    continue;
                }
                String fieldname = f.getName(cpool);
                Signature_attribute sigAttr = (Signature_attribute) f.attributes.get(Attribute.Signature);

                if (sigAttr == null) {
                    Set<Klass> types = parseDescriptor(f.descriptor);
                    String info = getFlag(flags) + " " + f.descriptor.getFieldType(cpool) + " " + fieldname;
                    addFieldTypes(types, info, flags);
                } else {
                    Type t = sigAttr.getParsedSignature().getType(cpool);
                    String info = getFlag(flags) + " " + t + " " + fieldname;
                    addFieldTypes(getKlass(t), info, flags);
                }
                // parse attributes
                annotationParser.parseAttributes(f.attributes);
            } catch (ConstantPoolException ex) {
                throw new RuntimeException(ex);
            } catch (InvalidDescriptor ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private void parseMethods(boolean publicAPIs) {
        for (Method m : classfile.methods) {
            if (publicAPIs && !m.access_flags.is(ACC_PUBLIC) && !m.access_flags.is(ACC_PROTECTED)) {
                // only interest in the API level
                return;
            }

            parseMethod(m);
        }
    }

    String checkClassName(String classname) {
        int i = 0;
        while (i < classname.length()) {
            switch (classname.charAt(i)) {
                case 'Z':
                case 'B':
                case 'C':
                case 'S':
                case 'I':
                case 'J':
                case 'F':
                case 'D':
                    return "";
                case 'L':
                    if (!classname.endsWith(";")) {
                        throw new RuntimeException("Invalid classname " + classname);
                    }
                    return classname.substring(i + 1, classname.length() - 1);
                case '[':
                    i++;
                    break;
                default:
                    if (classname.endsWith(";")) {
                        throw new RuntimeException("Invalid classname " + classname);
                    }
                    return classname;

            }
        }
        throw new RuntimeException("Invalid classname " + classname);
    }

    private void addExtends(String classname) throws IOException {
        if (!buildDeps) {
            return;
        }

        addExtends(Klass.getKlass(classname));
    }

    private void addExtends(Klass k) {
        if (!buildDeps) {
            return;
        }

        ResolutionInfo resInfo = ResolutionInfo.resolvedExtends(this_klass, k);
        resInfo.setPublicAccess(classfile.access_flags.is(ACC_PUBLIC));
        this_klass.addDep(k, resInfo);
        k.addReferrer(this_klass, resInfo);
    }

    private void addImplements(String classname) throws IOException {
        if (!buildDeps) {
            return;
        }

        addImplements(Klass.getKlass(classname));
    }

    private void addImplements(Klass k) {
        if (!buildDeps) {
            return;
        }

        ResolutionInfo resInfo = ResolutionInfo.resolvedImplements(this_klass, k);
        resInfo.setPublicAccess(classfile.access_flags.is(ACC_PUBLIC));

        this_klass.addDep(k, resInfo);

        k.addReferrer(this_klass, resInfo);
    }

    private Set<Klass> getKlass(Type type) throws IOException {
        Set<Klass> refTypes = new TreeSet<Klass>();
        if (!buildDeps) {
            return refTypes;
        }

        type.accept(typevisitor, refTypes);
        return refTypes;
    }
    private Type.Visitor<Void, Set<Klass>> typevisitor = new Type.Visitor<Void, Set<Klass>>() {

        public Void visitSimpleType(SimpleType type, Set<Klass> klasses) {
            // nop
            return null;
        }

        public Void visitArrayType(ArrayType type, Set<Klass> klasses) {
            try {
                klasses.addAll(getKlass(type.elemType));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            return null;

        }

        public Void visitMethodType(MethodType type, Set<Klass> klasses) {
            throw new InternalError("Unexpected type " + type);
        }

        public Void visitClassSigType(ClassSigType type, Set<Klass> klasses) {
            try {
                if (type.superclassType != null) {
                    klasses.addAll(getKlass(type.superclassType));
                }
                if (type.superinterfaceTypes != null) {
                    for (Type t : type.superinterfaceTypes) {
                        klasses.addAll(getKlass(t));
                    }
                }
                if (type.typeParamTypes != null) {
                    for (Type t : type.typeParamTypes) {
                        klasses.addAll(getKlass(t));
                    }
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            return null;
        }

        public Void visitClassType(ClassType type, Set<Klass> klasses) {
            klasses.add(Klass.getKlass(type.getBinaryName()));
            if (type.typeArgs != null) {
                for (Type t : type.typeArgs) {
                    try {
                        klasses.addAll(getKlass(t));
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
            return null;

        }

        public Void visitTypeParamType(TypeParamType type, Set<Klass> klasses) {
            try {
                if (type.classBound != null) {
                    klasses.addAll(getKlass(type.classBound));
                }
                if (type.interfaceBounds != null) {
                    for (Type t : type.interfaceBounds) {
                        klasses.addAll(getKlass(t));
                    }
                }

            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            return null;

        }

        public Void visitWildcardType(WildcardType type, Set<Klass> klasses) {
            if (type.boundType != null) {
                try {
                    klasses.addAll(getKlass(type.boundType));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
            return null;

        }
    };

    private void printMethod(Method m) {
        try {
            System.out.println("parsing " + m.getName(classfile.constant_pool) + "(" +
                    m.descriptor.getParameterTypes(classfile.constant_pool) + ") return type " +
                    m.descriptor.getReturnType(classfile.constant_pool));

        } catch (ConstantPoolException ex) {
        } catch (InvalidDescriptor ex) {
        }
    }

    private static StringBuilder appendWord(StringBuilder sb, String word) {
        if (sb.length() > 0) {
            sb.append(" ");
        }
        sb.append(word);
        return sb;
    }

    private static String getFlag(AccessFlags flags) {
        StringBuilder modifier = new StringBuilder();
        if (flags.is(ACC_PUBLIC)) {
            modifier.append("public");
        }
        if (flags.is(ACC_PRIVATE)) {
            modifier.append("private");
        }
        if (flags.is(ACC_PROTECTED)) {
            modifier.append("protected");
        }
        if (flags.is(ACC_STATIC)) {
            appendWord(modifier, "static");
        }
        if (flags.is(ACC_FINAL)) {
            appendWord(modifier, "final");
        }
        if (flags.is(ACC_SYNCHRONIZED)) {
            // return "synchronized";
        }
        if (flags.is(0x80)) {
            // return (t == Type.Field ? "transient" : null);
            // return "transient";
        }
        if (flags.is(ACC_VOLATILE)) {
            // return "volatile";
        }
        if (flags.is(ACC_NATIVE)) {
            // return "native";
        }
        if (flags.is(ACC_ABSTRACT)) {
            appendWord(modifier, "abstract");
        }
        if (flags.is(ACC_STRICT)) {
            // return "strictfp";
        }
        if (flags.is(ACC_MODULE)) {
            appendWord(modifier, "module");
        }
        return modifier.toString();
    }

    private Klass.Method toKlassMethod(Method m, Descriptor d) {
        try {
            ConstantPool cpool = classfile.constant_pool;
            String methodname = m.getName(cpool);
            StringBuilder sb = new StringBuilder();
            sb.append(getFlag(m.access_flags));
            if (methodname.equals("<init>")) {
                String s = this_klass.getBasename() + d.getParameterTypes(cpool);
                appendWord(sb, s);
            } else if (methodname.equals("<clinit>")) {
                // <clinit>
                appendWord(sb, methodname);
            } else {
                String s = d.getReturnType(cpool) + " " + methodname + d.getParameterTypes(cpool);
                appendWord(sb, s);
            }
            String signature = sb.toString().replace('/', '.');
            return this_klass.getMethod(methodname, signature);
        } catch (ConstantPoolException ex) {
            throw new RuntimeException(ex);
        } catch (InvalidDescriptor ex) {
            throw new RuntimeException(ex);
        }
    }

    Klass.Method parseMethod(Method m) {
        AccessFlags flags = m.access_flags;
        Descriptor d;
        List<? extends Type> methodExceptions = null;
        try {
            ConstantPool cpool = classfile.constant_pool;
            Klass.Method kmethod;
            Signature_attribute sigAttr = (Signature_attribute) m.attributes.get(Attribute.Signature);
            if (sigAttr == null) {
                d = m.descriptor;
                Set<Klass> types = parseDescriptor(d);

                kmethod = toKlassMethod(m, d);
                addMethodTypes(types, kmethod, flags);
            } else {
                Type.MethodType methodType;
                Signature methodSig = sigAttr.getParsedSignature();
                d = methodSig;
                try {
                    kmethod = toKlassMethod(m, d);
                    methodType = (Type.MethodType) methodSig.getType(cpool);
                    addMethodTypes(getKlass(methodType.returnType), kmethod, flags);
                    if (methodType.paramTypes != null) {
                        for (Type t : methodType.paramTypes) {
                            addMethodTypes(getKlass(t), kmethod, flags);
                        }
                    }
                    if (methodType.typeParamTypes != null) {
                        for (Type t : methodType.typeParamTypes) {
                            addMethodTypes(getKlass(t), kmethod, flags);
                        }
                    }

                    methodExceptions = methodType.throwsTypes;
                    if (methodExceptions != null) {
                        if (methodExceptions.size() == 0) {
                            methodExceptions = null;
                        } else {
                            for (Type t : methodExceptions) {
                                addCheckedExceptionTypes(getKlass(t), kmethod, flags);
                            }
                        }
                    }
                } catch (ConstantPoolException e) {
                    throw new RuntimeException(e);
                }
            }

            Attribute e_attr = m.attributes.get(Attribute.Exceptions);
            if (e_attr != null && methodExceptions == null) {
                // if there are generic exceptions, there must be erased exceptions
                if (e_attr instanceof Exceptions_attribute) {
                    Exceptions_attribute exceptions = (Exceptions_attribute) e_attr;
                    for (int i = 0; i < exceptions.number_of_exceptions; i++) {
                        String classname = checkClassName(exceptions.getException(i, classfile.constant_pool));
                        if (classname.length() > 0 && buildDeps) {
                            Klass to = Klass.getKlass(classname);
                            ResolutionInfo resInfo = ResolutionInfo.resolvedCheckedException(this_klass, to, kmethod);
                            resInfo.setPublicAccess(flags.is(ACC_PUBLIC));

                            this_klass.addDep(to, resInfo);
                            to.addReferrer(this_klass, resInfo);
                        }
                    }
                } else {
                    throw new RuntimeException("Invalid attribute: " + e_attr);
                }
            }

            Code_attribute c_attr = (Code_attribute) m.attributes.get(Attribute.Code);
            if (c_attr != null) {
                codeAttributeParser.parse(c_attr, kmethod);
            }
            kmethod.isAbstract = classfile.access_flags.is(ACC_ABSTRACT);
            kmethod.setCodeLength(m.byteLength());

            // parse annotation attributes
            annotationParser.parseAttributes(m.attributes, kmethod);
            return kmethod;
        } catch (ConstantPoolException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void addFieldTypes(Set<Klass> types, String info, AccessFlags flags) {
        if (types.isEmpty() || !buildDeps) {
            return;
        }

        for (Klass to : types) {
            ResolutionInfo resInfo = ResolutionInfo.resolvedField(this_klass, to, info);
            resInfo.setPublicAccess(flags.is(ACC_PUBLIC));

            this_klass.addDep(to, resInfo);
            to.addReferrer(this_klass, resInfo);
        }
    }

    private void addReferencedTypes(Method m, Descriptor d, AccessFlags flags) {
        Set<Klass> types = parseDescriptor(d);

        Klass.Method method = toKlassMethod(m, d);
        addMethodTypes(types, method, flags);
    }

    private void addMethodTypes(Set<Klass> types, Klass.Method method, AccessFlags flags) {
        if (types.isEmpty() || !buildDeps) {
            return;
        }
        for (Klass to : types) {
            ResolutionInfo resInfo = ResolutionInfo.resolvedMethodSignature(this_klass, to, method);
            resInfo.setPublicAccess(flags.is(ACC_PUBLIC));

            this_klass.addDep(to, resInfo);
            to.addReferrer(this_klass, resInfo);
        }
    }

    private void addCheckedExceptionTypes(Set<Klass> types, Klass.Method method, AccessFlags flags) {
        if (types.isEmpty() || !buildDeps) {
            return;
        }
        for (Klass to : types) {
            ResolutionInfo resInfo = ResolutionInfo.resolvedCheckedException(this_klass, to, method);
            resInfo.setPublicAccess(flags.is(ACC_PUBLIC));

            this_klass.addDep(to, resInfo);
            to.addReferrer(this_klass, resInfo);
        }
    }

    private Set<Klass> parseDescriptor(Descriptor d) {
        Set<Klass> types = new TreeSet<Klass>();
        try {
            String desc = d.getValue(classfile.constant_pool);
            int p = 0;
            while (p < desc.length()) {
                String type;
                char ch;
                switch (ch = desc.charAt(p++)) {
                    case '(':
                    case ')':
                    case '[':
                    case 'B':
                    case 'C':
                    case 'D':
                    case 'F':
                    case 'I':
                    case 'J':
                    case 'S':
                    case 'Z':
                    case 'V':
                        continue;
                    case 'L':
                        int sep = desc.indexOf(';', p);
                        if (sep == -1) {
                            throw new RuntimeException("Invalid descriptor: " + (p - 1) + " " + desc);
                        }
                        type = checkClassName(desc.substring(p, sep));
                        p = sep + 1;
                        break;
                    default:
                        throw new RuntimeException("Invalid descriptor: " + (p - 1) + " " + desc);
                }

                if (!type.isEmpty() && buildDeps) {
                    Klass to = Klass.getKlass(type);
                    types.add(to);

                }
            }
        } catch (ConstantPoolException ex) {
            throw new RuntimeException(ex);
        }
        return types;
    }
}
