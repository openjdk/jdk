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
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.V1_7;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.CONSTRUCTOR_SUFFIX;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.DEFAULT_INIT_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.INIT;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.PROPERTYMAP_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.PROPERTYMAP_FIELD_NAME;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.OBJECT_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.PROTOTYPEOBJECT_SETCONSTRUCTOR;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.PROTOTYPEOBJECT_SETCONSTRUCTOR_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.PROTOTYPEOBJECT_TYPE;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTIONIMPL_INIT_DESC3;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTIONIMPL_INIT_DESC4;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTIONIMPL_TYPE;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_SETARITY;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_SETARITY_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_SETPROTOTYPE;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_SETPROTOTYPE_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_TYPE;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTOBJECT_INIT_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTOBJECT_TYPE;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import jdk.internal.org.objectweb.asm.Handle;

/**
 * This class generates constructor class for a @ClassInfo annotated class.
 *
 */
public class ConstructorGenerator extends ClassGenerator {
    private final ScriptClassInfo scriptClassInfo;
    private final String className;
    private final MemberInfo constructor;
    private final int memberCount;
    private final List<MemberInfo> specs;

    ConstructorGenerator(final ScriptClassInfo sci) {
        this.scriptClassInfo = sci;

        this.className = scriptClassInfo.getConstructorClassName();
        this.constructor = scriptClassInfo.getConstructor();
        this.memberCount = scriptClassInfo.getConstructorMemberCount();
        this.specs = scriptClassInfo.getSpecializedConstructors();
    }

    byte[] getClassBytes() {
        // new class extensing from ScriptObject
        final String superClass = (constructor != null)? SCRIPTFUNCTIONIMPL_TYPE : SCRIPTOBJECT_TYPE;
        cw.visit(V1_7, ACC_FINAL, className, null, superClass, null);
        if (memberCount > 0) {
            // add fields
            emitFields();
            // add <clinit>
            emitStaticInitializer();
        }
        // add <init>
        emitConstructor();

        if (constructor == null) {
            emitGetClassName(scriptClassInfo.getName());
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    // --Internals only below this point
    private void emitFields() {
        // Introduce "Function" type instance fields for each
        // constructor @Function in script class and introduce instance
        // fields for each constructor @Property in the script class.
        for (MemberInfo memInfo : scriptClassInfo.getMembers()) {
            if (memInfo.isConstructorFunction()) {
                addFunctionField(memInfo.getJavaName());
                memInfo = (MemberInfo)memInfo.clone();
                memInfo.setJavaDesc(OBJECT_DESC);
                memInfo.setJavaAccess(ACC_PUBLIC);
                addGetter(className, memInfo);
                addSetter(className, memInfo);
            } else if (memInfo.isConstructorProperty()) {
                if (memInfo.isStaticFinal()) {
                    addGetter(scriptClassInfo.getJavaName(), memInfo);
                } else {
                    addField(memInfo.getJavaName(), memInfo.getJavaDesc());
                    memInfo = (MemberInfo)memInfo.clone();
                    memInfo.setJavaAccess(ACC_PUBLIC);
                    addGetter(className, memInfo);
                    addSetter(className, memInfo);
                }
            }
        }

        addMapField();
    }

    private void emitStaticInitializer() {
        final MethodGenerator mi = makeStaticInitializer();
        emitStaticInitPrefix(mi, className, memberCount);

        for (final MemberInfo memInfo : scriptClassInfo.getMembers()) {
            if (memInfo.isConstructorFunction() || memInfo.isConstructorProperty()) {
                linkerAddGetterSetter(mi, className, memInfo);
            } else if (memInfo.isConstructorGetter()) {
                final MemberInfo setter = scriptClassInfo.findSetter(memInfo);
                linkerAddGetterSetter(mi, scriptClassInfo.getJavaName(), memInfo, setter);
            }
        }
        emitStaticInitSuffix(mi, className);
    }

    private void emitConstructor() {
        final MethodGenerator mi = makeConstructor();
        mi.visitCode();
        callSuper(mi);

        if (memberCount > 0) {
            // initialize Function type fields
            initFunctionFields(mi);
            // initialize data fields
            initDataFields(mi);
        }

        if (constructor != null) {
            final int arity = constructor.getArity();
            if (arity != MemberInfo.DEFAULT_ARITY) {
                mi.loadThis();
                mi.push(arity);
                mi.invokeVirtual(SCRIPTFUNCTION_TYPE, SCRIPTFUNCTION_SETARITY,
                        SCRIPTFUNCTION_SETARITY_DESC);
            }
        }
        mi.returnVoid();
        mi.computeMaxs();
        mi.visitEnd();
    }

    private void loadMap(final MethodGenerator mi) {
        if (memberCount > 0) {
            mi.getStatic(className, PROPERTYMAP_FIELD_NAME, PROPERTYMAP_DESC);
        }
    }

    private void callSuper(final MethodGenerator mi) {
        String superClass, superDesc;
        mi.loadThis();
        if (constructor == null) {
            // call ScriptObject.<init>
            superClass = SCRIPTOBJECT_TYPE;
            superDesc = (memberCount > 0) ? SCRIPTOBJECT_INIT_DESC : DEFAULT_INIT_DESC;
            loadMap(mi);
        } else {
            // call Function.<init>
            superClass = SCRIPTFUNCTIONIMPL_TYPE;
            superDesc = (memberCount > 0) ? SCRIPTFUNCTIONIMPL_INIT_DESC4 : SCRIPTFUNCTIONIMPL_INIT_DESC3;
            mi.loadLiteral(constructor.getName());
            mi.visitLdcInsn(new Handle(H_INVOKESTATIC, scriptClassInfo.getJavaName(), constructor.getJavaName(), constructor.getJavaDesc()));
            loadMap(mi);
            mi.memberInfoArray(scriptClassInfo.getJavaName(), specs); //pushes null if specs empty
        }

        mi.invokeSpecial(superClass, INIT, superDesc);
    }

    private void initFunctionFields(final MethodGenerator mi) {
        for (final MemberInfo memInfo : scriptClassInfo.getMembers()) {
            if (!memInfo.isConstructorFunction()) {
                continue;
            }
            mi.loadThis();
            newFunction(mi, scriptClassInfo.getJavaName(), memInfo, scriptClassInfo.findSpecializations(memInfo.getJavaName()));
            mi.putField(className, memInfo.getJavaName(), OBJECT_DESC);
        }
    }

    private void initDataFields(final MethodGenerator mi) {
         for (final MemberInfo memInfo : scriptClassInfo.getMembers()) {
            if (!memInfo.isConstructorProperty() || memInfo.isFinal()) {
                continue;
            }
            final Object value = memInfo.getValue();
            if (value != null) {
                mi.loadThis();
                mi.loadLiteral(value);
                mi.putField(className, memInfo.getJavaName(), memInfo.getJavaDesc());
            } else if (!memInfo.getInitClass().isEmpty()) {
                final String clazz = memInfo.getInitClass();
                mi.loadThis();
                mi.newObject(clazz);
                mi.dup();
                mi.invokeSpecial(clazz, INIT, DEFAULT_INIT_DESC);
                mi.putField(className, memInfo.getJavaName(), memInfo.getJavaDesc());
            }
        }

        if (constructor != null) {
            mi.loadThis();
            final String protoName = scriptClassInfo.getPrototypeClassName();
            mi.newObject(protoName);
            mi.dup();
            mi.invokeSpecial(protoName, INIT, DEFAULT_INIT_DESC);
            mi.dup();
            mi.loadThis();
            mi.invokeStatic(PROTOTYPEOBJECT_TYPE, PROTOTYPEOBJECT_SETCONSTRUCTOR,
                    PROTOTYPEOBJECT_SETCONSTRUCTOR_DESC);
            mi.invokeVirtual(SCRIPTFUNCTION_TYPE, SCRIPTFUNCTION_SETPROTOTYPE, SCRIPTFUNCTION_SETPROTOTYPE_DESC);
        }
    }

    /**
     * Entry point for ConstructorGenerator run separately as an application. Will display
     * usage. Takes one argument, a class name.
     * @param args args vector
     * @throws IOException if class can't be read
     */
    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: " + ConstructorGenerator.class.getName() + " <class>");
            System.exit(1);
        }

        final String className = args[0].replace('.', '/');
        final ScriptClassInfo sci = getScriptClassInfo(className + ".class");
        if (sci == null) {
            System.err.println("No @ScriptClass in " + className);
            System.exit(2);
            throw new IOException(); // get rid of warning for sci.verify() below - may be null
        }

        try {
            sci.verify();
        } catch (final Exception e) {
            System.err.println(e.getMessage());
            System.exit(3);
        }
        final ConstructorGenerator gen = new ConstructorGenerator(sci);
        try (FileOutputStream fos = new FileOutputStream(className + CONSTRUCTOR_SUFFIX + ".class")) {
            fos.write(gen.getClassBytes());
        }
    }
}
