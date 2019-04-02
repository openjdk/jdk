/*
 * Copyright (c) 2017, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHSTRINGDEDUP_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHSTRINGDEDUP_HPP

#include "gc/shared/stringdedup/stringDedup.hpp"
#include "memory/iterator.hpp"

class ShenandoahStringDedup : public StringDedup {
public:
  // Initialize string deduplication.
  static void initialize();

  // Enqueue a string to worker's local string dedup queue
  static void enqueue_candidate(oop java_string);

  // Deduplicate a string, the call is lock-free
  static void deduplicate(oop java_string);

  static void parallel_oops_do(BoolObjectClosure* is_alive, OopClosure* cl, uint worker_id);
  static void oops_do_slow(OopClosure* cl);

  static inline bool is_candidate(oop obj);

  static void unlink_or_oops_do(BoolObjectClosure* is_alive,
                                OopClosure* keep_alive,
                                bool allow_resize_and_rehash);

};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSTRINGDEDUP_HPP
