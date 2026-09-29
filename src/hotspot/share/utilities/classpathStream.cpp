/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/classpathStream.hpp"

ClasspathStream::ClasspathStream(const char* classpath) {
  _cp = classpath;
  skip_blank_paths();
}

char ClasspathStream::separator() {
  // All supported platforms have a single character path separator.
  return os::path_separator()[0];
}

void ClasspathStream::skip_blank_paths() {
  while (*_cp == separator()) {
    _cp++;
  }
}

const char* ClasspathStream::get_next() {
  assert(has_next(), "call this only after you checked has_next()");
  assert(*_cp != separator(), "ensured by constructor and get_next()");

  const char* end = _cp + 1;
  while (*end != separator() && *end != '\0') {
    end++;
  }

  int path_len = end - _cp;
  char* path = NEW_RESOURCE_ARRAY(char, path_len + 1);
  strncpy(path, _cp, path_len);
  path[path_len] = '\0';

  assert(strlen(path) > 0, "must be");

  _cp = end;
  skip_blank_paths();

  return path;
}
