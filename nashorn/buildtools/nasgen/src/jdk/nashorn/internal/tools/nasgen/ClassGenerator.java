/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.tools.nasgen;

import static jdk.internal.org.objectweb.asm.Opcodes.ACC_FINAL;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_STATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.ACCESSORPROPERTY_CREATE;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.ACCESSORPROPERTY_CREATE_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.ACCESSORPROPERTY_TYPE;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.ARRAYLIST_INIT_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.ARRAYLIST_TYPE;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.CLINIT;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.COLLECTIONS_EMPTY_LIST;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.COLLECTIONS_TYPE;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.COLLECTION_ADD;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.COLLECTION_ADD_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.COLLECTION_TYPE;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.DEFAULT_INIT_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.GETTER_PREFIX;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.GET_CLASS_NAME;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.GET_CLASS_NAME_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.INIT;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.LIST_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.OBJECT_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.PROPERTYMAP_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.PROPERTYMAP_FIELD_NAME;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.PROPERTYMAP_NEWMAP;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.PROPERTYMAP_NEWMAP_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.PROPERTYMAP_TYPE;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_CREATEBUILTIN;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_CREATEBUILTIN_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_CREATEBUILTIN_SPECS_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_SETARITY;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_SETARITY_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_SETDOCUMENTATIONKEY;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_SETDOCUMENTATIONKEY_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_TYPE;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SETTER_PREFIX;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.TYPE_OBJECT;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Type;
import jdk.nashorn.internal.tools.nasgen.MemberInfo.Kind;

/**
 * Base class for class generator classes.
 *
 */
public class ClassGenerator {
    /** ASM class writer used to output bytecode for this class */
    protected final ClassWriter cw;

    /**
     * Constructor
     */
    protected ClassGenerator() {
        this.cw = makeClassWriter();
    }

    MethodGenerator makeStaticInitializer() {
        return makeStaticInitializer(cw);
    }

    MethodGenerator makeConstructor() {
        return makeConstructor(cw);
    }

    MethodGenerator makeMethod(final int access, final String name, final String desc) {
        return makeMethod(cw, access, name, desc);
    }

    void addMapField() {
        addMapField(cw);
    }

    void addField(final String name, final String desc) {
        addField(cw, name, desc);
    }

    void addFunctionField(final String name) {
        addFunctionField(cw, name);
    }

    void addGetter(final String owner, final MemberInfo memInfo) {
        addGetter(cw, owner, memInfo);
    }

    void addSetter(final String owner, final MemberInfo memInfo) {
        addSetter(cw, owner, memInfo);
    }

    void emitGetClassName(final String name) {
        final MethodGenerator mi = makeMethod(ACC_PUBLIC, GET_CLASS_NAME, GET_CLASS_NAME_DESC);
        mi.loadLiteral(name);
        mi.returnValue();
        mi.computeMaxs();
        mi.visitEnd();
    }

    static ClassWriter makeClassWriter() {
        return new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(final String type1, final String type2) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (final RuntimeException | LinkageError e) {
                    return StringConstants.OBJECT_TYPE;
                }
            }
        };
    }

    static MethodGenerator makeStaticInitializer(final ClassVisitor cv) {
        return makeStaticInitializer(cv, CLINIT);
    }

    static MethodGenerator makeStaticInitializer(final ClassVisitor cv, final String name) {
        final int access =  ACC_PUBLIC | ACC_STATIC;
        final String desc = DEFAULT_INIT_DESC;
        final MethodVisitor mv = cv.visitMethod(access, name, desc, null, null);
        return new MethodGenerator(mv, access, name, desc);
    }

    static MethodGenerator makeConstructor(final ClassVisitor cv) {
        final int access = 0;
        final String name = INIT;
        final String desc = DEFAULT_INIT_DESC;
        final MethodVisitor mv = cv.visitMethod(access, name, desc, null, null);
        return new MethodGenerator(mv, access, name, desc);
    }

    static MethodGenerator makeMethod(final ClassVisitor cv, final int access, final String name, final String desc) {
        final MethodVisitor mv = cv.visitMethod(access, name, desc, null, null);
        return new MethodGenerator(mv, access, name, desc);
    }

    static void emitStaticInitPrefix(final MethodGenerator mi, final String className, final int memberCount) {
        mi.visitCode();
        if (memberCount > 0) {
            // new ArrayList(int)
            mi.newObject(ARRAYLIST_TYPE);
            mi.dup();
            mi.push(memberCount);
            mi.invokeSpecial(ARRAYLIST_TYPE, INIT, ARRAYLIST_INIT_DESC);
            // stack: ArrayList
        } else {
            // java.util.Collections.EMPTY_LIST
            mi.getStatic(COLLECTIONS_TYPE, COLLECTIONS_EMPTY_LIST, LIST_DESC);
            // stack List
        }
    }

    static void emitStaticInitSuffix(final MethodGenerator mi, final String className) {
        // stack: Collection
        // pmap = PropertyMap.newMap(Collection<Property>);
        mi.invokeStatic(PROPERTYMAP_TYPE, PROPERTYMAP_NEWMAP, PROPERTYMAP_NEWMAP_DESC);
        // $nasgenmap$ = pmap;
        mi.putStatic(className, PROPERTYMAP_FIELD_NAME, PROPERTYMAP_DESC);
        mi.returnVoid();
        mi.computeMaxs();
        mi.visitEnd();
    }

    @SuppressWarnings("fallthrough")
    private static Type memInfoType(final MemberInfo memInfo) {
        switch (memInfo.getJavaDesc().charAt(0)) {
            case 'I': return Type.INT_TYPE;
            case 'J': return Type.LONG_TYPE;
            case 'D': return Type.DOUBLE_TYPE;
            default:  assert false : memInfo.getJavaDesc();
            case 'L': return TYPE_OBJECT;
        }
    }

    private static String getterDesc(final MemberInfo memInfo) {
        return Type.getMethodDescriptor(memInfoType(memInfo));
    }

    private static String setterDesc(final MemberInfo memInfo) {
        return Type.getMethodDescriptor(Type.VOID_TYPE, memInfoType(memInfo));
    }

    static void addGetter(final ClassVisitor cv, final String owner, final MemberInfo memInfo) {
        final int access = ACC_PUBLIC;
        final String name = GETTER_PREFIX + memInfo.getJavaName();
        final String desc = getterDesc(memInfo);
        final MethodVisitor mv = cv.visitMethod(access, name, desc, null, null);
        final MethodGenerator mi = new MethodGenerator(mv, access, name, desc);
        mi.visitCode();
        if (memInfo.isStatic() && memInfo.getKind() == Kind.PROPERTY) {
            mi.getStatic(owner, memInfo.getJavaName(), memInfo.getJavaDesc());
        } else {
            mi.loadLocal(0);
            mi.getField(owner, memInfo.getJavaName(), memInfo.getJavaDesc());
        }
        mi.returnValue();
        mi.computeMaxs();
        mi.visitEnd();
    }

    static void addSetter(final ClassVisitor cv, final String owner, final MemberInfo memInfo) {
        final int access = ACC_PUBLIC;
        final String name = SETTER_PREFIX + memInfo.getJavaName();
        final String desc = setterDesc(memInfo);
        final MethodVisitor mv = cv.visitMethod(access, name, desc, null, null);
        final MethodGenerator mi = new MethodGenerator(mv, access, name, desc);
        mi.visitCode();
        if (memInfo.isStatic() && memInfo.getKind() == Kind.PROPERTY) {
            mi.loadLocal(1);
            mi.putStatic(owner, memInfo.getJavaName(), memInfo.getJavaDesc());
        } else {
            mi.loadLocal(0);
            mi.loadLocal(1);
            mi.putField(owner, memInfo.getJavaName(), memInfo.getJavaDesc());
        }
        mi.returnVoid();
        mi.computeMaxs();
        mi.visitEnd();
    }

    static void addMapField(final ClassVisitor cv) {
        // add a PropertyMap static field
        final FieldVisitor fv = cv.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
            PROPERTYMAP_FIELD_NAME, PROPERTYMAP_DESC, null, null);
        if (fv != null) {
            fv.visitEnd();
        }
    }

    static void addField(final ClassVisitor cv, final String name, final String desc) {
        final FieldVisitor fv = cv.visitField(ACC_PRIVATE, name, desc, null, null);
        if (fv != null) {
            fv.visitEnd();
        }
    }

    static void addFunctionField(final ClassVisitor cv, final String name) {
        addField(cv, name, OBJECT_DESC);
    }

    static void newFunction(final MethodGenerator mi, final String objName, final String className, final MemberInfo memInfo, final List<MemberInfo> specs) {
        final boolean arityFound = (memInfo.getArity() != MemberInfo.DEFAULT_ARITY);

        mi.loadLiteral(memInfo.getName());
        mi.visitLdcInsn(new Handle(H_INVOKESTATIC, className, memInfo.getJavaName(), memInfo.getJavaDesc()));

        assert specs != null;
        if (!specs.isEmpty()) {
            mi.memberInfoArray(className, specs);
            mi.invokeStatic(SCRIPTFUNCTION_TYPE, SCRIPTFUNCTION_CREATEBUILTIN, SCRIPTFUNCTION_CREATEBUILTIN_SPECS_DESC);
        } else {
            mi.invokeStatic(SCRIPTFUNCTION_TYPE, SCRIPTFUNCTION_CREATEBUILTIN, SCRIPTFUNCTION_CREATEBUILTIN_DESC);
        }

        if (arityFound) {
            mi.dup();
            mi.push(memInfo.getArity());
            mi.invokeVirtual(SCRIPTFUNCTION_TYPE, SCRIPTFUNCTION_SETARITY, SCRIPTFUNCTION_SETARITY_DESC);
        }

        mi.dup();
        mi.loadLiteral(memInfo.getDocumentationKey(objName));
        mi.invokeVirtual(SCRIPTFUNCTION_TYPE, SCRIPTFUNCTION_SETDOCUMENTATIONKEY, SCRIPTFUNCTION_SETDOCUMENTATIONKEY_DESC);
    }

    static void linkerAddGetterSetter(final MethodGenerator mi, final String className, final MemberInfo memInfo) {
        final String propertyName = memInfo.getName();
        // stack: Collection
        // dup of Collection instance
        mi.dup();

        // property = AccessorProperty.create(key, flags, getter, setter);
        mi.loadLiteral(propertyName);
        // setup flags
        mi.push(memInfo.getAttributes());
        // setup getter method handle
        String javaName = GETTER_PREFIX + memInfo.getJavaName();
        mi.visitLdcInsn(new Handle(H_INVOKEVIRTUAL, className, javaName, getterDesc(memInfo)));
        // setup setter method handle
        if (memInfo.isFinal()) {
            mi.pushNull();
        } else {
            javaName = SETTER_PREFIX + memInfo.getJavaName();
            mi.visitLdcInsn(new Handle(H_INVOKEVIRTUAL, className, javaName, setterDesc(memInfo)));
        }
        mi.invokeStatic(ACCESSORPROPERTY_TYPE, ACCESSORPROPERTY_CREATE, ACCESSORPROPERTY_CREATE_DESC);
        // boolean Collection.add(property)
        mi.invokeInterface(COLLECTION_TYPE, COLLECTION_ADD, COLLECTION_ADD_DESC);
        // pop return value of Collection.add
        mi.pop();
        // stack: Collection
    }

    static void linkerAddGetterSetter(final MethodGenerator mi, final String className, final MemberInfo getter, final MemberInfo setter) {
        final String propertyName = getter.getName();
        // stack: Collection
        // dup of Collection instance
        mi.dup();

        // property = AccessorProperty.create(key, flags, getter, setter);
        mi.loadLiteral(propertyName);
        // setup flags
        mi.push(getter.getAttributes());
        // setup getter method handle
        mi.visitLdcInsn(new Handle(H_INVOKESTATIC, className,
                getter.getJavaName(), getter.getJavaDesc()));
        // setup setter method handle
        if (setter == null) {
            mi.pushNull();
        } else {
            mi.visitLdcInsn(new Handle(H_INVOKESTATIC, className,
                    setter.getJavaName(), setter.getJavaDesc()));
        }
        mi.invokeStatic(ACCESSORPROPERTY_TYPE, ACCESSORPROPERTY_CREATE, ACCESSORPROPERTY_CREATE_DESC);
        // boolean Collection.add(property)
        mi.invokeInterface(COLLECTION_TYPE, COLLECTION_ADD, COLLECTION_ADD_DESC);
        // pop return value of Collection.add
        mi.pop();
        // stack: Collection
    }

    static ScriptClassInfo getScriptClassInfo(final String fileName) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fileName))) {
            return getScriptClassInfo(new ClassReader(bis));
        }
    }

    static ScriptClassInfo getScriptClassInfo(final byte[] classBuf) {
        return getScriptClassInfo(new ClassReader(classBuf));
    }

    private static ScriptClassInfo getScriptClassInfo(final ClassReader reader) {
        final ScriptClassInfoCollector scic = new ScriptClassInfoCollector();
        reader.accept(scic, 0);
        return scic.getScriptClassInfo();
    }
}
