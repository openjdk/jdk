/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.lang.invoke.MethodHandleStatics.UNSAFE;
import static java.lang.invoke.MethodHandleStatics.newInternalError;

/**
 * A VarHandle is a dynamically typed reference to a variable, or to a
 * parametrically-defined family of variables, including static fields,
 * non-static fields, array elements, or components of an off-heap data
 * structure.  Access to such variables is supported under various
 * <em>access modes</em>, including plain read/write access, volatile
 * read/write access, and compare-and-swap.
 *
 * <p>VarHandles are immutable and have no visible state.  VarHandles cannot be
 * subclassed by the user.
 *
 * <p>A VarHandle has:
 * <ul>
 * <li>a {@link #varType variable type}, referred to as {@code T}, which is the
 * type of variable(s) referenced by this VarHandle;
 * <li>a list of {@link #coordinateTypes coordinate types}, referred to as
 * {@code CT}, where the types (primitive and reference) are represented by
 * {@link Class} objects).  A list of arguments corresponding to instances of
 * the coordinate types uniquely locates a variable referenced by this
 * VarHandle; and
 * <li>a <em>shape</em>, that combines the variable type and coordinate types,
 * and is declared with the notation {@code (CT : T)}.  An empty list of
 * coordinate types is declared as {@code (empty)}.
 * </ul>
 *
 * <p>Factory methods that produce or {@link java.lang.invoke.MethodHandles.Lookup
 * lookup} VarHandle instances document the supported variable type, coordinate
 * types, and shape.
 *
 * For example, a VarHandle referencing a non-static field will declare a shape
 * of {@code (R : T)}, where {@code R} is the receiver type and
 * {@code T} is the field type, and where the VarHandle and an instance of the
 * receiver type can be utilized to access the field variable.
 * A VarHandle referencing array elements will declare a shape of
 * {@code (T[], int : T)}, where {@code T[]} is the array type and {@code T}
 * its component type, and where the VarHandle, an instance of the array type,
 * and an {@code int} index can be utilized to access an array element variable.
 *
 * <p>Each access mode is associated with a
 * <a href="MethodHandle.html#sigpoly">signature polymorphic</a> method of the
 * same name, where the VarHandle shape and access mode uniquely determine the
 * canonical {@link #accessModeType(AccessMode) access mode type},
 * which in turn determines the matching constraints on a valid symbolic
 * type descriptor at the call site of an access mode's method
 * <a href="VarHandle.html#invoke">invocation</a>.
 *
 * As such, VarHandles are dynamically and strongly typed.  Their arity,
 * argument types, and return type of an access mode method invocation are not
 * statically checked.  If they, and associated values, do not match the arity
 * and types of the access mode's type, an exception will be thrown.
 *
 * The parameter types of an access mode method type will consist of those that
 * are the VarHandles's coordinate types (in order), followed by access mode
 * parameter types specific to the access mode.
 *
 * <p>An access mode's method documents the form of its method signature, which
 * is derived from the access mode parameter types.  The form is declared with
 * the notation {@code (CT, P1 p1, P2 p2, ..., PN pn)R}, where {@code CT} is the
 * coordinate types (as documented by a VarHandle factory method), {@code P1},
 * {@code P2} and {@code PN} are the first, second and the n'th access mode
 * parameters named {@code p1}, {@code p2} and {@code pn} respectively, and
 * {@code R} is the return type.
 *
 * For example, for the generic shape of {@code (CT : T)} the
 * {@link #compareAndSet} access mode method documents that its method
 * signature is of the form {@code (CT, T expectedValue, T newValue)boolean},
 * where the parameter types named {@code extendedValue} and {@code newValue}
 * are the access mode parameter types.  If the VarHandle accesses array
 * elements with a shape of say {@code (T[], int : T)} then the access mode
 * method type is {@code (T[], int, T, T)boolean}.
 *
 * <p>Access modes are grouped into the following categories:
 * <ul>
 * <li>read access modes that get the value of a variable under specified
 * memory ordering effects.
 * The set of corresponding access mode methods belonging to this group
 * consists of the methods
 * {@link #get get},
 * {@link #getVolatile getVolatile},
 * {@link #getAcquire getAcquire},
 * {@link #getOpaque getOpaque}.
 * <li>write access modes that set the value of a variable under specified
 * memory ordering effects.
 * The set of corresponding access mode methods belonging to this group
 * consists of the methods
 * {@link #set set},
 * {@link #setVolatile setVolatile},
 * {@link #setRelease setRelease},
 * {@link #setOpaque setOpaque}.
 * <li>atomic update access modes that, for example, atomically compare and set
 * the value of a variable under specified memory ordering effects.
 * The set of corresponding access mode methods belonging to this group
 * consists of the methods
 * {@link #compareAndSet compareAndSet},
 * {@link #weakCompareAndSet weakCompareAndSet},
 * {@link #weakCompareAndSetVolatile weakCompareAndSetVolatile},
 * {@link #weakCompareAndSetAcquire weakCompareAndSetAcquire},
 * {@link #weakCompareAndSetRelease weakCompareAndSetRelease},
 * {@link #compareAndExchangeAcquire compareAndExchangeAcquire},
 * {@link #compareAndExchangeVolatile compareAndExchangeVolatile},
 * {@link #compareAndExchangeRelease compareAndExchangeRelease},
 * {@link #getAndSet getAndSet}.
 * <li>numeric atomic update access modes that, for example, atomically get and
 * set with addition the value of a variable under specified memory ordering
 * effects.
 * The set of corresponding access mode methods belonging to this group
 * consists of the methods
 * {@link #getAndAdd getAndAdd},
 * {@link #addAndGet addAndGet}.
 * </ul>
 *
 * <p>Factory methods that produce or {@link java.lang.invoke.MethodHandles.Lookup
 * lookup} VarHandle instances document the set of access modes that are
 * supported, which may also include documenting restrictions based on the
 * variable type and whether a variable is read-only.  If an access mode is not
 * supported then the corresponding signature-polymorphic method will on
 * invocation throw an {@code UnsupportedOperationException}.  Factory methods
 * should document any additional undeclared exceptions that may be thrown by
 * access mode methods.
 * The {@link #get get} access mode is supported for all
 * VarHandle instances and the corresponding method never throws
 * {@code UnsupportedOperationException}.
 * If a VarHandle references a read-only variable (for example a {@code final}
 * field) then write, atomic update and numeric atomic update access modes are
 * not supported and corresponding methods throw
 * {@code UnsupportedOperationException}.
 * Read/write access modes (if supported), with the exception of
 * {@code get} and {@code set}, provide atomic access for
 * reference types and all primitive types.
 * Unless stated otherwise in the documentation of a factory method, the access
 * modes {@code get} and {@code set} (if supported) provide atomic access for
 * reference types and all primitives types, with the exception of {@code long}
 * and {@code double} on 32-bit platforms.
 *
 * <p>Access modes will override any memory ordering effects specified at
 * the declaration site of a variable.  For example, a VarHandle accessing a
 * a field using the {@code get} access mode will access the field as
 * specified <em>by its access mode</em> even if that field is declared
 * {@code volatile}.  When mixed access is performed extreme care should be
 * taken since the Java Memory Model may permit surprising results.
 *
 * <p>In addition to supporting access to variables under various access modes,
 * a set of static methods, referred to as memory fence methods, is also
 * provided for fine-grained control of memory ordering.
 *
 * The Java Language Specification permits other threads to observe operations
 * as if they were executed in orders different than are apparent in program
 * source code, subject to constraints arising, for example, from the use of
 * locks, {@code volatile} fields or VarHandles.  The static methods,
 * {@link #fullFence fullFence}, {@link #acquireFence acquireFence},
 * {@link #releaseFence releaseFence}, {@link #loadLoadFence loadLoadFence} and
 * {@link #storeStoreFence storeStoreFence}, can also be used to impose
 * constraints.  Their specifications, as is the case for certain access modes,
 * are phrased in terms of the lack of "reorderings" -- observable ordering
 * effects that might otherwise occur if the fence was not present.  More
 * precise phrasing of the specification of access mode methods and memory fence
 * methods may accompany future updates of the Java Language Specification.
 *
 * <h1>Compilation of an access mode's method</h1>
 * A Java method call expression naming an access mode method can invoke a
 * VarHandle from Java source code.  From the viewpoint of source code, these
 * methods can take any arguments and their polymorphic result (if expressed)
 * can be cast to any return type.  Formally this is accomplished by giving the
 * access mode methods variable arity {@code Object} arguments and
 * {@code Object} return types (if the return type is polymorphic), but they
 * have an additional quality called <em>signature polymorphism</em> which
 * connects this freedom of invocation directly to the JVM execution stack.
 * <p>
 * As is usual with virtual methods, source-level calls to access mode methods
 * compile to an {@code invokevirtual} instruction.  More unusually, the
 * compiler must record the actual argument types, and may not perform method
 * invocation conversions on the arguments.  Instead, it must generate
 * instructions to push them on the stack according to their own unconverted
 * types.  The VarHandle object itself will be pushed on the stack before the
 * arguments.  The compiler then generates an {@code invokevirtual} instruction
 * that invokes the access mode method with a symbolic type descriptor which
 * describes the argument and return types.
 * <p>
 * To issue a complete symbolic type descriptor, the compiler must also
 * determine the return type (if polymorphic).  This is based on a cast on the
 * method invocation expression, if there is one, or else {@code Object} if the
 * invocation is an expression, or else {@code void} if the invocation is a
 * statement.  The cast may be to a primitive type (but not {@code void}).
 * <p>
 * As a corner case, an uncasted {@code null} argument is given a symbolic type
 * descriptor of {@code java.lang.Void}.  The ambiguity with the type
 * {@code Void} is harmless, since there are no references of type {@code Void}
 * except the null reference.
 *
 *
 * <h1><a name="invoke">Invocation of an access mode's method</a></h1>
 * The first time an {@code invokevirtual} instruction is executed it is linked
 * by symbolically resolving the names in the instruction and verifying that
 * the method call is statically legal.  This also holds for calls to access mode
 * methods.  In this case, the symbolic type descriptor emitted by the compiler
 * is checked for correct syntax, and names it contains are resolved.  Thus, an
 * {@code invokevirtual} instruction which invokes an access mode method will
 * always link, as long as the symbolic type descriptor is syntactically
 * well-formed and the types exist.
 * <p>
 * When the {@code invokevirtual} is executed after linking, the receiving
 * VarHandle's access mode type is first checked by the JVM to ensure that it
 * matches the symbolic type descriptor.  If the type
 * match fails, it means that the access mode method which the caller is
 * invoking is not present on the individual VarHandle being invoked.
 *
 * <p>
 * Invocation of an access mode's signature-polymorphic method behaves as if an
 * invocation of {@link MethodHandle#invoke}, where the receiving method handle
 * is bound to a VarHandle instance and the access mode.  More specifically, the
 * following:
 * <pre> {@code
 * VarHandle vh = ..
 * R r = (R) vh.{access-mode}(p1, p2, ..., pN);
 * }</pre>
 * behaves as if (modulo the access mode methods do not declare throwing of
 * {@code Throwable}):
 * <pre> {@code
 * VarHandle vh = ..
 * MethodHandle mh = MethodHandles.varHandleExactInvoker(
 *                       VarHandle.AccessMode.{access-mode},
 *                       vh.accessModeType(VarHandle.AccessMode.{access-mode}));
 *
 * mh = mh.bindTo(vh);
 * R r = (R) mh.invoke(p1, p2, ..., pN)
 * }</pre>
 * or, more concisely, behaves as if:
 * <pre> {@code
 * VarHandle vh = ..
 * MethodHandle mh = vh.toMethodHandle(VarHandle.AccessMode.{access-mode});
 *
 * R r = (R) mh.invoke(p1, p2, ..., pN)
 * }</pre>
 * In terms of equivalent {@code invokevirtual} bytecode behaviour an access
 * mode method invocation is equivalent to:
 * <pre> {@code
 * MethodHandle mh = MethodHandles.lookup().findVirtual(
 *                       VarHandle.class,
 *                       VarHandle.AccessMode.{access-mode}.methodName(),
 *                       MethodType.methodType(R, p1, p2, ..., pN));
 *
 * R r = (R) mh.invokeExact(vh, p1, p2, ..., pN)
 * }</pre>
 * where the desired method type is the symbolic type descriptor and a
 * {@link MethodHandle#invokeExact} is performed, since before invocation of the
 * target, the handle will apply reference casts as necessary and box, unbox, or
 * widen primitive values, as if by {@link MethodHandle#asType asType} (see also
 * {@link MethodHandles#varHandleInvoker}).
 *
 * <h1>Invocation checking</h1>
 * In typical programs, VarHandle access mode type matching will usually
 * succeed.  But if a match fails, the JVM will throw a
 * {@link WrongMethodTypeException}.
 * <p>
 * Thus, an access mode type mismatch which might show up as a linkage error
 * in a statically typed program can show up as a dynamic
 * {@code WrongMethodTypeException} in a program which uses VarHandles.
 * <p>
 * Because access mode types contain "live" {@code Class} objects, method type
 * matching takes into account both type names and class loaders.
 * Thus, even if a VarHandle {@code VH} is created in one class loader
 * {@code L1} and used in another {@code L2}, VarHandle access mode method
 * calls are type-safe, because the caller's symbolic type descriptor, as
 * resolved in {@code L2}, is matched against the original callee method's
 * symbolic type descriptor, as resolved in {@code L1}.  The resolution in
 * {@code L1} happens when {@code VH} is created and its access mode types are
 * assigned, while the resolution in {@code L2} happens when the
 * {@code invokevirtual} instruction is linked.
 * <p>
 * Apart from type descriptor checks, a VarHandles's capability to
 * access it's variables is unrestricted.
 * If a VarHandle is formed on a non-public variable by a class that has access
 * to that variable, the resulting VarHandle can be used in any place by any
 * caller who receives a reference to it.
 * <p>
 * Unlike with the Core Reflection API, where access is checked every time a
 * reflective method is invoked, VarHandle access checking is performed
 * <a href="MethodHandles.Lookup.html#access">when the VarHandle is
 * created</a>.
 * Thus, VarHandles to non-public variables, or to variables in non-public
 * classes, should generally be kept secret.  They should not be passed to
 * untrusted code unless their use from the untrusted code would be harmless.
 *
 *
 * <h1>VarHandle creation</h1>
 * Java code can create a VarHandle that directly accesses any field that is
 * accessible to that code.  This is done via a reflective, capability-based
 * API called {@link java.lang.invoke.MethodHandles.Lookup
 * MethodHandles.Lookup}.
 * For example, a VarHandle for a non-static field can be obtained
 * from {@link java.lang.invoke.MethodHandles.Lookup#findVarHandle
 * Lookup.findVarHandle}.
 * There is also a conversion method from Core Reflection API objects,
 * {@link java.lang.invoke.MethodHandles.Lookup#unreflectVarHandle
 * Lookup.unreflectVarHandle}.
 * <p>
 * Access to protected field members is restricted to receivers only of the
 * accessing class, or one of its subclasses, and the accessing class must in
 * turn be a subclass (or package sibling) of the protected member's defining
 * class.  If a VarHandle refers to a protected non-static field of a declaring
 * class outside the current package, the receiver argument will be narrowed to
 * the type of the accessing class.
 *
 * <h1>Interoperation between VarHandles and the Core Reflection API</h1>
 * Using factory methods in the {@link java.lang.invoke.MethodHandles.Lookup
 * Lookup} API, any field represented by a Core Reflection API object
 * can be converted to a behaviorally equivalent VarHandle.
 * For example, a reflective {@link java.lang.reflect.Field Field} can
 * be converted to a VarHandle using
 * {@link java.lang.invoke.MethodHandles.Lookup#unreflectVarHandle
 * Lookup.unreflectVarHandle}.
 * The resulting VarHandles generally provide more direct and efficient
 * access to the underlying fields.
 * <p>
 * As a special case, when the Core Reflection API is used to view the
 * signature polymorphic access mode methods in this class, they appear as
 * ordinary non-polymorphic methods.  Their reflective appearance, as viewed by
 * {@link java.lang.Class#getDeclaredMethod Class.getDeclaredMethod},
 * is unaffected by their special status in this API.
 * For example, {@link java.lang.reflect.Method#getModifiers
 * Method.getModifiers}
 * will report exactly those modifier bits required for any similarly
 * declared method, including in this case {@code native} and {@code varargs}
 * bits.
 * <p>
 * As with any reflected method, these methods (when reflected) may be invoked
 * directly via {@link java.lang.reflect.Method#invoke java.lang.reflect.Method.invoke},
 * via JNI, or indirectly via
 * {@link java.lang.invoke.MethodHandles.Lookup#unreflect Lookup.unreflect}.
 * However, such reflective calls do not result in access mode method
 * invocations.  Such a call, if passed the required argument (a single one, of
 * type {@code Object[]}), will ignore the argument and will throw an
 * {@code UnsupportedOperationException}.
 * <p>
 * Since {@code invokevirtual} instructions can natively invoke VarHandle
 * access mode methods under any symbolic type descriptor, this reflective view
 * conflicts with the normal presentation of these methods via bytecodes.
 * Thus, these native methods, when reflectively viewed by
 * {@code Class.getDeclaredMethod}, may be regarded as placeholders only.
 * <p>
 * In order to obtain an invoker method for a particular access mode type,
 * use {@link java.lang.invoke.MethodHandles#varHandleExactInvoker} or
 * {@link java.lang.invoke.MethodHandles#varHandleInvoker}.  The
 * {@link java.lang.invoke.MethodHandles.Lookup#findVirtual Lookup.findVirtual}
 * API is also able to return a method handle to call an access mode method for
 * any specified access mode type and is equivalent in behaviour to
 * {@link java.lang.invoke.MethodHandles#varHandleInvoker}.
 *
 * <h1>Interoperation between VarHandles and Java generics</h1>
 * A VarHandle can be obtained for a variable, such as a a field, which is
 * declared with Java generic types.  As with the Core Reflection API, the
 * VarHandle's variable type will be constructed from the erasure of the
 * source-level type.  When a VarHandle access mode method is invoked, the
 * types
 * of its arguments or the return value cast type may be generic types or type
 * instances.  If this occurs, the compiler will replace those types by their
 * erasures when it constructs the symbolic type descriptor for the
 * {@code invokevirtual} instruction.
 *
 * @see MethodHandle
 * @see MethodHandles
 * @see MethodType
 * @since 9
 */
public abstract class VarHandle {
    // Use explicit final fields rather than an @Stable array as
    // this can reduce the memory per handle
    // e.g. by 24 bytes on 64 bit architectures
    final MethodType typeGet;
    final MethodType typeSet;
    final MethodType typeCompareSwap;
    final MethodType typeCompareExchange;
    final MethodType typeGetAndUpdate;

    final VarForm vform;

    VarHandle(VarForm vform, Class<?> receiver, Class<?> value, Class<?>... intermediate) {
        this.vform = vform;

        // (Receiver, <Intermediates>)
        List<Class<?>> l = new ArrayList<>();
        if (receiver != null)
            l.add(receiver);
        l.addAll(Arrays.asList(intermediate));

        // (Receiver, <Intermediates>)Value
        this.typeGet = MethodType.methodType(value, l);

        // (Receiver, <Intermediates>, Value)void
        l.add(value);
        this.typeSet = MethodType.methodType(void.class, l);

        // (Receiver, <Intermediates>, Value)Value
        this.typeGetAndUpdate = MethodType.methodType(value, l);

        // (Receiver, <Intermediates>, Value, Value)boolean
        l.add(value);
        this.typeCompareSwap = MethodType.methodType(boolean.class, l);

        // (Receiver, <Intermediates>, Value, Value)Value
        this.typeCompareExchange = MethodType.methodType(value, l);
    }

    RuntimeException unsupported() {
        return new UnsupportedOperationException();
    }

    // Plain accessors

    /**
     * Returns the value of a variable, with memory semantics of reading as
     * if the variable was declared non-{@code volatile}.  Commonly referred to
     * as plain read access.
     *
     * <p>The method signature is of the form {@code (CT)T}.
     *
     * <p>The symbolic type descriptor at the call site of {@code get}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.GET)} on this VarHandle.
     *
     * <p>This access mode is supported by all VarHandle instances and never
     * throws {@code UnsupportedOperationException}.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT)}
     * , statically represented using varargs.
     * @return the signature-polymorphic result that is the value of the
     * variable
     * , statically represented using {@code Object}.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    Object get(Object... args);

    /**
     * Sets the value of a variable to the {@code newValue}, with memory
     * semantics of setting as if the variable was declared non-{@code volatile}
     * and non-{@code final}.  Commonly referred to as plain write access.
     *
     * <p>The method signature is of the form {@code (CT, T newValue)void}
     *
     * <p>The symbolic type descriptor at the call site of {@code set}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.SET)} on this VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T newValue)}
     * , statically represented using varargs.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    void set(Object... args);


    // Volatile accessors

    /**
     * Returns the value of a variable, with memory semantics of reading as if
     * the variable was declared {@code volatile}.
     *
     * <p>The method signature is of the form {@code (CT)T}.
     *
     * <p>The symbolic type descriptor at the call site of {@code getVolatile}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.GET_VOLATILE)} on this
     * VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT)}
     * , statically represented using varargs.
     * @return the signature-polymorphic result that is the value of the
     * variable
     * , statically represented using {@code Object}.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    Object getVolatile(Object... args);

    /**
     * Sets the value of a variable to the {@code newValue}, with memory
     * semantics of setting as if the variable was declared {@code volatile}.
     *
     * <p>The method signature is of the form {@code (CT, T newValue)void}.
     *
     * <p>The symbolic type descriptor at the call site of {@code setVolatile}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.SET_VOLATILE)} on this
     * VarHandle.
     *
     * @apiNote
     * Ignoring the many semantic differences from C and C++, this method has
     * memory ordering effects compatible with {@code memory_order_seq_cst}.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T newValue)}
     * , statically represented using varargs.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    void setVolatile(Object... args);


    /**
     * Returns the value of a variable, accessed in program order, but with no
     * assurance of memory ordering effects with respect to other threads.
     *
     * <p>The method signature is of the form {@code (CT)T}.
     *
     * <p>The symbolic type descriptor at the call site of {@code getOpaque}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.GET_OPAQUE)} on this
     * VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT)}
     * , statically represented using varargs.
     * @return the signature-polymorphic result that is the value of the
     * variable
     * , statically represented using {@code Object}.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    Object getOpaque(Object... args);

    /**
     * Sets the value of a variable to the {@code newValue}, in program order,
     * but with no assurance of memory ordering effects with respect to other
     * threads.
     *
     * <p>The method signature is of the form {@code (CT, T newValue)void}.
     *
     * <p>The symbolic type descriptor at the call site of {@code setOpaque}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.SET_OPAQUE)} on this
     * VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T newValue)}
     * , statically represented using varargs.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    void setOpaque(Object... args);


    // Lazy accessors

    /**
     * Returns the value of a variable, and ensures that subsequent loads and
     * stores are not reordered before this access.
     *
     * <p>The method signature is of the form {@code (CT)T}.
     *
     * <p>The symbolic type descriptor at the call site of {@code getAcquire}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.GET_ACQUIRE)} on this
     * VarHandle.
     *
     * @apiNote
     * Ignoring the many semantic differences from C and C++, this method has
     * memory ordering effects compatible with {@code memory_order_acquire}
     * ordering.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT)}
     * , statically represented using varargs.
     * @return the signature-polymorphic result that is the value of the
     * variable
     * , statically represented using {@code Object}.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    Object getAcquire(Object... args);

    /**
     * Sets the value of a variable to the {@code newValue}, and ensures that
     * prior loads and stores are not reordered after this access.
     *
     * <p>The method signature is of the form {@code (CT, T newValue)void}.
     *
     * <p>The symbolic type descriptor at the call site of {@code setRelease}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.SET_RELEASE)} on this
     * VarHandle.
     *
     * @apiNote
     * Ignoring the many semantic differences from C and C++, this method has
     * memory ordering effects compatible with {@code memory_order_release}
     * ordering.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T newValue)}
     * , statically represented using varargs.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    void setRelease(Object... args);


    // Compare and set accessors

    /**
     * Atomically sets the value of a variable to the {@code newValue} with the
     * memory semantics of {@link #setVolatile} if the variable's current value,
     * referred to as the <em>witness value</em>, {@code ==} the
     * {@code expectedValue}, as accessed with the memory semantics of
     * {@link #getVolatile}.
     *
     * <p>The method signature is of the form {@code (CT, T expectedValue, T newValue)boolean}.
     *
     * <p>The symbolic type descriptor at the call site of {@code
     * compareAndSet} must match the access mode type that is the result of
     * calling {@code accessModeType(VarHandle.AccessMode.COMPARE_AND_SET)} on
     * this VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T expectedValue, T newValue)}
     * , statically represented using varargs.
     * @return {@code true} if successful, otherwise {@code false} if the
     * witness value was not the same as the {@code expectedValue}.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     * @see #setVolatile(Object...)
     * @see #getVolatile(Object...)
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    boolean compareAndSet(Object... args);

    /**
     * Atomically sets the value of a variable to the {@code newValue} with the
     * memory semantics of {@link #setVolatile} if the variable's current value,
     * referred to as the <em>witness value</em>, {@code ==} the
     * {@code expectedValue}, as accessed with the memory semantics of
     * {@link #getVolatile}.
     *
     * <p>The method signature is of the form {@code (CT, T expectedValue, T newValue)T}.
     *
     * <p>The symbolic type descriptor at the call site of {@code
     * compareAndExchangeVolatile}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_VOLATILE)}
     * on this VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T expectedValue, T newValue)}
     * , statically represented using varargs.
     * @return the signature-polymorphic result that is the witness value, which
     * will be the same as the {@code expectedValue} if successful
     * , statically represented using {@code Object}.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     * @see #setVolatile(Object...)
     * @see #getVolatile(Object...)
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    Object compareAndExchangeVolatile(Object... args);

    /**
     * Atomically sets the value of a variable to the {@code newValue} with the
     * memory semantics of {@link #set} if the variable's current value,
     * referred to as the <em>witness value</em>, {@code ==} the
     * {@code expectedValue}, as accessed with the memory semantics of
     * {@link #getAcquire}.
     *
     * <p>The method signature is of the form {@code (CT, T expectedValue, T newValue)T}.
     *
     * <p>The symbolic type descriptor at the call site of {@code
     * compareAndExchangeAcquire}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_ACQUIRE)} on
     * this VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T expectedValue, T newValue)}
     * , statically represented using varargs.
     * @return the signature-polymorphic result that is the witness value, which
     * will be the same as the {@code expectedValue} if successful
     * , statically represented using {@code Object}.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     * @see #set(Object...)
     * @see #getAcquire(Object...)
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    Object compareAndExchangeAcquire(Object... args);

    /**
     * Atomically sets the value of a variable to the {@code newValue} with the
     * memory semantics of {@link #setRelease} if the variable's current value,
     * referred to as the <em>witness value</em>, {@code ==} the
     * {@code expectedValue}, as accessed with the memory semantics of
     * {@link #get}.
     *
     * <p>The method signature is of the form {@code (CT, T expectedValue, T newValue)T}.
     *
     * <p>The symbolic type descriptor at the call site of {@code
     * compareAndExchangeRelease}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_RELEASE)}
     * on this VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T expectedValue, T newValue)}
     * , statically represented using varargs.
     * @return the signature-polymorphic result that is the witness value, which
     * will be the same as the {@code expectedValue} if successful
     * , statically represented using {@code Object}.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     * @see #setRelease(Object...)
     * @see #get(Object...)
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    Object compareAndExchangeRelease(Object... args);

    // Weak (spurious failures allowed)

    /**
     * Possibly atomically sets the value of a variable to the {@code newValue}
     * with the semantics of {@link #set} if the variable's current value,
     * referred to as the <em>witness value</em>, {@code ==} the
     * {@code expectedValue}, as accessed with the memory semantics of
     * {@link #get}.
     *
     * <p>This operation may fail spuriously (typically, due to memory
     * contention) even if the witness value does match the expected value.
     *
     * <p>The method signature is of the form {@code (CT, T expectedValue, T newValue)boolean}.
     *
     * <p>The symbolic type descriptor at the call site of {@code
     * weakCompareAndSet} must match the access mode type that is the result of
     * calling {@code accessModeType(VarHandle.AccessMode.WEAK_COMPARE_AND_SET)}
     * on this VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T expectedValue, T newValue)}
     * , statically represented using varargs.
     * @return {@code true} if successful, otherwise {@code false} if the
     * witness value was not the same as the {@code expectedValue} or if this
     * operation spuriously failed.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     * @see #set(Object...)
     * @see #get(Object...)
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    boolean weakCompareAndSet(Object... args);

    /**
     * Possibly atomically sets the value of a variable to the {@code newValue}
     * with the memory semantics of {@link #setVolatile} if the variable's
     * current value, referred to as the <em>witness value</em>, {@code ==} the
     * {@code expectedValue}, as accessed with the memory semantics of
     * {@link #getVolatile}.
     *
     * <p>This operation may fail spuriously (typically, due to memory
     * contention) even if the witness value does match the expected value.
     *
     * <p>The method signature is of the form {@code (CT, T expectedValue, T newValue)boolean}.
     *
     * <p>The symbolic type descriptor at the call site of {@code
     * weakCompareAndSetVolatile} must match the access mode type that is the
     * result of calling {@code accessModeType(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_VOLATILE)}
     * on this VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T expectedValue, T newValue)}
     * , statically represented using varargs.
     * @return {@code true} if successful, otherwise {@code false} if the
     * witness value was not the same as the {@code expectedValue} or if this
     * operation spuriously failed.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     * @see #setVolatile(Object...)
     * @see #getVolatile(Object...)
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    boolean weakCompareAndSetVolatile(Object... args);

    /**
     * Possibly atomically sets the value of a variable to the {@code newValue}
     * with the semantics of {@link #set} if the variable's current value,
     * referred to as the <em>witness value</em>, {@code ==} the
     * {@code expectedValue}, as accessed with the memory semantics of
     * {@link #getAcquire}.
     *
     * <p>This operation may fail spuriously (typically, due to memory
     * contention) even if the witness value does match the expected value.
     *
     * <p>The method signature is of the form {@code (CT, T expectedValue, T newValue)boolean}.
     *
     * <p>The symbolic type descriptor at the call site of {@code
     * weakCompareAndSetAcquire}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_ACQUIRE)}
     * on this VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T expectedValue, T newValue)}
     * , statically represented using varargs.
     * @return {@code true} if successful, otherwise {@code false} if the
     * witness value was not the same as the {@code expectedValue} or if this
     * operation spuriously failed.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     * @see #set(Object...)
     * @see #getAcquire(Object...)
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    boolean weakCompareAndSetAcquire(Object... args);

    /**
     * Possibly atomically sets the value of a variable to the {@code newValue}
     * with the semantics of {@link #setRelease} if the variable's current
     * value, referred to as the <em>witness value</em>, {@code ==} the
     * {@code expectedValue}, as accessed with the memory semantics of
     * {@link #get}.
     *
     * <p>This operation may fail spuriously (typically, due to memory
     * contention) even if the witness value does match the expected value.
     *
     * <p>The method signature is of the form {@code (CT, T expectedValue, T newValue)boolean}.
     *
     * <p>The symbolic type descriptor at the call site of {@code
     * weakCompareAndSetRelease}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_RELEASE)}
     * on this VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T expectedValue, T newValue)}
     * , statically represented using varargs.
     * @return {@code true} if successful, otherwise {@code false} if the
     * witness value was not the same as the {@code expectedValue} or if this
     * operation spuriously failed.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     * @see #setRelease(Object...)
     * @see #get(Object...)
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    boolean weakCompareAndSetRelease(Object... args);

    /**
     * Atomically sets the value of a variable to the {@code newValue} with the
     * memory semantics of {@link #setVolatile} and returns the variable's
     * previous value, as accessed with the memory semantics of
     * {@link #getVolatile}.
     *
     * <p>The method signature is of the form {@code (CT, T newValue)T}.
     *
     * <p>The symbolic type descriptor at the call site of {@code getAndSet}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.GET_AND_SET)} on this
     * VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T newValue)}
     * , statically represented using varargs.
     * @return the signature-polymorphic result that is the previous value of
     * the variable
     * , statically represented using {@code Object}.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     * @see #setVolatile(Object...)
     * @see #getVolatile(Object...)
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    Object getAndSet(Object... args);


    // Primitive adders
    // Throw UnsupportedOperationException for refs

    /**
     * Atomically adds the {@code value} to the current value of a variable with
     * the memory semantics of {@link #setVolatile}, and returns the variable's
     * previous value, as accessed with the memory semantics of
     * {@link #getVolatile}.
     *
     * <p>The method signature is of the form {@code (CT, T value)T}.
     *
     * <p>The symbolic type descriptor at the call site of {@code getAndAdd}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.GET_AND_ADD)} on this
     * VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T value)}
     * , statically represented using varargs.
     * @return the signature-polymorphic result that is the previous value of
     * the variable
     * , statically represented using {@code Object}.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     * @see #setVolatile(Object...)
     * @see #getVolatile(Object...)
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    Object getAndAdd(Object... args);

    /**
     * Atomically adds the {@code value} to the current value of a variable with
     * the memory semantics of {@link #setVolatile}, and returns the variable's
     * current (updated) value, as accessed with the memory semantics of
     * {@link #getVolatile}.
     *
     * <p>The method signature is of the form {@code (CT, T value)T}.
     *
     * <p>The symbolic type descriptor at the call site of {@code addAndGet}
     * must match the access mode type that is the result of calling
     * {@code accessModeType(VarHandle.AccessMode.ADD_AND_GET)} on this
     * VarHandle.
     *
     * @param args the signature-polymorphic parameter list of the form
     * {@code (CT, T value)}
     * , statically represented using varargs.
     * @return the signature-polymorphic result that is the current value of
     * the variable
     * , statically represented using {@code Object}.
     * @throws UnsupportedOperationException if the access mode is unsupported
     * for this VarHandle.
     * @throws WrongMethodTypeException if the access mode type is not
     * compatible with the caller's symbolic type descriptor.
     * @throws ClassCastException if the access mode type is compatible with the
     * caller's symbolic type descriptor, but a reference cast fails.
     * @see #setVolatile(Object...)
     * @see #getVolatile(Object...)
     */
    public final native
    @MethodHandle.PolymorphicSignature
    @HotSpotIntrinsicCandidate
    Object addAndGet(Object... args);

    enum AccessType {
        GET,                    // 0
        SET,                    // 1
        COMPARE_AND_SWAP,       // 2
        COMPARE_AND_EXCHANGE,   // 3
        GET_AND_UPDATE;         // 4

        MethodType getMethodType(VarHandle vh) {
            return getMethodType(this.ordinal(), vh);
        }

        @ForceInline
        static MethodType getMethodType(int ordinal, VarHandle vh) {
            if (ordinal == 0) {
                return vh.typeGet;
            }
            else if (ordinal == 1) {
                return vh.typeSet;
            }
            else if (ordinal == 2) {
                return vh.typeCompareSwap;
            }
            else if (ordinal == 3) {
                return vh.typeCompareExchange;
            }
            else if (ordinal == 4) {
                return vh.typeGetAndUpdate;
            }
            else {
                throw new IllegalStateException("Illegal access type: " + ordinal);
            }
        }
    }

    /**
     * The set of access modes that specify how a variable, referenced by a
     * VarHandle, is accessed.
     */
    public enum AccessMode {
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#get VarHandle.get}
         */
        GET("get", AccessType.GET, Object.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#set VarHandle.set}
         */
        SET("set", AccessType.SET, void.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#getVolatile VarHandle.getVolatile}
         */
        GET_VOLATILE("getVolatile", AccessType.GET, Object.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#setVolatile VarHandle.setVolatile}
         */
        SET_VOLATILE("setVolatile", AccessType.SET, void.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#getAcquire VarHandle.getAcquire}
         */
        GET_ACQUIRE("getAcquire", AccessType.GET, Object.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#setRelease VarHandle.setRelease}
         */
        SET_RELEASE("setRelease", AccessType.SET, void.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#getOpaque VarHandle.getOpaque}
         */
        GET_OPAQUE("getOpaque", AccessType.GET, Object.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#setOpaque VarHandle.setOpaque}
         */
        SET_OPAQUE("setOpaque", AccessType.SET, void.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#compareAndSet VarHandle.compareAndSet}
         */
        COMPARE_AND_SET("compareAndSet", AccessType.COMPARE_AND_SWAP, boolean.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#compareAndExchangeVolatile VarHandle.compareAndExchangeVolatile}
         */
        COMPARE_AND_EXCHANGE_VOLATILE("compareAndExchangeVolatile", AccessType.COMPARE_AND_EXCHANGE, Object.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#compareAndExchangeAcquire VarHandle.compareAndExchangeAcquire}
         */
        COMPARE_AND_EXCHANGE_ACQUIRE("compareAndExchangeAcquire", AccessType.COMPARE_AND_EXCHANGE, Object.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#compareAndExchangeRelease VarHandle.compareAndExchangeRelease}
         */
        COMPARE_AND_EXCHANGE_RELEASE("compareAndExchangeRelease", AccessType.COMPARE_AND_EXCHANGE, Object.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#weakCompareAndSet VarHandle.weakCompareAndSet}
         */
        WEAK_COMPARE_AND_SET("weakCompareAndSet", AccessType.COMPARE_AND_SWAP, boolean.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#weakCompareAndSetVolatile VarHandle.weakCompareAndSetVolatile}
         */
        WEAK_COMPARE_AND_SET_VOLATILE("weakCompareAndSetVolatile", AccessType.COMPARE_AND_SWAP, boolean.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#weakCompareAndSetAcquire VarHandle.weakCompareAndSetAcquire}
         */
        WEAK_COMPARE_AND_SET_ACQUIRE("weakCompareAndSetAcquire", AccessType.COMPARE_AND_SWAP, boolean.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#weakCompareAndSetRelease VarHandle.weakCompareAndSetRelease}
         */
        WEAK_COMPARE_AND_SET_RELEASE("weakCompareAndSetRelease", AccessType.COMPARE_AND_SWAP, boolean.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#getAndSet VarHandle.getAndSet}
         */
        GET_AND_SET("getAndSet", AccessType.GET_AND_UPDATE, Object.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#getAndAdd VarHandle.getAndAdd}
         */
        GET_AND_ADD("getAndAdd", AccessType.GET_AND_UPDATE, Object.class),
        /**
         * The access mode whose access is specified by the corresponding
         * method
         * {@link VarHandle#addAndGet VarHandle.addAndGet}
         */
        ADD_AND_GET("addAndGet", AccessType.GET_AND_UPDATE, Object.class),
        ;

        static final Map<String, AccessMode> methodNameToAccessMode;
        static {
            // Initial capacity of # values is sufficient to avoid resizes
            // for the smallest table size (32)
            methodNameToAccessMode = new HashMap<>(AccessMode.values().length);
            for (AccessMode am : AccessMode.values()) {
                methodNameToAccessMode.put(am.methodName, am);
            }
        }

        final String methodName;
        final AccessType at;
        final boolean isPolyMorphicInReturnType;
        final Class<?> returnType;

        AccessMode(final String methodName, AccessType at, Class<?> returnType) {
            this.methodName = methodName;
            this.at = at;

            // Assert method name is correctly derived from value name
            assert methodName.equals(toMethodName(name()));
            // Assert that return type is correct
            // Otherwise, when disabled avoid using reflection
            assert returnType == getReturnType(methodName);

            this.returnType = returnType;
            isPolyMorphicInReturnType = returnType != Object.class;
        }

        /**
         * Returns the {@code VarHandle} signature-polymorphic method name
         * associated with this {@code AccessMode} value
         *
         * @return the signature-polymorphic method name
         * @see #valueFromMethodName
         */
        public String methodName() {
            return methodName;
        }

        /**
         * Returns the {@code AccessMode} value associated with the specified
         * {@code VarHandle} signature-polymorphic method name.
         *
         * @param methodName the signature-polymorphic method name
         * @return the {@code AccessMode} value
         * @throws IllegalArgumentException if there is no {@code AccessMode}
         *         value associated with method name (indicating the method
         *         name does not correspond to a {@code VarHandle}
         *         signature-polymorphic method name).
         * @see #methodName
         */
        public static AccessMode valueFromMethodName(String methodName) {
            AccessMode am = methodNameToAccessMode.get(methodName);
            if (am != null) return am;
            throw new IllegalArgumentException("No AccessMode value for method name " + methodName);
        }

        private static String toMethodName(String name) {
            StringBuilder s = new StringBuilder(name.toLowerCase());
            int i;
            while ((i = s.indexOf("_")) !=  -1) {
                s.deleteCharAt(i);
                s.setCharAt(i, Character.toUpperCase(s.charAt(i)));
            }
            return s.toString();
        }

        private static Class<?> getReturnType(String name) {
            try {
                Method m = VarHandle.class.getMethod(name, Object[].class);
                return m.getReturnType();
            }
            catch (Exception e) {
                throw newInternalError(e);
            }
        }

        @ForceInline
        static MemberName getMemberName(int ordinal, VarForm vform) {
            return vform.table[ordinal];
        }
    }

    static final class AccessDescriptor {
        final MethodType symbolicMethodType;
        final int type;
        final int mode;

        public AccessDescriptor(MethodType symbolicMethodType, int type, int mode) {
            this.symbolicMethodType = symbolicMethodType;
            this.type = type;
            this.mode = mode;
        }
    }

    /**
     * Returns the variable type of variables referenced by this VarHandle.
     *
     * @return the variable type of variables referenced by this VarHandle
     */
    public final Class<?> varType() {
        return typeSet.parameterType(typeSet.parameterCount() - 1);
    }

    /**
     * Returns the coordinate types for this VarHandle.
     *
     * @return the coordinate types for this VarHandle. The returned
     * list is unmodifiable
     */
    public final List<Class<?>> coordinateTypes() {
        return typeGet.parameterList();
    }

    /**
     * Obtains the canonical access mode type for this VarHandle and a given
     * access mode.
     *
     * <p>The access mode type's parameter types will consist of a prefix that
     * is the coordinate types of this VarHandle followed by further
     * types as defined by the access mode's method.
     * The access mode type's return type is defined by the return type of the
     * access mode's method.
     *
     * @param accessMode the access mode, corresponding to the
     * signature-polymorphic method of the same name
     * @return the access mode type for the given access mode
     */
    public final MethodType accessModeType(AccessMode accessMode) {
        return accessMode.at.getMethodType(this);
    }


    /**
     * Returns {@code true} if the given access mode is supported, otherwise
     * {@code false}.
     *
     * <p>The return of a {@code false} value for a given access mode indicates
     * that an {@code UnsupportedOperationException} is thrown on invocation
     * of the corresponding access mode's signature-polymorphic method.
     *
     * @param accessMode the access mode, corresponding to the
     * signature-polymorphic method of the same name
     * @return {@code true} if the given access mode is supported, otherwise
     * {@code false}.
     */
    public final boolean isAccessModeSupported(AccessMode accessMode) {
        return AccessMode.getMemberName(accessMode.ordinal(), vform) != null;
    }

    /**
     * Obtains a method handle bound to this VarHandle and the given access
     * mode.
     *
     * @apiNote This method, for a VarHandle {@code vh} and access mode
     * {@code {access-mode}}, returns a method handle that is equivalent to
     * method handle {@code bhm} in the following code (though it may be more
     * efficient):
     * <pre>{@code
     * MethodHandle mh = MethodHandles.varHandleExactInvoker(
     *                       vh.accessModeType(VarHandle.AccessMode.{access-mode}));
     *
     * MethodHandle bmh = mh.bindTo(vh);
     * }</pre>
     *
     * @param accessMode the access mode, corresponding to the
     * signature-polymorphic method of the same name
     * @return a method handle bound to this VarHandle and the given access mode
     */
    public final MethodHandle toMethodHandle(AccessMode accessMode) {
        MemberName mn = AccessMode.getMemberName(accessMode.ordinal(), vform);
        if (mn != null) {
            return DirectMethodHandle.make(mn).
                    bindTo(this).
                    asType(accessMode.at.getMethodType(this));
        }
        else {
            // Ensure an UnsupportedOperationException is thrown
            return MethodHandles.varHandleInvoker(accessMode, accessModeType(accessMode)).
                    bindTo(this);
        }
    }

    /*non-public*/
    final void updateVarForm(VarForm newVForm) {
        if (vform == newVForm) return;
        UNSAFE.putObject(this, VFORM_OFFSET, newVForm);
        UNSAFE.fullFence();
    }

    static final BiFunction<String, List<Integer>, ArrayIndexOutOfBoundsException>
            AIOOBE_SUPPLIER = Objects.outOfBoundsExceptionFormatter(
            new Function<String, ArrayIndexOutOfBoundsException>() {
                @Override
                public ArrayIndexOutOfBoundsException apply(String s) {
                    return new ArrayIndexOutOfBoundsException(s);
                }
            });

    private static final long VFORM_OFFSET;

    static {
        try {
            VFORM_OFFSET = UNSAFE.objectFieldOffset(VarHandle.class.getDeclaredField("vform"));
        }
        catch (ReflectiveOperationException e) {
            throw newInternalError(e);
        }
    }


    // Fence methods

    /**
     * Ensures that loads and stores before the fence will not be reordered
     * with
     * loads and stores after the fence.
     *
     * @apiNote Ignoring the many semantic differences from C and C++, this
     * method has memory ordering effects compatible with
     * {@code atomic_thread_fence(memory_order_seq_cst)}
     */
    @ForceInline
    public static void fullFence() {
        UNSAFE.fullFence();
    }

    /**
     * Ensures that loads before the fence will not be reordered with loads and
     * stores after the fence.
     *
     * @apiNote Ignoring the many semantic differences from C and C++, this
     * method has memory ordering effects compatible with
     * {@code atomic_thread_fence(memory_order_acquire)}
     */
    @ForceInline
    public static void acquireFence() {
        UNSAFE.loadFence();
    }

    /**
     * Ensures that loads and stores before the fence will not be
     * reordered with stores after the fence.
     *
     * @apiNote Ignoring the many semantic differences from C and C++, this
     * method has memory ordering effects compatible with
     * {@code atomic_thread_fence(memory_order_release)}
     */
    @ForceInline
    public static void releaseFence() {
        UNSAFE.storeFence();
    }

    /**
     * Ensures that loads before the fence will not be reordered with
     * loads after the fence.
     */
    @ForceInline
    public static void loadLoadFence() {
        UNSAFE.loadLoadFence();
    }

    /**
     * Ensures that stores before the fence will not be reordered with
     * stores after the fence.
     */
    @ForceInline
    public static void storeStoreFence() {
        UNSAFE.storeStoreFence();
    }
}
