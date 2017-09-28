/*
 * Copyright (c) 2008, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * The {@code java.lang.invoke} package contains dynamic language support provided directly by
 * the Java core class libraries and virtual machine.
 *
 * <p>
 * As described in the Java Virtual Machine Specification,
 * certain types in this package have special relations to dynamic
 * language support in the virtual machine:
 * <ul>
 * <li>The classes {@link java.lang.invoke.MethodHandle MethodHandle}
 * {@link java.lang.invoke.VarHandle VarHandle} contain
 * <a href="MethodHandle.html#sigpoly">signature polymorphic methods</a>
 * which can be linked regardless of their type descriptor.
 * Normally, method linkage requires exact matching of type descriptors.
 * </li>
 *
 * <li>The JVM bytecode format supports immediate constants of
 * the classes {@link java.lang.invoke.MethodHandle MethodHandle} and {@link java.lang.invoke.MethodType MethodType}.
 * </li>
 * </ul>
 *
 * <h1><a id="jvm_mods"></a>Summary of relevant Java Virtual Machine changes</h1>
 * The following low-level information summarizes relevant parts of the
 * Java Virtual Machine specification.  For full details, please see the
 * current version of that specification.
 *
 * Each occurrence of an {@code invokedynamic} instruction is called a <em>dynamic call site</em>.
 * <h2><a id="indyinsn"></a>{@code invokedynamic} instructions</h2>
 * A dynamic call site is originally in an unlinked state.  In this state, there is
 * no target method for the call site to invoke.
 * <p>
 * Before the JVM can execute a dynamic call site (an {@code invokedynamic} instruction),
 * the call site must first be <em>linked</em>.
 * Linking is accomplished by calling a <em>bootstrap method</em>
 * which is given the static information content of the call site,
 * and which must produce a {@link java.lang.invoke.MethodHandle method handle}
 * that gives the behavior of the call site.
 * <p>
 * Each {@code invokedynamic} instruction statically specifies its own
 * bootstrap method as a constant pool reference.
 * The constant pool reference also specifies the call site's name and type descriptor,
 * just like {@code invokevirtual} and the other invoke instructions.
 * <p>
 * Linking starts with resolving the constant pool entry for the
 * bootstrap method, and resolving a {@link java.lang.invoke.MethodType MethodType} object for
 * the type descriptor of the dynamic call site.
 * This resolution process may trigger class loading.
 * It may therefore throw an error if a class fails to load.
 * This error becomes the abnormal termination of the dynamic
 * call site execution.
 * Linkage does not trigger class initialization.
 * <p>
 * The bootstrap method is invoked on at least three values:
 * <ul>
 * <li>a {@code MethodHandles.Lookup}, a lookup object on the <em>caller class</em>
 *     in which dynamic call site occurs </li>
 * <li>a {@code String}, the method name mentioned in the call site </li>
 * <li>a {@code MethodType}, the resolved type descriptor of the call </li>
 * <li>optionally, any number of additional static arguments taken from the constant pool </li>
 * </ul>
 * <p>
 * In all cases, bootstrap method invocation is as if by
 * {@link java.lang.invoke.MethodHandle#invokeWithArguments MethodHandle.invokeWithArguments},
 * (This is also equivalent to
 * {@linkplain java.lang.invoke.MethodHandle#invoke generic invocation}
 * if the number of arguments is small enough.)
 * <p>
 * For an {@code invokedynamic} instruction, the
 * returned result must be convertible to a non-null reference to a
 * {@link java.lang.invoke.CallSite CallSite}.
 * If the returned result cannot be converted to the expected type,
 * {@link java.lang.BootstrapMethodError BootstrapMethodError} is thrown.
 * The type of the call site's target must be exactly equal to the type
 * derived from the dynamic call site's type descriptor and passed to
 * the bootstrap method, otherwise a {@code BootstrapMethodError} is thrown.
 * On success the call site then becomes permanently linked to the dynamic call
 * site.
 * <p>
 * If an exception, {@code E} say, occurs when linking the call site then the
 * linkage fails and terminates abnormally. {@code E} is rethrown if the type of
 * {@code E} is {@code Error} or a subclass, otherwise a
 * {@code BootstrapMethodError} that wraps {@code E} is thrown.
 * If this happens, the same {@code Error} or subclass will the thrown for all
 * subsequent attempts to execute the dynamic call site.
 * <h2>timing of linkage</h2>
 * A dynamic call site is linked just before its first execution.
 * The bootstrap method call implementing the linkage occurs within
 * a thread that is attempting a first execution.
 * <p>
 * If there are several such threads, the bootstrap method may be
 * invoked in several threads concurrently.
 * Therefore, bootstrap methods which access global application
 * data must take the usual precautions against race conditions.
 * In any case, every {@code invokedynamic} instruction is either
 * unlinked or linked to a unique {@code CallSite} object.
 * <p>
 * In an application which requires dynamic call sites with individually
 * mutable behaviors, their bootstrap methods should produce distinct
 * {@link java.lang.invoke.CallSite CallSite} objects, one for each linkage request.
 * Alternatively, an application can link a single {@code CallSite} object
 * to several {@code invokedynamic} instructions, in which case
 * a change to the target method will become visible at each of
 * the instructions.
 * <p>
 * If several threads simultaneously execute a bootstrap method for a single dynamic
 * call site, the JVM must choose one {@code CallSite} object and install it visibly to
 * all threads.  Any other bootstrap method calls are allowed to complete, but their
 * results are ignored, and their dynamic call site invocations proceed with the originally
 * chosen target object.

 * <p style="font-size:smaller;">
 * <em>Discussion:</em>
 * These rules do not enable the JVM to duplicate dynamic call sites,
 * or to issue &ldquo;causeless&rdquo; bootstrap method calls.
 * Every dynamic call site transitions at most once from unlinked to linked,
 * just before its first invocation.
 * There is no way to undo the effect of a completed bootstrap method call.
 *
 * <h2>types of bootstrap methods</h2>
 * As long as each bootstrap method can be correctly invoked
 * by {@code MethodHandle.invoke}, its detailed type is arbitrary.
 * For example, the first argument could be {@code Object}
 * instead of {@code MethodHandles.Lookup}, and the return type
 * could also be {@code Object} instead of {@code CallSite}.
 * (Note that the types and number of the stacked arguments limit
 * the legal kinds of bootstrap methods to appropriately typed
 * static methods and constructors of {@code CallSite} subclasses.)
 * <p>
 * If a given {@code invokedynamic} instruction specifies no static arguments,
 * the instruction's bootstrap method will be invoked on three arguments,
 * conveying the instruction's caller class, name, and method type.
 * If the {@code invokedynamic} instruction specifies one or more static arguments,
 * those values will be passed as additional arguments to the method handle.
 * (Note that because there is a limit of 255 arguments to any method,
 * at most 251 extra arguments can be supplied to a non-varargs bootstrap method,
 * since the bootstrap method
 * handle itself and its first three arguments must also be stacked.)
 * The bootstrap method will be invoked as if by {@code MethodHandle.invokeWithArguments}.
 * A variable-arity bootstrap method can accept thousands of static arguments,
 * subject only by limits imposed by the class-file format.
 * <p>
 * The normal argument conversion rules for {@code MethodHandle.invoke} apply to all stacked arguments.
 * For example, if a pushed value is a primitive type, it may be converted to a reference by boxing conversion.
 * If the bootstrap method is a variable arity method (its modifier bit {@code 0x0080} is set),
 * then some or all of the arguments specified here may be collected into a trailing array parameter.
 * (This is not a special rule, but rather a useful consequence of the interaction
 * between {@code CONSTANT_MethodHandle} constants, the modifier bit for variable arity methods,
 * and the {@link java.lang.invoke.MethodHandle#asVarargsCollector asVarargsCollector} transformation.)
 * <p>
 * Given these rules, here are examples of legal bootstrap method declarations,
 * given various numbers {@code N} of extra arguments.
 * The first row (marked {@code *}) will work for any number of extra arguments.
 * <table class="plain" style="vertical-align:top">
 * <caption style="display:none">Static argument types</caption>
 * <thead>
 * <tr><th scope="col">N</th><th scope="col">Sample bootstrap method</th></tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row" style="font-weight:normal; vertical-align:top">*</th><td>
 *     <ul style="list-style:none; padding-left: 0; margin:0">
 *     <li><code>CallSite bootstrap(Lookup caller, String name, MethodType type, Object... args)</code>
 *     <li><code>CallSite bootstrap(Object... args)</code>
 *     <li><code>CallSite bootstrap(Object caller, Object... nameAndTypeWithArgs)</code>
 *     </ul></td></tr>
 * <tr><th scope="row" style="font-weight:normal; vertical-align:top">0</th><td>
 *     <ul style="list-style:none; padding-left: 0; margin:0">
 *     <li><code>CallSite bootstrap(Lookup caller, String name, MethodType type)</code>
 *     <li><code>CallSite bootstrap(Lookup caller, Object... nameAndType)</code>
 *     </ul></td></tr>
 * <tr><th scope="row" style="font-weight:normal; vertical-align:top">1</th><td>
 *     <code>CallSite bootstrap(Lookup caller, String name, MethodType type, Object arg)</code></td></tr>
 * <tr><th scope="row" style="font-weight:normal; vertical-align:top">2</th><td>
 *     <ul style="list-style:none; padding-left: 0; margin:0">
 *     <li><code>CallSite bootstrap(Lookup caller, String name, MethodType type, Object... args)</code>
 *     <li><code>CallSite bootstrap(Lookup caller, String name, MethodType type, String... args)</code>
 *     <li><code>CallSite bootstrap(Lookup caller, String name, MethodType type, String x, int y)</code>
 *     </ul></td></tr>
 * </tbody>
 * </table>
 * The last example assumes that the extra arguments are of type
 * {@code String} and {@code Integer} (or {@code int}), respectively.
 * The second-to-last example assumes that all extra arguments are of type
 * {@code String}.
 * The other examples work with all types of extra arguments.
 * <p>
 * As noted above, the actual method type of the bootstrap method can vary.
 * For example, the fourth argument could be {@code MethodHandle},
 * if that is the type of the corresponding constant in
 * the {@code CONSTANT_InvokeDynamic} entry.
 * In that case, the {@code MethodHandle.invoke} call will pass the extra method handle
 * constant as an {@code Object}, but the type matching machinery of {@code MethodHandle.invoke}
 * will cast the reference back to {@code MethodHandle} before invoking the bootstrap method.
 * (If a string constant were passed instead, by badly generated code, that cast would then fail,
 * resulting in a {@code BootstrapMethodError}.)
 * <p>
 * Note that, as a consequence of the above rules, the bootstrap method may accept a primitive
 * argument, if it can be represented by a constant pool entry.
 * However, arguments of type {@code boolean}, {@code byte}, {@code short}, or {@code char}
 * cannot be created for bootstrap methods, since such constants cannot be directly
 * represented in the constant pool, and the invocation of the bootstrap method will
 * not perform the necessary narrowing primitive conversions.
 * <p>
 * Extra bootstrap method arguments are intended to allow language implementors
 * to safely and compactly encode metadata.
 * In principle, the name and extra arguments are redundant,
 * since each call site could be given its own unique bootstrap method.
 * Such a practice would be likely to produce large class files and constant pools.
 *
 * @author John Rose, JSR 292 EG
 * @since 1.7
 */

package java.lang.invoke;
