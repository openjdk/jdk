/*
 * Copyright 2000-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * NOTE:  this file was copied from javax.net.ssl.SSLContextSpi
 */

package com.sun.net.ssl;

import java.util.*;
import java.security.*;
import javax.net.ssl.*;

/**
 * This class defines the <i>Service Provider Interface</i> (<b>SPI</b>)
 * for the <code>SSLContext</code> class.
 *
 * <p> All the abstract methods in this class must be implemented by each
 * cryptographic service provider who wishes to supply the implementation
 * of a particular SSL context.
 *
 * @deprecated As of JDK 1.4, this implementation-specific class was
 *      replaced by {@link javax.net.ssl.SSLContextSpi}.
 */
@Deprecated
public abstract class SSLContextSpi {
    /**
     * Initializes this context.
     *
     * @param km the sources of authentication keys
     * @param tm the sources of peer authentication trust decisions
     * @param random the source of randomness for this generator
     */
    protected abstract void engineInit(KeyManager[] ah, TrustManager[] th,
        SecureRandom sr) throws KeyManagementException;

    /**
     * Returns a <code>SocketFactory</code> object for this
     * context.
     *
     * @return the factory
     */
    protected abstract SSLSocketFactory engineGetSocketFactory();

    /**
     * Returns a <code>ServerSocketFactory</code> object for
     * this context.
     *
     * @return the factory
     */
    protected abstract SSLServerSocketFactory engineGetServerSocketFactory();
}
