/*
* Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/filemap.hpp"
#include "classfile/classFileParser.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoadInfo.hpp"
#include "classfile/klassFactory.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.hpp"
#include "oops/oopsHierarchy.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include "prims/jvmtiRedefineClasses.hpp"
#include "runtime/arguments.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "utilities/macros.hpp"
#include "utilities/utf8.hpp"
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
  assert(ik != nullptr, "sanity");
  assert(ik->is_shared(), "expecting a shared class");
  if (JvmtiExport::should_post_class_file_load_hook()) {
    ResourceMark rm(THREAD);
    // Post the CFLH
    JvmtiCachedClassFileData* cached_class_file = nullptr;
    if (cfs == nullptr) {
      cfs = FileMapInfo::open_stream_for_jvmti(ik, class_loader, CHECK_NULL);
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
      s2 path_index = ik->shared_classpath_index();
      ClassFileStream* stream = new ClassFileStream(ptr,
                                                    end_ptr - ptr,
                                                    cfs->source(),
                                                    ClassFileStream::verify);
      ClassLoadInfo cl_info(protection_domain);
      ClassFileParser parser(stream,
                             class_name,
                             loader_data,
                             &cl_info,
                             ClassFileParser::BROADCAST, // publicity level
                             JVM_CLASSFILE_MAJOR_VERSION,
                             CHECK_NULL);
      const ClassInstanceInfo* cl_inst_info = cl_info.class_hidden_info_ptr();
      InstanceKlass* new_ik = parser.create_instance_klass(true, // changed_by_loadhook
                                                           *cl_inst_info,  // dynamic_nest_host and classData
                                                           CHECK_NULL);

      if (cached_class_file != nullptr) {
        new_ik->set_cached_class_file(cached_class_file);
      }

      if (class_loader.is_null()) {
        new_ik->set_classpath_index(path_index);
      }

      return new_ik;
    }
  }
#endif

  return nullptr;
}


static ClassFileStream* check_class_file_load_hook(ClassFileStream* stream,
                                                   Symbol* name,
                                                   ClassLoaderData* loader_data,
                                                   Handle protection_domain,
                                                   JvmtiCachedClassFileData** cached_class_file,
                                                   TRAPS) {

  assert(stream != nullptr, "invariant");

  if (JvmtiExport::should_post_class_file_load_hook()) {
    const JavaThread* jt = THREAD;

    Handle class_loader(THREAD, loader_data->class_loader());

    // Get the cached class file bytes (if any) from the class that
    // is being retransformed. If class file load hook provides
    // modified class data during class loading or redefinition,
    // new cached class file buffer should be allocated.
    // We use jvmti_thread_state()
    // instead of JvmtiThreadState::state_for(jt) so we don't allocate
    // a JvmtiThreadState any earlier than necessary. This will help
    // avoid the bug described by 7126851.

    JvmtiThreadState* state = jt->jvmti_thread_state();

    if (state != nullptr) {
      Klass* k = state->get_class_being_redefined();
      if (k != nullptr && state->get_class_load_kind() == jvmti_class_load_kind_retransform) {
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

int get_stream_major_version(ClassFileStream* stream) {
  // Magic value
  const u4 magic = stream->get_u4_fast();

  // Version numbers
  int minor_version = stream->get_u2_fast();
  int major_version = stream->get_u2_fast();
  stream->set_current(stream->buffer());
  return major_version;
}

bool is_old_stream(int major_version) {
  return (major_version < JAVA_7_VERSION);
}

InstanceKlass* KlassFactory::regenerate_from_stream(ClassFileStream* stream, Symbol* name, ClassLoaderData* loader_data, const ClassLoadInfo& cl_info, TRAPS) {
  HandleMark hm(THREAD);
  assert(Arguments::is_dumping_archive(), "must be dumping");

  int major_version = get_stream_major_version(stream);
  stream->set_current(stream->buffer());

  typeArrayOop bytecode = oopFactory::new_byteArray(stream->length(), CHECK_NULL);

  // Copy Classfile from stream to a java byte array
  ArrayAccess<>::arraycopy_from_native(reinterpret_cast<const jbyte*>(stream->buffer()),
        bytecode,
        typeArrayOopDesc::element_offset<jbyte>(0),
        (size_t)stream->length());

  typeArrayHandle bufhandle(THREAD, bytecode);
  JavaValue result(T_ARRAY);
  JavaCallArguments args;
  args.push_oop(bufhandle); // Push class byte array as argument
  args.push_int(false); // Set Preverifier Verbose to argument to false in patch() method
  Klass* k = SystemDictionary::resolve_or_fail(vmSymbols::jdk_internal_vm_Preverifier(), false, CHECK_NULL);

  // Call Preverifier.patch()
  JavaCalls::call_static(&result,
        k,
        vmSymbols::preverifier_patch(),
        vmSymbols::byte_array_bool_byte_array_signature(),
        &args,
        THREAD);
  if (HAS_PENDING_EXCEPTION) {
    Handle ex(THREAD, PENDING_EXCEPTION);
    CLEAR_PENDING_EXCEPTION;
    stringStream fn1;
    static int unknown_count = 0;

    return nullptr;
  }

  oop result_oop = result.get_oop();
  assert(result_oop != NULL, "should be non-null");
  assert(result_oop->is_typeArray(), "Result must be a byte array");
  typeArrayHandle result_array(THREAD, typeArrayOop(result_oop));
  int length = result_array->length();
  assert(length >= 0, "class_bytes_length must not be negative: %d", length);

  u1* class_bytes = NEW_RESOURCE_ARRAY_RETURN_NULL(u1, length);
  if (class_bytes == NULL) {
    THROW_0(vmSymbols::java_lang_OutOfMemoryError());
  }

  // Copy output back to stream
  ArrayAccess<>::arraycopy_to_native(result_array(),
        typeArrayOopDesc::element_offset<jbyte>(0),
        reinterpret_cast<jbyte*>(class_bytes), length);

  ClassFileStream* newStream = new ClassFileStream(class_bytes, length, stream->source(), stream->need_verify());
  newStream->set_current(newStream->buffer());
  stream = newStream;

  ClassFileParser new_parser(stream,
                             name,
                             loader_data,
                             &cl_info,
                             ClassFileParser::BROADCAST, // publicity level
                             major_version,
                             CHECK_NULL);

  const ClassInstanceInfo* cl_inst_info = cl_info.class_hidden_info_ptr();
  return new_parser.create_instance_klass(true, *cl_inst_info , CHECK_NULL);
}

InstanceKlass* KlassFactory::create_from_stream(ClassFileStream* stream,
                                                Symbol* name,
                                                ClassLoaderData* loader_data,
                                                const ClassLoadInfo& cl_info,
                                                TRAPS) {

  assert(stream != nullptr, "invariant");
  assert(loader_data != nullptr, "invariant");

  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);

  JvmtiCachedClassFileData* cached_class_file = nullptr;

  ClassFileStream* old_stream = stream;

  // increment counter
  THREAD->statistical_info().incr_define_class_count();

  // Skip this processing for VM hidden classes
  if (!cl_info.is_hidden()) {
    stream = check_class_file_load_hook(stream,
                                        name,
                                        loader_data,
                                        cl_info.protection_domain(),
                                        &cached_class_file,
                                        CHECK_NULL);
  }

  int major_version = get_stream_major_version(stream);
  InstanceKlass* result;

  ClassFileParser parser(stream,
                         name,
                         loader_data,
                         &cl_info,
                         ClassFileParser::BROADCAST, // publicity level
                         major_version,
                         CHECK_NULL);

  const ClassInstanceInfo* cl_inst_info = cl_info.class_hidden_info_ptr();
  result = parser.create_instance_klass(old_stream != stream, *cl_inst_info, CHECK_NULL);
  assert(result != nullptr, "result cannot be null with no pending exception");

  if (Arguments::is_dumping_archive() && major_version < 50) {
    //Save the old stream to be used for regeneration at dump time
    int old_length = stream->length();
    char* old_stream = (char*)os::malloc(stream->length(), mtClass);
    memcpy(old_stream, (char*) stream->buffer(), stream->length());

    result->set_old_stream(old_stream, old_length);
  }

  assert(result != NULL, "result cannot be null with no pending exception");
  if (cached_class_file != NULL) {
    // JVMTI: we have an InstanceKlass now, tell it about the cached bytes
    result->set_cached_class_file(cached_class_file);
  }

  JFR_ONLY(ON_KLASS_CREATION(result, parser, THREAD);)

#if INCLUDE_CDS
  if (Arguments::is_dumping_archive()) {
    ClassLoader::record_result(THREAD, result, stream, old_stream != stream);
  }
#endif // INCLUDE_CDS

  return result;
}
