/*
 * Copyright (c) 2019, Red Hat, Inc. All rights reserved.
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

#include "precompiled.hpp"

#include "gc/shenandoah/shenandoahConcurrentRoots.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"

bool ShenandoahConcurrentRoots::can_do_concurrent_roots() {
  // Don't support traversal GC at this moment
  return !ShenandoahHeap::heap()->is_traversal_mode();
}

bool ShenandoahConcurrentRoots::should_do_concurrent_roots() {
  return can_do_concurrent_roots() &&
         !ShenandoahHeap::heap()->is_stw_gc_in_progress();
}

bool ShenandoahConcurrentRoots::can_do_concurrent_class_unloading() {
#if defined(X86) && !defined(SOLARIS)
  return ShenandoahCodeRootsStyle == 2 &&
         ClassUnloading &&
         strcmp(ShenandoahGCMode, "traversal") != 0;
#else
  return false;
#endif
}

bool ShenandoahConcurrentRoots::should_do_concurrent_class_unloading() {
  return can_do_concurrent_class_unloading() &&
         !ShenandoahHeap::heap()->is_stw_gc_in_progress();
}
