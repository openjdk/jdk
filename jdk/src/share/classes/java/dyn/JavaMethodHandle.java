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

import sun.dyn.Access;

/**
 * A Java method handle is a deprecated proposal for extending
 * the basic method handle type with additional
 * programmer defined methods and fields.
 * Its behavior as a method handle is determined at instance creation time,
 * by providing the new instance with an "entry point" method handle
 * to handle calls.  This entry point must accept a leading argument
 * whose type is the Java method handle itself or a supertype, and the
 * entry point is always called with the Java method handle itself as
 * the first argument.  This is similar to ordinary virtual methods, which also
 * accept the receiver object {@code this} as an implicit leading argument.
 * The {@code MethodType} of the Java method handle is the same as that
 * of the entry point method handle, with the leading parameter type
 * omitted.
 * <p>
 * Here is an example of usage, creating a hybrid object/functional datum:
 * <p><blockquote><pre>
 * class Greeter extends JavaMethodHandle {
 *     private String greeting = "hello";
 *     public void setGreeting(String s) { greeting = s; }
 *     public void run() { System.out.println(greeting+", "+greetee); }
 *     private final String greetee;
 *     Greeter(String greetee) {
 *         super(RUN); // alternatively, super("run")
 *         this.greetee = greetee;
 *     }
 *     // the entry point function is computed once:
 *     private static final MethodHandle RUN
 *         = MethodHandles.lookup().findVirtual(Greeter.class, "run",
 *               MethodType.make(void.class));
 * }
 * // class Main { public static void main(String... av) { ...
 * Greeter greeter = new Greeter("world");
 * greeter.run();  // prints "hello, world"
 * // Statically typed method handle invocation (most direct):
 * MethodHandle mh = greeter;
 * mh.&lt;void&gt;invokeExact();  // also prints "hello, world"
 * // Dynamically typed method handle invocation:
 * MethodHandles.invokeExact(greeter);  // also prints "hello, world"
 * greeter.setGreeting("howdy");
 * mh.invokeExact();  // prints "howdy, world" (object-like mutable behavior)
 * </pre></blockquote>
 * <p>
 * In the example of {@code Greeter}, the method {@code run} provides the entry point.
 * The entry point need not be a constant value; it may be independently
 * computed in each call to the constructor.  The entry point does not
 * even need to be a method on the {@code Greeter} class, though
 * that is the typical case.
 * <p>
 * The entry point may also be provided symbolically, in which case the the
 * {@code JavaMethodHandle} constructor performs the lookup of the entry point.
 * This makes it possible to use {@code JavaMethodHandle} to create an anonymous
 * inner class:
 * <p><blockquote><pre>
 * // We can also do this with symbolic names and/or inner classes:
 * MethodHandles.invokeExact(new JavaMethodHandle("yow") {
 *     void yow() { System.out.println("yow, world"); }
 * });
 * </pre></blockquote>
 * <p>
 * Here is similar lower-level code which works in terms of a bound method handle.
 * <p><blockquote><pre>
 *     class Greeter {
 *         public void run() { System.out.println("hello, "+greetee); }
 *         private final String greetee;
 *         Greeter(String greetee) { this.greetee = greetee; }
 *         // the entry point function is computed once:
 *         private static final MethodHandle RUN
 *             = MethodHandles.findVirtual(Greeter.class, "run",
 *                   MethodType.make(void.class));
 *     }
 *     // class Main { public static void main(String... av) { ...
 *     Greeter greeter = new Greeter("world");
 *     greeter.run();  // prints "hello, world"
 *     MethodHandle mh = MethodHanndles.insertArgument(Greeter.RUN, 0, greeter);
 *     mh.invokeExact();  // also prints "hello, world"
 * </pre></blockquote>
 * Note that the method handle must be separately created as a view on the base object.
 * This increases footprint, complexity, and dynamic indirections.
 * <p>
 * Here is a pure functional value expressed most concisely as an anonymous inner class:
 * <p><blockquote><pre>
 *     // class Main { public static void main(String... av) { ...
 *     final String greetee = "world";
 *     MethodHandle greeter = new JavaMethodHandle("run") {
 *         private void run() { System.out.println("hello, "+greetee); }
 *     }
 *     greeter.invokeExact();  // prints "hello, world"
 * </pre></blockquote>
 * <p>
 * Here is an abstract parameterized lvalue, efficiently expressed as a subtype of MethodHandle,
 * and instantiated as an anonymous class.  The data structure is a handle to 1-D array,
 * with a specialized index type (long).  It is created by inner class, and uses
 * signature-polymorphic APIs throughout.
 * <p><blockquote><pre>
 *     abstract class AssignableMethodHandle extends JavaMethodHandle {
 *       private final MethodHandle setter;
 *       public MethodHandle setter() { return setter; }
 *       public AssignableMethodHandle(String get, String set) {
 *         super(get);
 *         MethodType getType = this.type();
 *         MethodType setType = getType.insertParameterType(getType.parameterCount(), getType.returnType()).changeReturnType(void.class);
 *         this.setter = MethodHandles.publicLookup().bind(this, set, setType);
 *       }
 *     }
 *     // class Main { public static void main(String... av) { ...
 *     final Number[] stuff = { 123, 456 };
 *     AssignableMethodHandle stuffPtr = new AssignableMethodHandle("get", "set") {
 *         public Number get(long i)           { return stuff[(int)i]; }
 *         public void   set(long i, Object x) {        stuff[(int)i] = x; }
 *     }
 *     int x = (Integer) stuffPtr.&lt;Number&gt;invokeExact(1L);  // 456
 *     stuffPtr.setter().&lt;void&gt;invokeExact(0L, (Number) 789);  // replaces 123 with 789
 * </pre></blockquote>
 * @see MethodHandle
 * @deprecated The JSR 292 EG intends to replace {@code JavaMethodHandle} with
 * an interface-based API for mixing method handle behavior with other classes.
 * @author John Rose, JSR 292 EG
 */
public abstract class JavaMethodHandle
        // Note: This is an implementation inheritance hack, and will be removed
        // with a JVM change which moves the required hidden behavior onto this class.
        extends sun.dyn.BoundMethodHandle
{
    private static final Access IMPL_TOKEN = Access.getToken();

    /**
     * When creating a {@code JavaMethodHandle}, the actual method handle
     * invocation behavior will be delegated to the specified {@code entryPoint}.
     * This may be any method handle which can take the newly constructed object
     * as a leading parameter.
     * <p>
     * The method handle type of {@code this} (i.e, the fully constructed object)
     * will be {@code entryPoint}, minus the leading argument.
     * The leading argument will be bound to {@code this} on every method
     * handle invocation.
     * @param entryPoint the method handle to handle calls
     */
    protected JavaMethodHandle(MethodHandle entryPoint) {
        super(entryPoint);
    }

    /**
     * Create a method handle whose entry point is a non-static method
     * visible in the exact (most specific) class of
     * the newly constructed object.
     * <p>
     * The method is specified by name and type, as if via this expression:
     * {@code MethodHandles.lookup().findVirtual(this.getClass(), name, type)}.
     * The class defining the method might be an anonymous inner class.
     * <p>
     * The method handle type of {@code this} (i.e, the fully constructed object)
     * will be the given method handle type.
     * A call to {@code this} will invoke the selected method.
     * The receiver argument will be bound to {@code this} on every method
     * handle invocation.
     * <p>
     * <i>Rationale:</i>
     * Although this constructor may seem to be a mere luxury,
     * it is not subsumed by the more general constructor which
     * takes any {@code MethodHandle} as the entry point argument.
     * In order to convert an entry point name to a method handle,
     * the self-class of the object is required (in order to do
     * the lookup).  The self-class, in turn, is generally not
     * available at the time of the constructor invocation,
     * due to the rules of Java and the JVM verifier.
     * One cannot call {@code this.getClass()}, because
     * the value of {@code this} is inaccessible at the point
     * of the constructor call.  (Changing this would require
     * change to the Java language, verifiers, and compilers.)
     * In particular, this constructor allows {@code JavaMethodHandle}s
     * to be created in combination with the anonymous inner class syntax.
     * @param entryPointName the name of the entry point method
     * @param type (optional) the desired type of the method handle
     */
    protected JavaMethodHandle(String entryPointName, MethodType type) {
        super(entryPointName, type, true);

    }

    /**
     * Create a method handle whose entry point is a non-static method
     * visible in the exact (most specific) class of
     * the newly constructed object.
     * <p>
     * The method is specified only by name.
     * There must be exactly one method of that name visible in the object class,
     * either inherited or locally declared.
     * (That is, the method must not be overloaded.)
     * <p>
     * The method handle type of {@code this} (i.e, the fully constructed object)
     * will be the same as the type of the selected non-static method.
     * The receiver argument will be bound to {@code this} on every method
     * handle invocation.
     * <p>ISSUE: This signature wildcarding feature does not correspond to
     * any MethodHandles.Lookup API element.  Can we eliminate it?
     * Alternatively, it is useful for naming non-overloaded methods.
     * Shall we make type arguments optional in the Lookup methods,
     * throwing an error in cases of ambiguity?
     * <p>
     * For this method's rationale, see the documentation
     * for {@link #JavaMethodHandle(String,MethodType)}.
     * @param entryPointName the name of the entry point method
     */
    protected JavaMethodHandle(String entryPointName) {
        super(entryPointName, (MethodType) null, false);
    }
}
