/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.dyn;

//import sun.dyn.*;

import sun.dyn.Access;
import sun.dyn.MethodHandleImpl;

/**
 * A method handle is a typed reference to the entry point of a method.
 * <p>
 * Method handles are strongly typed according to signature.
 * They are not distinguished by method name or enclosing class.
 * A method handle must be invoked under a signature which exactly matches
 * the method handle's own type.
 * <p>
 * Every method handle confesses its type via the <code>type</code> accessor.
 * The structure of this type is a series of classes, one of which is
 * the return type of the method (or <code>void.class</code> if none).
 * <p>
 * Every method handle appears as an object containing a method named
 * <code>invoke</code>, whose signature exactly matches
 * the method handle's type.
 * A normal Java method call (using the <code>invokevirtual</code> instruction)
 * can invoke this method from Java source code (if language support is present).
 * <p>
 * Every call to a method handle specifies an intended method type,
 * which must exactly match the type of the method handle.
 * (The type is specified in the <code>invokevirtual</code> instruction,
 * via a {@code CONSTANT_NameAndType} constant pool entry.)
 * The call looks within the receiver object for a method
 * named <code>invoke</code> of the intended method type.
 * The call fails with a {@link WrongMethodTypeException}
 * if the method does not exist, even if there is an <code>invoke</code>
 * method of a closely similar signature.
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
 * <code>invoke</code> from an <code>invokevirtual</code> instruction.
 * The receiver class type must be <code>MethodHandle</code> and the method name
 * must be <code>invoke</code>.  The signature of the invocation
 * (after resolving symbolic type names) must exactly match the method type
 * of the target method.
 * <p>
 * Bytecode in an extended JVM can directly obtain a method handle
 * for any accessible method from a <code>ldc</code> instruction
 * which refers to a <code>CONSTANT_Methodref</code> or
 * <code>CONSTANT_InterfaceMethodref</code> constant pool entry.
 * <p>
 * All JVMs can also use a reflective API called <code>MethodHandles</code>
 * for creating and calling method handles.
 * <p>
 * A method reference may refer either to a static or non-static method.
 * In the non-static case, the method handle type includes an explicit
 * receiver argument, prepended before any other arguments.
 * In the method handle's type, the initial receiver argument is typed
 * according to the class under which the method was initially requested.
 * (E.g., if a non-static method handle is obtained via <code>ldc</code>,
 * the type of the receiver is the class named in the constant pool entry.)
 * <p>
 * When a method handle to a virtual method is invoked, the method is
 * always looked up in the receiver (that is, the first argument).
 * <p>
 * A non-virtual method handles to a specific virtual method implementation
 * can also be created.  These do not perform virtual lookup based on
 * receiver type.  Such a method handle simulates the effect of
 * an <code>invokespecial</code> instruction to the same method.
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
    // interface MethodHandle<T extends MethodType<R,A...>>
    // { T type(); <R,A...> public R invoke(A...); }

    final private MethodType type;

    /**
     * Report the type of this method handle.
     * Every invocation of this method handle must exactly match this type.
     * @return the method handle type
     */
    public MethodType type() {
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
        this.type = type;
    }
}
