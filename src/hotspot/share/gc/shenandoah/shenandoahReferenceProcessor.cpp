/*
 * Copyright (c) 2020, Red Hat, Inc. and/or its affiliates.
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
#include "classfile/javaClasses.hpp"
#include "gc/shenandoah/shenandoahOopClosures.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"

bool ShenandoahReferenceProcessor::discover_reference(oop obj, ReferenceType type) {
  if (type == REF_FINAL) {
    Thread* thread = Thread::current();
    ShenandoahMarkRefsSuperClosure* cl = ShenandoahThreadLocalData::mark_closure(thread);
    bool strong = cl->is_strong();
    cl->set_strong(false);
    if (UseCompressedOops) {
      cl->do_oop(reinterpret_cast<narrowOop*>(java_lang_ref_Reference::referent_addr_raw(obj)));
    } else {
      cl->do_oop(reinterpret_cast<oop*>(java_lang_ref_Reference::referent_addr_raw(obj)));
    }
    cl->set_strong(strong);
  }
  return false;
}
