/*
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
 * Copyright (c) 2000-2011 INRIA, France Telecom
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

package jdk.internal.org.objectweb.asm;

/**
 * A visitor to visit a Java class. The methods of this class must be called in the following order:
 * {@code visit} [ {@code visitSource} ] [ {@code visitModule} ][ {@code visitNestHost} ][ {@code
 * visitOuterClass} ] ( {@code visitAnnotation} | {@code visitTypeAnnotation} | {@code
 * visitAttribute} )* ( {@code visitNestMember} | [ {@code * visitPermittedSubclass} ] | {@code
 * visitInnerClass} | {@code visitRecordComponent} | {@code visitField} | {@code visitMethod} )*
 * {@code visitEnd}.
 *
 * @author Eric Bruneton
 */
public abstract class ClassVisitor {

    /**
      * The ASM API version implemented by this visitor. The value of this field must be one of the
      * {@code ASM}<i>x</i> values in {@link Opcodes}.
      */
    protected final int api;

    /** The class visitor to which this visitor must delegate method calls. May be {@literal null}. */
    protected ClassVisitor cv;

    /**
      * Constructs a new {@link ClassVisitor}.
      *
      * @param api the ASM API version implemented by this visitor. Must be one of the {@code
      *     ASM}<i>x</i> values in {@link Opcodes}.
      */
    protected ClassVisitor(final int api) {
        this(api, null);
    }

    /**
      * Constructs a new {@link ClassVisitor}.
      *
      * @param api the ASM API version implemented by this visitor. Must be one of the {@code
      *     ASM}<i>x</i> values in {@link Opcodes}.
      * @param classVisitor the class visitor to which this visitor must delegate method calls. May be
      *     null.
      */
    protected ClassVisitor(final int api, final ClassVisitor classVisitor) {
        if (api != Opcodes.ASM9
                && api != Opcodes.ASM8
                && api != Opcodes.ASM7
                && api != Opcodes.ASM6
                && api != Opcodes.ASM5
                && api != Opcodes.ASM4) {
            throw new IllegalArgumentException("Unsupported api " + api);
        }
        this.api = api;
        this.cv = classVisitor;
    }

    /**
      * The class visitor to which this visitor must delegate method calls. May be {@literal null}.
      *
      * @return the class visitor to which this visitor must delegate method calls, or {@literal null}.
      */
    public ClassVisitor getDelegate() {
        return cv;
    }

    /**
      * Visits the header of the class.
      *
      * @param version the class version. The minor version is stored in the 16 most significant bits,
      *     and the major version in the 16 least significant bits.
      * @param access the class's access flags (see {@link Opcodes}). This parameter also indicates if
      *     the class is deprecated {@link Opcodes#ACC_DEPRECATED} or a record {@link
      *     Opcodes#ACC_RECORD}.
      * @param name the internal name of the class (see {@link Type#getInternalName()}).
      * @param signature the signature of this class. May be {@literal null} if the class is not a
      *     generic one, and does not extend or implement generic classes or interfaces.
      * @param superName the internal of name of the super class (see {@link Type#getInternalName()}).
      *     For interfaces, the super class is {@link Object}. May be {@literal null}, but only for the
      *     {@link Object} class.
      * @param interfaces the internal names of the class's interfaces (see {@link
      *     Type#getInternalName()}). May be {@literal null}.
      */
    public void visit(
            final int version,
            final int access,
            final String name,
            final String signature,
            final String superName,
            final String[] interfaces) {
        if (api < Opcodes.ASM8 && (access & Opcodes.ACC_RECORD) != 0) {
            throw new UnsupportedOperationException("Records requires ASM8");
        }
        if (cv != null) {
            cv.visit(version, access, name, signature, superName, interfaces);
        }
    }

    /**
      * Visits the source of the class.
      *
      * @param source the name of the source file from which the class was compiled. May be {@literal
      *     null}.
      * @param debug additional debug information to compute the correspondence between source and
      *     compiled elements of the class. May be {@literal null}.
      */
    public void visitSource(final String source, final String debug) {
        if (cv != null) {
            cv.visitSource(source, debug);
        }
    }

    /**
      * Visit the module corresponding to the class.
      *
      * @param name the fully qualified name (using dots) of the module.
      * @param access the module access flags, among {@code ACC_OPEN}, {@code ACC_SYNTHETIC} and {@code
      *     ACC_MANDATED}.
      * @param version the module version, or {@literal null}.
      * @return a visitor to visit the module values, or {@literal null} if this visitor is not
      *     interested in visiting this module.
      */
    public ModuleVisitor visitModule(final String name, final int access, final String version) {
        if (api < Opcodes.ASM6) {
            throw new UnsupportedOperationException("Module requires ASM6");
        }
        if (cv != null) {
            return cv.visitModule(name, access, version);
        }
        return null;
    }

    /**
      * Visits the nest host class of the class. A nest is a set of classes of the same package that
      * share access to their private members. One of these classes, called the host, lists the other
      * members of the nest, which in turn should link to the host of their nest. This method must be
      * called only once and only if the visited class is a non-host member of a nest. A class is
      * implicitly its own nest, so it's invalid to call this method with the visited class name as
      * argument.
      *
      * @param nestHost the internal name of the host class of the nest (see {@link
      *     Type#getInternalName()}).
      */
    public void visitNestHost(final String nestHost) {
        if (api < Opcodes.ASM7) {
            throw new UnsupportedOperationException("NestHost requires ASM7");
        }
        if (cv != null) {
            cv.visitNestHost(nestHost);
        }
    }

    /**
      * Visits the enclosing class of the class. This method must be called only if this class is a
      * local or anonymous class. See the JVMS 4.7.7 section for more details.
      *
      * @param owner internal name of the enclosing class of the class (see {@link
      *     Type#getInternalName()}).
      * @param name the name of the method that contains the class, or {@literal null} if the class is
      *     not enclosed in a method or constructor of its enclosing class (e.g. if it is enclosed in
      *     an instance initializer, static initializer, instance variable initializer, or class
      *     variable initializer).
      * @param descriptor the descriptor of the method that contains the class, or {@literal null} if
      *     the class is not enclosed in a method or constructor of its enclosing class (e.g. if it is
      *     enclosed in an instance initializer, static initializer, instance variable initializer, or
      *     class variable initializer).
      */
    public void visitOuterClass(final String owner, final String name, final String descriptor) {
        if (cv != null) {
            cv.visitOuterClass(owner, name, descriptor);
        }
    }

    /**
      * Visits an annotation of the class.
      *
      * @param descriptor the class descriptor of the annotation class.
      * @param visible {@literal true} if the annotation is visible at runtime.
      * @return a visitor to visit the annotation values, or {@literal null} if this visitor is not
      *     interested in visiting this annotation.
      */
    public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
        if (cv != null) {
            return cv.visitAnnotation(descriptor, visible);
        }
        return null;
    }

    /**
      * Visits an annotation on a type in the class signature.
      *
      * @param typeRef a reference to the annotated type. The sort of this type reference must be
      *     {@link TypeReference#CLASS_TYPE_PARAMETER}, {@link
      *     TypeReference#CLASS_TYPE_PARAMETER_BOUND} or {@link TypeReference#CLASS_EXTENDS}. See
      *     {@link TypeReference}.
      * @param typePath the path to the annotated type argument, wildcard bound, array element type, or
      *     static inner type within 'typeRef'. May be {@literal null} if the annotation targets
      *     'typeRef' as a whole.
      * @param descriptor the class descriptor of the annotation class.
      * @param visible {@literal true} if the annotation is visible at runtime.
      * @return a visitor to visit the annotation values, or {@literal null} if this visitor is not
      *     interested in visiting this annotation.
      */
    public AnnotationVisitor visitTypeAnnotation(
            final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
        if (api < Opcodes.ASM5) {
            throw new UnsupportedOperationException("TypeAnnotation requires ASM5");
        }
        if (cv != null) {
            return cv.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
        }
        return null;
    }

    /**
      * Visits a non standard attribute of the class.
      *
      * @param attribute an attribute.
      */
    public void visitAttribute(final Attribute attribute) {
        if (cv != null) {
            cv.visitAttribute(attribute);
        }
    }

    /**
      * Visits a member of the nest. A nest is a set of classes of the same package that share access
      * to their private members. One of these classes, called the host, lists the other members of the
      * nest, which in turn should link to the host of their nest. This method must be called only if
      * the visited class is the host of a nest. A nest host is implicitly a member of its own nest, so
      * it's invalid to call this method with the visited class name as argument.
      *
      * @param nestMember the internal name of a nest member (see {@link Type#getInternalName()}).
      */
    public void visitNestMember(final String nestMember) {
        if (api < Opcodes.ASM7) {
            throw new UnsupportedOperationException("NestMember requires ASM7");
        }
        if (cv != null) {
            cv.visitNestMember(nestMember);
        }
    }

    /**
      * Visits a permitted subclasses. A permitted subclass is one of the allowed subclasses of the
      * current class.
      *
      * @param permittedSubclass the internal name of a permitted subclass (see {@link
      *     Type#getInternalName()}).
      */
    public void visitPermittedSubclass(final String permittedSubclass) {
        if (api < Opcodes.ASM9) {
            throw new UnsupportedOperationException("PermittedSubclasses requires ASM9");
        }
        if (cv != null) {
            cv.visitPermittedSubclass(permittedSubclass);
        }
    }

    /**
      * Visits information about an inner class. This inner class is not necessarily a member of the
      * class being visited. More precisely, every class or interface C which is referenced by this
      * class and which is not a package member must be visited with this method. This class must
      * reference its nested class or interface members, and its enclosing class, if any. See the JVMS
      * 4.7.6 section for more details.
      *
      * @param name the internal name of C (see {@link Type#getInternalName()}).
      * @param outerName the internal name of the class or interface C is a member of (see {@link
      *     Type#getInternalName()}). Must be {@literal null} if C is not the member of a class or
      *     interface (e.g. for local or anonymous classes).
      * @param innerName the (simple) name of C. Must be {@literal null} for anonymous inner classes.
      * @param access the access flags of C originally declared in the source code from which this
      *     class was compiled.
      */
    public void visitInnerClass(
            final String name, final String outerName, final String innerName, final int access) {
        if (cv != null) {
            cv.visitInnerClass(name, outerName, innerName, access);
        }
    }

    /**
      * Visits a record component of the class.
      *
      * @param name the record component name.
      * @param descriptor the record component descriptor (see {@link Type}).
      * @param signature the record component signature. May be {@literal null} if the record component
      *     type does not use generic types.
      * @return a visitor to visit this record component annotations and attributes, or {@literal null}
      *     if this class visitor is not interested in visiting these annotations and attributes.
      */
    public RecordComponentVisitor visitRecordComponent(
            final String name, final String descriptor, final String signature) {
        if (api < Opcodes.ASM8) {
            throw new UnsupportedOperationException("Record requires ASM8");
        }
        if (cv != null) {
            return cv.visitRecordComponent(name, descriptor, signature);
        }
        return null;
    }

    /**
      * Visits a field of the class.
      *
      * @param access the field's access flags (see {@link Opcodes}). This parameter also indicates if
      *     the field is synthetic and/or deprecated.
      * @param name the field's name.
      * @param descriptor the field's descriptor (see {@link Type}).
      * @param signature the field's signature. May be {@literal null} if the field's type does not use
      *     generic types.
      * @param value the field's initial value. This parameter, which may be {@literal null} if the
      *     field does not have an initial value, must be an {@link Integer}, a {@link Float}, a {@link
      *     Long}, a {@link Double} or a {@link String} (for {@code int}, {@code float}, {@code long}
      *     or {@code String} fields respectively). <i>This parameter is only used for static
      *     fields</i>. Its value is ignored for non static fields, which must be initialized through
      *     bytecode instructions in constructors or methods.
      * @return a visitor to visit field annotations and attributes, or {@literal null} if this class
      *     visitor is not interested in visiting these annotations and attributes.
      */
    public FieldVisitor visitField(
            final int access,
            final String name,
            final String descriptor,
            final String signature,
            final Object value) {
        if (cv != null) {
            return cv.visitField(access, name, descriptor, signature, value);
        }
        return null;
    }

    /**
      * Visits a method of the class. This method <i>must</i> return a new {@link MethodVisitor}
      * instance (or {@literal null}) each time it is called, i.e., it should not return a previously
      * returned visitor.
      *
      * @param access the method's access flags (see {@link Opcodes}). This parameter also indicates if
      *     the method is synthetic and/or deprecated.
      * @param name the method's name.
      * @param descriptor the method's descriptor (see {@link Type}).
      * @param signature the method's signature. May be {@literal null} if the method parameters,
      *     return type and exceptions do not use generic types.
      * @param exceptions the internal names of the method's exception classes (see {@link
      *     Type#getInternalName()}). May be {@literal null}.
      * @return an object to visit the byte code of the method, or {@literal null} if this class
      *     visitor is not interested in visiting the code of this method.
      */
    public MethodVisitor visitMethod(
            final int access,
            final String name,
            final String descriptor,
            final String signature,
            final String[] exceptions) {
        if (cv != null) {
            return cv.visitMethod(access, name, descriptor, signature, exceptions);
        }
        return null;
    }

    /**
      * Visits the end of the class. This method, which is the last one to be called, is used to inform
      * the visitor that all the fields and methods of the class have been visited.
      */
    public void visitEnd() {
        if (cv != null) {
            cv.visitEnd();
        }
    }
}
