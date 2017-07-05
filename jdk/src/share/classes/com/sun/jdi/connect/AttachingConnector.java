/*
 * Copyright 1998-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jdi.connect;

import com.sun.jdi.VirtualMachine;
import java.util.Map;
import java.io.IOException;

/**
 * A connector which attaches to a previously running target VM.
 *
 * @author Gordon Hirsch
 * @since  1.3
 */
public interface AttachingConnector extends Connector {
    /**
     * Attaches to a running application and and returns a
     * mirror of its VM.
     * <p>
     * The connector uses the given argument map in
     * attaching the application. These arguments will include addressing
     * information that identifies the VM.
     * The argument map associates argument name strings to instances
     * of {@link Connector.Argument}. The default argument map for a
     * connector can be obtained through {@link Connector#defaultArguments}.
     * Argument map values can be changed, but map entries should not be
     * added or deleted.
     *
     * @param arguments the argument map to be used in launching the VM.
     * @return the {@link VirtualMachine} mirror of the target VM.
     *
     * @throws TransportTimeoutException when the Connector encapsulates
     * a transport that supports a timeout when attaching, a
     * {@link Connector.Argument} representing a timeout has been set
     * in the argument map, and a timeout occurs when trying to attach
     * to the target VM.
     *
     * @throws java.io.IOException when unable to attach.
     * Specific exceptions are dependent on the Connector implementation
     * in use.
     * @throws IllegalConnectorArgumentsException when one of the
     * connector arguments is invalid.
     */
    VirtualMachine attach(Map<String,? extends Connector.Argument> arguments)
        throws IOException, IllegalConnectorArgumentsException;
}
