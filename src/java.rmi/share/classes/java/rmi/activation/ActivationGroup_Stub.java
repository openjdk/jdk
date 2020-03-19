/*
 * Copyright (c) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.rmi.activation;

import java.lang.reflect.Method;
import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.UnexpectedException;
import java.rmi.server.RemoteRef;
import java.rmi.server.RemoteStub;

/**
 * {@code ActivationGroup_Stub} is a stub class for the subclasses of {@code java.rmi.activation.ActivationGroup}
 * that are exported as a {@code java.rmi.server.UnicastRemoteObject}.
 *
 * @since 1.2
 */
@SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
public final class ActivationGroup_Stub
        extends RemoteStub
        implements ActivationInstantiator, Remote {

    @java.io.Serial
    private static final long serialVersionUID = 2;

    private static Method $method_newInstance_0;

    static {
        try {
            $method_newInstance_0 =
                    ActivationInstantiator.class.getMethod("newInstance",
                            new Class<?>[] {ActivationID.class, ActivationDesc.class});
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(
                    "stub class initialization failed");
        }
    }

    /**
     * Constructs a stub for the {@code ActivationGroup} class.
     * It invokes the superclass {@code RemoteStub(RemoteRef)}
     * constructor with its argument, {@code ref}.
     *
     * @param ref a remote ref
     */
    public ActivationGroup_Stub(RemoteRef ref) {
        super(ref);
    }

    /**
     * Stub method for {@code ActivationGroup.newInstance}.  Invokes
     * the {@code invoke} method on this instance's
     * {@code RemoteObject.ref} field, with {@code this} as the
     * first argument, a two-element {@code Object[]} as the second
     * argument (with {@code id} as the first element and
     * {@code desc} as the second element), and -5274445189091581345L
     * as the third argument, and returns the result.  If that invocation
     * throws a {@code RuntimeException}, {@code RemoteException},
     * or an {@code ActivationException}, then that exception is
     * thrown to the caller.  If that invocation throws any other
     * {@code java.lang.Exception}, then a
     * {@code java.rmi.UnexpectedException} is thrown to the caller
     * with the original exception as the cause.
     *
     * @param id   an activation identifier
     * @param desc an activation descriptor
     * @return the result of the invocation
     * @throws RemoteException     if invocation results in a {@code RemoteException}
     * @throws ActivationException if invocation results in an {@code ActivationException}
     */
    public MarshalledObject newInstance(ActivationID id,
                                        ActivationDesc desc)
            throws RemoteException, ActivationException {
        try {
            Object $result = ref.invoke(this, $method_newInstance_0,
                    new Object[]{id, desc}, -5274445189091581345L);
            return ((MarshalledObject) $result);
        } catch (RuntimeException | RemoteException | ActivationException e) {
            throw e;
        } catch (Exception e) {
            throw new UnexpectedException("undeclared checked exception", e);
        }
    }
}
