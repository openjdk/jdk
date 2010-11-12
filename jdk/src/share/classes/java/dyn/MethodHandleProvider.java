/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

/**
 * An interface for an object to provide a target {@linkplain MethodHandle method handle} to a {@code invokedynamic} instruction.
 * There are many function-like objects in various Java APIs.
 * This interface provides a standard way for such function-like objects to be bound
 * to a dynamic call site, by providing a view of their behavior in the form of a low-level method handle.
 * <p>
 * The type {@link MethodHandle} is a concrete class whose implementation
 * hierarchy (if any) may be tightly coupled to the underlying JVM implementation.
 * It cannot also serve as a base type for user-defined functional APIs.
 * For this reason, {@code MethodHandle} cannot be subclassed to add new
 * behavior to method handles.  But this interface can be used to provide
 * a link between a user-defined function and the {@code invokedynamic}
 * instruction and the method handle API.
 */
public interface MethodHandleProvider {
    /** Produce a method handle which will serve as a behavioral proxy for the current object.
     *  The type and invocation behavior of the proxy method handle are user-defined,
     *  and should have some relation to the intended meaning of the original object itself.
     *  <p>
     *  The current object may have a changeable behavior.
     *  For example, {@link CallSite} has a {@code setTarget} method which changes its invocation.
     *  In such a case, it is <em>incorrect</em> for {@code asMethodHandle} to return
     *  a method handle whose behavior may diverge from that of the current object.
     *  Rather, the returned method handle must stably and permanently access
     *  the behavior of the current object, even if that behavior is changeable.
     *  <p>
     *  The reference identity of the proxy method handle is not guaranteed to
     *  have any particular relation to the reference identity of the object.
     *  In particular, several objects with the same intended meaning could
     *  share a common method handle, or the same object could return different
     *  method handles at different times.  In the latter case, the different
     *  method handles should have the same type and invocation behavior,
     *  and be usable from any thread at any time.
     *  In particular, if a MethodHandleProvider is bound to an <code>invokedynamic</code>
     *  call site, the proxy method handle extracted at the time of binding
     *  will be used for an unlimited time, until the call site is rebound.
     *  <p>
     *  The type {@link MethodHandle} itself implements {@code MethodHandleProvider}, and
     *  for this method simply returns {@code this}.
     */
    public MethodHandle asMethodHandle();

    /** Produce a method handle of a given type which will serve as a behavioral proxy for the current object.
     *  As for the no-argument version {@link #asMethodHandle()}, the invocation behavior of the
     *  proxy method handle is user-defined.  But the type must be the given type,
     *  or else a {@link WrongMethodTypeException} must be thrown.
     *  <p>
     *  If the current object somehow represents a variadic or overloaded behavior,
     *  the method handle returned for a given type might represent only a subset of
     *  the current object's repertoire of behaviors, which correspond to that type.
     */
    public MethodHandle asMethodHandle(MethodType type) throws WrongMethodTypeException;
}
