/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#include "services/attachListenerPosix.hpp"
static_assert(sizeof(off_t) == 8, "Expected Large File Support in this file");

int PosixAttachListener::pd_accept(struct sockaddr *addr, socklen_t *len) {
  int s;
  RESTARTABLE(::accept(listener(), addr, len), s);
  return s;
}

bool PosixAttachListener::pd_credential_check(int s) {
  struct ucred cred_info;
  socklen_t optlen = sizeof(cred_info);
  if (::getsockopt(s, SOL_SOCKET, SO_PEERCRED, (void *)&cred_info, &optlen) == -1) {
    log_debug(attach)("Failed to get socket option SO_PEERCRED");
    return false;
  }
  if (!os::Posix::matches_effective_uid_and_gid_or_root(cred_info.uid, cred_info.gid)) {
    log_debug(attach)("euid/egid check failed (%d/%d vs %d/%d)", cred_info.uid, cred_info.gid,
                      geteuid(), getegid());
    return false;
  }
  return true;
}
