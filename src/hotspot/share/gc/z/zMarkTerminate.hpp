/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZMARKTERMINATE_HPP
#define SHARE_GC_Z_ZMARKTERMINATE_HPP

#include "gc/z/zLock.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"

class ZMarkStripeSet;

class ZMarkTerminate {
private:
  uint           _nworkers;
  Atomic<uint>   _nworking;
  Atomic<uint>   _nawakening;
  Atomic<bool>   _resurrected;
  ZConditionLock _lock;

  void maybe_reduce_stripes(ZMarkStripeSet* stripes, size_t used_nstripes);

public:
  ZMarkTerminate();

  void reset(uint nworkers);
  void leave();

  bool saturated() const;

  void wake_up();
  bool try_terminate(ZMarkStripeSet* stripes, size_t used_nstripes);
  void set_resurrected(bool value);
  bool resurrected() const;
};

#endif // SHARE_GC_Z_ZMARKTERMINATE_HPP
