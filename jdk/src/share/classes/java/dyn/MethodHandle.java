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

package java.dyn;

//import sun.dyn.*;

import sun.dyn.Access;
import sun.dyn.MethodHandleImpl;

import static java.dyn.MethodHandles.invokers;  // package-private API
import static sun.dyn.MemberName.newIllegalArgumentException;  // utility

/**
 * A method handle is a typed, directly executable reference to an underlying method,
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
 *
 * <h3>Method handle contents</h3>
 * Method handles are dynamically and strongly typed according to type descriptor.
 * They are not distinguished by the name or defining class of their underlying methods.
 * A method handle must be invoked using type descriptor which matches
 * the method handle's own {@linkplain #type method type}.
 * <p>
 * Every method handle reports its type via the {@link #type type} accessor.
 * This type descriptor is a {@link java.dyn.MethodType MethodType} object,
 * whose structure is a series of classes, one of which is
 * the return type of the method (or {@code void.class} if none).
 * <p>
 * A method handle's type controls the types of invocations it accepts,
 * and the kinds of transformations that apply to it.
 * <p>
 * A method handle contains a pair of special invoker methods
 * called {@link #invokeExact invokeExact} and {@link #invokeGeneric invokeGeneric}.
 * Both invoker methods provide direct access to the method handle's
 * underlying method, constructor, field, or other operation,
 * as modified by transformations of arguments and return values.
 * Both invokers accept calls which exactly match the method handle's own type.
 * The {@code invokeGeneric} invoker also accepts a range of other call types.
 * <p>
 * Method handles are immutable and have no visible state.
 * Of course, they can be bound to underlying methods or data which exhibit state.
 * With respect to the Java Memory Model, any method handle will behave
 * as if all of its (internal) fields are final variables.  This means that any method
 * handle made visible to the application will always be fully formed.
 * This is true even if the method handle is published through a shared
 * variable in a data race.
 * <p>
 * Method handles cannot be subclassed by the user.
 * Implementations may (or may not) create internal subclasses of {@code MethodHandle}
 * which may be visible via the {@link java.lang.Object#getClass Object.getClass}
 * operation.  The programmer should not draw conclusions about a method handle
 * from its specific class, as the method handle class hierarchy (if any)
 * may change from time to time or across implementations from different vendors.
 *
 * <h3>Method handle compilation</h3>
 * A Java method call expression naming {@code invokeExact} or {@code invokeGeneric}
 * can invoke a method handle from Java source code.
 * From the viewpoint of source code, these methods can take any arguments
 * and their result can be cast to any return type.
 * Formally this is accomplished by giving the invoker methods
 * {@code Object} return types and variable-arity {@code Object} arguments,
 * but they have an additional quality called <em>signature polymorphism</em>
 * which connects this freedom of invocation directly to the JVM execution stack.
 * <p>
 * As is usual with virtual methods, source-level calls to {@code invokeExact}
 * and {@code invokeGeneric} compile to an {@code invokevirtual} instruction.
 * More unusually, the compiler must record the actual argument types,
 * and may not perform method invocation conversions on the arguments.
 * Instead, it must push them on the stack according to their own unconverted types.
 * The method handle object itself is pushed on the stack before the arguments.
 * The compiler then calls the method handle with a type descriptor which
 * describes the argument and return types.
 * <p>
 * To issue a complete type descriptor, the compiler must also determine
 * the return type.  This is based on a cast on the method invocation expression,
 * if there is one, or else {@code Object} if the invocation is an expression
 * or else {@code void} if the invocation is a statement.
 * The cast may be to a primitive type (but not {@code void}).
 * <p>
 * As a corner case, an uncasted {@code null} argument is given
 * a type descriptor of {@code java.lang.Void}.
 * The ambiguity with the type {@code Void} is harmless, since there are no references of type
 * {@code Void} except the null reference.
 *
 * <h3>Method handle invocation</h3>
 * The first time a {@code invokevirtual} instruction is executed
 * it is linked, by symbolically resolving the names in the instruction
 * and verifying that the method call is statically legal.
 * This is true of calls to {@code invokeExact} and {@code invokeGeneric}.
 * In this case, the type descriptor emitted by the compiler is checked for
 * correct syntax and names it contains are resolved.
 * Thus, an {@code invokevirtual} instruction which invokes
 * a method handle will always link, as long
 * as the type descriptor is syntactically well-formed
 * and the types exist.
 * <p>
 * When the {@code invokevirtual} is executed after linking,
 * the receiving method handle's type is first checked by the JVM
 * to ensure that it matches the descriptor.
 * If the type match fails, it means that the method which the
 * caller is invoking is not present on the individual
 * method handle being invoked.
 * <p>
 * In the case of {@code invokeExact}, the type descriptor of the invocation
 * (after resolving symbolic type names) must exactly match the method type
 * of the receiving method handle.
 * In the case of {@code invokeGeneric}, the resolved type descriptor
 * must be a valid argument to the receiver's {@link #asType asType} method.
 * Thus, {@code invokeGeneric} is more permissive than {@code invokeExact}.
 * <p>
 * After type matching, a call to {@code invokeExact} directly
 * and immediately invoke the method handle's underlying method
 * (or other behavior, as the case may be).
 * <p>
 * A call to {@code invokeGeneric} works the same as a call to
 * {@code invokeExact}, if the type descriptor specified by the caller
 * exactly matches the method handle's own type.
 * If there is a type mismatch, {@code invokeGeneric} attempts
 * to adjust the type of the receiving method handle,
 * as if by a call to {@link #asType asType},
 * to obtain an exactly invokable method handle {@code M2}.
 * This allows a more powerful negotiation of method type
 * between caller and callee.
 * <p>
 * (Note: The adjusted method handle {@code M2} is not directly observable,
 * and implementations are therefore not required to materialize it.)
 *
 * <h3>Invocation checking</h3>
 * In typical programs, method handle type matching will usually succeed.
 * But if a match fails, the JVM will throw a {@link WrongMethodTypeException},
 * either directly (in the case of {@code invokeExact}) or indirectly as if
 * by a failed call to {@code asType} (in the case of {@code invokeGeneric}).
 * <p>
 * Thus, a method type mismatch which might show up as a linkage error
 * in a statically typed program can show up as
 * a dynamic {@code WrongMethodTypeException}
 * in a program which uses method handles.
 * <p>
 * Because method types contain "live" {@code Class} objects,
 * method type matching takes into account both types names and class loaders.
 * Thus, even if a method handle {@code M} is created in one
 * class loader {@code L1} and used in another {@code L2},
 * method handle calls are type-safe, because the caller's type
 * descriptor, as resolved in {@code L2},
 * is matched against the original callee method's type descriptor,
 * as resolved in {@code L1}.
 * The resolution in {@code L1} happens when {@code M} is created
 * and its type is assigned, while the resolution in {@code L2} happens
 * when the {@code invokevirtual} instruction is linked.
 * <p>
 * Apart from the checking of type descriptors,
 * a method handle's capability to call its underlying method is unrestricted.
 * If a method handle is formed on a non-public method by a class
 * that has access to that method, the resulting handle can be used
 * in any place by any caller who receives a reference to it.
 * <p>
 * Unlike with the Core Reflection API, where access is checked every time
 * a reflective method is invoked,
 * method handle access checking is performed
 * <a href="MethodHandles.Lookup.html#access">when the method handle is created</a>.
 * In the case of {@code ldc} (see below), access checking is performed as part of linking
 * the constant pool entry underlying the constant method handle.
 * <p>
 * Thus, handles to non-public methods, or to methods in non-public classes,
 * should generally be kept secret.
 * They should not be passed to untrusted code unless their use from
 * the untrusted code would be harmless.
 *
 * <h3>Method handle creation</h3>
 * Java code can create a method handle that directly accesses
 * any method, constructor, or field that is accessible to that code.
 * This is done via a reflective, capability-based API called
 * {@link java.dyn.MethodHandles.Lookup MethodHandles.Lookup}
 * For example, a static method handle can be obtained
 * from {@link java.dyn.MethodHandles.Lookup#findStatic Lookup.findStatic}.
 * There are also conversion methods from Core Reflection API objects,
 * such as {@link java.dyn.MethodHandles.Lookup#unreflect Lookup.unreflect}.
 * <p>
 * Like classes and strings, method handles that correspond to accessible
 * fields, methods, and constructors can also be represented directly
 * in a class file's constant pool as constants to be loaded by {@code ldc} bytecodes.
 * A new type of constant pool entry, {@code CONSTANT_MethodHandle},
 * refers directly to an associated {@code CONSTANT_Methodref},
 * {@code CONSTANT_InterfaceMethodref}, or {@code CONSTANT_Fieldref}
 * constant pool entry.
 * (For more details on method handle constants,
 * see the <a href="package-summary.html#mhcon">package summary</a>.)
 * <p>
 * Method handles produced by lookups or constant loads from methods or
 * constructors with the variable arity modifier bit ({@code 0x0080})
 * have a corresponding variable arity, as if they were defined with
 * the help of {@link #asVarargsCollector asVarargsCollector}.
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
 * A non-virtual method handle to a specific virtual method implementation
 * can also be created.  These do not perform virtual lookup based on
 * receiver type.  Such a method handle simulates the effect of
 * an {@code invokespecial} instruction to the same method.
 *
 * <h3>Usage examples</h3>
 * Here are some examples of usage:
 * <p><blockquote><pre>
Object x, y; String s; int i;
MethodType mt; MethodHandle mh;
MethodHandles.Lookup lookup = MethodHandles.lookup();
// mt is (char,char)String
mt = MethodType.methodType(String.class, char.class, char.class);
mh = lookup.findVirtual(String.class, "replace", mt);
s = (String) mh.invokeExact("daddy",'d','n');
// invokeExact(Ljava/lang/String;CC)Ljava/lang/String;
assert(s.equals("nanny"));
// weakly typed invocation (using MHs.invoke)
s = (String) mh.invokeWithArguments("sappy", 'p', 'v');
assert(s.equals("savvy"));
// mt is (Object[])List
mt = MethodType.methodType(java.util.List.class, Object[].class);
mh = lookup.findStatic(java.util.Arrays.class, "asList", mt);
assert(mh.isVarargsCollector());
x = mh.invokeGeneric("one", "two");
// invokeGeneric(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;
assert(x.equals(java.util.Arrays.asList("one","two")));
// mt is (Object,Object,Object)Object
mt = MethodType.genericMethodType(3);
mh = mh.asType(mt);
x = mh.invokeExact((Object)1, (Object)2, (Object)3);
// invokeExact(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
assert(x.equals(java.util.Arrays.asList(1,2,3)));
// mt is { =&gt; int}
mt = MethodType.methodType(int.class);
mh = lookup.findVirtual(java.util.List.class, "size", mt);
i = (int) mh.invokeExact(java.util.Arrays.asList(1,2,3));
// invokeExact(Ljava/util/List;)I
assert(i == 3);
mt = MethodType.methodType(void.class, String.class);
mh = lookup.findVirtual(java.io.PrintStream.class, "println", mt);
mh.invokeExact(System.out, "Hello, world.");
// invokeExact(Ljava/io/PrintStream;Ljava/lang/String;)V
 * </pre></blockquote>
 * Each of the above calls to {@code invokeExact} or {@code invokeGeneric}
 * generates a single invokevirtual instruction with
 * the type descriptor indicated in the following comment.
 *
 * <h3>Exceptions</h3>
 * The methods {@code invokeExact} and {@code invokeGeneric} are declared
 * to throw {@link java.lang.Throwable Throwable},
 * which is to say that there is no static restriction on what a method handle
 * can throw.  Since the JVM does not distinguish between checked
 * and unchecked exceptions (other than by their class, of course),
 * there is no particular effect on bytecode shape from ascribing
 * checked exceptions to method handle invocations.  But in Java source
 * code, methods which perform method handle calls must either explicitly
 * throw {@code java.lang.Throwable Throwable}, or else must catch all
 * throwables locally, rethrowing only those which are legal in the context,
 * and wrapping ones which are illegal.
 *
 * <h3><a name="sigpoly"></a>Signature polymorphism</h3>
 * The unusual compilation and linkage behavior of
 * {@code invokeExact} and {@code invokeGeneric}
 * is referenced by the term <em>signature polymorphism</em>.
 * A signature polymorphic method is one which can operate with
 * any of a wide range of call signatures and return types.
 * In order to make this work, both the Java compiler and the JVM must
 * give special treatment to signature polymorphic methods.
 * <p>
 * In source code, a call to a signature polymorphic method will
 * compile, regardless of the requested type descriptor.
 * As usual, the Java compiler emits an {@code invokevirtual}
 * instruction with the given type descriptor against the named method.
 * The unusual part is that the type descriptor is derived from
 * the actual argument and return types, not from the method declaration.
 * <p>
 * When the JVM processes bytecode containing signature polymorphic calls,
 * it will successfully link any such call, regardless of its type descriptor.
 * (In order to retain type safety, the JVM will guard such calls with suitable
 * dynamic type checks, as described elsewhere.)
 * <p>
 * Bytecode generators, including the compiler back end, are required to emit
 * untransformed type descriptors for these methods.
 * Tools which determine symbolic linkage are required to accept such
 * untransformed descriptors, without reporting linkage errors.
 * <p>
 * For the sake of tools (but not as a programming API), the signature polymorphic
 * methods are marked with a private yet standard annotation,
 * {@code @java.dyn.MethodHandle.PolymorphicSignature}.
 * The annotation's retention is {@code RUNTIME}, so that all tools can see it.
 *
 * <h3>Formal rules for processing signature polymorphic methods</h3>
 * <p>
 * The following methods (and no others) are signature polymorphic:
 * <ul>
 * <li>{@link java.dyn.MethodHandle#invokeExact   MethodHandle.invokeExact}
 * <li>{@link java.dyn.MethodHandle#invokeGeneric MethodHandle.invokeGeneric}
 * </ul>
 * <p>
 * A signature polymorphic method will be declared with the following properties:
 * <ul>
 * <li>It must be native.
 * <li>It must take a single varargs parameter of the form {@code Object...}.
 * <li>It must produce a return value of type {@code Object}.
 * <li>It must be contained within the {@code java.dyn} package.
 * </ul>
 * Because of these requirements, a signature polymorphic method is able to accept
 * any number and type of actual arguments, and can, with a cast, produce a value of any type.
 * However, the JVM will treat these declaration features as a documentation convention,
 * rather than a description of the actual structure of the methods as executed.
 * <p>
 * When a call to a signature polymorphic method is compiled, the associated linkage information for
 * its arguments is not array of {@code Object} (as for other similar varargs methods)
 * but rather the erasure of the static types of all the arguments.
 * <p>
 * In an argument position of a method invocation on a signature polymorphic method,
 * a null literal has type {@code java.lang.Void}, unless cast to a reference type.
 * (Note: This typing rule allows the null type to have its own encoding in linkage information
 * distinct from other types.
 * <p>
 * The linkage information for the return type is derived from a context-dependent target typing convention.
 * The return type for a signature polymorphic method invocation is determined as follows:
 * <ul>
 * <li>If the method invocation expression is an expression statement, the method is {@code void}.
 * <li>Otherwise, if the method invocation expression is the immediate operand of a cast,
 * the return type is the erasure of the cast type.
 * <li>Otherwise, the return type is the method's nominal return type, {@code Object}.
 * </ul>
 * (Programmers are encouraged to use explicit casts unless it is clear that a signature polymorphic
 * call will be used as a plain {@code Object} expression.)
 * <p>
 * The linkage information for argument and return types is stored in the descriptor for the
 * compiled (bytecode) call site. As for any invocation instruction, the arguments and return value
 * will be passed directly on the JVM stack, in accordance with the descriptor,
 * and without implicit boxing or unboxing.
 *
 * <h3>Interoperation between method handles and the Core Reflection API</h3>
 * Using factory methods in the {@link java.dyn.MethodHandles.Lookup Lookup} API,
 * any class member represented by a Core Reflection API object
 * can be converted to a behaviorally equivalent method handle.
 * For example, a reflective {@link java.lang.reflect.Method Method} can
 * be converted to a method handle using
 * {@link java.dyn.MethodHandles.Lookup#unreflect Lookup.unreflect}.
 * The resulting method handles generally provide more direct and efficient
 * access to the underlying class members.
 * <p>
 * As a special case,
 * when the Core Reflection API is used to view the signature polymorphic
 * methods {@code invokeExact} or {@code invokeGeneric} in this class,
 * they appear as single, non-polymorphic native methods.
 * Calls to these native methods do not result in method handle invocations.
 * Since {@code invokevirtual} instructions can natively
 * invoke method handles under any type descriptor, this reflective view conflicts
 * with the normal presentation via bytecodes.
 * Thus, these two native methods, as viewed by
 * {@link java.lang.Class#getDeclaredMethod Class.getDeclaredMethod},
 * are placeholders only.
 * If invoked via {@link java.lang.reflect.Method#invoke Method.invoke},
 * they will throw {@code UnsupportedOperationException}.
 * <p>
 * In order to obtain an invoker method for a particular type descriptor,
 * use {@link java.dyn.MethodHandles#exactInvoker MethodHandles.exactInvoker},
 * or {@link java.dyn.MethodHandles#genericInvoker MethodHandles.genericInvoker}.
 * The {@link java.dyn.MethodHandles.Lookup#findVirtual Lookup.findVirtual}
 * API is also able to return a method handle
 * to call {@code invokeExact} or {@code invokeGeneric},
 * for any specified type descriptor .
 *
 * <h3>Interoperation between method handles and Java generics</h3>
 * A method handle can be obtained on a method, constructor, or field
 * which is declared with Java generic types.
 * As with the Core Reflection API, the type of the method handle
 * will constructed from the erasure of the source-level type.
 * When a method handle is invoked, the types of its arguments
 * or the return value cast type may be generic types or type instances.
 * If this occurs, the compiler will replace those
 * types by their erasures when when it constructs the type descriptor
 * for the {@code invokevirtual} instruction.
 * <p>
 * Method handles do not represent
 * their function-like types in terms of Java parameterized (generic) types,
 * because there are three mismatches between function-like types and parameterized
 * Java types.
 * <ul>
 * <li>Method types range over all possible arities,
 * from no arguments to up to 255 of arguments (a limit imposed by the JVM).
 * Generics are not variadic, and so cannot represent this.</li>
 * <li>Method types can specify arguments of primitive types,
 * which Java generic types cannot range over.</li>
 * <li>Higher order functions over method handles (combinators) are
 * often generic across a wide range of function types, including
 * those of multiple arities.  It is impossible to represent such
 * genericity with a Java type parameter.</li>
 * </ul>
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
    static { MethodHandleImpl.initStatics(); }

    // interface MethodHandle<R throws X extends Exception,A...>
    // { MethodType<R throws X,A...> type(); public R invokeExact(A...) throws X; }

    /**
     * Internal marker interface which distinguishes (to the Java compiler)
     * those methods which are <a href="MethodHandle.html#sigpoly">signature polymorphic</a>.
     */
    @java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD})
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @interface PolymorphicSignature { }

    private MethodType type;

    /**
     * Report the type of this method handle.
     * Every invocation of this method handle via {@code invokeExact} must exactly match this type.
     * @return the method handle type
     */
    public MethodType type() {
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

    /**
     * Invoke the method handle, allowing any caller type descriptor, but requiring an exact type match.
     * The type descriptor at the call site of {@code invokeExact} must
     * exactly match this method handle's {@link #type type}.
     * No conversions are allowed on arguments or return values.
     * <p>
     * When this method is observed via the Core Reflection API,
     * it will appear as a single native method, taking an object array and returning an object.
     * If this native method is invoked directly via
     * {@link java.lang.reflect.Method#invoke Method.invoke}, via JNI,
     * or indirectly via {@link java.dyn.MethodHandles.Lookup#unreflect Lookup.unreflect},
     * it will throw an {@code UnsupportedOperationException}.
     * @throws WrongMethodTypeException if the target's type is not identical with the caller's type descriptor
     * @throws Throwable anything thrown by the underlying method propagates unchanged through the method handle call
     */
    public final native @PolymorphicSignature Object invokeExact(Object... args) throws Throwable;

    /**
     * Invoke the method handle, allowing any caller type descriptor,
     * and optionally performing conversions on arguments and return values.
     * <p>
     * If the call site type descriptor exactly matches this method handle's {@link #type type},
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
     * The type descriptor at the call site of {@code invokeGeneric} must
     * be a valid argument to the receivers {@code asType} method.
     * In particular, the caller must specify the same argument arity
     * as the callee's type,
     * if the callee is not a {@linkplain #asVarargsCollector variable arity collector}.
     * <p>
     * When this method is observed via the Core Reflection API,
     * it will appear as a single native method, taking an object array and returning an object.
     * If this native method is invoked directly via
     * {@link java.lang.reflect.Method#invoke Method.invoke}, via JNI,
     * or indirectly via {@link java.dyn.MethodHandles.Lookup#unreflect Lookup.unreflect},
     * it will throw an {@code UnsupportedOperationException}.
     * @throws WrongMethodTypeException if the target's type cannot be adjusted to the caller's type descriptor
     * @throws ClassCastException if the target's type can be adjusted to the caller, but a reference cast fails
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
     * MethodHandle invoker = MethodHandles.spreadInvoker(this.type(), 0);
     * Object result = invoker.invokeExact(this, arguments);
     * </pre></blockquote>
     * <p>
     * Unlike the signature polymorphic methods {@code invokeExact} and {@code invokeGeneric},
     * {@code invokeWithArguments} can be accessed normally via the Core Reflection API and JNI.
     * It can therefore be used as a bridge between native or reflective code and method handles.
     *
     * @param arguments the arguments to pass to the target
     * @return the result returned by the target
     * @throws ClassCastException if an argument cannot be converted by reference casting
     * @throws WrongMethodTypeException if the target's type cannot be adjusted to take the given number of {@code Object} arguments
     * @throws Throwable anything thrown by the target method invocation
     * @see MethodHandles#spreadInvoker
     */
    public Object invokeWithArguments(Object... arguments) throws Throwable {
        int argc = arguments == null ? 0 : arguments.length;
        MethodType type = type();
        if (type.parameterCount() != argc) {
            // simulate invokeGeneric
            return asType(MethodType.genericMethodType(argc)).invokeWithArguments(arguments);
        }
        if (argc <= 10) {
            MethodHandle invoker = invokers(type).genericInvoker();
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
        MethodHandle invoker = invokers(type).spreadInvoker(0);
        return invoker.invokeExact(this, arguments);
    }
    /** Equivalent to {@code invokeWithArguments(arguments.toArray())}. */
    public Object invokeWithArguments(java.util.List<?> arguments) throws Throwable {
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
     * except for variable arity method handles produced by {@link #asVarargsCollector asVarargsCollector}.
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
     * Make an adapter which accepts a trailing array argument
     * and spreads its elements as positional arguments.
     * The new method handle adapts, as its <i>target</i>,
     * the current method handle.  The type of the adapter will be
     * the same as the type of the target, except that the final
     * {@code arrayLength} parameters of the target's type are replaced
     * by a single array parameter of type {@code arrayType}.
     * <p>
     * If the array element type differs from any of the corresponding
     * argument types on the original target,
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
     * @see #asCollector
     */
    public MethodHandle asSpreader(Class<?> arrayType, int arrayLength) {
        Class<?> arrayElement = arrayType.getComponentType();
        if (arrayElement == null)  throw newIllegalArgumentException("not an array type");
        MethodType oldType = type();
        int nargs = oldType.parameterCount();
        if (nargs < arrayLength)  throw newIllegalArgumentException("bad spread array length");
        int keepPosArgs = nargs - arrayLength;
        MethodType newType = oldType.dropParameterTypes(keepPosArgs, nargs);
        newType = newType.insertParameterTypes(keepPosArgs, arrayType);
        return MethodHandles.spreadArguments(this, newType);
    }

    /**
     * Make an adapter which accepts a given number of trailing
     * positional arguments and collects them into an array argument.
     * The new method handle adapts, as its <i>target</i>,
     * the current method handle.  The type of the adapter will be
     * the same as the type of the target, except that a single trailing
     * parameter (usually of type {@code arrayType}) is replaced by
     * {@code arrayLength} parameters whose type is element type of {@code arrayType}.
     * <p>
     * If the array type differs from the final argument type on the original target,
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
     * <p>
     * (<em>Note:</em> The {@code arrayType} is often identical to the last
     * parameter type of the original target.
     * It is an explicit argument for symmetry with {@code asSpreader}, and also
     * to allow the target to use a simple {@code Object} as its last parameter type.)
     * <p>
     * In order to create a collecting adapter which is not restricted to a particular
     * number of collected arguments, use {@link #asVarargsCollector asVarargsCollector} instead.
     * @param arrayType often {@code Object[]}, the type of the array argument which will collect the arguments
     * @param arrayLength the number of arguments to collect into a new array argument
     * @return a new method handle which collects some trailing argument
     *         into an array, before calling the original method handle
     * @throws IllegalArgumentException if {@code arrayType} is not an array type
     *         or {@code arrayType} is not assignable to this method handle's trailing parameter type,
     *         or {@code arrayLength} is not a legal array size
     * @throws WrongMethodTypeException if the implied {@code asType} call fails
     * @see #asSpreader
     * @see #asVarargsCollector
     */
    public MethodHandle asCollector(Class<?> arrayType, int arrayLength) {
        Class<?> arrayElement = arrayType.getComponentType();
        if (arrayElement == null)  throw newIllegalArgumentException("not an array type");
        MethodType oldType = type();
        int nargs = oldType.parameterCount();
        if (nargs == 0)  throw newIllegalArgumentException("no trailing argument");
        MethodType newType = oldType.dropParameterTypes(nargs-1, nargs);
        newType = newType.insertParameterTypes(nargs-1,
                    java.util.Collections.<Class<?>>nCopies(arrayLength, arrayElement));
        return MethodHandles.collectArguments(this, newType);
    }

    /**
     * Make a <em>variable arity</em> adapter which is able to accept
     * any number of trailing positional arguments and collect them
     * into an array argument.
     * <p>
     * The type and behavior of the adapter will be the same as
     * the type and behavior of the target, except that certain
     * {@code invokeGeneric} and {@code asType} requests can lead to
     * trailing positional arguments being collected into target's
     * trailing parameter.
     * Also, the last parameter type of the adapter will be
     * {@code arrayType}, even if the target has a different
     * last parameter type.
     * <p>
     * When called with {@link #invokeExact invokeExact}, the adapter invokes
     * the target with no argument changes.
     * (<em>Note:</em> This behavior is different from a
     * {@linkplain #asCollector fixed arity collector},
     * since it accepts a whole array of indeterminate length,
     * rather than a fixed number of arguments.)
     * <p>
     * When called with {@link #invokeGeneric invokeGeneric}, if the caller
     * type is the same as the adapter, the adapter invokes the target as with
     * {@code invokeExact}.
     * (This is the normal behavior for {@code invokeGeneric} when types match.)
     * <p>
     * Otherwise, if the caller and adapter arity are the same, and the
     * trailing parameter type of the caller is a reference type identical to
     * or assignable to the trailing parameter type of the adapter,
     * the arguments and return values are converted pairwise,
     * as if by {@link MethodHandles#convertArguments convertArguments}.
     * (This is also normal behavior for {@code invokeGeneric} in such a case.)
     * <p>
     * Otherwise, the arities differ, or the adapter's trailing parameter
     * type is not assignable from the corresponding caller type.
     * In this case, the adapter replaces all trailing arguments from
     * the original trailing argument position onward, by
     * a new array of type {@code arrayType}, whose elements
     * comprise (in order) the replaced arguments.
     * <p>
     * The caller type must provides as least enough arguments,
     * and of the correct type, to satisfy the target's requirement for
     * positional arguments before the trailing array argument.
     * Thus, the caller must supply, at a minimum, {@code N-1} arguments,
     * where {@code N} is the arity of the target.
     * Also, there must exist conversions from the incoming arguments
     * to the target's arguments.
     * As with other uses of {@code invokeGeneric}, if these basic
     * requirements are not fulfilled, a {@code WrongMethodTypeException}
     * may be thrown.
     * <p>
     * In all cases, what the target eventually returns is returned unchanged by the adapter.
     * <p>
     * In the final case, it is exactly as if the target method handle were
     * temporarily adapted with a {@linkplain #asCollector fixed arity collector}
     * to the arity required by the caller type.
     * (As with {@code asCollector}, if the array length is zero,
     * a shared constant may be used instead of a new array.
     * If the implied call to {@code asCollector} would throw
     * an {@code IllegalArgumentException} or {@code WrongMethodTypeException},
     * the call to the variable arity adapter must throw
     * {@code WrongMethodTypeException}.)
     * <p>
     * The behavior of {@link #asType asType} is also specialized for
     * variable arity adapters, to maintain the invariant that
     * {@code invokeGeneric} is always equivalent to an {@code asType}
     * call to adjust the target type, followed by {@code invokeExact}.
     * Therefore, a variable arity adapter responds
     * to an {@code asType} request by building a fixed arity collector,
     * if and only if the adapter and requested type differ either
     * in arity or trailing argument type.
     * The resulting fixed arity collector has its type further adjusted
     * (if necessary) to the requested type by pairwise conversion,
     * as if by another application of {@code asType}.
     * <p>
     * When a method handle is obtained by executing an {@code ldc} instruction
     * of a {@code CONSTANT_MethodHandle} constant, and the target method is marked
     * as a variable arity method (with the modifier bit {@code 0x0080}),
     * the method handle will accept multiple arities, as if the method handle
     * constant were created by means of a call to {@code asVarargsCollector}.
     * <p>
     * In order to create a collecting adapter which collects a predetermined
     * number of arguments, and whose type reflects this predetermined number,
     * use {@link #asCollector asCollector} instead.
     * <p>
     * No method handle transformations produce new method handles with
     * variable arity, unless they are documented as doing so.
     * Therefore, besides {@code asVarargsCollector},
     * all methods in {@code MethodHandle} and {@code MethodHandles}
     * will return a method handle with fixed arity,
     * except in the cases where they are specified to return their original
     * operand (e.g., {@code asType} of the method handle's own type).
     * <p>
     * Calling {@code asVarargsCollector} on a method handle which is already
     * of variable arity will produce a method handle with the same type and behavior.
     * It may (or may not) return the original variable arity method handle.
     * <p>
     * Here is an example, of a list-making variable arity method handle:
     * <blockquote><pre>
MethodHandle asList = publicLookup()
  .findStatic(Arrays.class, "asList", methodType(List.class, Object[].class))
  .asVarargsCollector(Object[].class);
assertEquals("[]", asList.invokeGeneric().toString());
assertEquals("[1]", asList.invokeGeneric(1).toString());
assertEquals("[two, too]", asList.invokeGeneric("two", "too").toString());
Object[] argv = { "three", "thee", "tee" };
assertEquals("[three, thee, tee]", asList.invokeGeneric(argv).toString());
List ls = (List) asList.invokeGeneric((Object)argv);
assertEquals(1, ls.size());
assertEquals("[three, thee, tee]", Arrays.toString((Object[])ls.get(0)));
     * </pre></blockquote>
     * <p style="font-size:smaller;">
     * <em>Discussion:</em>
     * These rules are designed as a dynamically-typed variation
     * of the Java rules for variable arity methods.
     * In both cases, callers to a variable arity method or method handle
     * can either pass zero or more positional arguments, or else pass
     * pre-collected arrays of any length.  Users should be aware of the
     * special role of the final argument, and of the effect of a
     * type match on that final argument, which determines whether
     * or not a single trailing argument is interpreted as a whole
     * array or a single element of an array to be collected.
     * Note that the dynamic type of the trailing argument has no
     * effect on this decision, only a comparison between the static
     * type descriptor of the call site and the type of the method handle.)
     * <p style="font-size:smaller;">
     * As a result of the previously stated rules, the variable arity behavior
     * of a method handle may be suppressed, by binding it to the exact invoker
     * of its own type, as follows:
     * <blockquote><pre>
MethodHandle vamh = publicLookup()
  .findStatic(Arrays.class, "asList", methodType(List.class, Object[].class))
  .asVarargsCollector(Object[].class);
MethodHandle mh = MethodHandles.exactInvoker(vamh.type()).bindTo(vamh);
assert(vamh.type().equals(mh.type()));
assertEquals("[1, 2, 3]", vamh.invokeGeneric(1,2,3).toString());
boolean failed = false;
try { mh.invokeGeneric(1,2,3); }
catch (WrongMethodTypeException ex) { failed = true; }
assert(failed);
     * </pre></blockquote>
     * This transformation has no behavioral effect if the method handle is
     * not of variable arity.
     *
     * @param arrayType often {@code Object[]}, the type of the array argument which will collect the arguments
     * @return a new method handle which can collect any number of trailing arguments
     *         into an array, before calling the original method handle
     * @throws IllegalArgumentException if {@code arrayType} is not an array type
     *         or {@code arrayType} is not assignable to this method handle's trailing parameter type
     * @see #asCollector
     * @see #isVarargsCollector
     */
    public MethodHandle asVarargsCollector(Class<?> arrayType) {
        Class<?> arrayElement = arrayType.getComponentType();
        if (arrayElement == null)  throw newIllegalArgumentException("not an array type");
        return MethodHandles.asVarargsCollector(this, arrayType);
    }

    /**
     * Determine if this method handle
     * supports {@linkplain #asVarargsCollector variable arity} calls.
     * Such method handles arise from the following sources:
     * <ul>
     * <li>a call to {@linkplain #asVarargsCollector asVarargsCollector}
     * <li>a call to a {@linkplain java.dyn.MethodHandles.Lookup lookup method}
     *     which resolves to a variable arity Java method or constructor
     * <li>an {@code ldc} instruction of a {@code CONSTANT_MethodHandle}
     *     which resolves to a variable arity Java method or constructor
     * </ul>
     * @return true if this method handle accepts more than one arity of {@code invokeGeneric} calls
     * @see #asVarargsCollector
     */
    public boolean isVarargsCollector() {
        return false;
    }

    /**
     * Bind a value {@code x} to the first argument of a method handle, without invoking it.
     * The new method handle adapts, as its <i>target</i>,
     * to the current method handle.
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
    public MethodHandle bindTo(Object x) {
        return MethodHandles.insertArguments(this, 0, x);
    }

    /**
     * Returns a string representation of the method handle,
     * starting with the string {@code "MethodHandle"} and
     * ending with the string representation of the method handle's type.
     * In other words, this method returns a string equal to the value of:
     * <blockquote><pre>
     * "MethodHandle" + type().toString()
     * </pre></blockquote>
     * <p>
     * Note:  Future releases of this API may add further information
     * to the string representation.
     * Therefore, the present syntax should not be parsed by applications.
     *
     * @return a string representation of the method handle
     */
    @Override
    public String toString() {
        return MethodHandleImpl.getNameString(IMPL_TOKEN, this);
    }
}
