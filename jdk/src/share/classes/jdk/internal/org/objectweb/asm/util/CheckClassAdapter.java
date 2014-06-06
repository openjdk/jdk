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

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.TypePath;
import jdk.internal.org.objectweb.asm.TypeReference;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.tree.analysis.Analyzer;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;
import jdk.internal.org.objectweb.asm.tree.analysis.Frame;
import jdk.internal.org.objectweb.asm.tree.analysis.SimpleVerifier;

/**
 * A {@link ClassVisitor} that checks that its methods are properly used. More
 * precisely this class adapter checks each method call individually, based
 * <i>only</i> on its arguments, but does <i>not</i> check the <i>sequence</i>
 * of method calls. For example, the invalid sequence
 * <tt>visitField(ACC_PUBLIC, "i", "I", null)</tt> <tt>visitField(ACC_PUBLIC,
 * "i", "D", null)</tt> will <i>not</i> be detected by this class adapter.
 *
 * <p>
 * <code>CheckClassAdapter</code> can be also used to verify bytecode
 * transformations in order to make sure transformed bytecode is sane. For
 * example:
 *
 * <pre>
 *   InputStream is = ...; // get bytes for the source class
 *   ClassReader cr = new ClassReader(is);
 *   ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
 *   ClassVisitor cv = new <b>MyClassAdapter</b>(new CheckClassAdapter(cw));
 *   cr.accept(cv, 0);
 *
 *   StringWriter sw = new StringWriter();
 *   PrintWriter pw = new PrintWriter(sw);
 *   CheckClassAdapter.verify(new ClassReader(cw.toByteArray()), false, pw);
 *   assertTrue(sw.toString(), sw.toString().length()==0);
 * </pre>
 *
 * Above code runs transformed bytecode trough the
 * <code>CheckClassAdapter</code>. It won't be exactly the same verification as
 * JVM does, but it run data flow analysis for the code of each method and
 * checks that expectations are met for each method instruction.
 *
 * <p>
 * If method bytecode has errors, assertion text will show the erroneous
 * instruction number and dump of the failed method with information about
 * locals and stack slot for each instruction. For example (format is -
 * insnNumber locals : stack):
 *
 * <pre>
 * jdk.internal.org.objectweb.asm.tree.analysis.AnalyzerException: Error at instruction 71: Expected I, but found .
 *   at jdk.internal.org.objectweb.asm.tree.analysis.Analyzer.analyze(Analyzer.java:289)
 *   at jdk.internal.org.objectweb.asm.util.CheckClassAdapter.verify(CheckClassAdapter.java:135)
 * ...
 * remove()V
 * 00000 LinkedBlockingQueue$Itr . . . . . . . .  :
 *   ICONST_0
 * 00001 LinkedBlockingQueue$Itr . . . . . . . .  : I
 *   ISTORE 2
 * 00001 LinkedBlockingQueue$Itr <b>.</b> I . . . . . .  :
 * ...
 *
 * 00071 LinkedBlockingQueue$Itr <b>.</b> I . . . . . .  :
 *   ILOAD 1
 * 00072 <b>?</b>
 *   INVOKESPECIAL java/lang/Integer.&lt;init&gt; (I)V
 * ...
 * </pre>
 *
 * In the above output you can see that variable 1 loaded by
 * <code>ILOAD 1</code> instruction at position <code>00071</code> is not
 * initialized. You can also see that at the beginning of the method (code
 * inserted by the transformation) variable 2 is initialized.
 *
 * <p>
 * Note that when used like that, <code>CheckClassAdapter.verify()</code> can
 * trigger additional class loading, because it is using
 * <code>SimpleVerifier</code>.
 *
 * @author Eric Bruneton
 */
public class CheckClassAdapter extends ClassVisitor {

    /**
     * The class version number.
     */
    private int version;

    /**
     * <tt>true</tt> if the visit method has been called.
     */
    private boolean start;

    /**
     * <tt>true</tt> if the visitSource method has been called.
     */
    private boolean source;

    /**
     * <tt>true</tt> if the visitOuterClass method has been called.
     */
    private boolean outer;

    /**
     * <tt>true</tt> if the visitEnd method has been called.
     */
    private boolean end;

    /**
     * The already visited labels. This map associate Integer values to Label
     * keys.
     */
    private Map<Label, Integer> labels;

    /**
     * <tt>true</tt> if the method code must be checked with a BasicVerifier.
     */
    private boolean checkDataFlow;

    /**
     * Checks a given class.
     * <p>
     * Usage: CheckClassAdapter &lt;binary class name or class file name&gt;
     *
     * @param args
     *            the command line arguments.
     *
     * @throws Exception
     *             if the class cannot be found, or if an IO exception occurs.
     */
    public static void main(final String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Verifies the given class.");
            System.err.println("Usage: CheckClassAdapter "
                    + "<fully qualified class name or class file name>");
            return;
        }
        ClassReader cr;
        if (args[0].endsWith(".class")) {
            cr = new ClassReader(new FileInputStream(args[0]));
        } else {
            cr = new ClassReader(args[0]);
        }

        verify(cr, false, new PrintWriter(System.err));
    }

    /**
     * Checks a given class.
     *
     * @param cr
     *            a <code>ClassReader</code> that contains bytecode for the
     *            analysis.
     * @param loader
     *            a <code>ClassLoader</code> which will be used to load
     *            referenced classes. This is useful if you are verifiying
     *            multiple interdependent classes.
     * @param dump
     *            true if bytecode should be printed out not only when errors
     *            are found.
     * @param pw
     *            write where results going to be printed
     */
    public static void verify(final ClassReader cr, final ClassLoader loader,
            final boolean dump, final PrintWriter pw) {
        ClassNode cn = new ClassNode();
        cr.accept(new CheckClassAdapter(cn, false), ClassReader.SKIP_DEBUG);

        Type syperType = cn.superName == null ? null : Type
                .getObjectType(cn.superName);
        List<MethodNode> methods = cn.methods;

        List<Type> interfaces = new ArrayList<Type>();
        for (Iterator<String> i = cn.interfaces.iterator(); i.hasNext();) {
            interfaces.add(Type.getObjectType(i.next()));
        }

        for (int i = 0; i < methods.size(); ++i) {
            MethodNode method = methods.get(i);
            SimpleVerifier verifier = new SimpleVerifier(
                    Type.getObjectType(cn.name), syperType, interfaces,
                    (cn.access & Opcodes.ACC_INTERFACE) != 0);
            Analyzer<BasicValue> a = new Analyzer<BasicValue>(verifier);
            if (loader != null) {
                verifier.setClassLoader(loader);
            }
            try {
                a.analyze(cn.name, method);
                if (!dump) {
                    continue;
                }
            } catch (Exception e) {
                e.printStackTrace(pw);
            }
            printAnalyzerResult(method, a, pw);
        }
        pw.flush();
    }

    /**
     * Checks a given class
     *
     * @param cr
     *            a <code>ClassReader</code> that contains bytecode for the
     *            analysis.
     * @param dump
     *            true if bytecode should be printed out not only when errors
     *            are found.
     * @param pw
     *            write where results going to be printed
     */
    public static void verify(final ClassReader cr, final boolean dump,
            final PrintWriter pw) {
        verify(cr, null, dump, pw);
    }

    static void printAnalyzerResult(MethodNode method, Analyzer<BasicValue> a,
            final PrintWriter pw) {
        Frame<BasicValue>[] frames = a.getFrames();
        Textifier t = new Textifier();
        TraceMethodVisitor mv = new TraceMethodVisitor(t);

        pw.println(method.name + method.desc);
        for (int j = 0; j < method.instructions.size(); ++j) {
            method.instructions.get(j).accept(mv);

            StringBuilder sb = new StringBuilder();
            Frame<BasicValue> f = frames[j];
            if (f == null) {
                sb.append('?');
            } else {
                for (int k = 0; k < f.getLocals(); ++k) {
                    sb.append(getShortName(f.getLocal(k).toString()))
                            .append(' ');
                }
                sb.append(" : ");
                for (int k = 0; k < f.getStackSize(); ++k) {
                    sb.append(getShortName(f.getStack(k).toString()))
                            .append(' ');
                }
            }
            while (sb.length() < method.maxStack + method.maxLocals + 1) {
                sb.append(' ');
            }
            pw.print(Integer.toString(j + 100000).substring(1));
            pw.print(" " + sb + " : " + t.text.get(t.text.size() - 1));
        }
        for (int j = 0; j < method.tryCatchBlocks.size(); ++j) {
            method.tryCatchBlocks.get(j).accept(mv);
            pw.print(" " + t.text.get(t.text.size() - 1));
        }
        pw.println();
    }

    private static String getShortName(final String name) {
        int n = name.lastIndexOf('/');
        int k = name.length();
        if (name.charAt(k - 1) == ';') {
            k--;
        }
        return n == -1 ? name : name.substring(n + 1, k);
    }

    /**
     * Constructs a new {@link CheckClassAdapter}. <i>Subclasses must not use
     * this constructor</i>. Instead, they must use the
     * {@link #CheckClassAdapter(int, ClassVisitor, boolean)} version.
     *
     * @param cv
     *            the class visitor to which this adapter must delegate calls.
     */
    public CheckClassAdapter(final ClassVisitor cv) {
        this(cv, true);
    }

    /**
     * Constructs a new {@link CheckClassAdapter}. <i>Subclasses must not use
     * this constructor</i>. Instead, they must use the
     * {@link #CheckClassAdapter(int, ClassVisitor, boolean)} version.
     *
     * @param cv
     *            the class visitor to which this adapter must delegate calls.
     * @param checkDataFlow
     *            <tt>true</tt> to perform basic data flow checks, or
     *            <tt>false</tt> to not perform any data flow check (see
     *            {@link CheckMethodAdapter}). This option requires valid
     *            maxLocals and maxStack values.
     * @throws IllegalStateException
     *             If a subclass calls this constructor.
     */
    public CheckClassAdapter(final ClassVisitor cv, final boolean checkDataFlow) {
        this(Opcodes.ASM5, cv, checkDataFlow);
        if (getClass() != CheckClassAdapter.class) {
            throw new IllegalStateException();
        }
    }

    /**
     * Constructs a new {@link CheckClassAdapter}.
     *
     * @param api
     *            the ASM API version implemented by this visitor. Must be one
     *            of {@link Opcodes#ASM4} or {@link Opcodes#ASM5}.
     * @param cv
     *            the class visitor to which this adapter must delegate calls.
     * @param checkDataFlow
     *            <tt>true</tt> to perform basic data flow checks, or
     *            <tt>false</tt> to not perform any data flow check (see
     *            {@link CheckMethodAdapter}). This option requires valid
     *            maxLocals and maxStack values.
     */
    protected CheckClassAdapter(final int api, final ClassVisitor cv,
            final boolean checkDataFlow) {
        super(api, cv);
        this.labels = new HashMap<Label, Integer>();
        this.checkDataFlow = checkDataFlow;
    }

    // ------------------------------------------------------------------------
    // Implementation of the ClassVisitor interface
    // ------------------------------------------------------------------------

    @Override
    public void visit(final int version, final int access, final String name,
            final String signature, final String superName,
            final String[] interfaces) {
        if (start) {
            throw new IllegalStateException("visit must be called only once");
        }
        start = true;
        checkState();
        checkAccess(access, Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL
                + Opcodes.ACC_SUPER + Opcodes.ACC_INTERFACE
                + Opcodes.ACC_ABSTRACT + Opcodes.ACC_SYNTHETIC
                + Opcodes.ACC_ANNOTATION + Opcodes.ACC_ENUM
                + Opcodes.ACC_DEPRECATED + 0x40000); // ClassWriter.ACC_SYNTHETIC_ATTRIBUTE
        if (name == null || !name.endsWith("package-info")) {
            CheckMethodAdapter.checkInternalName(name, "class name");
        }
        if ("java/lang/Object".equals(name)) {
            if (superName != null) {
                throw new IllegalArgumentException(
                        "The super class name of the Object class must be 'null'");
            }
        } else {
            CheckMethodAdapter.checkInternalName(superName, "super class name");
        }
        if (signature != null) {
            checkClassSignature(signature);
        }
        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            if (!"java/lang/Object".equals(superName)) {
                throw new IllegalArgumentException(
                        "The super class name of interfaces must be 'java/lang/Object'");
            }
        }
        if (interfaces != null) {
            for (int i = 0; i < interfaces.length; ++i) {
                CheckMethodAdapter.checkInternalName(interfaces[i],
                        "interface name at index " + i);
            }
        }
        this.version = version;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitSource(final String file, final String debug) {
        checkState();
        if (source) {
            throw new IllegalStateException(
                    "visitSource can be called only once.");
        }
        source = true;
        super.visitSource(file, debug);
    }

    @Override
    public void visitOuterClass(final String owner, final String name,
            final String desc) {
        checkState();
        if (outer) {
            throw new IllegalStateException(
                    "visitOuterClass can be called only once.");
        }
        outer = true;
        if (owner == null) {
            throw new IllegalArgumentException("Illegal outer class owner");
        }
        if (desc != null) {
            CheckMethodAdapter.checkMethodDesc(desc);
        }
        super.visitOuterClass(owner, name, desc);
    }

    @Override
    public void visitInnerClass(final String name, final String outerName,
            final String innerName, final int access) {
        checkState();
        CheckMethodAdapter.checkInternalName(name, "class name");
        if (outerName != null) {
            CheckMethodAdapter.checkInternalName(outerName, "outer class name");
        }
        if (innerName != null) {
            int start = 0;
            while (start < innerName.length()
                    && Character.isDigit(innerName.charAt(start))) {
                start++;
            }
            if (start == 0 || start < innerName.length()) {
                CheckMethodAdapter.checkIdentifier(innerName, start, -1,
                        "inner class name");
            }
        }
        checkAccess(access, Opcodes.ACC_PUBLIC + Opcodes.ACC_PRIVATE
                + Opcodes.ACC_PROTECTED + Opcodes.ACC_STATIC
                + Opcodes.ACC_FINAL + Opcodes.ACC_INTERFACE
                + Opcodes.ACC_ABSTRACT + Opcodes.ACC_SYNTHETIC
                + Opcodes.ACC_ANNOTATION + Opcodes.ACC_ENUM);
        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public FieldVisitor visitField(final int access, final String name,
            final String desc, final String signature, final Object value) {
        checkState();
        checkAccess(access, Opcodes.ACC_PUBLIC + Opcodes.ACC_PRIVATE
                + Opcodes.ACC_PROTECTED + Opcodes.ACC_STATIC
                + Opcodes.ACC_FINAL + Opcodes.ACC_VOLATILE
                + Opcodes.ACC_TRANSIENT + Opcodes.ACC_SYNTHETIC
                + Opcodes.ACC_ENUM + Opcodes.ACC_DEPRECATED + 0x40000); // ClassWriter.ACC_SYNTHETIC_ATTRIBUTE
        CheckMethodAdapter.checkUnqualifiedName(version, name, "field name");
        CheckMethodAdapter.checkDesc(desc, false);
        if (signature != null) {
            checkFieldSignature(signature);
        }
        if (value != null) {
            CheckMethodAdapter.checkConstant(value);
        }
        FieldVisitor av = super
                .visitField(access, name, desc, signature, value);
        return new CheckFieldAdapter(av);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name,
            final String desc, final String signature, final String[] exceptions) {
        checkState();
        checkAccess(access, Opcodes.ACC_PUBLIC + Opcodes.ACC_PRIVATE
                + Opcodes.ACC_PROTECTED + Opcodes.ACC_STATIC
                + Opcodes.ACC_FINAL + Opcodes.ACC_SYNCHRONIZED
                + Opcodes.ACC_BRIDGE + Opcodes.ACC_VARARGS + Opcodes.ACC_NATIVE
                + Opcodes.ACC_ABSTRACT + Opcodes.ACC_STRICT
                + Opcodes.ACC_SYNTHETIC + Opcodes.ACC_DEPRECATED + 0x40000); // ClassWriter.ACC_SYNTHETIC_ATTRIBUTE
        if (!"<init>".equals(name) && !"<clinit>".equals(name)) {
            CheckMethodAdapter.checkMethodIdentifier(version, name,
                    "method name");
        }
        CheckMethodAdapter.checkMethodDesc(desc);
        if (signature != null) {
            checkMethodSignature(signature);
        }
        if (exceptions != null) {
            for (int i = 0; i < exceptions.length; ++i) {
                CheckMethodAdapter.checkInternalName(exceptions[i],
                        "exception name at index " + i);
            }
        }
        CheckMethodAdapter cma;
        if (checkDataFlow) {
            cma = new CheckMethodAdapter(access, name, desc, super.visitMethod(
                    access, name, desc, signature, exceptions), labels);
        } else {
            cma = new CheckMethodAdapter(super.visitMethod(access, name, desc,
                    signature, exceptions), labels);
        }
        cma.version = version;
        return cma;
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc,
            final boolean visible) {
        checkState();
        CheckMethodAdapter.checkDesc(desc, false);
        return new CheckAnnotationAdapter(super.visitAnnotation(desc, visible));
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(final int typeRef,
            final TypePath typePath, final String desc, final boolean visible) {
        checkState();
        int sort = typeRef >>> 24;
        if (sort != TypeReference.CLASS_TYPE_PARAMETER
                && sort != TypeReference.CLASS_TYPE_PARAMETER_BOUND
                && sort != TypeReference.CLASS_EXTENDS) {
            throw new IllegalArgumentException("Invalid type reference sort 0x"
                    + Integer.toHexString(sort));
        }
        checkTypeRefAndPath(typeRef, typePath);
        CheckMethodAdapter.checkDesc(desc, false);
        return new CheckAnnotationAdapter(super.visitTypeAnnotation(typeRef,
                typePath, desc, visible));
    }

    @Override
    public void visitAttribute(final Attribute attr) {
        checkState();
        if (attr == null) {
            throw new IllegalArgumentException(
                    "Invalid attribute (must not be null)");
        }
        super.visitAttribute(attr);
    }

    @Override
    public void visitEnd() {
        checkState();
        end = true;
        super.visitEnd();
    }

    // ------------------------------------------------------------------------
    // Utility methods
    // ------------------------------------------------------------------------

    /**
     * Checks that the visit method has been called and that visitEnd has not
     * been called.
     */
    private void checkState() {
        if (!start) {
            throw new IllegalStateException(
                    "Cannot visit member before visit has been called.");
        }
        if (end) {
            throw new IllegalStateException(
                    "Cannot visit member after visitEnd has been called.");
        }
    }

    /**
     * Checks that the given access flags do not contain invalid flags. This
     * method also checks that mutually incompatible flags are not set
     * simultaneously.
     *
     * @param access
     *            the access flags to be checked
     * @param possibleAccess
     *            the valid access flags.
     */
    static void checkAccess(final int access, final int possibleAccess) {
        if ((access & ~possibleAccess) != 0) {
            throw new IllegalArgumentException("Invalid access flags: "
                    + access);
        }
        int pub = (access & Opcodes.ACC_PUBLIC) == 0 ? 0 : 1;
        int pri = (access & Opcodes.ACC_PRIVATE) == 0 ? 0 : 1;
        int pro = (access & Opcodes.ACC_PROTECTED) == 0 ? 0 : 1;
        if (pub + pri + pro > 1) {
            throw new IllegalArgumentException(
                    "public private and protected are mutually exclusive: "
                            + access);
        }
        int fin = (access & Opcodes.ACC_FINAL) == 0 ? 0 : 1;
        int abs = (access & Opcodes.ACC_ABSTRACT) == 0 ? 0 : 1;
        if (fin + abs > 1) {
            throw new IllegalArgumentException(
                    "final and abstract are mutually exclusive: " + access);
        }
    }

    /**
     * Checks a class signature.
     *
     * @param signature
     *            a string containing the signature that must be checked.
     */
    public static void checkClassSignature(final String signature) {
        // ClassSignature:
        // FormalTypeParameters? ClassTypeSignature ClassTypeSignature*

        int pos = 0;
        if (getChar(signature, 0) == '<') {
            pos = checkFormalTypeParameters(signature, pos);
        }
        pos = checkClassTypeSignature(signature, pos);
        while (getChar(signature, pos) == 'L') {
            pos = checkClassTypeSignature(signature, pos);
        }
        if (pos != signature.length()) {
            throw new IllegalArgumentException(signature + ": error at index "
                    + pos);
        }
    }

    /**
     * Checks a method signature.
     *
     * @param signature
     *            a string containing the signature that must be checked.
     */
    public static void checkMethodSignature(final String signature) {
        // MethodTypeSignature:
        // FormalTypeParameters? ( TypeSignature* ) ( TypeSignature | V ) (
        // ^ClassTypeSignature | ^TypeVariableSignature )*

        int pos = 0;
        if (getChar(signature, 0) == '<') {
            pos = checkFormalTypeParameters(signature, pos);
        }
        pos = checkChar('(', signature, pos);
        while ("ZCBSIFJDL[T".indexOf(getChar(signature, pos)) != -1) {
            pos = checkTypeSignature(signature, pos);
        }
        pos = checkChar(')', signature, pos);
        if (getChar(signature, pos) == 'V') {
            ++pos;
        } else {
            pos = checkTypeSignature(signature, pos);
        }
        while (getChar(signature, pos) == '^') {
            ++pos;
            if (getChar(signature, pos) == 'L') {
                pos = checkClassTypeSignature(signature, pos);
            } else {
                pos = checkTypeVariableSignature(signature, pos);
            }
        }
        if (pos != signature.length()) {
            throw new IllegalArgumentException(signature + ": error at index "
                    + pos);
        }
    }

    /**
     * Checks a field signature.
     *
     * @param signature
     *            a string containing the signature that must be checked.
     */
    public static void checkFieldSignature(final String signature) {
        int pos = checkFieldTypeSignature(signature, 0);
        if (pos != signature.length()) {
            throw new IllegalArgumentException(signature + ": error at index "
                    + pos);
        }
    }

    /**
     * Checks the reference to a type in a type annotation.
     *
     * @param typeRef
     *            a reference to an annotated type.
     * @param typePath
     *            the path to the annotated type argument, wildcard bound, array
     *            element type, or static inner type within 'typeRef'. May be
     *            <tt>null</tt> if the annotation targets 'typeRef' as a whole.
     */
    static void checkTypeRefAndPath(int typeRef, TypePath typePath) {
        int mask = 0;
        switch (typeRef >>> 24) {
        case TypeReference.CLASS_TYPE_PARAMETER:
        case TypeReference.METHOD_TYPE_PARAMETER:
        case TypeReference.METHOD_FORMAL_PARAMETER:
            mask = 0xFFFF0000;
            break;
        case TypeReference.FIELD:
        case TypeReference.METHOD_RETURN:
        case TypeReference.METHOD_RECEIVER:
        case TypeReference.LOCAL_VARIABLE:
        case TypeReference.RESOURCE_VARIABLE:
        case TypeReference.INSTANCEOF:
        case TypeReference.NEW:
        case TypeReference.CONSTRUCTOR_REFERENCE:
        case TypeReference.METHOD_REFERENCE:
            mask = 0xFF000000;
            break;
        case TypeReference.CLASS_EXTENDS:
        case TypeReference.CLASS_TYPE_PARAMETER_BOUND:
        case TypeReference.METHOD_TYPE_PARAMETER_BOUND:
        case TypeReference.THROWS:
        case TypeReference.EXCEPTION_PARAMETER:
            mask = 0xFFFFFF00;
            break;
        case TypeReference.CAST:
        case TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
        case TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT:
        case TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
        case TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT:
            mask = 0xFF0000FF;
            break;
        default:
            throw new IllegalArgumentException("Invalid type reference sort 0x"
                    + Integer.toHexString(typeRef >>> 24));
        }
        if ((typeRef & ~mask) != 0) {
            throw new IllegalArgumentException("Invalid type reference 0x"
                    + Integer.toHexString(typeRef));
        }
        if (typePath != null) {
            for (int i = 0; i < typePath.getLength(); ++i) {
                int step = typePath.getStep(i);
                if (step != TypePath.ARRAY_ELEMENT
                        && step != TypePath.INNER_TYPE
                        && step != TypePath.TYPE_ARGUMENT
                        && step != TypePath.WILDCARD_BOUND) {
                    throw new IllegalArgumentException(
                            "Invalid type path step " + i + " in " + typePath);
                }
                if (step != TypePath.TYPE_ARGUMENT
                        && typePath.getStepArgument(i) != 0) {
                    throw new IllegalArgumentException(
                            "Invalid type path step argument for step " + i
                                    + " in " + typePath);
                }
            }
        }
    }

    /**
     * Checks the formal type parameters of a class or method signature.
     *
     * @param signature
     *            a string containing the signature that must be checked.
     * @param pos
     *            index of first character to be checked.
     * @return the index of the first character after the checked part.
     */
    private static int checkFormalTypeParameters(final String signature, int pos) {
        // FormalTypeParameters:
        // < FormalTypeParameter+ >

        pos = checkChar('<', signature, pos);
        pos = checkFormalTypeParameter(signature, pos);
        while (getChar(signature, pos) != '>') {
            pos = checkFormalTypeParameter(signature, pos);
        }
        return pos + 1;
    }

    /**
     * Checks a formal type parameter of a class or method signature.
     *
     * @param signature
     *            a string containing the signature that must be checked.
     * @param pos
     *            index of first character to be checked.
     * @return the index of the first character after the checked part.
     */
    private static int checkFormalTypeParameter(final String signature, int pos) {
        // FormalTypeParameter:
        // Identifier : FieldTypeSignature? (: FieldTypeSignature)*

        pos = checkIdentifier(signature, pos);
        pos = checkChar(':', signature, pos);
        if ("L[T".indexOf(getChar(signature, pos)) != -1) {
            pos = checkFieldTypeSignature(signature, pos);
        }
        while (getChar(signature, pos) == ':') {
            pos = checkFieldTypeSignature(signature, pos + 1);
        }
        return pos;
    }

    /**
     * Checks a field type signature.
     *
     * @param signature
     *            a string containing the signature that must be checked.
     * @param pos
     *            index of first character to be checked.
     * @return the index of the first character after the checked part.
     */
    private static int checkFieldTypeSignature(final String signature, int pos) {
        // FieldTypeSignature:
        // ClassTypeSignature | ArrayTypeSignature | TypeVariableSignature
        //
        // ArrayTypeSignature:
        // [ TypeSignature

        switch (getChar(signature, pos)) {
        case 'L':
            return checkClassTypeSignature(signature, pos);
        case '[':
            return checkTypeSignature(signature, pos + 1);
        default:
            return checkTypeVariableSignature(signature, pos);
        }
    }

    /**
     * Checks a class type signature.
     *
     * @param signature
     *            a string containing the signature that must be checked.
     * @param pos
     *            index of first character to be checked.
     * @return the index of the first character after the checked part.
     */
    private static int checkClassTypeSignature(final String signature, int pos) {
        // ClassTypeSignature:
        // L Identifier ( / Identifier )* TypeArguments? ( . Identifier
        // TypeArguments? )* ;

        pos = checkChar('L', signature, pos);
        pos = checkIdentifier(signature, pos);
        while (getChar(signature, pos) == '/') {
            pos = checkIdentifier(signature, pos + 1);
        }
        if (getChar(signature, pos) == '<') {
            pos = checkTypeArguments(signature, pos);
        }
        while (getChar(signature, pos) == '.') {
            pos = checkIdentifier(signature, pos + 1);
            if (getChar(signature, pos) == '<') {
                pos = checkTypeArguments(signature, pos);
            }
        }
        return checkChar(';', signature, pos);
    }

    /**
     * Checks the type arguments in a class type signature.
     *
     * @param signature
     *            a string containing the signature that must be checked.
     * @param pos
     *            index of first character to be checked.
     * @return the index of the first character after the checked part.
     */
    private static int checkTypeArguments(final String signature, int pos) {
        // TypeArguments:
        // < TypeArgument+ >

        pos = checkChar('<', signature, pos);
        pos = checkTypeArgument(signature, pos);
        while (getChar(signature, pos) != '>') {
            pos = checkTypeArgument(signature, pos);
        }
        return pos + 1;
    }

    /**
     * Checks a type argument in a class type signature.
     *
     * @param signature
     *            a string containing the signature that must be checked.
     * @param pos
     *            index of first character to be checked.
     * @return the index of the first character after the checked part.
     */
    private static int checkTypeArgument(final String signature, int pos) {
        // TypeArgument:
        // * | ( ( + | - )? FieldTypeSignature )

        char c = getChar(signature, pos);
        if (c == '*') {
            return pos + 1;
        } else if (c == '+' || c == '-') {
            pos++;
        }
        return checkFieldTypeSignature(signature, pos);
    }

    /**
     * Checks a type variable signature.
     *
     * @param signature
     *            a string containing the signature that must be checked.
     * @param pos
     *            index of first character to be checked.
     * @return the index of the first character after the checked part.
     */
    private static int checkTypeVariableSignature(final String signature,
            int pos) {
        // TypeVariableSignature:
        // T Identifier ;

        pos = checkChar('T', signature, pos);
        pos = checkIdentifier(signature, pos);
        return checkChar(';', signature, pos);
    }

    /**
     * Checks a type signature.
     *
     * @param signature
     *            a string containing the signature that must be checked.
     * @param pos
     *            index of first character to be checked.
     * @return the index of the first character after the checked part.
     */
    private static int checkTypeSignature(final String signature, int pos) {
        // TypeSignature:
        // Z | C | B | S | I | F | J | D | FieldTypeSignature

        switch (getChar(signature, pos)) {
        case 'Z':
        case 'C':
        case 'B':
        case 'S':
        case 'I':
        case 'F':
        case 'J':
        case 'D':
            return pos + 1;
        default:
            return checkFieldTypeSignature(signature, pos);
        }
    }

    /**
     * Checks an identifier.
     *
     * @param signature
     *            a string containing the signature that must be checked.
     * @param pos
     *            index of first character to be checked.
     * @return the index of the first character after the checked part.
     */
    private static int checkIdentifier(final String signature, int pos) {
        if (!Character.isJavaIdentifierStart(getChar(signature, pos))) {
            throw new IllegalArgumentException(signature
                    + ": identifier expected at index " + pos);
        }
        ++pos;
        while (Character.isJavaIdentifierPart(getChar(signature, pos))) {
            ++pos;
        }
        return pos;
    }

    /**
     * Checks a single character.
     *
     * @param signature
     *            a string containing the signature that must be checked.
     * @param pos
     *            index of first character to be checked.
     * @return the index of the first character after the checked part.
     */
    private static int checkChar(final char c, final String signature, int pos) {
        if (getChar(signature, pos) == c) {
            return pos + 1;
        }
        throw new IllegalArgumentException(signature + ": '" + c
                + "' expected at index " + pos);
    }

    /**
     * Returns the signature car at the given index.
     *
     * @param signature
     *            a signature.
     * @param pos
     *            an index in signature.
     * @return the character at the given index, or 0 if there is no such
     *         character.
     */
    private static char getChar(final String signature, int pos) {
        return pos < signature.length() ? signature.charAt(pos) : (char) 0;
    }
}
