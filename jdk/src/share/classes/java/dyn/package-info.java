/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * <li>The JVM bytecode format supports immediate constants of
 * the classes {@link java.dyn.MethodHandle MethodHandle} and {@link java.dyn.MethodType MethodType}.
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
 * (The tag value 17 is also temporarily allowed.  See below.)
 * The entry specifies the following information:
 * <ul>
 * <li>a bootstrap method (a {@link java.dyn.MethodHandle MethodHandle} constant)</li>
 * <li>the dynamic invocation name (a UTF8 string)</li>
 * <li>the argument and return types of the call (encoded as a signature in a UTF8 string)</li>
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
 * <p style="font-size:smaller;">
 * (Historic Note: Some older JVMs may allow the index of a {@code CONSTANT_NameAndType}
 * instead of a {@code CONSTANT_InvokeDynamic}.  In earlier, obsolete versions of this API, the
 * bootstrap method was specified dynamically, in a per-class basis, during class initialization.)
 *
 * <h3><a name="indycon"></a>constant pool entries for {@code invokedynamic} instructions</h3>
 * If a constant pool entry has the tag {@code CONSTANT_InvokeDynamic} (decimal 18),
 * it must contain exactly four more bytes after the tag.
 * These bytes are interpreted as two 16-bit indexes, in the usual {@code u2} format.
 * The first pair of bytes after the tag must be an index into a side table called the
 * <em>bootstrap method table</em>, which is stored in the {@code BootstrapMethods}
 * attribute as <a href="#bsmattr">described below</a>.
 * The second pair of bytes must be an index to a {@code CONSTANT_NameAndType}.
 * This table is not part of the constant pool.  Instead, it is stored
 * in a class attribute named {@code BootstrapMethods}, described below.
 * <p>
 * The first index specifies a bootstrap method used by the associated dynamic call sites.
 * The second index specifies the method name, argument types, and return type of the dynamic call site.
 * The structure of such an entry is therefore analogous to a {@code CONSTANT_Methodref},
 * except that the bootstrap method specifier reference replaces
 * the {@code CONSTANT_Class} reference of a {@code CONSTANT_Methodref} entry.
 * <p>
 * Some older JVMs may allow an older constant pool entry tag of decimal 17.
 * The format and behavior of a constant pool entry with this tag is identical to
 * an entry with a tag of decimal 18, except that the first index refers directly
 * to a {@code CONSTANT_MethodHandle} to use as the bootstrap method.
 * This format does not require the bootstrap method table.
 *
 * <p style="font-size:smaller;">
 * <em>(Note: The Proposed Final Draft of this specification is likely to support
 * only the tag 18, not the tag 17.)</em>
 *
 * <h3><a name="mtcon"></a>constant pool entries for {@linkplain java.dyn.MethodType method types}</h3>
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
 * <h3><a name="mhcon"></a>constant pool entries for {@linkplain java.dyn.MethodHandle method handles}</h3>
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
 * type is created.  Any classes mentioned in this reification will be loaded if necessary,
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
 * The special name {@code <clinit>} is not allowed.
 * The special name {@code <init>} is not allowed except for subtag 8 as shown.
 * <p>
 * The JVM verifier and linker apply the same access checks and restrictions for these references as for the hypothetical
 * bytecode instructions specified in the last column of the table.  In particular, method handles to
 * private and protected members can be created in exactly those classes for which the corresponding
 * normal accesses are legal.
 * <p>
 * A constant may refer to a method or constructor with the {@code varargs}
 * bit (hexadecimal {@code 80}) set in its modifier bitmask.
 * The method handle constant produced for such a method behaves the same
 * as if the {@code varargs} bit were not set.
 * The argument-collecting behavior of {@code varargs} can be emulated by
 * adapting the method handle constant with
 * {@link java.dyn.MethodHandle#asCollector asCollector}.
 * There is no provision for doing this automatically.
 * <p>
 * Although the {@code CONSTANT_MethodHandle} and {@code CONSTANT_MethodType} constant types
 * resolve class names, they do not force class initialization.
 * Method handle constants for subtags {@code REF_getStatic}, {@code REF_putStatic}, and {@code REF_invokeStatic}
 * may force class initialization on their first invocation, just like the corresponding bytecodes.
 * <p>
 * The rules of section 5.4.3 of the
 * <a href="http://java.sun.com/docs/books/jvms/second_edition/html/ConstantPool.doc.html#73492">JVM Specification</a>
 * apply to the resolution of {@code CONSTANT_MethodType}, {@code CONSTANT_MethodHandle},
 * and {@code CONSTANT_InvokeDynamic} constants,
 * by the execution of {@code invokedynamic} and {@code ldc} instructions.
 * (Roughly speaking, this means that every use of a constant pool entry
 * must lead to the same outcome.
 * If the resoultion succeeds, the same object reference is produced
 * by every subsequent execution of the same instruction.
 * If the resolution of the constant causes an error to occur,
 * the same error will be re-thrown on every subsequent attempt
 * to use this particular constant.)
 * <p>
 * Constants created by the resolution of these constant pool types are not necessarily
 * interned.  Except for {@link CONSTANT_Class} and {@link CONSTANT_String} entries,
 * two distinct constant pool entries might not resolve to the same reference
 * even if they contain the same symbolic reference.
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
 * Next, the bootstrap method call is started, with four or five values being stacked:
 * <ul>
 * <li>a {@code MethodHandle}, the resolved bootstrap method itself </li>
 * <li>a {@code MethodHandles.Lookup}, a lookup object on the <em>caller class</em> in which dynamic call site occurs </li>
 * <li>a {@code String}, the method name mentioned in the call site </li>
 * <li>a {@code MethodType}, the resolved type signature of the call </li>
 * <li>optionally, a single object representing one or more <a href="#args">additional static arguments</a> </li>
 * </ul>
 * The method handle is then applied to the other values as if by
 * {@link java.dyn.MethodHandle#invokeGeneric invokeGeneric}.
 * The returned result must be a {@link java.dyn.CallSite CallSite} (or a subclass).
 * The type of the call site's target must be exactly equal to the type
 * derived from the dynamic call site signature and passed to
 * the bootstrap method.
 * The call site then becomes permanently linked to the dynamic call site.
 * <p>
 * As long as each bootstrap method can be correctly invoked
 * by <code>invokeGeneric</code>, its detailed type is arbitrary.
 * For example, the first argument could be {@code Object}
 * instead of {@code MethodHandles.Lookup}, and the return type
 * could also be {@code Object} instead of {@code CallSite}.
 * <p>
 * As with any method handle constant, a {@code varargs} modifier bit
 * on the bootstrap method is ignored.
 * <p>
 * Note that the first argument of the bootstrap method cannot be
 * a simple {@code Class} reference.  (This is a change from earlier
 * versions of this specification.  If the caller class is needed,
 * it is easy to {@linkplain java.dyn.MethodHandles.Lookup#lookupClass() extract it}
 * from the {@code Lookup} object.)
 * <p>
 * After resolution, the linkage process may fail in a variety of ways.
 * All failures are reported by an {@link java.dyn.InvokeDynamicBootstrapError InvokeDynamicBootstrapError},
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
 *     an object of type {@link java.dyn.CallSite CallSite} </li>
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
 * {@link java.dyn.CallSite CallSite} objects, one for each linkage request.
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
 * (Historic Note: Unlike some previous versions of this specification,
 * these rules do not enable the JVM to duplicate dynamic call sites,
 * or to issue &ldquo;causeless&rdquo; bootstrap method calls.
 * Every dynamic call site transitions at most once from unlinked to linked,
 * just before its first invocation.)
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
 * The caller class (expressed as a {@link java.dyn.MethodHandles.Lookup Lookup object},
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
 * If the value is a primitive type, it is converted to a reference by boxing conversion.
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
 * <tr><td>CONSTANT_MethodHandle</td><td><code>java.dyn.MethodHandle</code></td><td>the indexed method handle constant</td></tr>
 * <tr><td>CONSTANT_MethodType</td><td><code>java.dyn.MethodType</code></td><td>the indexed method type constant</td></tr>
 * </table>
 * </code>
 * <p>
 * If a given {@code invokedynamic} instruction specifies no static arguments,
 * the instruction's bootstrap method will be invoked on three arguments,
 * conveying the instruction's caller class, name, and method type.
 * If the {@code invokedynamic} instruction specifies one or more static arguments,
 * a fourth argument will be passed to the bootstrap argument,
 * either an {@code Object} reference to the sole extra argument (if there is one)
 * or an {@code Object} array of references to all the arguments (if there are two or more),
 * as if the bootstrap method is a variable-arity method.
 * <code>
 * <table border=1 cellpadding=5 summary="Static argument types">
 * <tr><th>N</th><th>sample bootstrap method</th></tr>
 * <tr><td>0</td><td><code>CallSite bootstrap(Lookup caller, String name, MethodType type)</code></td></tr>
 * <tr><td>1</td><td><code>CallSite bootstrap(Lookup caller, String name, MethodType type, Object arg)</code></td></tr>
 * <tr><td>2</td><td><code>CallSite bootstrap(Lookup caller, String name, MethodType type, Object... args)</code></td></tr>
 * </table>
 * </code>
 * <p>
 * The argument and return types listed here are used by the {@code invokeGeneric}
 * call to the bootstrap method.
 * As noted above, the actual method type of the bootstrap method can vary.
 * For example, the fourth argument could be {@code MethodHandle},
 * if that is the type of the corresponding constant in
 * the {@code CONSTANT_InvokeDynamic} entry.
 * In that case, the {@code invokeGeneric} call will pass the extra method handle
 * constant as an {@code Object}, but the type matching machinery of {@code invokeGeneric}
 * will cast the reference back to {@code MethodHandle} before invoking the bootstrap method.
 * (If a string constant were passed instead, by badly generated code, that cast would then fail.)
 * <p>
 * If the fourth argument is an array, the array element type must be {@code Object},
 * since object arrays (as produced by the JVM at this point) cannot be converted
 * to other array types.
 * <p>
 * If an array is provided, it will appear to be freshly allocated.
 * That is, the same array will not appear to two bootstrap method calls.
 * <p>
 * Extra bootstrap method arguments are intended to allow language implementors
 * to safely and compactly encode metadata.
 * In principle, the name and extra arguments are redundant,
 * since each call site could be given its own unique bootstrap method.
 * Such a practice is likely to produce large class files and constant pools.
 *
 * <p style="font-size:smaller;">
 * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
 * (Usage Note: There is no mechanism for specifying five or more positional arguments to the bootstrap method.
 * If there are two or more arguments, the Java code of the bootstrap method is required to extract them from
 * a varargs-style object array.
 * This design uses varargs because it anticipates some use cases where bootstrap arguments
 * contribute components of variable-length structures, such as virtual function tables
 * or interpreter token streams.
 * Such parameters would be awkward or impossible to manage if represented
 * as normal positional method arguments,
 * since there would need to be one Java method per length.
 * On balance, leaving out the varargs feature would cause more trouble to users than keeping it.
 * Also, this design allows bootstrap methods to be called in a limited JVM stack depth.
 * At both the user and JVM level, the difference between varargs and non-varargs
 * calling sequences can easily be bridged via the
 * {@link java.dyn.MethodHandle#asSpreader asSpreader}
 * and {@link java.dyn.MethodHandle#asSpreader asCollector} methods.)
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
struct CONSTANT_InvokeDynamic_17_info {
  u1 tag = 17;
  u2 bootstrap_method_index;   // index to CONSTANT_MethodHandle
  u2 name_and_type_index;      // same as for CONSTANT_Methodref, etc.
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
 * <p>
 * <em>Note: The Proposed Final Draft of JSR 292 may remove the constant tag 17,
 * for the sake of simplicity.</em>
 *
 * @author John Rose, JSR 292 EG
 */

package java.dyn;
