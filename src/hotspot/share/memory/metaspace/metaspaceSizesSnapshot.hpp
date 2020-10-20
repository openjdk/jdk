/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Twitter, Inc.
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

#ifndef SHARE_MEMORY_METASPACE_METASPACESIZESSNAPSHOT_HPP
#define SHARE_MEMORY_METASPACE_METASPACESIZESSNAPSHOT_HPP

#include "utilities/globalDefinitions.hpp"

namespace metaspace {

// Todo: clean up after jep387, see JDK-8251392
class MetaspaceSizesSnapshot {
public:
  MetaspaceSizesSnapshot();

  size_t used() const { return _used; }
  size_t committed() const { return _committed; }
  size_t non_class_used() const { return _non_class_used; }
  size_t non_class_committed() const { return _non_class_committed; }
  size_t class_used() const { return _class_used; }
  size_t class_committed() const { return _class_committed; }

private:
  const size_t _used;
  const size_t _committed;
  const size_t _non_class_used;
  const size_t _non_class_committed;
  const size_t _class_used;
  const size_t _class_committed;
};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_METASPACESIZESSNAPSHOT_HPP
