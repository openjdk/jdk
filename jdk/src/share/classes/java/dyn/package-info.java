/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

/**
 * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
 * This package contains dynamic language support provided directly by
 * the Java core class libraries and virtual machine.
 * <p>
 * Certain types in this package have special relations to dynamic
 * language support in the virtual machine:
 * <ul>
 * <li>In source code, a call to
 * {@link java.dyn.MethodHandle#invokeExact   MethodHandle.invokeExact} or
 * {@link java.dyn.MethodHandle#invokeGeneric MethodHandle.invokeGeneric}
 * will compile and link, regardless of the requested type signature.
 * As usual, the Java compiler emits an {@code invokevirtual}
 * instruction with the given signature against the named method.
 * The JVM links any such call (regardless of signature) to a dynamically
 * typed method handle invocation.  In the case of {@code invokeGeneric},
 * argument and return value conversions are applied.
 *
 * <li>In source code, the class {@link java.dyn.InvokeDynamic} appears to accept
 * any static method invocation, of any name and any signature.
 * But instead of emitting
 * an {@code invokestatic} instruction for such a call, the Java compiler emits
 * an {@code invokedynamic} instruction with the given name and signature.
 *
 * <li>When the JVM links an {@code invokedynamic} instruction, it calls the
 * {@linkplain java.dyn.Linkage#registerBootstrapMethod(Class, MethodHandle) bootstrap method}
 * of the containing class to obtain a {@linkplain java.dyn.CallSite call site} object through which
 * the call site will link its target {@linkplain java.dyn.MethodHandle method handle}.
 *
 * <li>The JVM bytecode format supports immediate constants of
 * the classes {@link java.dyn.MethodHandle} and {@link java.dyn.MethodType}.
 * </ul>
 *
 * <h2><a name="jvm_mods"></a>Corresponding JVM bytecode format changes</h2>
 * <em>The following low-level information is presented here as a preview of
 * changes being made to the Java Virtual Machine specification for JSR 292.</em>
 *
 * <h3>{@code invokedynamic} instruction format</h3>
 * In bytecode, an {@code invokedynamic} instruction is formatted as five bytes.
 * The first byte is the opcode 186 (hexadecimal {@code BA}).
 * The next two bytes are a constant pool index (in the same format as for the other {@code invoke} instructions).
 * The final two bytes are reserved for future use and required to be zero.
 * The constant pool reference is to a entry with tag {@code CONSTANT_NameAndType}
 * (decimal 12).  It is thus not a method reference of any sort, but merely
 * the method name, argument types, and return type of the dynamic call site.
 * <em>(TBD: The EG is discussing the possibility of a special constant pool entry type,
 * so that other information may be added, such as a per-instruction bootstrap
 * method and/or annotations.)</em>
 *
 * <h3>constant pool entries for {@code MethodType}s</h3>
 * If a constant pool entry has the tag {@code CONSTANT_MethodType} (decimal 16),
 * it must contain exactly two more bytes, which are an index to a {@code CONSTANT_Utf8}
 * entry which represents a method type signature.  The JVM will ensure that on first
 * execution of an {@code ldc} instruction for this entry, a {@link java.dyn.MethodType}
 * will be created which represents the signature.
 * Any classes mentioned in the {@code MethodType} will be loaded if necessary,
 * but not initialized.
 * Access checking and error reporting is performed exactly as it is for
 * references by {@code ldc} instructions to {@code CONSTANT_Class} constants.
 *
 * <h3>constant pool entries for {@code MethodHandle}s</h3>
 * If a constant pool entry has the tag {@code CONSTANT_MethodHandle} (decimal 15),
 * it must contain exactly three more bytes.  The first byte after the tag is a subtag
 * value in the range 1 through 9, and the last two are an index to a
 * {@code CONSTANT_Fieldref}, {@code CONSTANT_Methodref}, or
 * {@code CONSTANT_InterfaceMethodref} entry which represents a field or method
 * for which a method handle is to be created.
 * The JVM will ensure that on first execution of an {@code ldc} instruction
 * for this entry, a {@link java.dyn.MethodHandle} will be created which represents
 * the field or method reference, according to the specific mode implied by the subtag.
 * <p>
 * As with {@code CONSTANT_Class} and {@code CONSTANT_MethodType} constants,
 * the {@code Class} or {@code MethodType} object which reifies the field or method's
 * type is created.  Any classes mentioned in this reificaiton will be loaded if necessary,
 * but not initialized, and access checking and error reporting performed as usual.
 * <p>
 * The method handle itself will have a type and behavior determined by the subtag as follows:
 * <code>
 * <table border=1 cellpadding=5 summary="CONSTANT_MethodHandle subtypes">
 * <tr><th>N</th><th>subtag name</th><th>member</th><th>MH type</th><th>MH behavior</th></tr>
 * <tr><td>1</td><td>REF_getField</td><td>C.f:T</td><td>(C)T</td><td>getfield C.f:T</td></tr>
 * <tr><td>2</td><td>REF_getStatic</td><td>C.f:T</td><td>(&nbsp;)T</td><td>getstatic C.f:T</td></tr>
 * <tr><td>3</td><td>REF_putField</td><td>C.f:T</td><td>(C,T)void</td><td>putfield C.f:T</td></tr>
 * <tr><td>4</td><td>REF_putStatic</td><td>C.f:T</td><td>(T)void</td><td>putstatic C.f:T</td></tr>
 * <tr><td>5</td><td>REF_invokeVirtual</td><td>C.m(A*)T</td><td>(C,A*)T</td><td>invokevirtual C.m(A*)T</td></tr>
 * <tr><td>6</td><td>REF_invokeStatic</td><td>C.m(A*)T</td><td>(C,A*)T</td><td>invokestatic C.m(A*)T</td></tr>
 * <tr><td>7</td><td>REF_invokeSpecial</td><td>C.m(A*)T</td><td>(C,A*)T</td><td>invokespecial C.m(A*)T</td></tr>
 * <tr><td>8</td><td>REF_newInvokeSpecial</td><td>C.&lt;init&gt;(A*)void</td><td>(A*)C</td><td>new C; dup; invokespecial C.&lt;init&gt;(A*)void</td></tr>
 * <tr><td>9</td><td>REF_invokeInterface</td><td>C.m(A*)T</td><td>(C,A*)T</td><td>invokeinterface C.m(A*)T</td></tr>
 * </table>
 * </code>
 * <p>
 * The special names {@code <init>} and {@code <clinit>} are not allowed except for subtag 8 as shown.
 * <p>
 * The verifier applies the same access checks and restrictions for these references as for the hypothetical
 * bytecode instructions specified in the last column of the table.  In particular, method handles to
 * private and protected members can be created in exactly those classes for which the corresponding
 * normal accesses are legal.
 * <p>
 * None of these constant types force class initialization.
 * Method handles for subtags {@code REF_getStatic}, {@code REF_putStatic}, and {@code REF_invokeStatic}
 * may force class initialization on their first invocation, just like the corresponding bytecodes.
 *
 * @author John Rose, JSR 292 EG
 */

package java.dyn;
