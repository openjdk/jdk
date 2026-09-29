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

import jdk.internal.util.Architecture;
import jdk.internal.util.OperatingSystem;

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

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SYMBOL_LOOKUP = SymbolLookup.loaderLookup()
            .or(LINKER.defaultLookup());

    private static final ValueLayout.OfByte C_CHAR
            = (ValueLayout.OfByte) LINKER.canonicalLayouts().get("char");
    private static final ValueLayout.OfInt C_INT
            = (ValueLayout.OfInt) LINKER.canonicalLayouts().get("int");
    private static final ValueLayout.OfLong C_LONG
            = (ValueLayout.OfLong) LINKER.canonicalLayouts().get("long");
    private static final AddressLayout C_POINTER
            = ((AddressLayout) LINKER.canonicalLayouts().get("void*"))
            .withTargetLayout(MemoryLayout.sequenceLayout(java.lang.Long.MAX_VALUE, C_CHAR));
    private static final ValueLayout C_SIZE_T
            = (ValueLayout) LINKER.canonicalLayouts().get("size_t");

    private static final StructLayout CAPTURE_STATE_LAYOUT
            = Linker.Option.captureStateLayout();
    private static final VarHandle VH_errno = CAPTURE_STATE_LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("errno"));

    private static final MethodHandle MH_strerror
            = LINKER.downcallHandle(SYMBOL_LOOKUP.findOrThrow("strerror"),
                    FunctionDescriptor.of(C_POINTER, C_INT));

    private static final MethodHandle MH_getgroups
            = LINKER.downcallHandle(SYMBOL_LOOKUP.findOrThrow("getgroups"),
                    FunctionDescriptor.of(C_INT, C_INT, C_POINTER),
                    Linker.Option.captureCallState("errno"));
    private static final MethodHandle MH_getuid
            = LINKER.downcallHandle(SYMBOL_LOOKUP.findOrThrow("getuid"),
                    FunctionDescriptor.of(C_INT));

    // Some architectures require appropriate zero or sign extension to 64 bit.
    // Use long directly before https://bugs.openjdk.org/browse/JDK-8336664 is resolved.
    private static final boolean calling_convention_requires_int_as_long
            = Architecture.isPPC64() || Architecture.isPPC64LE() || Architecture.isS390();

    // getpwuid_r does not work on AIX, instead we use another similar function
    // extern int _posix_getpwuid_r(uid_t, struct passwd *, char *, int, struct passwd **)
    private static final MethodHandle MH_getpwuid_r
            = LINKER.downcallHandle(SYMBOL_LOOKUP.findOrThrow(
                            OperatingSystem.isAix() ? "_posix_getpwuid_r" : "getpwuid_r"),
                    FunctionDescriptor.of(C_INT,
                            calling_convention_requires_int_as_long ? C_LONG : C_INT,
                            C_POINTER, C_POINTER,
                            OperatingSystem.isAix() ? C_INT : C_SIZE_T,
                            C_POINTER));

    private static final GroupLayout ML_passwd = MemoryLayout.structLayout(
            C_POINTER.withName("pw_name"),
            C_POINTER.withName("pw_passwd"),
            C_INT.withName("pw_uid"),
            C_INT.withName("pw_gid"),
            // Different platforms have different fields in `struct passwd`.
            // While we don't need those fields here, the struct needs to be
            // big enough to avoid buffer overflow when `getpwuid_r` is called.
            MemoryLayout.paddingLayout(100));

    private static final VarHandle VH_pw_uid
            = ML_passwd.varHandle(groupElement("pw_uid"));
    private static final VarHandle VH_pw_gid
            = ML_passwd.varHandle(groupElement("pw_gid"));
    private static final VarHandle VH_pw_name
            = ML_passwd.varHandle(groupElement("pw_name"));

    // The buffer size for the getpwuid_r function:
    // 1. sysconf(_SC_GETPW_R_SIZE_MAX) on macOS is 4096 and 1024 on Linux,
    //    so we choose a bigger one.
    // 2. We do not call sysconf() here because even _SC_GETPW_R_SIZE_MAX
    //    could be different on different platforms.
    // 3. We choose int instead of long because the buffer_size argument
    //    might be `int` or `long` and converting from `long` to `int`
    //    requires an explicit cast.
    private static final int GETPW_R_SIZE_MAX = 4096;

    /**
     * Instantiate a {@code UnixSystem} and load
     * the native library to access the underlying system information.
     */
    public UnixSystem() {
        // The FFM code has only been tested on multiple platforms
        // (including macOS, Linux, AIX, etc) and might fail on other
        // *nix systems. Especially, the `passwd` struct could be defined
        // differently. I've checked several and an extra 100 chars at the
        // end seems enough.
        try (Arena scope = Arena.ofConfined()) {
            MemorySegment capturedState = scope.allocate(CAPTURE_STATE_LAYOUT);
            int groupnum = (int) MH_getgroups.invokeExact(capturedState, 0, MemorySegment.NULL);
            if (groupnum == -1) {
                throw new RuntimeException("getgroups returns " + groupnum);
            }

            var gs = scope.allocate(C_INT, groupnum);
            groupnum = (int) MH_getgroups.invokeExact(capturedState, groupnum, gs);
            if (groupnum == -1) {
                var errno = (int) VH_errno.get(capturedState, 0L);
                var errMsg = (MemorySegment) MH_strerror.invokeExact(errno);
                throw new RuntimeException("getgroups returns " + groupnum
                        + ". Reason: " + errMsg.reinterpret(Long.MAX_VALUE).getString(0));
            }

            groups = new long[groupnum];
            for (int i = 0; i < groupnum; i++) {
                groups[i] = Integer.toUnsignedLong(gs.getAtIndex(C_INT, i));
            }

            var pwd = scope.allocate(ML_passwd);
            var result = scope.allocate(C_POINTER);
            var buffer = scope.allocate(GETPW_R_SIZE_MAX);

            long tmpUid = Integer.toUnsignedLong((int) MH_getuid.invokeExact());

            // Do not call invokeExact because the type of buffer_size is not
            // always long in the underlying system.
            int out;
            if (calling_convention_requires_int_as_long) {
                out = (int) MH_getpwuid_r.invoke(
                        tmpUid, pwd, buffer, GETPW_R_SIZE_MAX, result);
            } else {
                out = (int) MH_getpwuid_r.invoke(
                        (int) tmpUid, pwd, buffer, GETPW_R_SIZE_MAX, result);
            }
            if (out != 0) {
                // If ERANGE (Result too large) is detected in a new platform,
                // consider adjusting GETPW_R_SIZE_MAX.
                var err = (MemorySegment) MH_strerror.invokeExact(out);
                throw new RuntimeException(err.reinterpret(Long.MAX_VALUE).getString(0));
            } else if (result.get(ValueLayout.ADDRESS, 0).equals(MemorySegment.NULL)) {
                throw new RuntimeException("the requested entry is not found");
            } else {
                // uid_t and gid_t were defined unsigned.
                uid = Integer.toUnsignedLong((int) VH_pw_uid.get(pwd, 0L));
                gid = Integer.toUnsignedLong((int) VH_pw_gid.get(pwd, 0L));
                username = ((MemorySegment) VH_pw_name.get(pwd, 0L)).getString(0);
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
