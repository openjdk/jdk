/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifdef COMPILER2
#ifndef SHARE_RUNTIME_HOTCODEGROUPER_HPP
#define SHARE_RUNTIME_HOTCODEGROUPER_HPP

#include "runtime/nonJavaThread.hpp"
#include "utilities/linkedlist.hpp"
#include "utilities/pair.hpp"
#include "runtime/hotCodeSampler.hpp"

class ThreadSampler;

class HotCodeGrouper : public NonJavaThread {
 private:
  static bool _is_initialized;

  static size_t _new_c2_nmethods_count;
  static size_t _total_c2_nmethods_count;

  void do_grouping(ThreadSampler& sampler);

 public:

  void run() override;

  virtual const char* name()      const override { return "Hot Code Grouper Thread"; }
  virtual const char* type_name() const override { return "HotCodeGrouper"; }

  static void initialize();
  static void unregister_nmethod(nmethod* nm);
  static void register_nmethod(nmethod* nm);
};

#endif // SHARE_RUNTIME_HOTCODEGROUPER_HPP
#endif // COMPILER2
