/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import java.security.CodeSource;
import java.security.ProtectionDomain;

/**
 * Responsible for loading script generated classes.
 *
 */
final class ScriptLoader extends NashornLoader {
    private static final String NASHORN_PKG_PREFIX = "jdk.nashorn.internal.";

    private final Context context;

    /*package-private*/ Context getContext() {
        return context;
    }

    /**
     * Constructor.
     */
    ScriptLoader(final ClassLoader parent, final Context context) {
        super(parent);
        this.context = context;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        checkPackageAccess(name);
        if (name.startsWith(NASHORN_PKG_PREFIX)) {
            return context.getSharedLoader().loadClass(name);
        }
        return super.loadClass(name, resolve);
    }

    // package-private and private stuff below this point

    /**
     * Install a class for use by the Nashorn runtime
     *
     * @param name Binary name of class.
     * @param data Class data bytes.
     * @param cs CodeSource code source of the class bytes.
     *
     * @return Installed class.
     */
    synchronized Class<?> installClass(final String name, final byte[] data, final CodeSource cs) {
        if (cs == null) {
            return defineClass(name, data, 0, data.length, new ProtectionDomain(null, getPermissions(null)));
        }
        return defineClass(name, data, 0, data.length, cs);
    }
}
