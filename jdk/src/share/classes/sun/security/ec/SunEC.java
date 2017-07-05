/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.ec;

import java.util.*;
import java.security.*;
import sun.security.action.PutAllAction;

/**
 * Provider class for the Elliptic Curve provider.
 * Supports EC keypair and parameter generation, ECDSA signing and
 * ECDH key agreement.
 *
 * IMPLEMENTATION NOTE:
 * The Java classes in this provider access a native ECC implementation
 * via JNI to a C++ wrapper class which in turn calls C functions.
 * The Java classes are packaged into the signed sunec.jar in the JRE
 * extensions directory and the C++ and C functions are packaged into
 * libsunecc.so or sunecc.dll in the JRE native libraries directory.
 *
 * @since   1.7
 */
public final class SunEC extends Provider {

    private static final long serialVersionUID = -2279741672933606418L;

    public SunEC() {
        super("SunEC", 1.7d, "Sun Elliptic Curve provider (EC, ECDSA, ECDH)");

        // if there is no security manager installed, put directly into
        // the provider. Otherwise, create a temporary map and use a
        // doPrivileged() call at the end to transfer the contents
        if (System.getSecurityManager() == null) {
            SunECEntries.putEntries(this);
        } else {
            Map<Object, Object> map = new HashMap<Object, Object>();
            SunECEntries.putEntries(map);
            AccessController.doPrivileged(new PutAllAction(this, map));
        }
    }

}
