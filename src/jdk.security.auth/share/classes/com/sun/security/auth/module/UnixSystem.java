/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.ffi.generated.jaas_unix.passwd;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static jdk.internal.ffi.generated.jaas_unix.jaas_unix_h.*;

/**
 * This class implementation retrieves and makes available Unix
 * UID/GID/groups information for the current user.
 *
 * @since 1.4
 */
public class UnixSystem {

    /** The current username. */
    protected String username;

    /** The current user ID. */
    protected long uid;

    /** The current group ID. */
    protected long gid;

    /** The current list of groups. */
    protected long[] groups;

    /**
     * Instantiate a {@code UnixSystem} and load
     * the native library to access the underlying system information.
     */
    public UnixSystem() {
        try (Arena scope = Arena.ofConfined()) {
            int groupnum = getgroups(0, MemorySegment.NULL);
            if (groupnum == -1) {
                throw new RuntimeException("getgroups returns " + groupnum);
            }

            var gs = scope.allocate(gid_t, groupnum);
            groupnum = getgroups(groupnum, gs);
            if (groupnum == -1) {
                throw new RuntimeException("getgroups returns " + groupnum);
            }

            groups = new long[groupnum];
            for (int i = 0; i < groupnum; i++) {
                groups[i] = gs.getAtIndex(gid_t, i);
            }

            var resbuf = passwd.allocate(scope);
            var pwd = scope.allocate(C_POINTER);
            var pwd_buf = scope.allocate(1024);
            int out = getpwuid_r(getuid(), resbuf, pwd_buf, pwd_buf.byteSize(), pwd);
            if (out != 0) {
                throw new RuntimeException("getpwuid_r returns " + out);
            }
            if (pwd.get(ValueLayout.ADDRESS, 0).equals(MemorySegment.NULL)) {
                throw new RuntimeException("getpwuid_r returns NULL result");
            }
            uid = passwd.pw_uid(resbuf);
            gid = passwd.pw_gid(resbuf);
            username = passwd.pw_name(resbuf).getString(0);
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
