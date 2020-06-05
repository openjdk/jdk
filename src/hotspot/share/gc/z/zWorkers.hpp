/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZWORKERS_HPP
#define SHARE_GC_Z_ZWORKERS_HPP

#include "gc/shared/workgroup.hpp"
#include "memory/allocation.hpp"

class ThreadClosure;
class ZTask;

class ZWorkers {
private:
  bool     _boost;
  WorkGang _workers;

  void run(ZTask* task, uint nworkers);

public:
  ZWorkers();

  uint nparallel() const;
  uint nparallel_no_boost() const;
  uint nconcurrent() const;
  uint nconcurrent_no_boost() const;
  uint nworkers() const;

  void set_boost(bool boost);

  void run_parallel(ZTask* task);
  void run_concurrent(ZTask* task);

  void threads_do(ThreadClosure* tc) const;
};

#endif // SHARE_GC_Z_ZWORKERS_HPP
