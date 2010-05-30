/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.server;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutput;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.activation.ActivationID;
import sun.rmi.transport.LiveRef;

/**
 * Server-side ref for a persistent remote impl.
 *
 * @author Ann Wollrath
 */
public class ActivatableServerRef extends UnicastServerRef2 {

    private static final long serialVersionUID = 2002967993223003793L;

    private ActivationID id;

    /**
     * Construct a Unicast server remote reference to be exported
     * on the specified port.
     */
    public ActivatableServerRef(ActivationID id, int port)
    {
        this(id, port, null, null);
    }

    /**
     * Construct a Unicast server remote reference to be exported
     * on the specified port.
     */
    public ActivatableServerRef(ActivationID id, int port,
                                RMIClientSocketFactory csf,
                                RMIServerSocketFactory ssf)
    {
        super(new LiveRef(port, csf, ssf));
        this.id = id;
    }

    /**
     * Returns the class of the ref type to be serialized
     */
    public String getRefClass(ObjectOutput out)
    {
        return "ActivatableServerRef";
    }

    /**
     * Return the client remote reference for this remoteRef.
     * In the case of a client RemoteRef "this" is the answer.
     * For  a server remote reference, a client side one will have to
     * found or created.
     */
    protected RemoteRef getClientRef() {
        return new ActivatableRef(id, new UnicastRef2(ref));
    }

    /**
     * Prevents serialization (because deserializaion is impossible).
     */
    public void writeExternal(ObjectOutput out) throws IOException {
        throw new NotSerializableException(
            "ActivatableServerRef not serializable");
    }
}
