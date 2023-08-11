/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "oops/instanceKlassFlags.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

void InstanceKlassFlags::print_on(outputStream* st) const {
#define IK_FLAGS_PRINT(name, ignore)          \
  if (name()) st->print(#name " ");
  IK_FLAGS_DO(IK_FLAGS_PRINT)
  IK_STATUS_DO(IK_FLAGS_PRINT)
#undef IK_FLAGS_PRINT
}

#if INCLUDE_CDS
void InstanceKlassFlags::set_shared_class_loader_type(s2 loader_type) {
  switch (loader_type) {
  case ClassLoader::BOOT_LOADER:
    _flags |= _misc_is_shared_boot_class;
    break;
  case ClassLoader::PLATFORM_LOADER:
    _flags |= _misc_is_shared_platform_class;
    break;
  case ClassLoader::APP_LOADER:
    _flags |= _misc_is_shared_app_class;
    break;
  default:
    ShouldNotReachHere();
    break;
  }
}

void InstanceKlassFlags::assign_class_loader_type(const ClassLoaderData* cld) {
  if (cld->is_boot_class_loader_data()) {
    set_shared_class_loader_type(ClassLoader::BOOT_LOADER);
  }
  else if (cld->is_platform_class_loader_data()) {
    set_shared_class_loader_type(ClassLoader::PLATFORM_LOADER);
  }
  else if (cld->is_system_class_loader_data()) {
    set_shared_class_loader_type(ClassLoader::APP_LOADER);
  }
}
#endif // INCLUDE_CDS

#ifdef ASSERT
void InstanceKlassFlags::assert_is_safe(bool set) {
  // Setting a flag is safe if it's set once or at a safepoint. RedefineClasses can set or
  // reset flags at a safepoint.
  assert(!set || SafepointSynchronize::is_at_safepoint(), "set once or at safepoint");
}
#endif // ASSERT
