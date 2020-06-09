/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.serviceprovider;

/**
 * Utility methods that provide access to isolate details.
 */
public final class IsolateUtil {

    /**
     * Gets the address of the current isolate or 0 if this not an isolate-aware runtime.
     */
    public static long getIsolateAddress() {
        // Substituted by
        // com.oracle.svm.graal.Target_org_graalvm_compiler_serviceprovider_IsolateUtil
        return 0;
    }

    /**
     * Gets an identifier for the current isolate or 0 if this not an isolate-aware runtime. The
     * returned value is guaranteed to be unique for the first {@code 2^64 - 1} isolates in the
     * process.
     */
    public static long getIsolateID() {
        // Substituted by
        // com.oracle.svm.graal.Target_org_graalvm_compiler_serviceprovider_IsolateUtil
        return 0;
    }

    /**
     * Gets a string identifying the current isolate.
     *
     * If this is not an isolate-aware runtime, an empty string is returned.
     *
     * If {@code withAddress == true}, then
     * {@code String.format("%d@%x", getIsolateID(), getIsolateAddress())} is returned.
     *
     * Otherwise, {@code String.valueOf(getIsolateID())} is returned.
     */
    public static String getIsolateID(boolean withAddress) {
        long isolateAddress = getIsolateAddress();
        if (isolateAddress == 0) {
            return "";
        }
        if (withAddress) {
            return String.format("%d@%x", getIsolateID(), isolateAddress);
        }
        return String.valueOf(getIsolateID());
    }

    private IsolateUtil() {
    }
}
