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

package sun.security.ssl;

import java.security.Provider;

import static sun.security.util.SecurityConstants.PROVIDER_VER;

// This is an internal provider used in the JSSE code for DH-as-KEM
// support. It doesn't actually get installed in the system's list
// of security providers that is searched at runtime.
// JSSE loads this provider internally.
// It exposes the internal DH-as-KEM adapter to Hybrid.KEMImpl through
// the public KEM API without exposing KEM.DH as a system provider
// service. The ML-KEM component is obtained from SunJCE.

final class HybridProvider {
    static final Provider PROVIDER = new ProviderImpl();

    private static final class ProviderImpl extends Provider {
        @java.io.Serial
        private static final long serialVersionUID = 0L;

        ProviderImpl() {
            super("DHAsKEM", PROVIDER_VER,
                    "Internal DH-as-KEM provider");
            put("KEM.DH", DHasKEM.class.getName());
        }
    }
}
