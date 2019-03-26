/*
* Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classFileParser.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/klassFactory.hpp"
#include "memory/filemap.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include "prims/jvmtiRedefineClasses.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_JFR
#include "jfr/support/jfrKlassExtension.hpp"
#endif


// called during initial loading of a shared class
InstanceKlass* KlassFactory::check_shared_class_file_load_hook(
                                          InstanceKlass* ik,
                                          Symbol* class_name,
                                          Handle class_loader,
                                          Handle protection_domain,
                                          const ClassFileStream *cfs,
                                          TRAPS) {
#if INCLUDE_CDS && INCLUDE_JVMTI
  assert(ik != NULL, "sanity");
  assert(ik->is_shared(), "expecting a shared class");
  if (JvmtiExport::should_post_class_file_load_hook()) {
    assert(THREAD->is_Java_thread(), "must be JavaThread");

    // Post the CFLH
    JvmtiCachedClassFileData* cached_class_file = NULL;
    if (cfs == NULL) {
      cfs = FileMapInfo::open_stream_for_jvmti(ik, CHECK_NULL);
    }
    unsigned char* ptr = (unsigned char*)cfs->buffer();
    unsigned char* end_ptr = ptr + cfs->length();
    unsigned char* old_ptr = ptr;
    JvmtiExport::post_class_file_load_hook(class_name,
                                           class_loader,
                                           protection_domain,
                                           &ptr,
                                           &end_ptr,
                                           &cached_class_file);
    if (old_ptr != ptr) {
      // JVMTI agent has modified class file data.
      // Set new class file stream using JVMTI agent modified class file data.
      ClassLoaderData* loader_data =
        ClassLoaderData::class_loader_data(class_loader());
      int path_index = ik->shared_classpath_index();
      ClassFileStream* stream = new ClassFileStream(ptr,
                                                    end_ptr - ptr,
                                                    cfs->source(),
                                                    ClassFileStream::verify);
      ClassFileParser parser(stream,
                             class_name,
                             loader_data,
                             protection_domain,
                             NULL,
                             NULL,
                             ClassFileParser::BROADCAST, // publicity level
                             CHECK_NULL);
      InstanceKlass* new_ik = parser.create_instance_klass(true /* changed_by_loadhook */,
                                                           CHECK_NULL);
      if (cached_class_file != NULL) {
        new_ik->set_cached_class_file(cached_class_file);
      }

      if (class_loader.is_null()) {
        ResourceMark rm;
        ClassLoader::add_package(class_name->as_C_string(), path_index, THREAD);
      }

      return new_ik;
    }
  }
#endif

  return NULL;
}


static ClassFileStream* check_class_file_load_hook(ClassFileStream* stream,
                                                   Symbol* name,
                                                   ClassLoaderData* loader_data,
                                                   Handle protection_domain,
                                                   JvmtiCachedClassFileData** cached_class_file,
                                                   TRAPS) {

  assert(stream != NULL, "invariant");

  if (JvmtiExport::should_post_class_file_load_hook()) {
    assert(THREAD->is_Java_thread(), "must be a JavaThread");
    const JavaThread* jt = (JavaThread*)THREAD;

    Handle class_loader(THREAD, loader_data->class_loader());

    // Get the cached class file bytes (if any) from the class that
    // is being redefined or retransformed. We use jvmti_thread_state()
    // instead of JvmtiThreadState::state_for(jt) so we don't allocate
    // a JvmtiThreadState any earlier than necessary. This will help
    // avoid the bug described by 7126851.

    JvmtiThreadState* state = jt->jvmti_thread_state();

    if (state != NULL) {
      Klass* k = state->get_class_being_redefined();

      if (k != NULL) {
        InstanceKlass* class_being_redefined = InstanceKlass::cast(k);
        *cached_class_file = class_being_redefined->get_cached_class_file();
      }
    }

    unsigned char* ptr = const_cast<unsigned char*>(stream->buffer());
    unsigned char* end_ptr = ptr + stream->length();

    JvmtiExport::post_class_file_load_hook(name,
                                           class_loader,
                                           protection_domain,
                                           &ptr,
                                           &end_ptr,
                                           cached_class_file);

    if (ptr != stream->buffer()) {
      // JVMTI agent has modified class file data.
      // Set new class file stream using JVMTI agent modified class file data.
      stream = new ClassFileStream(ptr,
                                   end_ptr - ptr,
                                   stream->source(),
                                   stream->need_verify());
    }
  }

  return stream;
}


InstanceKlass* KlassFactory::create_from_stream(ClassFileStream* stream,
                                                Symbol* name,
                                                ClassLoaderData* loader_data,
                                                Handle protection_domain,
                                                const InstanceKlass* unsafe_anonymous_host,
                                                GrowableArray<Handle>* cp_patches,
                                                TRAPS) {
  assert(stream != NULL, "invariant");
  assert(loader_data != NULL, "invariant");
  assert(THREAD->is_Java_thread(), "must be a JavaThread");

  ResourceMark rm;
  HandleMark hm;

  JvmtiCachedClassFileData* cached_class_file = NULL;

  ClassFileStream* old_stream = stream;

  // increment counter
  THREAD->statistical_info().incr_define_class_count();

  // Skip this processing for VM anonymous classes
  if (unsafe_anonymous_host == NULL) {
    stream = check_class_file_load_hook(stream,
                                        name,
                                        loader_data,
                                        protection_domain,
                                        &cached_class_file,
                                        CHECK_NULL);
  }

  ClassFileParser parser(stream,
                         name,
                         loader_data,
                         protection_domain,
                         unsafe_anonymous_host,
                         cp_patches,
                         ClassFileParser::BROADCAST, // publicity level
                         CHECK_NULL);

  InstanceKlass* result = parser.create_instance_klass(old_stream != stream, CHECK_NULL);
  assert(result == parser.create_instance_klass(old_stream != stream, THREAD), "invariant");

  if (result == NULL) {
    return NULL;
  }

  if (cached_class_file != NULL) {
    // JVMTI: we have an InstanceKlass now, tell it about the cached bytes
    result->set_cached_class_file(cached_class_file);
  }

  JFR_ONLY(ON_KLASS_CREATION(result, parser, THREAD);)

#if INCLUDE_CDS
  if (DumpSharedSpaces) {
    ClassLoader::record_result(result, stream, THREAD);
  }
#endif // INCLUDE_CDS

  return result;
}
