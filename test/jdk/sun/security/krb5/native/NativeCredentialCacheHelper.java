/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * JNI wrapper for native Kerberos credential cache operations.
 * Provides native methods to create in-memory credential caches and copy
 * Kerberos credentials from file credential caches for testing JAAS access.
 */
public class NativeCredentialCacheHelper {

    static {
        try {
            System.loadLibrary("NativeCredentialCacheHelper");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load NativeCredentialCacheHelper library: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Creates an in-memory credential ccache, copies credentials from a file ccache,
     * and sets KRB5CCNAME to the in-memory ccache.
     *
     * @param inMemoryCacheName The name for the MEMORY: ccache (e.g., "MEMORY:test123")
     * @param fileCacheName The FILE: ccache name to copy from (e.g., "FILE:/path/to/cache")
     * @return true if all operations succeeded, false if any operation failed
     */
    public static native boolean createInMemoryCacheFromFileCache(String inMemoryCacheName, String fileCacheName);
}
