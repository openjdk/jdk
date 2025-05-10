/*
 * Copyright (c) 2025, Red Hat Inc. All rights reserved.
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
 *
 */

#include "classfile/systemDictionaryShared.hpp"
#include "memory/iterator.hpp"
#include "memory/metaspace.hpp"
#include "memory/resourceArea.hpp"
#include "oops/klass.hpp"
#include "unittest.hpp"
#include "testutils.hpp"

// Test a repeated cycle of sample task works.
TEST_VM(SystemDictionaryShared, iterate_klasses) {

  if (!CDSConfig::is_using_archive()) {
    tty->print_cr("Skipping, CDS inactive.");
    return;
  }

  struct Closure : public ConstKlassClosure {
    int _c;
    Closure() : _c(0) {}
    void do_klass(const Klass* k) override {
      ResourceMark rm;
      LOG_HERE("%s", k->external_name());
      EXPECT_TRUE(k->is_shared()) << k->external_name();
      EXPECT_TRUE(Metaspace::is_in_shared_metaspace(k)) << k->external_name();
      _c++;
    }
  } cl_static, cl_dynamic;

  SystemDictionaryShared::iterate_klasses_in_shared_archive(&cl_static, true);
  ASSERT_GT(cl_static._c, 0);

  SystemDictionaryShared::iterate_klasses_in_shared_archive(&cl_static, false);
}
