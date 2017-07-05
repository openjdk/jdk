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

package java.dyn;

//import sun.dyn.*;

import sun.dyn.Access;
import sun.dyn.MethodHandleImpl;

import static java.dyn.MethodHandles.invokers;  // package-private API
import static sun.dyn.MemberName.newIllegalArgumentException;  // utility

/**
 * A method handle is a typed, directly executable reference to a method,
 * constructor, field, or similar low-level operation, with optional
 * conversion or substitution of arguments or return values.
 * <p>
 * Method handles are strongly typed according to signature.
 * They are not distinguished by method name or enclosing class.
 * A method handle must be invoked under a signature which exactly matches
 * the method handle's own {@link MethodType method type}.
 * <p>
 * Every method handle confesses its type via the {@code type} accessor.
 * The structure of this type is a series of classes, one of which is
 * the return type of the method (or {@code void.class} if none).
 * <p>
 * Every method handle appears as an object containing a method named
 * {@code invoke}, whose signature exactly matches
 * the method handle's type.
 * A Java method call expression, which compiles to an
 * {@code invokevirtual} instruction,
 * can invoke this method from Java source code.
 * <p>
 * Every call to a method handle specifies an intended method type,
 * which must exactly match the type of the method handle.
 * (The type is specified in the {@code invokevirtual} instruction,
 * via a {@code CONSTANT_NameAndType} constant pool entry.)
 * The call looks within the receiver object for a method
 * named {@code invoke} of the intended method type.
 * The call fails with a {@link WrongMethodTypeException}
 * if the method does not exist, even if there is an {@code invoke}
 * method of a closely similar signature.
 * As with other kinds
 * of methods in the JVM, signature matching during method linkage
 * is exact, and does not allow for language-level implicit conversions
 * such as {@code String} to {@code Object} or {@code short} to {@code int}.
 * <p>
 * A method handle is an unrestricted capability to call a method.
 * A method handle can be formed on a non-public method by a class
 * that has access to that method; the resulting handle can be used
 * in any place by any caller who receives a reference to it.  Thus, access
 * checking is performed when the method handle is created, not
 * (as in reflection) every time it is called.  Handles to non-public
 * methods, or in non-public classes, should generally be kept secret.
 * They should not be passed to untrusted code.
 * <p>
 * Bytecode in an extended JVM can directly call a method handle's
 * {@code invoke} from an {@code invokevirtual} instruction.
 * The receiver class type must be {@code MethodHandle} and the method name
 * must be {@code invoke}.  The signature of the invocation
 * (after resolving symbolic type names) must exactly match the method type
 * of the target method.
 * <p>
 * Every {@code invoke} method always throws {@link Exception},
 * which is to say that there is no static restriction on what a method handle
 * can throw.  Since the JVM does not distinguish between checked
 * and unchecked exceptions (other than by their class, of course),
 * there is no particular effect on bytecode shape from ascribing
 * checked exceptions to method handle invocations.  But in Java source
 * code, methods which perform method handle calls must either explicitly
 * throw {@code Exception}, or else must catch all checked exceptions locally.
 * <p>
 * Bytecode in an extended JVM can directly obtain a method handle
 * for any accessible method from a {@code ldc} instruction
 * which refers to a {@code CONSTANT_Methodref} or
 * {@code CONSTANT_InterfaceMethodref} constant pool entry.
 * <p>
 * All JVMs can also use a reflective API called {@code MethodHandles}
 * for creating and calling method handles.
 * <p>
 * A method reference may refer either to a static or non-static method.
 * In the non-static case, the method handle type includes an explicit
 * receiver argument, prepended before any other arguments.
 * In the method handle's type, the initial receiver argument is typed
 * according to the class under which the method was initially requested.
 * (E.g., if a non-static method handle is obtained via {@code ldc},
 * the type of the receiver is the class named in the constant pool entry.)
 * <p>
 * When a method handle to a virtual method is invoked, the method is
 * always looked up in the receiver (that is, the first argument).
 * <p>
 * A non-virtual method handles to a specific virtual method implementation
 * can also be created.  These do not perform virtual lookup based on
 * receiver type.  Such a method handle simulates the effect of
 * an {@code invokespecial} instruction to the same method.
 * <p>
 * Here are some examples of usage:
 * <p><blockquote><pre>
Object x, y; String s; int i;
MethodType mt; MethodHandle mh;
MethodHandles.Lookup lookup = MethodHandles.lookup();
// mt is {(char,char) =&gt; String}
mt = MethodType.methodType(String.class, char.class, char.class);
mh = lookup.findVirtual(String.class, "replace", mt);
// (Ljava/lang/String;CC)Ljava/lang/String;
s = mh.&lt;String&gt;invokeExact("daddy",'d','n');
assert(s.equals("nanny"));
// weakly typed invocation (using MHs.invoke)
s = (String) mh.invokeVarargs("sappy", 'p', 'v');
assert(s.equals("savvy"));
// mt is {Object[] =&gt; List}
mt = MethodType.methodType(java.util.List.class, Object[].class);
mh = lookup.findStatic(java.util.Arrays.class, "asList", mt);
// mt is {(Object,Object,Object) =&gt; Object}
mt = MethodType.genericMethodType(3);
mh = MethodHandles.collectArguments(mh, mt);
// mt is {(Object,Object,Object) =&gt; Object}
// (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
x = mh.invokeExact((Object)1, (Object)2, (Object)3);
assert(x.equals(java.util.Arrays.asList(1,2,3)));
// mt is { =&gt; int}
mt = MethodType.methodType(int.class);
mh = lookup.findVirtual(java.util.List.class, "size", mt);
// (Ljava/util/List;)I
i = mh.&lt;int&gt;invokeExact(java.util.Arrays.asList(1,2,3));
assert(i == 3);
 * </pre></blockquote>
 * Each of the above calls generates a single invokevirtual instruction
 * with the name {@code invoke} and the type descriptors indicated in the comments.
 * The argument types are taken directly from the actual arguments,
 * while the return type is taken from the type parameter.
 * (This type parameter may be a primitive, and it defaults to {@code Object}.)
 * <p>
 * <em>A note on generic typing:</em>  Method handles do not represent
 * their function types in terms of Java parameterized (generic) types,
 * because there are three mismatches between function types and parameterized
 * Java types.
 * <ol>
 * <li>Method types range over all possible arities,
 * from no arguments to an arbitrary number of arguments.
 * Generics are not variadic, and so cannot represent this.</li>
 * <li>Method types can specify arguments of primitive types,
 * which Java generic types cannot range over.</li>
 * <li>Higher order functions over method handles (combinators) are
 * often generic across a wide range of function types, including
 * those of multiple arities.  It is impossible to represent such
 * genericity with a Java type parameter.</li>
 * </ol>
 * Signature polymorphic methods in this class appear to be documented
 * as having type parameters for return types and a parameter, but that is
 * merely a documentation convention.  These type parameters do
 * not play a role in type-checking method handle invocations.
 * <p>
 * Note: Like classes and strings, method handles that correspond directly
 * to fields and methods can be represented directly as constants to be
 * loaded by {@code ldc} bytecodes.
 *
 * @see MethodType
 * @see MethodHandles
 * @author John Rose, JSR 292 EG
 */
public abstract class MethodHandle
        // Note: This is an implementation inheritance hack, and will be removed
        // with a JVM change which moves the required hidden state onto this class.
        extends MethodHandleImpl
{
    private static Access IMPL_TOKEN = Access.getToken();

    // interface MethodHandle<R throws X extends Exception,A...>
    // { MethodType<R throws X,A...> type(); public R invokeExact(A...) throws X; }

    /**
     * Internal marker interface which distinguishes (to the Java compiler)
     * those methods which are signature polymorphic.
     */
    @java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD,java.lang.annotation.ElementType.TYPE})
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
    @interface PolymorphicSignature { }

    private MethodType type;

    /**
     * Report the type of this method handle.
     * Every invocation of this method handle must exactly match this type.
     * @return the method handle type
     */
    public final MethodType type() {
        return type;
    }

    /**
     * The constructor for MethodHandle may only be called by privileged code.
     * Subclasses may be in other packages, but must possess
     * a token which they obtained from MH with a security check.
     * @param token non-null object which proves access permission
     * @param type type (permanently assigned) of the new method handle
     */
    protected MethodHandle(Access token, MethodType type) {
        super(token);
        Access.check(token);
        this.type = type;
    }

    private void initType(MethodType type) {
        type.getClass();  // elicit NPE
        if (this.type != null)  throw new InternalError();
        this.type = type;
    }

    static {
        // This hack allows the implementation package special access to
        // the internals of MethodHandle.  In particular, the MTImpl has all sorts
        // of cached information useful to the implementation code.
        MethodHandleImpl.setMethodHandleFriend(IMPL_TOKEN, new MethodHandleImpl.MethodHandleFriend() {
            public void initType(MethodHandle mh, MethodType type) { mh.initType(type); }
        });
    }

    /** The string of a direct method handle is the simple name of its target method.
     * The string of an adapter or bound method handle is the string of its
     * target method handle.
     * The string of a Java method handle is the string of its entry point method,
     * unless the Java method handle overrides the toString method.
     */
    @Override
    public String toString() {
        return MethodHandleImpl.getNameString(IMPL_TOKEN, this);
    }

    //// This is the "Method Handle Kernel API" discussed at the JVM Language Summit, 9/2009.
    //// Implementations here currently delegate to statics in MethodHandles.  Some of those statics
    //// will be deprecated.  Others will be kept as "algorithms" to supply degrees of freedom
    //// not present in the Kernel API.

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Invoke the method handle, allowing any caller signature, but requiring an exact signature match.
     * The signature at the call site of {@code invokeExact} must
     * exactly match this method handle's {@code type}.
     * No conversions are allowed on arguments or return values.
     */
    public final native @PolymorphicSignature <R,A> R invokeExact(A... args) throws Throwable;

    // FIXME: remove this transitional form
    /** @deprecated transitional form defined in EDR but removed in PFD */
    public final native @PolymorphicSignature <R,A> R invoke(A... args) throws Throwable;

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Invoke the method handle, allowing any caller signature,
     * and performing simple conversions for arguments and return types.
     * The signature at the call site of {@code invokeGeneric} must
     * have the same arity as this method handle's {@code type}.
     * The same conversions are allowed on arguments or return values as are supported by
     * by {@link MethodHandles#convertArguments}.
     * If the call site signature exactly matches this method handle's {@code type},
     * the call proceeds as if by {@link #invokeExact}.
     */
    public final native @PolymorphicSignature <R,A> R invokeGeneric(A... args) throws Throwable;

    // ?? public final native @PolymorphicSignature <R,A,V> R invokeVarargs(A args, V[] varargs) throws Throwable;

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Perform a varargs invocation, passing the arguments in the given array
     * to the method handle, as if via {@link #invokeGeneric} from a call site
     * which mentions only the type {@code Object}, and whose arity is the length
     * of the argument array.
     * <p>
     * The length of the arguments array must equal the parameter count
     * of the target's type.
     * The arguments array is spread into separate arguments.
     * <p>
     * In order to match the type of the target, the following argument
     * conversions are applied as necessary:
     * <ul>
     * <li>reference casting
     * <li>unboxing
     * </ul>
     * The following conversions are not applied:
     * <ul>
     * <li>primitive conversions (e.g., {@code byte} to {@code int}
     * <li>varargs conversions other than the initial spread
     * <li>any application-specific conversions (e.g., string to number)
     * </ul>
     * The result returned by the call is boxed if it is a primitive,
     * or forced to null if the return type is void.
     * <p>
     * This call is equivalent to the following code:
     * <p><blockquote><pre>
     *   MethodHandle invoker = MethodHandles.genericInvoker(this.type(), 0, true);
     *   Object result = invoker.invokeExact(this, arguments);
     * </pre></blockquote>
     * @param arguments the arguments to pass to the target
     * @return the result returned by the target
     * @see MethodHandles#genericInvoker
     */
    public final Object invokeVarargs(Object... arguments) throws Throwable {
        int argc = arguments == null ? 0 : arguments.length;
        MethodType type = type();
        if (argc <= 10) {
            MethodHandle invoker = MethodHandles.invokers(type).genericInvoker();
            switch (argc) {
                case 0:  return invoker.invokeExact(this);
                case 1:  return invoker.invokeExact(this,
                                    arguments[0]);
                case 2:  return invoker.invokeExact(this,
                                    arguments[0], arguments[1]);
                case 3:  return invoker.invokeExact(this,
                                    arguments[0], arguments[1], arguments[2]);
                case 4:  return invoker.invokeExact(this,
                                    arguments[0], arguments[1], arguments[2],
                                    arguments[3]);
                case 5:  return invoker.invokeExact(this,
                                    arguments[0], arguments[1], arguments[2],
                                    arguments[3], arguments[4]);
                case 6:  return invoker.invokeExact(this,
                                    arguments[0], arguments[1], arguments[2],
                                    arguments[3], arguments[4], arguments[5]);
                case 7:  return invoker.invokeExact(this,
                                    arguments[0], arguments[1], arguments[2],
                                    arguments[3], arguments[4], arguments[5],
                                    arguments[6]);
                case 8:  return invoker.invokeExact(this,
                                    arguments[0], arguments[1], arguments[2],
                                    arguments[3], arguments[4], arguments[5],
                                    arguments[6], arguments[7]);
                case 9:  return invoker.invokeExact(this,
                                    arguments[0], arguments[1], arguments[2],
                                    arguments[3], arguments[4], arguments[5],
                                    arguments[6], arguments[7], arguments[8]);
                case 10:  return invoker.invokeExact(this,
                                    arguments[0], arguments[1], arguments[2],
                                    arguments[3], arguments[4], arguments[5],
                                    arguments[6], arguments[7], arguments[8],
                                    arguments[9]);
            }
        }

        // more than ten arguments get boxed in a varargs list:
        MethodHandle invoker = MethodHandles.invokers(type).varargsInvoker(0);
        return invoker.invokeExact(this, arguments);
    }
    /** Equivalent to {@code invokeVarargs(arguments.toArray())}. */
    public final Object invokeVarargs(java.util.List<?> arguments) throws Throwable {
        return invokeVarargs(arguments.toArray());
    }

    /*  --- this is intentionally NOT a javadoc yet ---
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce an adapter method handle which adapts the type of the
     * current method handle to a new type by pairwise argument conversion.
     * The original type and new type must have the same number of arguments.
     * The resulting method handle is guaranteed to confess a type
     * which is equal to the desired new type.
     * <p>
     * If the original type and new type are equal, returns {@code this}.
     * <p>
     * The following conversions are applied as needed both to
     * arguments and return types.  Let T0 and T1 be the differing
     * new and old parameter types (or old and new return types)
     * for corresponding values passed by the new and old method types.
     * Given those types T0, T1, one of the following conversions is applied
     * if possible:
     * <ul>
     * <li>If T0 and T1 are references, and T1 is not an interface type,
     *     then a cast to T1 is applied.
     *     (The types do not need to be related in any particular way.)
     * <li>If T0 and T1 are references, and T1 is an interface type,
     *     then the value of type T0 is passed as a T1 without a cast.
     *     (This treatment of interfaces follows the usage of the bytecode verifier.)
     * <li>If T0 and T1 are primitives, then a Java casting
     *     conversion (JLS 5.5) is applied, if one exists.
     * <li>If T0 and T1 are primitives and one is boolean,
     *     the boolean is treated as a one-bit unsigned integer.
     *     (This treatment follows the usage of the bytecode verifier.)
     *     A conversion from another primitive type behaves as if
     *     it first converts to byte, and then masks all but the low bit.
     * <li>If T0 is a primitive and T1 a reference, a boxing
     *     conversion is applied if one exists, possibly followed by
     *     an reference conversion to a superclass.
     *     T1 must be a wrapper class or a supertype of one.
     *     If T1 is a wrapper class, T0 is converted if necessary
     *     to T1's primitive type by one of the preceding conversions.
     *     Otherwise, T0 is boxed, and its wrapper converted to T1.
     * <li>If T0 is a reference and T1 a primitive, an unboxing
     *     conversion is applied if one exists, possibly preceded by
     *     a reference conversion to a wrapper class.
     *     T0 must be a wrapper class or a supertype of one.
     *     If T0 is a wrapper class, its primitive value is converted
     *     if necessary to T1 by one of the preceding conversions.
     *     Otherwise, T0 is converted directly to the wrapper type for T1,
     *     which is then unboxed.
     * <li>If the return type T1 is void, any returned value is discarded
     * <li>If the return type T0 is void and T1 a reference, a null value is introduced.
     * <li>If the return type T0 is void and T1 a primitive, a zero value is introduced.
     * </ul>
     * <p>
     */
    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce an adapter method handle which adapts the type of the
     * current method handle to a new type by pairwise argument conversion.
     * The original type and new type must have the same number of arguments.
     * The resulting method handle is guaranteed to confess a type
     * which is equal to the desired new type.
     * <p>
     * If the original type and new type are equal, returns {@code this}.
     * <p>
     * This method is equivalent to {@link MethodHandles#convertArguments}.
     * @param newType the expected type of the new method handle
     * @return a method handle which delegates to {@code this} after performing
     *           any necessary argument conversions, and arranges for any
     *           necessary return value conversions
     * @throws IllegalArgumentException if the conversion cannot be made
     * @see MethodHandles#convertArguments
     */
    public final MethodHandle asType(MethodType newType) {
        return MethodHandles.convertArguments(this, newType);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle which adapts, as its <i>target</i>,
     * the current method handle.  The type of the adapter will be
     * the same as the type of the target, except that all but the first
     * {@code keepPosArgs} parameters of the target's type are replaced
     * by a single array parameter of type {@code Object[]}.
     * Thus, if {@code keepPosArgs} is zero, the adapter will take all
     * arguments in a single object array.
     * <p>
     * When called, the adapter replaces a trailing array argument
     * by the array's elements, each as its own argument to the target.
     * (The order of the arguments is preserved.)
     * They are converted pairwise by casting and/or unboxing
     * (as if by {@link MethodHandles#convertArguments})
     * to the types of the trailing parameters of the target.
     * Finally the target is called.
     * What the target eventually returns is returned unchanged by the adapter.
     * <p>
     * Before calling the target, the adapter verifies that the array
     * contains exactly enough elements to provide a correct argument count
     * to the target method handle.
     * (The array may also be null when zero elements are required.)
     * @param keepPosArgs the number of leading positional arguments to preserve
     * @return a new method handle which spreads its final argument,
     *         before calling the original method handle
     * @throws IllegalArgumentException if target does not have at least
     *         {@code keepPosArgs} parameter types
     */
    public final MethodHandle asSpreader(int keepPosArgs) {
        MethodType oldType = type();
        int nargs = oldType.parameterCount();
        MethodType newType = oldType.dropParameterTypes(keepPosArgs, nargs);
        newType = newType.insertParameterTypes(keepPosArgs, Object[].class);
        return MethodHandles.spreadArguments(this, newType);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle which adapts, as its <i>target</i>,
     * the current method handle.  The type of the adapter will be
     * the same as the type of the target, except that a single trailing
     * array parameter of type {@code Object[]} is replaced by
     * {@code spreadArrayArgs} parameters of type {@code Object}.
     * <p>
     * When called, the adapter replaces its trailing {@code spreadArrayArgs}
     * arguments by a single new {@code Object} array, whose elements
     * comprise (in order) the replaced arguments.
     * Finally the target is called.
     * What the target eventually returns is returned unchanged by the adapter.
     * <p>
     * (The array may also be a shared constant when {@code spreadArrayArgs} is zero.)
     * @param spreadArrayArgs the number of arguments to spread from the trailing array
     * @return a new method handle which collects some trailing argument
     *         into an array, before calling the original method handle
     * @throws IllegalArgumentException if the last argument of the target
     *         is not {@code Object[]}
     * @throws IllegalArgumentException if {@code spreadArrayArgs} is not
     *         a legal array size
     * @deprecated Provisional and unstable; use {@link MethodHandles#collectArguments}.
     */
    public final MethodHandle asCollector(int spreadArrayArgs) {
        MethodType oldType = type();
        int nargs = oldType.parameterCount();
        MethodType newType = oldType.dropParameterTypes(nargs-1, nargs);
        newType = newType.insertParameterTypes(nargs-1, MethodType.genericMethodType(spreadArrayArgs).parameterArray());
        return MethodHandles.collectArguments(this, newType);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle which binds the given argument
     * to the current method handle as <i>target</i>.
     * The type of the bound handle will be
     * the same as the type of the target, except that a single leading
     * reference parameter will be omitted.
     * <p>
     * When called, the bound handle inserts the given value {@code x}
     * as a new leading argument to the target.  The other arguments are
     * also passed unchanged.
     * What the target eventually returns is returned unchanged by the bound handle.
     * <p>
     * The reference {@code x} must be convertible to the first parameter
     * type of the target.
     * @param x  the value to bind to the first argument of the target
     * @return a new method handle which collects some trailing argument
     *         into an array, before calling the original method handle
     * @throws IllegalArgumentException if the target does not have a
     *         leading parameter type that is a reference type
     * @throws ClassCastException if {@code x} cannot be converted
     *         to the leading parameter type of the target
     * @deprecated Provisional and unstable; use {@link MethodHandles#insertArguments}.
     */
    public final MethodHandle bindTo(Object x) {
        return MethodHandles.insertArguments(this, 0, x);
    }
}
