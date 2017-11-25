/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "gc/shared/accessBarrierSupport.inline.hpp"
#include "oops/access.hpp"

DecoratorSet AccessBarrierSupport::resolve_unknown_oop_ref_strength(DecoratorSet decorators, oop base, ptrdiff_t offset) {
  DecoratorSet ds = decorators & ~ON_UNKNOWN_OOP_REF;
  if (!java_lang_ref_Reference::is_referent_field(base, offset)) {
    ds |= ON_STRONG_OOP_REF;
  } else if (java_lang_ref_Reference::is_phantom(base)) {
    ds |= ON_PHANTOM_OOP_REF;
  } else {
    ds |= ON_WEAK_OOP_REF;
  }
  return ds;
}
