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
package jdk.internal.org.objectweb.asm.util;

import java.io.PrintWriter;

import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.ModuleVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.TypePath;

/**
 * A {@link ClassVisitor} that prints the classes it visits with a
 * {@link Printer}. This class visitor can be used in the middle of a class
 * visitor chain to trace the class that is visited at a given point in this
 * chain. This may be useful for debugging purposes.
 * <p>
 * The trace printed when visiting the <tt>Hello</tt> class is the following:
 * <p>
 * <blockquote>
 *
 * <pre>
 * // class version 49.0 (49) // access flags 0x21 public class Hello {
 *
 * // compiled from: Hello.java
 *
 * // access flags 0x1 public &lt;init&gt; ()V ALOAD 0 INVOKESPECIAL
 * java/lang/Object &lt;init&gt; ()V RETURN MAXSTACK = 1 MAXLOCALS = 1
 *
 * // access flags 0x9 public static main ([Ljava/lang/String;)V GETSTATIC
 * java/lang/System out Ljava/io/PrintStream; LDC &quot;hello&quot;
 * INVOKEVIRTUAL java/io/PrintStream println (Ljava/lang/String;)V RETURN
 * MAXSTACK = 2 MAXLOCALS = 1 }
 * </pre>
 *
 * </blockquote> where <tt>Hello</tt> is defined by:
 * <p>
 * <blockquote>
 *
 * <pre>
 * public class Hello {
 *
 *     public static void main(String[] args) {
 *         System.out.println(&quot;hello&quot;);
 *     }
 * }
 * </pre>
 *
 * </blockquote>
 *
 * @author Eric Bruneton
 * @author Eugene Kuleshov
 */
public final class TraceClassVisitor extends ClassVisitor {

    /**
     * The print writer to be used to print the class. May be null.
     */
    private final PrintWriter pw;

    /**
     * The object that actually converts visit events into text.
     */
    public final Printer p;

    /**
     * Constructs a new {@link TraceClassVisitor}.
     *
     * @param pw
     *            the print writer to be used to print the class.
     */
    public TraceClassVisitor(final PrintWriter pw) {
        this(null, pw);
    }

    /**
     * Constructs a new {@link TraceClassVisitor}.
     *
     * @param cv
     *            the {@link ClassVisitor} to which this visitor delegates
     *            calls. May be <tt>null</tt>.
     * @param pw
     *            the print writer to be used to print the class.
     */
    public TraceClassVisitor(final ClassVisitor cv, final PrintWriter pw) {
        this(cv, new Textifier(), pw);
    }

    /**
     * Constructs a new {@link TraceClassVisitor}.
     *
     * @param cv
     *            the {@link ClassVisitor} to which this visitor delegates
     *            calls. May be <tt>null</tt>.
     * @param p
     *            the object that actually converts visit events into text.
     * @param pw
     *            the print writer to be used to print the class. May be null if
     *            you simply want to use the result via
     *            {@link Printer#getText()}, instead of printing it.
     */
    public TraceClassVisitor(final ClassVisitor cv, final Printer p,
            final PrintWriter pw) {
        super(Opcodes.ASM6, cv);
        this.pw = pw;
        this.p = p;
    }

    @Override
    public void visit(final int version, final int access, final String name,
            final String signature, final String superName,
            final String[] interfaces) {
        p.visit(version, access, name, signature, superName, interfaces);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitSource(final String file, final String debug) {
        p.visitSource(file, debug);
        super.visitSource(file, debug);
    }

    @Override
    public ModuleVisitor visitModule(String name, int flags,
            String version) {
        Printer p = this.p.visitModule(name, flags, version);
        ModuleVisitor mv = super.visitModule(name, flags, version);
        return new TraceModuleVisitor(mv, p);
    }

    @Override
    public void visitOuterClass(final String owner, final String name,
            final String desc) {
        p.visitOuterClass(owner, name, desc);
        super.visitOuterClass(owner, name, desc);
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc,
            final boolean visible) {
        Printer p = this.p.visitClassAnnotation(desc, visible);
        AnnotationVisitor av = cv == null ? null : cv.visitAnnotation(desc,
                visible);
        return new TraceAnnotationVisitor(av, p);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef,
            TypePath typePath, String desc, boolean visible) {
        Printer p = this.p.visitClassTypeAnnotation(typeRef, typePath, desc,
                visible);
        AnnotationVisitor av = cv == null ? null : cv.visitTypeAnnotation(
                typeRef, typePath, desc, visible);
        return new TraceAnnotationVisitor(av, p);
    }

    @Override
    public void visitAttribute(final Attribute attr) {
        p.visitClassAttribute(attr);
        super.visitAttribute(attr);
    }

    @Override
    public void visitInnerClass(final String name, final String outerName,
            final String innerName, final int access) {
        p.visitInnerClass(name, outerName, innerName, access);
        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public FieldVisitor visitField(final int access, final String name,
            final String desc, final String signature, final Object value) {
        Printer p = this.p.visitField(access, name, desc, signature, value);
        FieldVisitor fv = cv == null ? null : cv.visitField(access, name, desc,
                signature, value);
        return new TraceFieldVisitor(fv, p);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name,
            final String desc, final String signature, final String[] exceptions) {
        Printer p = this.p.visitMethod(access, name, desc, signature,
                exceptions);
        MethodVisitor mv = cv == null ? null : cv.visitMethod(access, name,
                desc, signature, exceptions);
        return new TraceMethodVisitor(mv, p);
    }

    @Override
    public void visitEnd() {
        p.visitClassEnd();
        if (pw != null) {
            p.print(pw);
            pw.flush();
        }
        super.visitEnd();
    }
}
