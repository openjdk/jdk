/*
 * Copyright (c) 2019, 2021, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHSTRINGDEDUP_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHSTRINGDEDUP_INLINE_HPP

#include "gc/shenandoah/shenandoahStringDedup.hpp"

#include "classfile/javaClasses.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "oops/markWord.hpp"

bool ShenandoahStringDedup::is_string_candidate(oop obj) {
  assert(Thread::current()->is_Worker_thread(),
        "Only from a GC worker thread");
  return java_lang_String::is_instance(obj) &&
         java_lang_String::value(obj) != nullptr;
}

bool ShenandoahStringDedup::dedup_requested(oop obj) {
  return java_lang_String::test_and_set_deduplication_requested(obj);
}

bool ShenandoahStringDedup::is_candidate(oop obj) {
  if (!is_string_candidate(obj)) {
    return false;
  }

  uint age = ShenandoahHeap::get_object_age(obj);
  return (age <= markWord::max_age) &&
         StringDedup::is_below_threshold_age(age) &&
         !dedup_requested(obj);
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSTRINGDEDUP_INLINE_HPP
