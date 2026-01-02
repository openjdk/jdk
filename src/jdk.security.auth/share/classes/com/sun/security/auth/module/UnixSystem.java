/*
 * Copyright (c) 2000, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.security.auth.module;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;

/**
 * This class implementation retrieves and makes available Unix
 * UID/GID/groups information for the current user.
 *
 * @since 1.4
 */
@SuppressWarnings("restricted")
public class UnixSystem {

    /**
     * The current username.
     */
    protected String username;

    /**
     * The current user ID.
     */
    protected long uid;

    /**
     * The current group ID.
     */
    protected long gid;

    /**
     * The current list of groups.
     */
    protected long[] groups;

    // Record the reason why getpwuid_r failed. UnixLoginModule
    // will display the message when debug is on.
    String getpwuid_r_error = null;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SYMBOL_LOOKUP = SymbolLookup.loaderLookup()
            .or(LINKER.defaultLookup());

    private static final ValueLayout.OfByte C_CHAR
            = (ValueLayout.OfByte) LINKER.canonicalLayouts().get("char");
    private static final ValueLayout.OfInt C_INT
            = (ValueLayout.OfInt) LINKER.canonicalLayouts().get("int");
    private static final AddressLayout C_POINTER
            = ((AddressLayout) LINKER.canonicalLayouts().get("void*"))
            .withTargetLayout(MemoryLayout.sequenceLayout(java.lang.Long.MAX_VALUE, C_CHAR));
    private static final ValueLayout.OfLong C_SIZE_T
            = (ValueLayout.OfLong) LINKER.canonicalLayouts().get("size_t");

    private static Linker.Option ccs = Linker.Option.captureCallState("errno");
    private static final StructLayout capturedStateLayout = Linker.Option.captureStateLayout();
    private static final VarHandle errnoHandle = capturedStateLayout.varHandle(
            MemoryLayout.PathElement.groupElement("errno"));

    private static final MethodHandle strerror = LINKER
            .downcallHandle(SYMBOL_LOOKUP.findOrThrow("strerror"),
                    FunctionDescriptor.of(C_POINTER, C_INT));

    private static final MethodHandle getgroups = LINKER
            .downcallHandle(SYMBOL_LOOKUP.findOrThrow("getgroups"),
                    FunctionDescriptor.of(C_INT, C_INT, C_POINTER), ccs);
    private static final MethodHandle getuid = LINKER
            .downcallHandle(SYMBOL_LOOKUP.findOrThrow("getuid"),
                    FunctionDescriptor.of(C_INT));
    private static final MethodHandle getgid = LINKER
            .downcallHandle(SYMBOL_LOOKUP.findOrThrow("getgid"),
                    FunctionDescriptor.of(C_INT));
    private static final MethodHandle getpwuid_r = LINKER
            .downcallHandle(SYMBOL_LOOKUP.findOrThrow("getpwuid_r"),
                    FunctionDescriptor.of(C_INT, C_INT, C_POINTER, C_POINTER,
                            C_SIZE_T, C_POINTER));

    private static final GroupLayout passwd_layout = MemoryLayout.structLayout(
            C_POINTER.withName("pw_name"),
            C_POINTER.withName("pw_passwd"),
            C_INT.withName("pw_uid"),
            C_INT.withName("pw_gid"),
            // Different platforms have different fields in `struct passwd`.
            // While we don't need those fields here, the struct needs to be
            // big enough to avoid buffer overflow when `getpwuid_r` is called.
            MemoryLayout.sequenceLayout(100, C_CHAR).withName("dummy"));

    private static final ValueLayout.OfInt pw_uid_layout
            = (ValueLayout.OfInt) passwd_layout.select(groupElement("pw_uid"));
    private static final long pw_uid_offset
            = passwd_layout.byteOffset(groupElement("pw_uid"));
    private static final ValueLayout.OfInt pw_gid_layout
            = (ValueLayout.OfInt) passwd_layout.select(groupElement("pw_gid"));
    private static final long pw_gid_offset
            = passwd_layout.byteOffset(groupElement("pw_gid"));
    private static final AddressLayout pw_name_layout
            = (AddressLayout) passwd_layout.select(groupElement("pw_name"));
    private static final long pw_name_offset
            = passwd_layout.byteOffset(groupElement("pw_name"));

    // sysconf(_SC_GETPW_R_SIZE_MAX) on macOS is 4096 and 1024 on Linux
    private static final long GETPW_R_SIZE_MAX = 4096L;

    /**
     * Instantiate a {@code UnixSystem} and load
     * the native library to access the underlying system information.
     */
    public UnixSystem() {
        try (Arena scope = Arena.ofConfined()) {
            MemorySegment capturedState = scope.allocate(capturedStateLayout);
            int groupnum = (int) getgroups.invokeExact(capturedState, 0, MemorySegment.NULL);
            if (groupnum == -1) {
                throw new RuntimeException("getgroups returns " + groupnum);
            }

            var gs = scope.allocate(C_INT, groupnum);
            groupnum = (int) getgroups.invokeExact(capturedState, groupnum, gs);
            if (groupnum == -1) {
                var errno = (int) errnoHandle.get(capturedState, 0L);
                var errMsg = (MemorySegment) strerror.invokeExact(errno);
                throw new RuntimeException("getgroups returns " + groupnum
                        + ". Reason: " + errMsg.reinterpret(Long.MAX_VALUE).getString(0));
            }

            groups = new long[groupnum];
            for (int i = 0; i < groupnum; i++) {
                groups[i] = gs.getAtIndex(C_INT, i);
            }

            var pwd = scope.allocate(passwd_layout);
            var result = scope.allocate(C_POINTER);
            var buffer = scope.allocate(GETPW_R_SIZE_MAX);
            var tmpUid = (int)getuid.invokeExact();
            // Do not call invokeExact because GETPW_R_SIZE_MAX is not
            // always size_t on the system.
            int out = (int) getpwuid_r.invoke(
                    tmpUid, pwd, buffer, GETPW_R_SIZE_MAX, result);
            if (out != 0 || result.get(ValueLayout.ADDRESS, 0).equals(MemorySegment.NULL)) {
                if (out != 0) {
                    var err = (MemorySegment) strerror.invokeExact(out);
                    getpwuid_r_error = err.reinterpret(Long.MAX_VALUE).getString(0);
                } else {
                    getpwuid_r_error = "the requested entry is not found";
                }
                uid = tmpUid;
                gid = (int)getgid.invokeExact();
                username = null;
            } else {
                uid = pwd.get(pw_uid_layout, pw_uid_offset);
                gid = pwd.get(pw_gid_layout, pw_gid_offset);
                username = pwd.get(pw_name_layout, pw_name_offset).getString(0);
            }
        } catch (Throwable t) {
            var error = new UnsatisfiedLinkError("FFM calls failed");
            error.initCause(t);
            throw error;
        }
    }

    /**
     * Get the username for the current Unix user.
     *
     * @return the username for the current Unix user.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Get the UID for the current Unix user.
     *
     * @return the UID for the current Unix user.
     */
    public long getUid() {
        return uid;
    }

    /**
     * Get the GID for the current Unix user.
     *
     * @return the GID for the current Unix user.
     */
    public long getGid() {
        return gid;
    }

    /**
     * Get the supplementary groups for the current Unix user.
     *
     * @return the supplementary groups for the current Unix user.
     */
    public long[] getGroups() {
        return groups == null ? null : groups.clone();
    }
}
