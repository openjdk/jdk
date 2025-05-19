/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotClassLocation.hpp"
#include "cds/cds_globals.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/dynamicArchive.hpp"
#include "cds/filemap.hpp"
#include "cds/heapShared.hpp"
#include "classfile/classFileParser.hpp"
#include "classfile/classLoader.inline.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoadInfo.hpp"
#include "classfile/klassFactory.hpp"
#include "classfile/modules.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/arguments.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/stringUtils.hpp"

void ClassLoaderExt::append_boot_classpath(ClassPathEntry* new_entry) {
  if (CDSConfig::is_using_archive()) {
    warning("Sharing is only supported for boot loader classes because bootstrap classpath has been appended");
    FileMapInfo::current_info()->set_has_platform_or_app_classes(false);
    if (DynamicArchive::is_mapped()) {
      FileMapInfo::dynamic_info()->set_has_platform_or_app_classes(false);
    }
  }
  ClassLoader::add_to_boot_append_entries(new_entry);
}

int ClassLoaderExt::compare_module_names(const char** p1, const char** p2) {
  return strcmp(*p1, *p2);
}

void ClassLoaderExt::record_result_for_builtin_loader(s2 classpath_index, InstanceKlass* result, bool redefined) {
  assert(CDSConfig::is_dumping_archive(), "sanity");

  oop loader = result->class_loader();
  s2 classloader_type;
  if (SystemDictionary::is_system_class_loader(loader)) {
    classloader_type = ClassLoader::APP_LOADER;
    AOTClassLocationConfig::dumptime_set_has_app_classes();
  } else if (SystemDictionary::is_platform_class_loader(loader)) {
    classloader_type = ClassLoader::PLATFORM_LOADER;
    AOTClassLocationConfig::dumptime_set_has_platform_classes();
  } else {
    precond(loader == nullptr);
    classloader_type = ClassLoader::BOOT_LOADER;
  }

  if (CDSConfig::is_dumping_preimage_static_archive() || CDSConfig::is_dumping_dynamic_archive()) {
    if (!AOTClassLocationConfig::dumptime()->is_valid_classpath_index(classpath_index, result)) {
      classpath_index = -1;
    }
  }

  AOTClassLocationConfig::dumptime_update_max_used_index(classpath_index);
  result->set_shared_classpath_index(classpath_index);
  result->set_shared_class_loader_type(classloader_type);

#if INCLUDE_CDS_JAVA_HEAP
  if (CDSConfig::is_dumping_heap() && AllowArchivingWithJavaAgent && classloader_type == ClassLoader::BOOT_LOADER &&
      classpath_index < 0 && redefined) {
    // When dumping the heap (which happens only during static dump), classes for the built-in
    // loaders are always loaded from known locations (jimage, classpath or modulepath),
    // so classpath_index should always be >= 0.
    // The only exception is when a java agent is used during dump time (for testing
    // purposes only). If a class is transformed by the agent, the AOTClassLocation of
    // this class may point to an unknown location. This may break heap object archiving,
    // which requires all the boot classes to be from known locations. This is an
    // uncommon scenario (even in test cases). Let's simply disable heap object archiving.
    ResourceMark rm;
    log_warning(cds)("CDS heap objects cannot be written because class %s maybe modified by ClassFileLoadHook.",
                     result->external_name());
    CDSConfig::disable_heap_dumping();
  }
#endif // INCLUDE_CDS_JAVA_HEAP
}
