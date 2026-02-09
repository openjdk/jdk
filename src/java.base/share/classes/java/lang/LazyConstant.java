/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.lang.LazyConstantImpl;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A lazy constant is a holder of contents that can be set at most once.
 * <p>
 * A lazy constant is created using the factory method
 * {@linkplain LazyConstant#of(Supplier) LazyConstant.of({@code <computing function>})}.
 * <p>
 * When created, the lazy constant is <em>not initialized</em>, meaning it has no contents.
 * <p>
 * The lazy constant (of type {@code T}) can then be <em>initialized</em>
 * (and its contents retrieved) by calling {@linkplain #get() get()}. The first time
 * {@linkplain #get() get()} is called, the underlying <em>computing function</em>
 * (provided at construction) will be invoked and the result will be used to initialize
 * the constant.
 * <p>
 * Once a lazy constant is initialized, its contents can <em>never change</em>
 * and will be retrieved over and over again upon subsequent {@linkplain #get() get()}
 * invocations.
 * <p>
 * Consider the following example where a lazy constant field "{@code logger}" holds
 * an object of type {@code Logger}:
 *
 * {@snippet lang = java:
 * public class Component {
 *
 *    // Creates a new uninitialized lazy constant
 *    private final LazyConstant<Logger> logger =
 *            // @link substring="of" target="#of" :
 *            LazyConstant.of( () -> Logger.create(Component.class) );
 *
 *    public void process() {
 *        logger.get().info("Process started");
 *        // ...
 *    }
 * }
 * }
 * <p>
 * Initially, the lazy constant is <em>not initialized</em>. When {@code logger.get()}
 * is first invoked, it evaluates the computing function and initializes the constant to
 * the result; the result is then returned to the client. Hence, {@linkplain #get() get()}
 * guarantees that the constant is <em>initialized</em> before it returns, barring
 * any exceptions.
 * <p>
 * Furthermore, {@linkplain #get() get()} guarantees that, out of several threads trying to
 * invoke the computing function simultaneously, {@linkplain ##thread-safety only one is
 * ever selected} for computation. This property is crucial as evaluation of the computing
 * function may have side effects, for example, the call above to {@code Logger.create()}
 * may result in storage resources being prepared.
 *
 * <h2 id="exception-handling">Exception handling</h2>
 * If the computing function returns {@code null}, a {@linkplain NullPointerException}
 * is thrown. Hence, a lazy constant can never hold a {@code null} value. Clients who
 * want to use a nullable constant can wrap the value into an {@linkplain Optional} holder.
 * <p>
 * If the computing function recursively invokes itself via the lazy constant, an
 * {@linkplain IllegalStateException} is thrown, and the lazy constant is not initialized.
 *
 * <h2 id="composition">Composing lazy constants</h2>
 * A lazy constant can depend on other lazy constants, forming a dependency graph
 * that can be lazily computed but where access to individual elements can still be
 * performant. In the following example, a single {@code Foo} and a {@code Bar}
 * instance (that is dependent on the {@code Foo} instance) are lazily created, both of
 * which are held by lazy constants:
 *
 * {@snippet lang = java:
 * public final class DependencyUtil {
 *
 *     private DependencyUtil() {}
 *
 *     public static class Foo {
 *          // ...
 *      }
 *
 *     public static class Bar {
 *         public Bar(Foo foo) {
 *              // ...
 *         }
 *     }
 *
 *     private static final LazyConstant<Foo> FOO = LazyConstant.of( Foo::new );
 *     private static final LazyConstant<Bar> BAR = LazyConstant.of( () -> new Bar(FOO.get()) );
 *
 *     public static Foo foo() {
 *         return FOO.get();
 *     }
 *
 *     public static Bar bar() {
 *         return BAR.get();
 *     }
 *
 * }
 * }
 * Calling {@code BAR.get()} will create the {@code Bar} singleton if it is not already
 * created. Upon such a creation, a dependent {@code Foo} will first be created if
 * the {@code Foo} does not already exist.
 *
 * <h2 id="thread-safety">Thread Safety</h2>
 * A lazy constant is guaranteed to be initialized atomically and at most once. If
 * competing threads are racing to initialize a lazy constant, only one updating thread
 * runs the computing function (which runs on the caller's thread and is hereafter denoted
 * <em>the computing thread</em>), while the other threads are blocked until the constant
 * is initialized, after which the other threads observe the lazy constant is initialized
 * and leave the constant unchanged and will never invoke any computation.
 * <p>
 * The invocation of the computing function and the resulting initialization of
 * the constant {@linkplain java.util.concurrent##MemoryVisibility <em>happens-before</em>}
 * the initialized constant's content is read. Hence, the initialized constant's content,
 * including any {@code final} fields of any newly created objects, is safely published.
 * <p>
 * Thread interruption does not cancel the initialization of a lazy constant. In other
 * words, if the computing thread is interrupted, {@code LazyConstant::get} doesn't clear
 * the interrupted thread’s status, nor does it throw an {@linkplain InterruptedException}.
 * <p>
 * If the computing function blocks indefinitely, other threads operating on this
 * lazy constant may block indefinitely; no timeouts or cancellations are provided.
 *
 * <h2 id="performance">Performance</h2>
 * The contents of a lazy constant can never change after the lazy constant has been
 * initialized. Therefore, a JVM implementation may, for an initialized lazy constant,
 * elide all future reads of that lazy constant's contents and instead use the contents
 * that has been previously observed. We call this optimization <em>constant folding</em>.
 * This is only possible if there is a direct reference from a {@code static final} field
 * to a lazy constant or if there is a chain from a {@code static final} field -- via one
 * or more <em>trusted fields</em> (i.e., {@code static final} fields,
 * {@linkplain Record record} fields, or final instance fields in hidden classes) --
 * to a lazy constant.
 *
 * <h2 id="miscellaneous">Miscellaneous</h2>
 * Except for {@linkplain Object#equals(Object) equals(obj)} and
 * {@linkplain #orElse(Object) orElse(other)} parameters, all method parameters
 * must be <em>non-null</em>, or a {@link NullPointerException} will be thrown.
 *
 * @apiNote Once a lazy constant is initialized, its contents cannot ever be removed.
 *          This can be a source of an unintended memory leak. More specifically,
 *          a lazy constant {@linkplain java.lang.ref##reachability strongly references}
 *          it contents. Hence, the contents of a lazy constant will be reachable as long
 *          as the lazy constant itself is reachable.
 *          <p>
 *          While it's possible to store an array inside a lazy constant, doing so will
 *          not result in improved access performance of the array elements. Instead, a
 *          {@linkplain List#ofLazy(int, IntFunction) lazy list} of arbitrary depth can
 *          be used, which provides constant components.
 *          <p>
 *          The {@code LazyConstant} type is not {@link Serializable}.
 *          <p>
 *          Use in static initializers may interact with class initialization order;
 *          cyclic initialization may result in initialization errors as described
 *          in section {@jls 12.4} of <cite>The Java Language Specification</cite>.
 *
 * @implNote
 *           A lazy constant is free to synchronize on itself. Hence, care must be
 *           taken when directly or indirectly synchronizing on a lazy constant.
 *           A lazy constant is unmodifiable but its contents may or may not be
 *           immutable (e.g., it may hold an {@linkplain ArrayList}).
 *
 * @param <T> type of the constant
 *
 * @since 26
 *
 * @see Optional
 * @see Supplier
 * @see List#ofLazy(int, IntFunction)
 * @see Map#ofLazy(Set, Function)
 * @jls 12.4 Initialization of Classes and Interfaces
 * @jls 17.4.5 Happens-before Order
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
public sealed interface LazyConstant<T>
        extends Supplier<T>
        permits LazyConstantImpl {

    /**
     * {@return the contents of this lazy constant if initialized, otherwise,
     *          returns {@code other}}
     * <p>
     * This method never triggers initialization of this lazy constant and will observe
     * initialization by other threads atomically (i.e., it returns the contents
     * if and only if the initialization has already completed).
     *
     * @param other value to return if the content is not initialized
     *              (can be {@code null})
     */
    T orElse(T other);

    /**
     * {@return the contents of this initialized constant. If not initialized, first
     *          computes and initializes this constant using the computing function}
     * <p>
     * After this method returns successfully, the constant is guaranteed to be
     * initialized.
     * <p>
     * If the computing function throws, the throwable is relayed to the caller and
     * the lazy constant remains uninitialized; a subsequent call to get() may then
     * attempt the computation again.
     */
    T get();

    /**
     * {@return {@code true} if the constant is initialized, {@code false} otherwise}
     * <p>
     * This method never triggers initialization of this lazy constant and will observe
     * changes in the initialization state made by other threads atomically.
     */
    boolean isInitialized();

    // Object methods

    /**
     * {@return {@code true} if this lazy constant is the same instance as
     *          the provided {@code obj}, otherwise {@code false}}
     * <p>
     * In other words, equals compares the identity of this lazy constant and {@code obj}
     * to determine equality. Hence, two distinct lazy constants with the same contents are
     * <em>not</em> equal.
     * <p>
     * This method never triggers initialization of this lazy constant.
     */
    @Override
    boolean equals(Object obj);

    /**
     * {@return the {@linkplain System#identityHashCode(Object) identity hash code} for
     *          this lazy constant}
     *
     * This method never triggers initialization of this lazy constant.
     */
    @Override
    int hashCode();

    /**
     * {@return a string suitable for debugging}
     * <p>
     * This method never triggers initialization of this lazy constant and will observe
     * initialization by other threads atomically (i.e., it observes the
     * contents if and only if the initialization has already completed).
     * <p>
     * If this lazy constant is initialized, an implementation-dependent string
     * containing the {@linkplain Object#toString()} of the
     * contents will be returned; otherwise, an implementation-dependent string is
     * returned that indicates this lazy constant is not yet initialized.
     */
    @Override
    String toString();

    // Factory

    /**
     * {@return a lazy constant whose contents is to be computed later via the provided
     *          {@code computingFunction}}
     * <p>
     * The returned lazy constant strongly references the provided
     * {@code computingFunction} at least until initialization completes successfully.
     * <p>
     * If the provided computing function is already an instance of
     * {@code LazyConstant}, the method is free to return the provided computing function
     * directly.
     *
     * @implNote  after initialization completes successfully, the computing function is
     *            no longer strongly referenced and becomes eligible for
     *            garbage collection.
     *
     * @param computingFunction in the form of a {@linkplain Supplier} to be used
     *                          to initialize the constant
     * @param <T>               type of the constant
     *
     */
    @SuppressWarnings("unchecked")
    static <T> LazyConstant<T> of(Supplier<? extends T> computingFunction) {
        Objects.requireNonNull(computingFunction);
        if (computingFunction instanceof LazyConstant<? extends T> lc) {
            return (LazyConstant<T>) lc;
        }
        return LazyConstantImpl.ofLazy(computingFunction);
    }

}
