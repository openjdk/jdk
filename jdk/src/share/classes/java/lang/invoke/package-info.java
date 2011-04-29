/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * Certain types in this package have special relations to dynamic
 * language support in the virtual machine:
 * <ul>
 * <li>The class {@link java.lang.invoke.MethodHandle MethodHandle} contains
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
 * <h2><a name="jvm_mods"></a>Corresponding JVM bytecode format changes</h2>
 * <em>The following low-level information is presented here as a preview of
 * changes being made to the Java Virtual Machine specification for JSR 292.
 * This information will be incorporated in a future version of the JVM specification.</em>
 *
 * <h3><a name="indyinsn"></a>{@code invokedynamic} instruction format</h3>
 * In bytecode, an {@code invokedynamic} instruction is formatted as five bytes.
 * The first byte is the opcode 186 (hexadecimal {@code BA}).
 * The next two bytes are a constant pool index (in the same format as for the other {@code invoke} instructions).
 * The final two bytes are reserved for future use and required to be zero.
 * The constant pool reference of an {@code invokedynamic} instruction is to a entry
 * with tag {@code CONSTANT_InvokeDynamic} (decimal 18).  See below for its format.
 * The entry specifies the following information:
 * <ul>
 * <li>a bootstrap method (a {@link java.lang.invoke.MethodHandle MethodHandle} constant)</li>
 * <li>the dynamic invocation name (a UTF8 string)</li>
 * <li>the argument and return types of the call (encoded as a type descriptor in a UTF8 string)</li>
 * <li>optionally, a sequence of additional <em>static arguments</em> to the bootstrap method ({@code ldc}-type constants)</li>
 * </ul>
 * <p>
 * Each instance of an {@code invokedynamic} instruction is called a <em>dynamic call site</em>.
 * Multiple instances of an {@code invokedynamic} instruction can share a single
 * {@code CONSTANT_InvokeDynamic} entry.
 * In any case, distinct call sites always have distinct linkage state.
 * <p>
 * A dynamic call site is originally in an unlinked state.  In this state, there is
 * no target method for the call site to invoke.
 * A dynamic call site is linked by means of a bootstrap method,
 * as <a href="#bsm">described below</a>.
 *
 * <h3><a name="indycon"></a>constant pool entries for {@code invokedynamic} instructions</h3>
 * If a constant pool entry has the tag {@code CONSTANT_InvokeDynamic} (decimal 18),
 * it must contain exactly four more bytes after the tag.
 * These bytes are interpreted as two 16-bit indexes, in the usual {@code u2} format.
 * The first pair of bytes after the tag must be an index into a side table called the
 * <em>bootstrap method table</em>, which is stored in the {@code BootstrapMethods}
 * attribute as <a href="#bsmattr">described below</a>.
 * The second pair of bytes must be an index to a {@code CONSTANT_NameAndType}.
 * <p>
 * The first index specifies a bootstrap method used by the associated dynamic call sites.
 * The second index specifies the method name, argument types, and return type of the dynamic call site.
 * The structure of such an entry is therefore analogous to a {@code CONSTANT_Methodref},
 * except that the bootstrap method specifier reference replaces
 * the {@code CONSTANT_Class} reference of a {@code CONSTANT_Methodref} entry.
 *
 * <h3><a name="mtcon"></a>constant pool entries for {@linkplain java.lang.invoke.MethodType method types}</h3>
 * If a constant pool entry has the tag {@code CONSTANT_MethodType} (decimal 16),
 * it must contain exactly two more bytes, which must be an index to a {@code CONSTANT_Utf8}
 * entry which represents a method type descriptor.
 * <p>
 * The JVM will ensure that on first
 * execution of an {@code ldc} instruction for this entry, a {@link java.lang.invoke.MethodType MethodType}
 * will be created which represents the type descriptor.
 * Any classes mentioned in the {@code MethodType} will be loaded if necessary,
 * but not initialized.
 * Access checking and error reporting is performed exactly as it is for
 * references by {@code ldc} instructions to {@code CONSTANT_Class} constants.
 *
 * <h3><a name="mhcon"></a>constant pool entries for {@linkplain java.lang.invoke.MethodHandle method handles}</h3>
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
 * for this entry, a {@link java.lang.invoke.MethodHandle MethodHandle} will be created which represents
 * the field or method reference, according to the specific mode implied by the subtag.
 * <p>
 * As with {@code CONSTANT_Class} and {@code CONSTANT_MethodType} constants,
 * the {@code Class} or {@code MethodType} object which reifies the field or method's
 * type is created.  Any classes mentioned in this reification will be loaded if necessary,
 * but not initialized, and access checking and error reporting performed as usual.
 * <p>
 * Unlike the reflective {@code Lookup} API, there are no security manager calls made
 * when these constants are resolved.
 * <p>
 * The method handle itself will have a type and behavior determined by the subtag as follows:
 * <code>
 * <table border=1 cellpadding=5 summary="CONSTANT_MethodHandle subtypes">
 * <tr><th>N</th><th>subtag name</th><th>member</th><th>MH type</th><th>bytecode behavior</th><th>lookup expression</th></tr>
 * <tr><td>1</td><td>REF_getField</td><td>C.f:T</td><td>(C)T</td><td>getfield C.f:T</td>
 *               <td>{@linkplain java.lang.invoke.MethodHandles.Lookup#findGetter findGetter(C.class,"f",T.class)}</td></tr>
 * <tr><td>2</td><td>REF_getStatic</td><td>C.f:T</td><td>(&nbsp;)T</td><td>getstatic C.f:T</td>
 *               <td>{@linkplain java.lang.invoke.MethodHandles.Lookup#findStaticGetter findStaticGetter(C.class,"f",T.class)}</td></tr>
 * <tr><td>3</td><td>REF_putField</td><td>C.f:T</td><td>(C,T)void</td><td>putfield C.f:T</td>
 *               <td>{@linkplain java.lang.invoke.MethodHandles.Lookup#findSetter findSetter(C.class,"f",T.class)}</td></tr>
 * <tr><td>4</td><td>REF_putStatic</td><td>C.f:T</td><td>(T)void</td><td>putstatic C.f:T</td>
 *               <td>{@linkplain java.lang.invoke.MethodHandles.Lookup#findStaticSetter findStaticSetter(C.class,"f",T.class)}</td></tr>
 * <tr><td>5</td><td>REF_invokeVirtual</td><td>C.m(A*)T</td><td>(C,A*)T</td><td>invokevirtual C.m(A*)T</td>
 *               <td>{@linkplain java.lang.invoke.MethodHandles.Lookup#findVirtual findVirtual(C.class,"m",MT)}</td></tr>
 * <tr><td>6</td><td>REF_invokeStatic</td><td>C.m(A*)T</td><td>(C,A*)T</td><td>invokestatic C.m(A*)T</td>
 *               <td>{@linkplain java.lang.invoke.MethodHandles.Lookup#findStatic findStatic(C.class,"m",MT)}</td></tr>
 * <tr><td>7</td><td>REF_invokeSpecial</td><td>C.m(A*)T</td><td>(C,A*)T</td><td>invokespecial C.m(A*)T</td>
 *               <td>{@linkplain java.lang.invoke.MethodHandles.Lookup#findSpecial findSpecial(C.class,"m",MT,this.class)}</td></tr>
 * <tr><td>8</td><td>REF_newInvokeSpecial</td><td>C.&lt;init&gt;(A*)void</td><td>(A*)C</td><td>new C; dup; invokespecial C.&lt;init&gt;(A*)void</td>
 *               <td>{@linkplain java.lang.invoke.MethodHandles.Lookup#findConstructor findConstructor(C.class,MT)}</td></tr>
 * <tr><td>9</td><td>REF_invokeInterface</td><td>C.m(A*)T</td><td>(C,A*)T</td><td>invokeinterface C.m(A*)T</td>
 *               <td>{@linkplain java.lang.invoke.MethodHandles.Lookup#findVirtual findVirtual(C.class,"m",MT)}</td></tr>
 * </table>
 * </code>
 * Here, the type {@code C} is taken from the {@code CONSTANT_Class} reference associated
 * with the {@code CONSTANT_NameAndType} descriptor.
 * The field name {@code f} or method name {@code m} is taken from the {@code CONSTANT_NameAndType}
 * as is the result type {@code T} and (in the case of a method or constructor) the argument type sequence
 * {@code A*}.
 * <p>
 * Each method handle constant has an equivalent instruction sequence called its <em>bytecode behavior</em>.
 * In general, creating a method handle constant can be done in exactly the same circumstances that
 * the JVM would successfully resolve the symbolic references in the bytecode behavior.
 * Also, the type of a method handle constant is such that a valid {@code invokeExact} call
 * on the method handle has exactly the same JVM stack effects as the <em>bytecode behavior</em>.
 * Finally, calling a method handle constant on a valid set of arguments has exactly the same effect
 * and returns the same result (if any) as the corresponding <em>bytecode behavior</em>.
 * <p>
 * Each method handle constant also has an equivalent reflective <em>lookup expression</em>,
 * which is a query to a method in {@link java.lang.invoke.MethodHandles.Lookup}.
 * In the example lookup method expression given in the table above, the name {@code MT}
 * stands for a {@code MethodType} built from {@code T} and the sequence of argument types {@code A*}.
 * (Note that the type {@code C} is not prepended to the query type {@code MT} even if the member is non-static.)
 * In the case of {@code findSpecial}, the name {@code this.class} refers to the class containing
 * the bytecodes.
 * <p>
 * The special name {@code <clinit>} is not allowed.
 * The special name {@code <init>} is not allowed except for subtag 8 as shown.
 * <p>
 * The JVM verifier and linker apply the same access checks and restrictions for these references as for the hypothetical
 * bytecode instructions specified in the last column of the table.
 * A method handle constant will successfully resolve to a method handle if the symbolic references
 * of the corresponding bytecode instruction(s) would also resolve successfully.
 * Otherwise, an attempt to resolve the constant will throw equivalent linkage errors.
 * In particular, method handles to
 * private and protected members can be created in exactly those classes for which the corresponding
 * normal accesses are legal.
 * <p>
 * A constant may refer to a method or constructor with the {@code varargs}
 * bit (hexadecimal {@code 0x0080}) set in its modifier bitmask.
 * The method handle constant produced for such a method behaves as if
 * it were created by {@link java.lang.invoke.MethodHandle#asVarargsCollector asVarargsCollector}.
 * In other words, the constant method handle will exhibit variable arity,
 * when invoked via {@code invokeGeneric}.
 * On the other hand, its behavior with respect to {@code invokeExact} will be the same
 * as if the {@code varargs} bit were not set.
 * <p>
 * Although the {@code CONSTANT_MethodHandle} and {@code CONSTANT_MethodType} constant types
 * resolve class names, they do not force class initialization.
 * Method handle constants for subtags {@code REF_getStatic}, {@code REF_putStatic}, and {@code REF_invokeStatic}
 * may force class initialization on their first invocation, just like the corresponding bytecodes.
 * <p>
 * The rules of section 5.4.3 of
 * <cite>The Java&trade; Virtual Machine Specification</cite>
 * apply to the resolution of {@code CONSTANT_MethodType}, {@code CONSTANT_MethodHandle},
 * and {@code CONSTANT_InvokeDynamic} constants,
 * by the execution of {@code invokedynamic} and {@code ldc} instructions.
 * (Roughly speaking, this means that every use of a constant pool entry
 * must lead to the same outcome.
 * If the resolution succeeds, the same object reference is produced
 * by every subsequent execution of the same instruction.
 * If the resolution of the constant causes an error to occur,
 * the same error will be re-thrown on every subsequent attempt
 * to use this particular constant.)
 * <p>
 * Constants created by the resolution of these constant pool types are not necessarily
 * interned.  Except for {@code CONSTANT_Class} and {@code CONSTANT_String} entries,
 * two distinct constant pool entries might not resolve to the same reference
 * even if they contain the same symbolic reference.
 *
 * <h2><a name="bsm"></a>Bootstrap Methods</h2>
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
 * Next, the bootstrap method call is started, with at least four values being stacked:
 * <ul>
 * <li>a {@code MethodHandle}, the resolved bootstrap method itself </li>
 * <li>a {@code MethodHandles.Lookup}, a lookup object on the <em>caller class</em> in which dynamic call site occurs </li>
 * <li>a {@code String}, the method name mentioned in the call site </li>
 * <li>a {@code MethodType}, the resolved type descriptor of the call </li>
 * <li>optionally, one or more <a href="#args">additional static arguments</a> </li>
 * </ul>
 * The method handle is then applied to the other values as if by
 * {@link java.lang.invoke.MethodHandle#invokeGeneric invokeGeneric}.
 * The returned result must be a {@link java.lang.invoke.CallSite CallSite} (or a subclass).
 * The type of the call site's target must be exactly equal to the type
 * derived from the dynamic call site's type descriptor and passed to
 * the bootstrap method.
 * The call site then becomes permanently linked to the dynamic call site.
 * <p>
 * As long as each bootstrap method can be correctly invoked
 * by <code>invokeGeneric</code>, its detailed type is arbitrary.
 * For example, the first argument could be {@code Object}
 * instead of {@code MethodHandles.Lookup}, and the return type
 * could also be {@code Object} instead of {@code CallSite}.
 * (Note that the types and number of the stacked arguments limit
 * the legal kinds of bootstrap methods to appropriately typed
 * static methods and constructors of {@code CallSite} subclasses.)
 * <p>
 * After resolution, the linkage process may fail in a variety of ways.
 * All failures are reported by a {@link java.lang.BootstrapMethodError BootstrapMethodError},
 * which is thrown as the abnormal termination of the dynamic call
 * site execution.
 * The following circumstances will cause this:
 * <ul>
 * <li>the index to the bootstrap method specifier is out of range </li>
 * <li>the bootstrap method cannot be resolved </li>
 * <li>the {@code MethodType} to pass to the bootstrap method cannot be resolved </li>
 * <li>a static argument to the bootstrap method cannot be resolved
 *     (i.e., a {@code CONSTANT_Class}, {@code CONSTANT_MethodType},
 *     or {@code CONSTANT_MethodHandle} argument cannot be linked) </li>
 * <li>the bootstrap method has the wrong arity,
 *     causing {@code invokeGeneric} to throw {@code WrongMethodTypeException} </li>
 * <li>the bootstrap method has a wrong argument or return type </li>
 * <li>the bootstrap method invocation completes abnormally </li>
 * <li>the result from the bootstrap invocation is not a reference to
 *     an object of type {@link java.lang.invoke.CallSite CallSite} </li>
 * <li>the target of the {@code CallSite} does not have a target of
 *     the expected {@code MethodType} </li>
 * </ul>
 *
 * <h3><a name="linktime"></a>timing of linkage</h3>
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
 *
 * <p style="font-size:smaller;">
 * <em>Discussion:</em>
 * These rules do not enable the JVM to duplicate dynamic call sites,
 * or to issue &ldquo;causeless&rdquo; bootstrap method calls.
 * Every dynamic call site transitions at most once from unlinked to linked,
 * just before its first invocation.
 * There is no way to undo the effect of a completed bootstrap method call.
 *
 * <h3><a name="bsmattr">the {@code BootstrapMethods} attribute </h3>
 * Each {@code CONSTANT_InvokeDynamic} entry contains an index which references
 * a bootstrap method specifier; all such specifiers are contained in a separate array.
 * This array is defined by a class attribute named {@code BootstrapMethods}.
 * The body of this attribute consists of a sequence of byte pairs, all interpreted as
 * as 16-bit counts or constant pool indexes, in the {@code u2} format.
 * The attribute body starts with a count of bootstrap method specifiers,
 * which is immediately followed by the sequence of specifiers.
 * <p>
 * Each bootstrap method specifier contains an index to a
 * {@code CONSTANT_MethodHandle} constant, which is the bootstrap
 * method itself.
 * This is followed by a count, and then a sequence (perhaps empty) of
 * indexes to <a href="#args">additional static arguments</a>
 * for the bootstrap method.
 * <p>
 * During class loading, the verifier must check the structure of the
 * {@code BootstrapMethods} attribute.  In particular, each constant
 * pool index must be of the correct type.  A bootstrap method index
 * must refer to a {@code CONSTANT_MethodHandle} (tag 15).
 * Every other index must refer to a valid operand of an
 * {@code ldc_w} or {@code ldc2_w} instruction (tag 3..8 or 15..16).
 *
 * <h3><a name="args">static arguments to the bootstrap method</h3>
 * An {@code invokedynamic} instruction specifies at least three arguments
 * to pass to its bootstrap method:
 * The caller class (expressed as a {@link java.lang.invoke.MethodHandles.Lookup Lookup object},
 * the name (extracted from the {@code CONSTANT_NameAndType} entry),
 * and the type (also extracted from the {@code CONSTANT_NameAndType} entry).
 * The {@code invokedynamic} instruction may specify additional metadata values
 * to pass to its bootstrap method.
 * Collectively, these values are called <em>static arguments</em> to the
 * {@code invokedynamic} instruction, because they are used once at link
 * time to determine the instruction's behavior on subsequent sets of
 * <em>dynamic arguments</em>.
 * <p>
 * Static arguments are used to communicate application-specific meta-data
 * to the bootstrap method.
 * Drawn from the constant pool, they may include references to classes, method handles,
 * strings, or numeric data that may be relevant to the task of linking that particular call site.
 * <p>
 * Static arguments are specified constant pool indexes stored in the {@code BootstrapMethods} attribute.
 * Before the bootstrap method is invoked, each index is used to compute an {@code Object}
 * reference to the indexed value in the constant pool.
 * The valid constant pool entries are listed in this table:
 * <code>
 * <table border=1 cellpadding=5 summary="Static argument types">
 * <tr><th>entry type</th><th>argument type</th><th>argument value</th></tr>
 * <tr><td>CONSTANT_String</td><td><code>java.lang.String</code></td><td>the indexed string literal</td></tr>
 * <tr><td>CONSTANT_Class</td><td><code>java.lang.Class</code></td><td>the indexed class, resolved</td></tr>
 * <tr><td>CONSTANT_Integer</td><td><code>java.lang.Integer</code></td><td>the indexed int value</td></tr>
 * <tr><td>CONSTANT_Long</td><td><code>java.lang.Long</code></td><td>the indexed long value</td></tr>
 * <tr><td>CONSTANT_Float</td><td><code>java.lang.Float</code></td><td>the indexed float value</td></tr>
 * <tr><td>CONSTANT_Double</td><td><code>java.lang.Double</code></td><td>the indexed double value</td></tr>
 * <tr><td>CONSTANT_MethodHandle</td><td><code>java.lang.invoke.MethodHandle</code></td><td>the indexed method handle constant</td></tr>
 * <tr><td>CONSTANT_MethodType</td><td><code>java.lang.invoke.MethodType</code></td><td>the indexed method type constant</td></tr>
 * </table>
 * </code>
 * <p>
 * If a given {@code invokedynamic} instruction specifies no static arguments,
 * the instruction's bootstrap method will be invoked on three arguments,
 * conveying the instruction's caller class, name, and method type.
 * If the {@code invokedynamic} instruction specifies one or more static arguments,
 * those values will be passed as additional arguments to the method handle.
 * (Note that because there is a limit of 255 arguments to any method,
 * at most 252 extra arguments can be supplied.)
 * The bootstrap method will be invoked as if by either {@code invokeGeneric}
 * or {@code invokeWithArguments}.  (There is no way to tell the difference.)
 * <p>
 * The normal argument conversion rules for {@code invokeGeneric} apply to all stacked arguments.
 * For example, if a pushed value is a primitive type, it may be converted to a reference by boxing conversion.
 * If the bootstrap method is a variable arity method (its modifier bit {@code 0x0080} is set),
 * then some or all of the arguments specified here may be collected into a trailing array parameter.
 * (This is not a special rule, but rather a useful consequence of the interaction
 * between {@code CONSTANT_MethodHandle} constants, the modifier bit for variable arity methods,
 * and the {@code java.lang.invoke.MethodHandle#asVarargsCollector asVarargsCollector} transformation.)
 * <p>
 * Given these rules, here are examples of legal bootstrap method declarations,
 * given various numbers {@code N} of extra arguments.
 * The first rows (marked {@code *}) will work for any number of extra arguments.
 * <code>
 * <table border=1 cellpadding=5 summary="Static argument types">
 * <tr><th>N</th><th>sample bootstrap method</th></tr>
 * <tr><td>*</td><td><code>CallSite bootstrap(Lookup caller, String name, MethodType type, Object... args)</code></td></tr>
 * <tr><td>*</td><td><code>CallSite bootstrap(Object... args)</code></td></tr>
 * <tr><td>*</td><td><code>CallSite bootstrap(Object caller, Object... nameAndTypeWithArgs)</code></td></tr>
 * <tr><td>0</td><td><code>CallSite bootstrap(Lookup caller, String name, MethodType type)</code></td></tr>
 * <tr><td>0</td><td><code>CallSite bootstrap(Lookup caller, Object... nameAndType)</code></td></tr>
 * <tr><td>1</td><td><code>CallSite bootstrap(Lookup caller, String name, MethodType type, Object arg)</code></td></tr>
 * <tr><td>2</td><td><code>CallSite bootstrap(Lookup caller, String name, MethodType type, Object... args)</code></td></tr>
 * <tr><td>2</td><td><code>CallSite bootstrap(Lookup caller, String name, MethodType type, String... args)</code></td></tr>
 * <tr><td>2</td><td><code>CallSite bootstrap(Lookup caller, String name, MethodType type, String x, int y)</code></td></tr>
 * </table>
 * </code>
 * The last example assumes that the extra arguments are of type
 * {@code CONSTANT_String} and {@code CONSTANT_Integer}, respectively.
 * The second-to-last example assumes that all extra arguments are of type
 * {@code CONSTANT_String}.
 * The other examples work with all types of extra arguments.
 * <p>
 * As noted above, the actual method type of the bootstrap method can vary.
 * For example, the fourth argument could be {@code MethodHandle},
 * if that is the type of the corresponding constant in
 * the {@code CONSTANT_InvokeDynamic} entry.
 * In that case, the {@code invokeGeneric} call will pass the extra method handle
 * constant as an {@code Object}, but the type matching machinery of {@code invokeGeneric}
 * will cast the reference back to {@code MethodHandle} before invoking the bootstrap method.
 * (If a string constant were passed instead, by badly generated code, that cast would then fail,
 * resulting in a {@code BootstrapMethodError}.)
 * <p>
 * Extra bootstrap method arguments are intended to allow language implementors
 * to safely and compactly encode metadata.
 * In principle, the name and extra arguments are redundant,
 * since each call site could be given its own unique bootstrap method.
 * Such a practice is likely to produce large class files and constant pools.
 *
 * <h2><a name="structs"></a>Structure Summary</h2>
 * <blockquote><pre>// summary of constant and attribute structures
struct CONSTANT_MethodHandle_info {
  u1 tag = 15;
  u1 reference_kind;       // 1..8 (one of REF_invokeVirtual, etc.)
  u2 reference_index;      // index to CONSTANT_Fieldref or *Methodref
}
struct CONSTANT_MethodType_info {
  u1 tag = 16;
  u2 descriptor_index;    // index to CONSTANT_Utf8, as in NameAndType
}
struct CONSTANT_InvokeDynamic_info {
  u1 tag = 18;
  u2 bootstrap_method_attr_index;  // index into BootstrapMethods_attr
  u2 name_and_type_index;          // index to CONSTANT_NameAndType, as in Methodref
}
struct BootstrapMethods_attr {
 u2 name;  // CONSTANT_Utf8 = "BootstrapMethods"
 u4 size;
 u2 bootstrap_method_count;
 struct bootstrap_method_specifier {
   u2 bootstrap_method_ref;  // index to CONSTANT_MethodHandle
   u2 bootstrap_argument_count;
   u2 bootstrap_arguments[bootstrap_argument_count];  // constant pool indexes
 } bootstrap_methods[bootstrap_method_count];
}
 * </pre></blockquote>
 *
 * @author John Rose, JSR 292 EG
 * @since 1.7
 */

package java.lang.invoke;
