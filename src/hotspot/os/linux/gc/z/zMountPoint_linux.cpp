/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gcLogPrecious.hpp"
#include "gc/z/zArray.inline.hpp"
#include "gc/z/zErrno.hpp"
#include "gc/z/zMountPoint_linux.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/permitForbiddenFunctions.hpp"

#include <stdio.h>
#include <unistd.h>

// Mount information, see proc(5) for more details.
#define PROC_SELF_MOUNTINFO        "/proc/self/mountinfo"

ZMountPoint::ZMountPoint(const char* filesystem, const char** preferred_mountpoints) {
  if (AllocateHeapAt != nullptr) {
    // Use specified path
    _path = os::strdup(AllocateHeapAt, mtGC);
  } else {
    // Find suitable path
    _path = find_mountpoint(filesystem, preferred_mountpoints);
  }
}

ZMountPoint::~ZMountPoint() {
  os::free(_path);
  _path = nullptr;
}

char* ZMountPoint::get_mountpoint(const char* line, const char* filesystem) const {
  char* line_mountpoint = nullptr;
  char* line_filesystem = nullptr;

  // Parse line and return a newly allocated string containing the mount point if
  // the line contains a matching filesystem and the mount point is accessible by
  // the current user.
  // sscanf, using %m, will return malloced memory. Need raw ::free, not os::free.
  if (sscanf(line, "%*u %*u %*u:%*u %*s %ms %*[^-]- %ms", &line_mountpoint, &line_filesystem) != 2 ||
      strcmp(line_filesystem, filesystem) != 0 ||
      access(line_mountpoint, R_OK|W_OK|X_OK) != 0) {
    // Not a matching or accessible filesystem
    permit_forbidden_function::free(line_mountpoint);
    line_mountpoint = nullptr;
  }

  permit_forbidden_function::free(line_filesystem);

  return line_mountpoint;
}

void ZMountPoint::get_mountpoints(const char* filesystem, ZArray<char*>* mountpoints) const {
  FILE* fd = os::fopen(PROC_SELF_MOUNTINFO, "r");
  if (fd == nullptr) {
    ZErrno err;
    log_error_p(gc)("Failed to open %s: %s", PROC_SELF_MOUNTINFO, err.to_string());
    return;
  }

  char* line = nullptr;
  size_t length = 0;

  while (getline(&line, &length, fd) != -1) {
    char* const mountpoint = get_mountpoint(line, filesystem);
    if (mountpoint != nullptr) {
      mountpoints->append(mountpoint);
    }
  }

  // readline will return malloced memory. Need raw ::free, not os::free.
  permit_forbidden_function::free(line);
  fclose(fd);
}

void ZMountPoint::free_mountpoints(ZArray<char*>* mountpoints) const {
  ZArrayIterator<char*> iter(mountpoints);
  for (char* mountpoint; iter.next(&mountpoint);) {
    permit_forbidden_function::free(mountpoint); // *not* os::free
  }
  mountpoints->clear();
}

char* ZMountPoint::find_preferred_mountpoint(const char* filesystem,
                                              ZArray<char*>* mountpoints,
                                              const char** preferred_mountpoints) const {
  // Find preferred mount point
  ZArrayIterator<char*> iter1(mountpoints);
  for (char* mountpoint; iter1.next(&mountpoint);) {
    for (const char** preferred = preferred_mountpoints; *preferred != nullptr; preferred++) {
      if (!strcmp(mountpoint, *preferred)) {
        // Preferred mount point found
        return os::strdup(mountpoint, mtGC);
      }
    }
  }

  // Preferred mount point not found
  log_error_p(gc)("More than one %s filesystem found:", filesystem);
  ZArrayIterator<char*> iter2(mountpoints);
  for (char* mountpoint; iter2.next(&mountpoint);) {
    log_error_p(gc)("  %s", mountpoint);
  }

  return nullptr;
}

char* ZMountPoint::find_mountpoint(const char* filesystem, const char** preferred_mountpoints) const {
  char* path = nullptr;
  ZArray<char*> mountpoints;

  get_mountpoints(filesystem, &mountpoints);

  if (mountpoints.length() == 0) {
    // No mount point found
    log_error_p(gc)("Failed to find an accessible %s filesystem", filesystem);
  } else if (mountpoints.length() == 1) {
    // One mount point found
    path = os::strdup(mountpoints.at(0), mtGC);
  } else {
    // More than one mount point found
    path = find_preferred_mountpoint(filesystem, &mountpoints, preferred_mountpoints);
  }

  free_mountpoints(&mountpoints);

  return path;
}

const char* ZMountPoint::get() const {
  return _path;
}
