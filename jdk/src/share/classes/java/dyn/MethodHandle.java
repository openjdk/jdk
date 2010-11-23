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

package java.dyn;

//import sun.dyn.*;

import sun.dyn.Access;
import sun.dyn.MethodHandleImpl;

import static java.dyn.MethodHandles.invokers;  // package-private API
import static sun.dyn.MemberName.newIllegalArgumentException;  // utility

/**
 * A method handle is a typed, directly executable reference to a method,
 * constructor, field, or similar low-level operation, with optional
 * transformations of arguments or return values.
 * These transformations are quite general, and include such patterns as
 * {@linkplain #asType conversion},
 * {@linkplain #bindTo insertion},
 * {@linkplain java.dyn.MethodHandles#dropArguments deletion},
 * and {@linkplain java.dyn.MethodHandles#filterArguments substitution}.
 * <p>
 * <em>Note: The super-class of MethodHandle is Object.
 *     Any other super-class visible in the Reference Implementation
 *     will be removed before the Proposed Final Draft.
 *     Also, the final version will not include any public or
 *     protected constructors.</em>
 * <p>
 * Method handles are strongly typed according to signature.
 * They are not distinguished by method name or enclosing class.
 * A method handle must be invoked under a signature which matches
 * the method handle's own {@linkplain MethodType method type}.
 * <p>
 * Every method handle reports its type via the {@link #type type} accessor.
 * The structure of this type is a series of classes, one of which is
 * the return type of the method (or {@code void.class} if none).
 * <p>
 * Every method handle appears as an object containing a method named
 * {@link #invokeExact invokeExact}, whose signature exactly matches
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
 * named {@code invokeExact} of the intended method type.
 * The call fails with a {@link WrongMethodTypeException}
 * if the method does not exist, even if there is an {@code invokeExact}
 * method of a closely similar signature.
 * As with other kinds
 * of methods in the JVM, signature matching during method linkage
 * is exact, and does not allow for language-level implicit conversions
 * such as {@code String} to {@code Object} or {@code short} to {@code int}.
 * <p>
 * Each individual method handle also contains a method named
 * {@link #invokeGeneric invokeGeneric}, whose type is the same
 * as {@code invokeExact}, and is therefore also reported by
 * the {@link #type type} accessor.
 * A call to {@code invokeGeneric} works the same as a call to
 * {@code invokeExact}, if the signature specified by the caller
 * exactly matches the method handle's own type.
 * If there is a type mismatch, {@code invokeGeneric} attempts
 * to adjust the type of the target method handle
 * (as if by a call to {@link #asType asType})
 * to obtain an exactly invokable target.
 * This allows a more powerful negotiation of method type
 * between caller and callee.
 * <p>
 * A method handle is an unrestricted capability to call a method.
 * A method handle can be formed on a non-public method by a class
 * that has access to that method; the resulting handle can be used
 * in any place by any caller who receives a reference to it.  Thus, access
 * checking is performed when the method handle is created, not
 * (as in reflection) every time it is called.  Handles to non-public
 * methods, or in non-public classes, should generally be kept secret.
 * They should not be passed to untrusted code unless their use from
 * the untrusted code would be harmless.
 * <p>
 * Bytecode in the JVM can directly call a method handle's
 * {@code invokeExact} method from an {@code invokevirtual} instruction.
 * The receiver class type must be {@code MethodHandle} and the method name
 * must be {@code invokeExact}.  The signature of the invocation
 * (after resolving symbolic type names) must exactly match the method type
 * of the target method.
 * Similarly, bytecode can directly call a method handle's {@code invokeGeneric}
 * method.  The signature of the invocation (after resolving symbolic type names)
 * must either exactly match the method type or be a valid argument to
 * the target's {@link #asType asType} method.
 * <p>
 * Every {@code invokeExact} and {@code invokeGeneric} method always
 * throws {@link java.lang.Throwable Throwable},
 * which is to say that there is no static restriction on what a method handle
 * can throw.  Since the JVM does not distinguish between checked
 * and unchecked exceptions (other than by their class, of course),
 * there is no particular effect on bytecode shape from ascribing
 * checked exceptions to method handle invocations.  But in Java source
 * code, methods which perform method handle calls must either explicitly
 * throw {@code java.lang.Throwable Throwable}, or else must catch all
 * throwables locally, rethrowing only those which are legal in the context,
 * and wrapping ones which are illegal.
 * <p>
 * Bytecode in the JVM can directly obtain a method handle
 * for any accessible method from a {@code ldc} instruction
 * which refers to a {@code CONSTANT_Methodref} or
 * {@code CONSTANT_InterfaceMethodref} constant pool entry.
 * <p>
 * Java code can also use a reflective API called
 * {@link java.dyn.MethodHandles.Lookup MethodHandles.Lookup}
 * for creating and calling method handles.
 * For example, a static method handle can be obtained
 * from {@link java.dyn.MethodHandles.Lookup#findStatic Lookup.findStatic}.
 * There are also bridge methods from Core Reflection API objects,
 * such as {@link java.dyn.MethodHandles.Lookup#unreflect Lookup.ureflect}.
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
s = (String) mh.invokeExact("daddy",'d','n');
assert(s.equals("nanny"));
// weakly typed invocation (using MHs.invoke)
s = (String) mh.invokeWithArguments("sappy", 'p', 'v');
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
i = (int) mh.invokeExact(java.util.Arrays.asList(1,2,3));
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
 * from no arguments to up to 255 of arguments (a limit imposed by the JVM).
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
 * Like classes and strings, method handles that correspond to accessible
 * fields, methods, and constructors can be represented directly
 * in a class file's constant pool as constants to be loaded by {@code ldc} bytecodes.
 * Loading such a constant causes the component classes of its type to be loaded as necessary.
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
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @interface PolymorphicSignature { }

    private MethodType type;

    /**
     * Report the type of this method handle.
     * Every invocation of this method handle via {@code invokeExact} must exactly match this type.
     * @return the method handle type
     */
    public final MethodType type() {
        return type;
    }

    /**
     * <em>CONSTRUCTOR WILL BE REMOVED FOR PFD:</em>
     * Temporary constructor in early versions of the Reference Implementation.
     * Method handle inheritance (if any) will be contained completely within
     * the {@code java.dyn} package.
     */
    // The constructor for MethodHandle may only be called by privileged code.
    // Subclasses may be in other packages, but must possess
    // a token which they obtained from MH with a security check.
    // @param token non-null object which proves access permission
    // @param type type (permanently assigned) of the new method handle
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

    /** Produce a printed representation that displays information about this call site
     *  that may be useful to the human reader.
     */
    @Override
    public String toString() {
        return MethodHandleImpl.getNameString(IMPL_TOKEN, this);
    }

    /**
     * Invoke the method handle, allowing any caller signature, but requiring an exact signature match.
     * The signature at the call site of {@code invokeExact} must
     * exactly match this method handle's {@link #type type}.
     * No conversions are allowed on arguments or return values.
     * @throws WrongMethodTypeException if the target's type is not identical with the caller's type signature
     * @throws Throwable anything thrown by the underlying method propagates unchanged through the method handle call
     */
    public final native @PolymorphicSignature Object invokeExact(Object... args) throws Throwable;

    /**
     * Invoke the method handle, allowing any caller signature,
     * and optionally performing conversions for arguments and return types.
     * <p>
     * If the call site signature exactly matches this method handle's {@link #type type},
     * the call proceeds as if by {@link #invokeExact invokeExact}.
     * <p>
     * Otherwise, the call proceeds as if this method handle were first
     * adjusted by calling {@link #asType asType} to adjust this method handle
     * to the required type, and then the call proceeds as if by
     * {@link #invokeExact invokeExact} on the adjusted method handle.
     * <p>
     * There is no guarantee that the {@code asType} call is actually made.
     * If the JVM can predict the results of making the call, it may perform
     * adaptations directly on the caller's arguments,
     * and call the target method handle according to its own exact type.
     * <p>
     * If the method handle is equipped with a
     * {@linkplain #withTypeHandler type handler}, the handler must produce
     * an entry point of the call site's exact type.
     * Otherwise, the signature at the call site of {@code invokeGeneric} must
     * be a valid argument to the standard {@code asType} method.
     * In particular, the caller must specify the same argument arity
     * as the callee's type.
     * @throws WrongMethodTypeException if the target's type cannot be adjusted to the caller's type signature
     * @throws Throwable anything thrown by the underlying method propagates unchanged through the method handle call
     */
    public final native @PolymorphicSignature Object invokeGeneric(Object... args) throws Throwable;

    /**
     * Perform a varargs invocation, passing the arguments in the given array
     * to the method handle, as if via {@link #invokeGeneric invokeGeneric} from a call site
     * which mentions only the type {@code Object}, and whose arity is the length
     * of the argument array.
     * <p>
     * Specifically, execution proceeds as if by the following steps,
     * although the methods are not guaranteed to be called if the JVM
     * can predict their effects.
     * <ul>
     * <li>Determine the length of the argument array as {@code N}.
     *     For a null reference, {@code N=0}. </li>
     * <li>Determine the generic type {@code TN} of {@code N} arguments as
     *     as {@code TN=MethodType.genericMethodType(N)}.</li>
     * <li>Force the original target method handle {@code MH0} to the
     *     required type, as {@code MH1 = MH0.asType(TN)}. </li>
     * <li>Spread the array into {@code N} separate arguments {@code A0, ...}. </li>
     * <li>Invoke the type-adjusted method handle on the unpacked arguments:
     *     MH1.invokeExact(A0, ...). </li>
     * <li>Take the return value as an {@code Object} reference. </li>
     * </ul>
     * <p>
     * Because of the action of the {@code asType} step, the following argument
     * conversions are applied as necessary:
     * <ul>
     * <li>reference casting
     * <li>unboxing
     * <li>widening primitive conversions
     * </ul>
     * <p>
     * The result returned by the call is boxed if it is a primitive,
     * or forced to null if the return type is void.
     * <p>
     * This call is equivalent to the following code:
     * <p><blockquote><pre>
     * MethodHandle invoker = MethodHandles.varargsInvoker(this.type(), 0);
     * Object result = invoker.invokeExact(this, arguments);
     * </pre></blockquote>
     * @param arguments the arguments to pass to the target
     * @return the result returned by the target
     * @throws WrongMethodTypeException if the target's type cannot be adjusted to take the arguments
     * @throws Throwable anything thrown by the target method invocation
     * @see MethodHandles#varargsInvoker
     */
    public final Object invokeWithArguments(Object... arguments) throws Throwable {
        int argc = arguments == null ? 0 : arguments.length;
        MethodType type = type();
        if (type.parameterCount() != argc) {
            // simulate invokeGeneric
            return asType(MethodType.genericMethodType(argc)).invokeWithArguments(arguments);
        }
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
    /** Equivalent to {@code invokeWithArguments(arguments.toArray())}. */
    public final Object invokeWithArguments(java.util.List<?> arguments) throws Throwable {
        return invokeWithArguments(arguments.toArray());
    }
    @Deprecated
    public final Object invokeVarargs(Object... arguments) throws Throwable {
        return invokeWithArguments(arguments);
    }
    @Deprecated
    public final Object invokeVarargs(java.util.List<?> arguments) throws Throwable {
        return invokeWithArguments(arguments.toArray());
    }

    /**
     * Produce an adapter method handle which adapts the type of the
     * current method handle to a new type
     * The resulting method handle is guaranteed to report a type
     * which is equal to the desired new type.
     * <p>
     * If the original type and new type are equal, returns {@code this}.
     * <p>
     * This method provides the crucial behavioral difference between
     * {@link #invokeExact invokeExact} and {@link #invokeGeneric invokeGeneric}.  The two methods
     * perform the same steps when the caller's type descriptor is identical
     * with the callee's, but when the types differ, {@link #invokeGeneric invokeGeneric}
     * also calls {@code asType} (or some internal equivalent) in order
     * to match up the caller's and callee's types.
     * <p>
     * This method is equivalent to {@link MethodHandles#convertArguments convertArguments},
     * except for method handles produced by {@link #withTypeHandler withTypeHandler},
     * in which case the specified type handler is used for calls to {@code asType}.
     * <p>
     * Note that the default behavior of {@code asType} only performs
     * pairwise argument conversion and return value conversion.
     * Because of this, unless the method handle has a type handler,
     * the original type and new type must have the same number of arguments.
     *
     * @param newType the expected type of the new method handle
     * @return a method handle which delegates to {@code this} after performing
     *           any necessary argument conversions, and arranges for any
     *           necessary return value conversions
     * @throws WrongMethodTypeException if the conversion cannot be made
     * @see MethodHandles#convertArguments
     */
    public MethodHandle asType(MethodType newType) {
        return MethodHandles.convertArguments(this, newType);
    }

    /**
     * Produce a method handle which adapts, as its <i>target</i>,
     * the current method handle.  The type of the adapter will be
     * the same as the type of the target, except that the final
     * {@code arrayLength} parameters of the target's type are replaced
     * by a single array parameter of type {@code arrayType}.
     * <p>
     * If the array element type differs from any of the corresponding
     * argument types on original target,
     * the original target is adapted to take the array elements directly,
     * as if by a call to {@link #asType asType}.
     * <p>
     * When called, the adapter replaces a trailing array argument
     * by the array's elements, each as its own argument to the target.
     * (The order of the arguments is preserved.)
     * They are converted pairwise by casting and/or unboxing
     * to the types of the trailing parameters of the target.
     * Finally the target is called.
     * What the target eventually returns is returned unchanged by the adapter.
     * <p>
     * Before calling the target, the adapter verifies that the array
     * contains exactly enough elements to provide a correct argument count
     * to the target method handle.
     * (The array may also be null when zero elements are required.)
     * @param arrayType usually {@code Object[]}, the type of the array argument from which to extract the spread arguments
     * @param arrayLength the number of arguments to spread from an incoming array argument
     * @return a new method handle which spreads its final array argument,
     *         before calling the original method handle
     * @throws IllegalArgumentException if {@code arrayType} is not an array type
     * @throws IllegalArgumentException if target does not have at least
     *         {@code arrayLength} parameter types
     * @throws WrongMethodTypeException if the implied {@code asType} call fails
     */
    public final MethodHandle asSpreader(Class<?> arrayType, int arrayLength) {
        Class<?> arrayElement = arrayType.getComponentType();
        if (arrayElement == null)  throw newIllegalArgumentException("not an array type");
        MethodType oldType = type();
        int nargs = oldType.parameterCount();
        if (nargs < arrayLength)  throw newIllegalArgumentException("bad spread array length");
        int keepPosArgs = nargs - arrayLength;
        MethodType newType = oldType.dropParameterTypes(keepPosArgs, nargs);
        newType = newType.insertParameterTypes(keepPosArgs, arrayElement);
        return MethodHandles.spreadArguments(this, newType);
    }

    /**
     * Produce a method handle which adapts, as its <i>target</i>,
     * the current method handle.  The type of the adapter will be
     * the same as the type of the target, except that a single trailing
     * parameter (usually of type {@code arrayType}) is replaced by
     * {@code arrayLength} parameters whose type is element type of {@code arrayType}.
     * <p>
     * If the array type differs from the final argument type on original target,
     * the original target is adapted to take the array type directly,
     * as if by a call to {@link #asType asType}.
     * <p>
     * When called, the adapter replaces its trailing {@code arrayLength}
     * arguments by a single new array of type {@code arrayType}, whose elements
     * comprise (in order) the replaced arguments.
     * Finally the target is called.
     * What the target eventually returns is returned unchanged by the adapter.
     * <p>
     * (The array may also be a shared constant when {@code arrayLength} is zero.)
     * @param arrayType usually {@code Object[]}, the type of the array argument which will collect the arguments
     * @param arrayLength the number of arguments to collect into a new array argument
     * @return a new method handle which collects some trailing argument
     *         into an array, before calling the original method handle
     * @throws IllegalArgumentException if {@code arrayType} is not an array type
               or {@code arrayType} is not assignable to this method handle's trailing parameter type
     * @throws IllegalArgumentException if {@code arrayLength} is not
     *         a legal array size
     * @throws WrongMethodTypeException if the implied {@code asType} call fails
     */
    public final MethodHandle asCollector(Class<?> arrayType, int arrayLength) {
        Class<?> arrayElement = arrayType.getComponentType();
        if (arrayElement == null)  throw newIllegalArgumentException("not an array type");
        MethodType oldType = type();
        int nargs = oldType.parameterCount();
        MethodType newType = oldType.dropParameterTypes(nargs-1, nargs);
        newType = newType.insertParameterTypes(nargs-1,
                    java.util.Collections.<Class<?>>nCopies(arrayLength, arrayElement));
        return MethodHandles.collectArguments(this, newType);
    }

    /**
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
     * @see MethodHandles#insertArguments
     */
    public final MethodHandle bindTo(Object x) {
        return MethodHandles.insertArguments(this, 0, x);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Create a new method handle with the same type as this one,
     * but whose {@code asType} method invokes the given
     * {@code typeHandler} on this method handle,
     * instead of the standard {@code MethodHandles.convertArguments}.
     * <p>
     * The new method handle will have the same behavior as the
     * old one when invoked by {@code invokeExact}.
     * For {@code invokeGeneric} calls which exactly match
     * the method type, the two method handles will also
     * have the same behavior.
     * For other {@code invokeGeneric} calls, the {@code typeHandler}
     * will control the behavior of the new method handle.
     * <p>
     * Thus, a method handle with an {@code asType} handler can
     * be configured to accept more than one arity of {@code invokeGeneric}
     * call, and potentially every possible arity.
     * It can also be configured to supply default values for
     * optional arguments, when the caller does not specify them.
     * <p>
     * The given method handle must take two arguments and return
     * one result.  The result it returns must be a method handle
     * of exactly the requested type.  If the result returned by
     * the target is null, a {@link NullPointerException} is thrown,
     * else if the type of the target does not exactly match
     * the requested type, a {@link WrongMethodTypeException} is thrown.
     * <p>
     * Therefore, the type handler is invoked as if by this code:
     * <blockquote><pre>
     * MethodHandle target = this;      // original method handle
     * MethodHandle adapter = ...;      // adapted method handle
     * MethodType requestedType = ...;  // argument to asType()
     * if (type().equals(requestedType))
     *    return adapter;
     * MethodHandle result = (MethodHandle)
     *    typeHandler.invokeGeneric(target, requestedType);
     * if (!result.type().equals(requestedType))
     *    throw new WrongMethodTypeException();
     * return result;
     * </pre></blockquote>
     * <p>
     * For example, here is a list-making variable-arity method handle:
     * <blockquote><pre>
MethodHandle makeEmptyList = MethodHandles.constant(List.class, Arrays.asList());
MethodHandle asList = lookup()
  .findStatic(Arrays.class, "asList", methodType(List.class, Object[].class));
static MethodHandle collectingTypeHandler(MethodHandle base, MethodType newType) {
  return asList.asCollector(Object[].class, newType.parameterCount()).asType(newType);
}
MethodHandle collectingTypeHandler = lookup()
  .findStatic(lookup().lookupClass(), "collectingTypeHandler",
     methodType(MethodHandle.class, MethodHandle.class, MethodType.class));
MethodHandle makeAnyList = makeEmptyList.withTypeHandler(collectingTypeHandler);

System.out.println(makeAnyList.invokeGeneric()); // prints []
System.out.println(makeAnyList.invokeGeneric(1)); // prints [1]
System.out.println(makeAnyList.invokeGeneric("two", "too")); // prints [two, too]
     * <pre><blockquote>
     */
    public MethodHandle withTypeHandler(MethodHandle typeHandler) {
        return MethodHandles.withTypeHandler(this, typeHandler);
    }
}
