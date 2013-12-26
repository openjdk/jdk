/*
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.net.ssl.internal.ssl;

import sun.security.ssl.SunJSSE;

/**
 * Main class for the SunJSSE provider. The actual code was moved to the
 * class sun.security.ssl.SunJSSE, but for backward compatibility we
 * continue to use this class as the main Provider class.
 */
public final class Provider extends SunJSSE {

    private static final long serialVersionUID = 3231825739635378733L;

    // standard constructor
    public Provider() {
        super();
    }

    // preferred constructor to enable FIPS mode at runtime
    public Provider(java.security.Provider cryptoProvider) {
        super(cryptoProvider);
    }

    // constructor to enable FIPS mode from java.security file
    public Provider(String cryptoProvider) {
        super(cryptoProvider);
    }

    // public for now, but we may want to change it or not document it.
    public static synchronized boolean isFIPS() {
        return SunJSSE.isFIPS();
    }

    /**
     * Installs the JSSE provider.
     */
    public static synchronized void install() {
        /* nop. Remove this method in the future. */
    }

}
