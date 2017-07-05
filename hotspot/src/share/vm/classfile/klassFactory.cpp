/*
* Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/sharedClassUtil.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include "prims/jvmtiRedefineClasses.hpp"
#include "trace/traceMacros.hpp"

// called during initial loading of a shared class
instanceKlassHandle KlassFactory::check_shared_class_file_load_hook(
                                          instanceKlassHandle ik,
                                          Symbol* class_name,
                                          Handle class_loader,
                                          Handle protection_domain, TRAPS) {
#if INCLUDE_CDS && INCLUDE_JVMTI
  assert(ik.not_null(), "sanity");
  assert(ik()->is_shared(), "expecting a shared class");

  if (JvmtiExport::should_post_class_file_load_hook()) {
    assert(THREAD->is_Java_thread(), "must be JavaThread");

    // Post the CFLH
    JvmtiCachedClassFileData* cached_class_file = NULL;
    JvmtiCachedClassFileData* archived_class_data = ik->get_archived_class_data();
    assert(archived_class_data != NULL, "shared class has no archived class data");
    unsigned char* ptr =
        VM_RedefineClasses::get_cached_class_file_bytes(archived_class_data);
    unsigned char* end_ptr =
        ptr + VM_RedefineClasses::get_cached_class_file_len(archived_class_data);
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
      SharedClassPathEntry* ent =
        (SharedClassPathEntry*)FileMapInfo::shared_classpath(path_index);
      ClassFileStream* stream = new ClassFileStream(ptr,
                                                    end_ptr - ptr,
                                                    ent == NULL ? NULL : ent->_name,
                                                    ClassFileStream::verify);
      ClassFileParser parser(stream,
                             class_name,
                             loader_data,
                             protection_domain,
                             NULL,
                             NULL,
                             ClassFileParser::BROADCAST, // publicity level
                             CHECK_NULL);
      instanceKlassHandle new_ik = parser.create_instance_klass(true /* changed_by_loadhook */,
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
      KlassHandle* h_class_being_redefined =
        state->get_class_being_redefined();

      if (h_class_being_redefined != NULL) {
        instanceKlassHandle ikh_class_being_redefined =
          instanceKlassHandle(THREAD, (*h_class_being_redefined)());

        *cached_class_file = ikh_class_being_redefined->get_cached_class_file();
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


instanceKlassHandle KlassFactory::create_from_stream(ClassFileStream* stream,
                                                     Symbol* name,
                                                     ClassLoaderData* loader_data,
                                                     Handle protection_domain,
                                                     const InstanceKlass* host_klass,
                                                     GrowableArray<Handle>* cp_patches,
                                                     TRAPS) {
  assert(stream != NULL, "invariant");
  assert(loader_data != NULL, "invariant");
  assert(THREAD->is_Java_thread(), "must be a JavaThread");

  ResourceMark rm;
  HandleMark hm;

  JvmtiCachedClassFileData* cached_class_file = NULL;

  ClassFileStream* old_stream = stream;

  // Skip this processing for VM anonymous classes
  if (host_klass == NULL) {
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
                         host_klass,
                         cp_patches,
                         ClassFileParser::BROADCAST, // publicity level
                         CHECK_NULL);

  instanceKlassHandle result = parser.create_instance_klass(old_stream != stream, CHECK_NULL);
  assert(result == parser.create_instance_klass(old_stream != stream, THREAD), "invariant");

  if (result.is_null()) {
    return NULL;
  }

  if (cached_class_file != NULL) {
    // JVMTI: we have an InstanceKlass now, tell it about the cached bytes
    result->set_cached_class_file(cached_class_file);
  }

  if (InstanceKlass::should_store_fingerprint()) {
    result->store_fingerprint(!result->is_anonymous() ? stream->compute_fingerprint() : 0);
  }

  TRACE_KLASS_CREATION(result, parser, THREAD);

#if INCLUDE_CDS && INCLUDE_JVMTI
  if (DumpSharedSpaces) {
    assert(cached_class_file == NULL, "Sanity");
    // Archive the class stream data into the optional data section
    JvmtiCachedClassFileData *p;
    int len;
    const unsigned char *bytes;
    // event based tracing might set cached_class_file
    if ((bytes = result->get_cached_class_file_bytes()) != NULL) {
      len = result->get_cached_class_file_len();
    } else {
      len = stream->length();
      bytes = stream->buffer();
    }
    p = (JvmtiCachedClassFileData*)MetaspaceShared::optional_data_space_alloc(
                    offset_of(JvmtiCachedClassFileData, data) + len);
    p->length = len;
    memcpy(p->data, bytes, len);
    result->set_archived_class_data(p);
  }
#endif

  return result;
}
