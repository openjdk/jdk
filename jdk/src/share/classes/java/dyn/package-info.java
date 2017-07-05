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
 * </li>
 *
 * <li>In source code, the class {@link java.dyn.InvokeDynamic InvokeDynamic} appears to accept
 * any static method invocation, of any name and any signature.
 * But instead of emitting
 * an {@code invokestatic} instruction for such a call, the Java compiler emits
 * an {@code invokedynamic} instruction with the given name and signature.
 * </li>
 *
 * <li>The JVM bytecode format supports immediate constants of
 * the classes {@link java.dyn.MethodHandle MethodHandle} and {@link java.dyn.MethodType MethodType}.
 * </li>
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
 * The constant pool reference of an {@code invokedynamic} instruction is to a entry
 * with tag {@code CONSTANT_InvokeDynamic} (decimal 17).  See below for its format.
 * The entry specifies the bootstrap method (a {@link java.dyn.MethodHandle MethodHandle} constant),
 * the dynamic invocation name, and the argument types and return type of the call.
 * <p>
 * Each instance of an {@code invokedynamic} instruction is called a <em>dynamic call site</em>.
 * Multiple instances of an {@code invokedynamic} instruction can share a single
 * {@code CONSTANT_InvokeDynamic} entry.
 * In any case, distinct call sites always have distinct linkage state.
 * <p>
 * Moreover, for the purpose of distinguishing dynamic call sites,
 * the JVM is allowed (but not required) to make internal copies
 * of {@code invokedynamic} instructions, each one
 * constituting a separate dynamic call site with its own linkage state.
 * Such copying, if it occurs, cannot be observed except indirectly via
 * execution of bootstrap methods and target methods.
 * <p>
 * A dynamic call site is originally in an unlinked state.  In this state, there is
 * no target method for the call site to invoke.
 * A dynamic call site is linked by means of a bootstrap method,
 * as <a href="#bsm">described below</a>.
 * <p>
 * <em>(Historic Note: Some older JVMs may allow the index of a {@code CONSTANT_NameAndType}
 * instead of a {@code CONSTANT_InvokeDynamic}.  In earlier, obsolete versions of this API, the
 * bootstrap method was specified dynamically, in a per-class basis, during class initialization.)</em>
 *
 * <h3>constant pool entries for {@code invokedynamic} instructions</h3>
 * If a constant pool entry has the tag {@code CONSTANT_InvokeDynamic} (decimal 17),
 * it must contain exactly four more bytes.
 * The first two bytes after the tag must be an index to a {@code CONSTANT_MethodHandle}
 * entry, and the second two bytes must be an index to a {@code CONSTANT_NameAndType}.
 * The first index specifies a bootstrap method used by the associated dynamic call sites.
 * The second index specifies the method name, argument types, and return type of the dynamic call site.
 * The structure of such an entry is therefore analogous to a {@code CONSTANT_Methodref},
 * except that the {@code CONSTANT_Class} reference in a {@code CONSTANT_Methodref} entry
 * is replaced by a bootstrap method reference.
 *
 * <h3>constant pool entries for {@code MethodType}s</h3>
 * If a constant pool entry has the tag {@code CONSTANT_MethodType} (decimal 16),
 * it must contain exactly two more bytes, which must be an index to a {@code CONSTANT_Utf8}
 * entry which represents a method type signature.
 * <p>
 * The JVM will ensure that on first
 * execution of an {@code ldc} instruction for this entry, a {@link java.dyn.MethodType MethodType}
 * will be created which represents the signature.
 * Any classes mentioned in the {@code MethodType} will be loaded if necessary,
 * but not initialized.
 * Access checking and error reporting is performed exactly as it is for
 * references by {@code ldc} instructions to {@code CONSTANT_Class} constants.
 *
 * <h3>constant pool entries for {@code MethodHandle}s</h3>
 * If a constant pool entry has the tag {@code CONSTANT_MethodHandle} (decimal 15),
 * it must contain exactly three more bytes.  The first byte after the tag is a subtag
 * value which must be in the range 1 through 9, and the last two must be an index to a
 * {@code CONSTANT_Fieldref}, {@code CONSTANT_Methodref}, or
 * {@code CONSTANT_InterfaceMethodref} entry which represents a field or method
 * for which a method handle is to be created.
 * Furthermore, the subtag value and the type of the constant index value
 * must agree according to the table below.
 * <p>
 * The JVM will ensure that on first execution of an {@code ldc} instruction
 * for this entry, a {@link java.dyn.MethodHandle MethodHandle} will be created which represents
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
 * <h2><a name="bsm"></a>Bootstrap Methods</h2>
 * Before the JVM can execute a dynamic call site (an {@code invokedynamic} instruction),
 * the call site must first be <em>linked</em>.
 * Linking is accomplished by calling a <em>bootstrap method</em>
 * which is given the static information content of the call site,
 * and which must produce a {@link java.dyn.MethodHandle method handle}
 * that gives the behavior of the call site.
 * <p>
 * Each {@code invokedynamic} instruction statically specifies its own
 * bootstrap method as a constant pool reference.
 * The constant pool reference also specifies the call site's name and type signature,
 * just like {@code invokevirtual} and the other invoke instructions.
 * <p>
 * Linking starts with resolving the constant pool entry for the
 * bootstrap method, and resolving a {@link java.dyn.MethodType MethodType} object for
 * the type signature of the dynamic call site.
 * This resolution process may trigger class loading.
 * It may therefore throw an error if a class fails to load.
 * This error becomes the abnormal termination of the dynamic
 * call site execution.
 * Linkage does not trigger class initialization.
 * <p>
 * Next, the bootstrap method call is started, with four values being stacked:
 * <ul>
 * <li>a {@code MethodHandle}, the resolved bootstrap method itself </li>
 * <li>a {@code Class}, the <em>caller class</em> in which dynamic call site occurs </li>
 * <li>a {@code String}, the method name mentioned in the call site </li>
 * <li>a {@code MethodType}, the resolved type signature of the call </li>
 * </ul>
 * The method handle is then applied to the other values as if by
 * {@linkplain java.dyn.MethodHandle#invokeGeneric the <code>invokeGeneric</code> method}.
 * The returned result must be a {@link java.dyn.CallSite CallSite}, a {@link java.dyn.MethodHandle MethodHandle},
 * or another {@link java.dyn.MethodHandleProvider MethodHandleProvider} value.
 * The method {@linkplain java.dyn.MethodHandleProvider#asMethodHandle asMethodHandle}
 * is then called on the returned value.  The result of that second
 * call is the {@code MethodHandle} which becomes the
 * permanent binding for the dynamic call site.
 * That method handle's type must be exactly equal to the type
 * derived from the dynamic call site signature and passed to
 * the bootstrap method.
 * <p>
 * After resolution, the linkage process may fail in a variety of ways.
 * All failures are reported by an {@link java.dyn.InvokeDynamicBootstrapError InvokeDynamicBootstrapError},
 * which is thrown as the abnormal termination of the dynamic call
 * site execution.
 * The following circumstances will cause this:
 * <ul>
 * <li>the bootstrap method invocation completes abnormally </li>
 * <li>the result from the bootstrap invocation is not a reference to
 *     an object of type {@link java.dyn.MethodHandleProvider MethodHandleProvider} </li>
 * <li>the call to {@code asMethodHandle} completes abnormally </li>
 * <li>the call to {@code asMethodHandle} fails to return a reference to
 *     an object of type {@link java.dyn.MethodHandle MethodHandle} </li>
 * <li>the method handle produced by {@code asMethodHandle} does not have
 *     the expected {@code MethodType} </li>
 * </ul>
 * <h3>timing of linkage</h3>
 * A dynamic call site is linked just before its first execution.
 * The bootstrap method call implementing the linkage occurs within
 * a thread that is attempting a first execution.
 * <p>
 * If there are several such threads, the JVM picks one thread
 * and runs the bootstrap method while the others wait for the
 * invocation to terminate normally or abnormally.
 * <p>
 * After a bootstrap method is called and a method handle target
 * successfully extracted, the JVM attempts to link the instruction
 * being executed to the target method handle.
 * This may fail if there has been intervening linkage
 * or invalidation event for the same instruction.
 * If such a failure occurs, the dynamic call site must be
 * re-executed from the beginning, either re-linking it
 * (if it has been invalidated) or invoking the target
 * (if it the instruction has been linked by some other means).
 * <p>
 * If the instruction is linked successfully, the target method
 * handle is invoked to complete the instruction execution.
 * The state of linkage continues until the method containing the
 * dynamic call site is garbage collected, or the dynamic call site
 * is invalidated by an explicit request,
 * such as {@link java.dyn.Linkage#invalidateCallerClass Linkage.invalidateCallerClass}.
 * <p>
 * In an application which requires dynamic call sites with individually
 * mutable behaviors, their bootstrap methods should produce distinct
 * {@link java.dyn.CallSite CallSite} objects, one for each linkage request.
 * <p>
 * If a class containing {@code invokedynamic} instructions
 * is {@linkplain java.dyn.Linkage#invalidateCallerClass(Class) invalidated},
 * subsequent execution of those {@code invokedynamic} instructions
 * will require linking.
 * It is as if they had never been executed in the first place.
 * (However, invalidation does not cause constant pool entries to be
 * resolved a second time.)
 * <p>
 * Invalidation events and bootstrap method calls for a particular
 * dynamic call site are globally ordered relative to each other.
 * When an invokedynamic instruction is invalidated, if there is
 * simultaneously a bootstrap method invocation in process
 * (in the same thread or a different thread), the result
 * eventually returned must not be used to link the call site.
 * Put another way, when a call site is invalidated, its
 * subsequent linkage (if any) must be performed by a bootstrap method
 * call initiated after the invalidation occurred.
 * <p>
 * If several threads simultaneously execute a bootstrap method for a single dynamic
 * call site, the JVM must choose one target object and installs it visibly to
 * all threads.  Any other bootstrap method calls are allowed to complete, but their
 * results are ignored, and their dynamic call site invocations proceed with the originally
 * chosen target object.
 * <p>
 * The JVM is free to duplicate dynamic call sites.
 * This means that, even if a class contains just one {@code invokedynamic}
 * instruction, its bootstrap method may be executed several times,
 * once for each duplicate.  Thus, bootstrap method code should not
 * assume an exclusive one-to-one correspondence between particular occurrences
 * of {@code invokedynamic} bytecodes in class files and linkage events.
 * <p>
 * In principle, each individual execution of an {@code invokedynamic}
 * instruction could be deemed (by a conforming implementation) to be a separate
 * duplicate, requiring its own execution of the bootstrap method.
 * However, implementations are expected to perform code duplication
 * (if at all) in order to improve performance, not make it worse.
 *
 * @author John Rose, JSR 292 EG
 */

package java.dyn;
