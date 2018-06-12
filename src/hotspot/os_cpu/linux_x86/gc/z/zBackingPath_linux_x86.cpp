/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/z/zArray.inline.hpp"
#include "gc/z/zBackingPath_linux_x86.hpp"
#include "gc/z/zErrno.hpp"
#include "logging/log.hpp"

#include <stdio.h>
#include <unistd.h>

// Mount information, see proc(5) for more details.
#define PROC_SELF_MOUNTINFO        "/proc/self/mountinfo"

ZBackingPath::ZBackingPath(const char* filesystem, const char* preferred_path) {
  if (ZPath != NULL) {
    // Use specified path
    _path = strdup(ZPath);
  } else {
    // Find suitable path
    _path = find_mountpoint(filesystem, preferred_path);
  }
}

ZBackingPath::~ZBackingPath() {
  free(_path);
  _path = NULL;
}

char* ZBackingPath::get_mountpoint(const char* line, const char* filesystem) const {
  char* line_mountpoint = NULL;
  char* line_filesystem = NULL;

  // Parse line and return a newly allocated string containing the mountpoint if
  // the line contains a matching filesystem and the mountpoint is accessible by
  // the current user.
  if (sscanf(line, "%*u %*u %*u:%*u %*s %ms %*[^-]- %ms", &line_mountpoint, &line_filesystem) != 2 ||
      strcmp(line_filesystem, filesystem) != 0 ||
      access(line_mountpoint, R_OK|W_OK|X_OK) != 0) {
    // Not a matching or accessible filesystem
    free(line_mountpoint);
    line_mountpoint = NULL;
  }

  free(line_filesystem);

  return line_mountpoint;
}

void ZBackingPath::get_mountpoints(ZArray<char*>* mountpoints, const char* filesystem) const {
  FILE* fd = fopen(PROC_SELF_MOUNTINFO, "r");
  if (fd == NULL) {
    ZErrno err;
    log_error(gc, init)("Failed to open %s: %s", PROC_SELF_MOUNTINFO, err.to_string());
    return;
  }

  char* line = NULL;
  size_t length = 0;

  while (getline(&line, &length, fd) != -1) {
    char* const mountpoint = get_mountpoint(line, filesystem);
    if (mountpoint != NULL) {
      mountpoints->add(mountpoint);
    }
  }

  free(line);
  fclose(fd);
}

void ZBackingPath::free_mountpoints(ZArray<char*>* mountpoints) const {
  ZArrayIterator<char*> iter(mountpoints);
  for (char* mountpoint; iter.next(&mountpoint);) {
    free(mountpoint);
  }
  mountpoints->clear();
}

char* ZBackingPath::find_mountpoint(const char* filesystem, const char* preferred_mountpoint) const {
  char* path = NULL;
  ZArray<char*> mountpoints;

  get_mountpoints(&mountpoints, filesystem);

  if (mountpoints.size() == 0) {
    // No filesystem found
    log_error(gc, init)("Failed to find an accessible %s filesystem", filesystem);
  } else if (mountpoints.size() == 1) {
    // One filesystem found
    path = strdup(mountpoints.at(0));
  } else if (mountpoints.size() > 1) {
    // More than one filesystem found
    ZArrayIterator<char*> iter(&mountpoints);
    for (char* mountpoint; iter.next(&mountpoint);) {
      if (!strcmp(mountpoint, preferred_mountpoint)) {
        // Preferred mount point found
        path = strdup(mountpoint);
        break;
      }
    }

    if (path == NULL) {
      // Preferred mount point not found
      log_error(gc, init)("More than one %s filesystem found:", filesystem);
      ZArrayIterator<char*> iter2(&mountpoints);
      for (char* mountpoint; iter2.next(&mountpoint);) {
        log_error(gc, init)("  %s", mountpoint);
      }
    }
  }

  free_mountpoints(&mountpoints);

  return path;
}

const char* ZBackingPath::get() const {
  return _path;
}
