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

import static jdk.nashorn.internal.tools.nasgen.ScriptClassInfo.SCRIPT_CLASS_ANNO_DESC;
import static jdk.nashorn.internal.tools.nasgen.ScriptClassInfo.WHERE_ENUM_DESC;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.tools.nasgen.MemberInfo.Kind;

/**
 * This class collects all @ScriptClass and other annotation information from a
 * compiled .class file. Enforces that @Function/@Getter/@Setter/@Constructor
 * methods are declared to be 'static'.
 */
public class ScriptClassInfoCollector extends ClassVisitor {
    private String scriptClassName;
    private List<MemberInfo> scriptMembers;
    private String javaClassName;

    ScriptClassInfoCollector(final ClassVisitor visitor) {
        super(Opcodes.ASM4, visitor);
    }

    ScriptClassInfoCollector() {
        this(new NullVisitor());
    }

    private void addScriptMember(final MemberInfo memInfo) {
        if (scriptMembers == null) {
            scriptMembers = new ArrayList<>();
        }
        scriptMembers.add(memInfo);
    }

    @Override
    public void visit(final int version, final int access, final String name, final String signature,
           final String superName, final String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        javaClassName = name;
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
        final AnnotationVisitor delegateAV = super.visitAnnotation(desc, visible);
        if (SCRIPT_CLASS_ANNO_DESC.equals(desc)) {
            return new AnnotationVisitor(Opcodes.ASM4, delegateAV) {
                @Override
                public void visit(final String name, final Object value) {
                    if ("value".equals(name)) {
                        scriptClassName = (String) value;
                    }
                    super.visit(name, value);
                }
            };
        }

        return delegateAV;
    }

    @Override
    public FieldVisitor visitField(final int fieldAccess, final String fieldName, final String fieldDesc, final String signature, final Object value) {
        final FieldVisitor delegateFV = super.visitField(fieldAccess, fieldName, fieldDesc, signature, value);

        return new FieldVisitor(Opcodes.ASM4, delegateFV) {
            @Override
            public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                final AnnotationVisitor delegateAV = super.visitAnnotation(descriptor, visible);

                if (ScriptClassInfo.PROPERTY_ANNO_DESC.equals(descriptor)) {
                    final MemberInfo memInfo = new MemberInfo();

                    memInfo.setKind(Kind.PROPERTY);
                    memInfo.setJavaName(fieldName);
                    memInfo.setJavaDesc(fieldDesc);
                    memInfo.setJavaAccess(fieldAccess);

                    if ((fieldAccess & Opcodes.ACC_STATIC) != 0) {
                        memInfo.setValue(value);
                    }

                    addScriptMember(memInfo);

                    return new AnnotationVisitor(Opcodes.ASM4, delegateAV) {
                        // These could be "null" if values are not suppiled,
                        // in which case we have to use the default values.
                        private String  name;
                        private Integer attributes;
                        private String  clazz = "";
                        private Where   where;

                        @Override
                        public void visit(final String annotationName, final Object annotationValue) {
                            switch (annotationName) {
                            case "name":
                                this.name = (String) annotationValue;
                                break;
                            case "attributes":
                                this.attributes = (Integer) annotationValue;
                                break;
                            case "clazz":
                                this.clazz = (annotationValue == null) ? "" : annotationValue.toString();
                                break;
                            default:
                                break;
                            }
                            super.visit(annotationName, annotationValue);
                        }

                        @Override
                        public void visitEnum(final String enumName, final String desc, final String enumValue) {
                            if ("where".equals(enumName) && WHERE_ENUM_DESC.equals(desc)) {
                                this.where = Where.valueOf(enumValue);
                            }
                            super.visitEnum(enumName, desc, enumValue);
                        }

                        @Override
                        public void visitEnd() {
                            super.visitEnd();
                            memInfo.setName(name == null ? fieldName : name);
                            memInfo.setAttributes(attributes == null
                                    ? MemberInfo.DEFAULT_ATTRIBUTES : attributes);
                            clazz = clazz.replace('.', '/');
                            memInfo.setInitClass(clazz);
                            memInfo.setWhere(where == null? Where.INSTANCE : where);
                        }
                    };
                }

                return delegateAV;
            }
        };
    }

    private void error(final String javaName, final String javaDesc, final String msg) {
        throw new RuntimeException(scriptClassName + "." + javaName + javaDesc + " : " + msg);
    }

    @Override
    public MethodVisitor visitMethod(final int methodAccess, final String methodName,
            final String methodDesc, final String signature, final String[] exceptions) {

        final MethodVisitor delegateMV = super.visitMethod(methodAccess, methodName, methodDesc,
                signature, exceptions);

        return new MethodVisitor(Opcodes.ASM4, delegateMV) {

            @Override
            public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                final AnnotationVisitor delegateAV = super.visitAnnotation(descriptor, visible);
                final Kind annoKind = ScriptClassInfo.annotations.get(descriptor);

                if (annoKind != null) {
                    if ((methodAccess & Opcodes.ACC_STATIC) == 0) {
                        error(methodName, methodDesc, "nasgen method annotations cannot be on instance methods");
                    }

                    final MemberInfo memInfo = new MemberInfo();

                    memInfo.setKind(annoKind);
                    memInfo.setJavaName(methodName);
                    memInfo.setJavaDesc(methodDesc);
                    memInfo.setJavaAccess(methodAccess);

                    addScriptMember(memInfo);

                    return new AnnotationVisitor(Opcodes.ASM4, delegateAV) {
                        // These could be "null" if values are not suppiled,
                        // in which case we have to use the default values.
                        private String  name;
                        private Integer attributes;
                        private Integer arity;
                        private Where   where;

                        @Override
                        public void visit(final String annotationName, final Object annotationValue) {
                            switch (annotationName) {
                            case "name":
                                this.name = (String)annotationValue;
                                break;
                            case "attributes":
                                this.attributes = (Integer)annotationValue;
                                break;
                            case "arity":
                                this.arity = (Integer)annotationValue;
                                break;
                            default:
                                break;
                            }

                            super.visit(annotationName, annotationValue);
                        }

                        @Override
                        public void visitEnum(final String enumName, final String desc, final String enumValue) {
                            if ("where".equals(enumName) && WHERE_ENUM_DESC.equals(desc)) {
                                this.where = Where.valueOf(enumValue);
                            }
                            super.visitEnum(enumName, desc, enumValue);
                        }

                        @Override
                        public void visitEnd() {
                            super.visitEnd();

                            if (memInfo.getKind() == Kind.CONSTRUCTOR) {
                                memInfo.setName(name == null ? scriptClassName : name);
                            } else {
                                memInfo.setName(name == null ? methodName : name);
                            }
                            memInfo.setAttributes(attributes == null ? MemberInfo.DEFAULT_ATTRIBUTES : attributes);

                            memInfo.setArity((arity == null)? MemberInfo.DEFAULT_ARITY : arity);
                            if (where == null) {
                                // by default @Getter/@Setter belongs to INSTANCE
                                // @Function belong to PROTOTYPE.
                                switch (memInfo.getKind()) {
                                    case GETTER:
                                    case SETTER:
                                        where = Where.INSTANCE;
                                        break;
                                    case SPECIALIZED_CONSTRUCTOR:
                                    case CONSTRUCTOR:
                                        where = Where.CONSTRUCTOR;
                                        break;
                                    case FUNCTION:
                                        where = Where.PROTOTYPE;
                                        break;
                                    case SPECIALIZED_FUNCTION:
                                        //TODO is this correct
                                    default:
                                        break;
                                }
                            }
                            memInfo.setWhere(where);
                        }
                    };
                }

                return delegateAV;
            }
        };
    }

    ScriptClassInfo getScriptClassInfo() {
        ScriptClassInfo sci = null;
        if (scriptClassName != null) {
            sci = new ScriptClassInfo();
            sci.setName(scriptClassName);
            if (scriptMembers == null) {
                scriptMembers = Collections.emptyList();
            }
            sci.setMembers(scriptMembers);
            sci.setJavaName(javaClassName);
        }
        return sci;
    }

    /**
     * External entry point for ScriptClassInfoCollector if invoked from the command line
     * @param args argument vector, args contains a class for which to collect info
     * @throws IOException if there were problems parsing args or class
     */
    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: " + ScriptClassInfoCollector.class.getName() + " <class>");
            System.exit(1);
        }

        args[0] = args[0].replace('.', '/');
        final ScriptClassInfoCollector scic = new ScriptClassInfoCollector();
        try (final BufferedInputStream bis = new BufferedInputStream(new FileInputStream(args[0] + ".class"))) {
            final ClassReader reader = new ClassReader(bis);
            reader.accept(scic, 0);
        }
        final ScriptClassInfo sci = scic.getScriptClassInfo();
        final PrintStream out = System.out;
        if (sci != null) {
            out.println("script class: " + sci.getName());
            out.println("===================================");
            for (final MemberInfo memInfo : sci.getMembers()) {
                out.println("kind : " + memInfo.getKind());
                out.println("name : " + memInfo.getName());
                out.println("attributes: " + memInfo.getAttributes());
                out.println("javaName: " + memInfo.getJavaName());
                out.println("javaDesc: " + memInfo.getJavaDesc());
                out.println("where: " + memInfo.getWhere());
                out.println("=====================================");
            }
        } else {
            out.println(args[0] + " is not a @ScriptClass");
        }
    }
}
