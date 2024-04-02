/*
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_KLASS_INLINE_HPP
#define SHARE_OOPS_KLASS_INLINE_HPP

#include "oops/klass.hpp"

#include "classfile/classLoaderData.inline.hpp"
#include "oops/klassVtable.hpp"
#include "oops/markWord.hpp"

// This loads and keeps the klass's loader alive.
inline oop Klass::klass_holder() const {
  return class_loader_data()->holder();
}

inline bool Klass::is_non_strong_hidden() const {
  return access_flags().is_hidden_class() &&
         class_loader_data()->has_class_mirror_holder();
}

// Iff the class loader (or mirror for non-strong hidden classes) is alive the
// Klass is considered alive. This is safe to call before the CLD is marked as
// unloading, and hence during concurrent class unloading.
// This returns false if the Klass is unloaded, or about to be unloaded because the holder of
// the CLD is no longer strongly reachable.
// The return value of this function may change from true to false after a safepoint. So the caller
// of this function must ensure that a safepoint doesn't happen while interpreting the return value.
inline bool Klass::is_loader_alive() const {
  return class_loader_data()->is_alive();
}

inline oop Klass::java_mirror() const {
  return _java_mirror.resolve();
}

inline oop Klass::java_mirror_no_keepalive() const {
  return _java_mirror.peek();
}

inline klassVtable Klass::vtable() const {
  return klassVtable(const_cast<Klass*>(this), start_of_vtable(), vtable_length() / vtableEntry::size());
}

inline oop Klass::class_loader() const {
  return class_loader_data()->class_loader();
}

inline vtableEntry* Klass::start_of_vtable() const {
  return (vtableEntry*) ((address)this + in_bytes(vtable_start_offset()));
}

inline ByteSize Klass::vtable_start_offset() {
  return in_ByteSize(InstanceKlass::header_size() * wordSize);
}

#endif // SHARE_OOPS_KLASS_INLINE_HPP
