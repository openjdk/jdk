/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "gc/shared/oopStorage.inline.hpp"
#include "gc/shared/weakProcessor.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/jniHandles.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_JFR
#include "jfr/jfr.hpp"
#endif

void WeakProcessor::weak_oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive) {
  JNIHandles::weak_oops_do(is_alive, keep_alive);
  JvmtiExport::weak_oops_do(is_alive, keep_alive);
  SystemDictionary::vm_weak_oop_storage()->weak_oops_do(is_alive, keep_alive);
  JFR_ONLY(Jfr::weak_oops_do(is_alive, keep_alive);)
}

void WeakProcessor::oops_do(OopClosure* closure) {
  AlwaysTrueClosure always_true;
  weak_oops_do(&always_true, closure);
}
