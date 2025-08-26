
/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/filemap.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/javaThreadStatus.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeBlob.hpp"
#include "code/codeCache.hpp"
#include "code/compiledIC.hpp"
#include "code/compressedStream.hpp"
#include "code/location.hpp"
#include "code/nmethod.hpp"
#include "code/pcDesc.hpp"
#include "code/stubs.hpp"
#include "code/vmreg.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/stringdedup/stringDedupThread.hpp"
#include "gc/shared/vmStructs_gc.hpp"
#include "interpreter/bytecodes.hpp"
#include "interpreter/interpreter.hpp"
#include "jfr/recorder/service/jfrRecorderThread.hpp"
#include "logging/logAsyncWriter.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/heap.hpp"
#include "memory/padded.hpp"
#include "memory/referenceType.hpp"
#include "memory/universe.hpp"
#include "memory/virtualspace.hpp"
#include "oops/array.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/arrayOop.hpp"
#include "oops/constMethod.hpp"
#include "oops/constantPool.hpp"
#include "oops/cpCache.hpp"
#include "oops/fieldInfo.hpp"
#include "oops/instanceClassLoaderKlass.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/instanceOop.hpp"
#include "oops/instanceStackChunkKlass.hpp"
#include "oops/klass.hpp"
#include "oops/klassVtable.hpp"
#include "oops/markWord.hpp"
#include "oops/method.hpp"
#include "oops/methodCounters.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.hpp"
#include "oops/resolvedFieldEntry.hpp"
#include "oops/resolvedIndyEntry.hpp"
#include "oops/resolvedMethodEntry.hpp"
#include "oops/symbol.hpp"
#include "oops/typeArrayKlass.hpp"
#include "oops/typeArrayOop.hpp"
#include "prims/jvmtiAgentThread.hpp"
#include "runtime/arguments.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/globals.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/monitorDeflationThread.hpp"
#include "runtime/notificationThread.hpp"
#include "runtime/os.hpp"
#include "runtime/osThread.hpp"
#include "runtime/perfMemory.hpp"
#include "runtime/serviceThread.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/vframeArray.hpp"
#include "runtime/vmStructs.hpp"
#include "runtime/vm_version.hpp"
#include "services/attachListener.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/vmError.hpp"
#ifdef COMPILER2
#include "opto/optoreg.hpp"
#endif // COMPILER2

#include CPU_HEADER(vmStructs)
#include OS_HEADER(vmStructs)

// Note: the cross-product of (c1, c2, product, nonproduct, ...),
// (nonstatic, static), and (unchecked, checked) has not been taken.
// Only the macros currently needed have been defined.

// A field whose type is not checked is given a null string as the
// type name, indicating an "opaque" type to the serviceability agent.

// NOTE: there is an interdependency between this file and
// HotSpotTypeDataBase.java, which parses the type strings.

#ifndef REG_COUNT
  #define REG_COUNT 0
#endif

#if INCLUDE_JVMTI
  #define JVMTI_STRUCTS(static_field) \
    static_field(JvmtiExport,                     _can_access_local_variables,                  bool)                                  \
    static_field(JvmtiExport,                     _can_hotswap_or_post_breakpoint,              bool)                                  \
    static_field(JvmtiExport,                     _can_post_on_exceptions,                      bool)                                  \
    static_field(JvmtiExport,                     _can_walk_any_space,                          bool)
#else
  #define JVMTI_STRUCTS(static_field)
#endif // INCLUDE_JVMTI

//--------------------------------------------------------------------------------
// VM_STRUCTS
//
// This list enumerates all of the fields the serviceability agent
// needs to know about. Be sure to see also the type table below this one.
// NOTE that there are platform-specific additions to this table in
// vmStructs_<os>_<cpu>.hpp.

#define VM_STRUCTS(nonstatic_field,                                                                                                  \
                   static_field,                                                                                                     \
                   volatile_static_field,                                                                                            \
                   unchecked_nonstatic_field,                                                                                        \
                   volatile_nonstatic_field,                                                                                         \
                   nonproduct_nonstatic_field)                                                                                       \
                                                                                                                                     \
  /*************/                                                                                                                    \
  /* GC fields */                                                                                                                    \
  /*************/                                                                                                                    \
                                                                                                                                     \
  VM_STRUCTS_GC(nonstatic_field,                                                                                                     \
                volatile_static_field,                                                                                               \
                volatile_nonstatic_field,                                                                                            \
                static_field,                                                                                                        \
                unchecked_nonstatic_field)                                                                                           \
                                                                                                                                     \
  /******************************************************************/                                                               \
  /* OopDesc and Klass hierarchies (NOTE: MethodData* incomplete)   */                                                               \
  /******************************************************************/                                                               \
                                                                                                                                     \
  volatile_nonstatic_field(oopDesc,            _mark,                                         markWord)                              \
  volatile_nonstatic_field(oopDesc,            _metadata._klass,                              Klass*)                                \
  volatile_nonstatic_field(oopDesc,            _metadata._compressed_klass,                   narrowKlass)                           \
  static_field(BarrierSet,                     _barrier_set,                                  BarrierSet*)                           \
  nonstatic_field(ArrayKlass,                  _dimension,                                    int)                                   \
  volatile_nonstatic_field(ArrayKlass,         _higher_dimension,                             ObjArrayKlass*)                        \
  volatile_nonstatic_field(ArrayKlass,         _lower_dimension,                              ArrayKlass*)                           \
  nonstatic_field(ConstantPool,                _tags,                                         Array<u1>*)                            \
  nonstatic_field(ConstantPool,                _cache,                                        ConstantPoolCache*)                    \
  nonstatic_field(ConstantPool,                _pool_holder,                                  InstanceKlass*)                        \
  nonstatic_field(ConstantPool,                _operands,                                     Array<u2>*)                            \
  nonstatic_field(ConstantPool,                _resolved_klasses,                             Array<Klass*>*)                        \
  nonstatic_field(ConstantPool,                _length,                                       int)                                   \
  nonstatic_field(ConstantPool,                _minor_version,                                u2)                                    \
  nonstatic_field(ConstantPool,                _major_version,                                u2)                                    \
  nonstatic_field(ConstantPool,                _generic_signature_index,                      u2)                                    \
  nonstatic_field(ConstantPool,                _source_file_name_index,                       u2)                                    \
  nonstatic_field(ConstantPoolCache,           _resolved_references,                          OopHandle)                             \
  nonstatic_field(ConstantPoolCache,           _reference_map,                                Array<u2>*)                            \
  nonstatic_field(ConstantPoolCache,           _constant_pool,                                ConstantPool*)                         \
  nonstatic_field(ConstantPoolCache,           _resolved_field_entries,                       Array<ResolvedFieldEntry>*)            \
  nonstatic_field(ResolvedFieldEntry,          _cpool_index,                                  u2)                                    \
  nonstatic_field(ConstantPoolCache,           _resolved_method_entries,                      Array<ResolvedMethodEntry>*)           \
  nonstatic_field(ResolvedMethodEntry,         _cpool_index,                                  u2)                                    \
  nonstatic_field(ConstantPoolCache,           _resolved_indy_entries,                        Array<ResolvedIndyEntry>*)             \
  nonstatic_field(ResolvedIndyEntry,           _cpool_index,                                  u2)                                    \
  volatile_nonstatic_field(InstanceKlass,      _array_klasses,                                ObjArrayKlass*)                        \
  nonstatic_field(InstanceKlass,               _methods,                                      Array<Method*>*)                       \
  nonstatic_field(InstanceKlass,               _default_methods,                              Array<Method*>*)                       \
  nonstatic_field(InstanceKlass,               _local_interfaces,                             Array<InstanceKlass*>*)                \
  nonstatic_field(InstanceKlass,               _transitive_interfaces,                        Array<InstanceKlass*>*)                \
  nonstatic_field(InstanceKlass,               _fieldinfo_stream,                             Array<u1>*)                            \
  nonstatic_field(InstanceKlass,               _constants,                                    ConstantPool*)                         \
  nonstatic_field(InstanceKlass,               _source_debug_extension,                       const char*)                           \
  nonstatic_field(InstanceKlass,               _inner_classes,                                Array<jushort>*)                       \
  nonstatic_field(InstanceKlass,               _nest_members,                                 Array<jushort>*)                       \
  nonstatic_field(InstanceKlass,               _nonstatic_field_size,                         int)                                   \
  nonstatic_field(InstanceKlass,               _static_field_size,                            int)                                   \
  nonstatic_field(InstanceKlass,               _static_oop_field_count,                       u2)                                    \
  nonstatic_field(InstanceKlass,               _nonstatic_oop_map_size,                       int)                                   \
  volatile_nonstatic_field(InstanceKlass,      _init_state,                                   InstanceKlass::ClassState)             \
  volatile_nonstatic_field(InstanceKlass,      _init_thread,                                  JavaThread*)                           \
  nonstatic_field(InstanceKlass,               _itable_len,                                   int)                                   \
  nonstatic_field(InstanceKlass,               _nest_host_index,                              u2)                                    \
  nonstatic_field(InstanceKlass,               _reference_type,                               u1)                                    \
  volatile_nonstatic_field(InstanceKlass,      _oop_map_cache,                                OopMapCache*)                          \
  nonstatic_field(InstanceKlass,               _jni_ids,                                      JNIid*)                                \
  nonstatic_field(InstanceKlass,               _osr_nmethods_head,                            nmethod*)                              \
  JVMTI_ONLY(nonstatic_field(InstanceKlass,    _breakpoints,                                  BreakpointInfo*))                      \
  volatile_nonstatic_field(InstanceKlass,      _methods_jmethod_ids,                          jmethodID*)                            \
  volatile_nonstatic_field(InstanceKlass,      _idnum_allocated_count,                        u2)                                    \
  nonstatic_field(InstanceKlass,               _annotations,                                  Annotations*)                          \
  nonstatic_field(InstanceKlass,               _method_ordering,                              Array<int>*)                           \
  nonstatic_field(InstanceKlass,               _default_vtable_indices,                       Array<int>*)                           \
  nonstatic_field(Klass,                       _super_check_offset,                           juint)                                 \
  nonstatic_field(Klass,                       _secondary_super_cache,                        Klass*)                                \
  nonstatic_field(Klass,                       _secondary_supers,                             Array<Klass*>*)                        \
  nonstatic_field(Klass,                       _primary_supers[0],                            Klass*)                                \
  nonstatic_field(Klass,                       _java_mirror,                                  OopHandle)                             \
  nonstatic_field(Klass,                       _super,                                        Klass*)                                \
  volatile_nonstatic_field(Klass,              _subklass,                                     Klass*)                                \
  nonstatic_field(Klass,                       _layout_helper,                                jint)                                  \
  nonstatic_field(Klass,                       _name,                                         Symbol*)                               \
  nonstatic_field(Klass,                       _access_flags,                                 AccessFlags)                           \
  volatile_nonstatic_field(Klass,              _next_sibling,                                 Klass*)                                \
  nonstatic_field(Klass,                       _next_link,                                    Klass*)                                \
  nonstatic_field(Klass,                       _vtable_len,                                   int)                                   \
  nonstatic_field(Klass,                       _class_loader_data,                            ClassLoaderData*)                      \
  nonstatic_field(vtableEntry,                 _method,                                       Method*)                               \
  nonstatic_field(MethodData,                  _size,                                         int)                                   \
  nonstatic_field(MethodData,                  _method,                                       Method*)                               \
  nonstatic_field(MethodData,                  _data_size,                                    int)                                   \
  nonstatic_field(MethodData,                  _data[0],                                      intptr_t)                              \
  nonstatic_field(MethodData,                  _parameters_type_data_di,                      int)                                   \
  nonstatic_field(MethodData,                  _compiler_counters._nof_decompiles,            uint)                                  \
  nonstatic_field(MethodData,                  _compiler_counters._nof_overflow_recompiles,   uint)                                  \
  nonstatic_field(MethodData,                  _compiler_counters._nof_overflow_traps,        uint)                                  \
  nonstatic_field(MethodData,                  _compiler_counters._trap_hist._array[0],       u1)                                    \
  nonstatic_field(MethodData,                  _eflags,                                       intx)                                  \
  nonstatic_field(MethodData,                  _arg_local,                                    intx)                                  \
  nonstatic_field(MethodData,                  _arg_stack,                                    intx)                                  \
  nonstatic_field(MethodData,                  _arg_returned,                                 intx)                                  \
  nonstatic_field(MethodData,                  _tenure_traps,                                 uint)                                  \
  nonstatic_field(MethodData,                  _invoke_mask,                                  int)                                   \
  nonstatic_field(MethodData,                  _backedge_mask,                                int)                                   \
  nonstatic_field(DataLayout,                  _header._struct._tag,                          u1)                                    \
  nonstatic_field(DataLayout,                  _header._struct._flags,                        u1)                                    \
  nonstatic_field(DataLayout,                  _header._struct._bci,                          u2)                                    \
  nonstatic_field(DataLayout,                  _header._struct._traps,                        u4)                                    \
  nonstatic_field(DataLayout,                  _cells[0],                                     intptr_t)                              \
  nonstatic_field(MethodCounters,              _invoke_mask,                                  int)                                   \
  nonstatic_field(MethodCounters,              _backedge_mask,                                int)                                   \
  COMPILER2_OR_JVMCI_PRESENT(nonstatic_field(MethodCounters, _interpreter_throwout_count,     u2))                                   \
  JVMTI_ONLY(nonstatic_field(MethodCounters,   _number_of_breakpoints,                        u2))                                   \
  nonstatic_field(MethodCounters,              _invocation_counter,                           InvocationCounter)                     \
  nonstatic_field(MethodCounters,              _backedge_counter,                             InvocationCounter)                     \
  nonstatic_field(Method,                      _constMethod,                                  ConstMethod*)                          \
  nonstatic_field(Method,                      _method_data,                                  MethodData*)                           \
  nonstatic_field(Method,                      _method_counters,                              MethodCounters*)                       \
  nonstatic_field(Method,                      _access_flags,                                 AccessFlags)                           \
  nonstatic_field(Method,                      _vtable_index,                                 int)                                   \
  nonstatic_field(Method,                      _intrinsic_id,                                 u2)                                    \
  volatile_nonstatic_field(Method,             _code,                                         nmethod*)                              \
  nonstatic_field(Method,                      _i2i_entry,                                    address)                               \
  volatile_nonstatic_field(Method,             _from_compiled_entry,                          address)                               \
  volatile_nonstatic_field(Method,             _from_interpreted_entry,                       address)                               \
  volatile_nonstatic_field(ConstMethod,        _fingerprint,                                  uint64_t)                              \
  nonstatic_field(ConstMethod,                 _constants,                                    ConstantPool*)                         \
  nonstatic_field(ConstMethod,                 _stackmap_data,                                Array<u1>*)                            \
  nonstatic_field(ConstMethod,                 _constMethod_size,                             int)                                   \
  nonstatic_field(ConstMethod,                 _flags._flags,                                 u4)                                    \
  nonstatic_field(ConstMethod,                 _code_size,                                    u2)                                    \
  nonstatic_field(ConstMethod,                 _name_index,                                   u2)                                    \
  nonstatic_field(ConstMethod,                 _signature_index,                              u2)                                    \
  nonstatic_field(ConstMethod,                 _method_idnum,                                 u2)                                    \
  nonstatic_field(ConstMethod,                 _max_stack,                                    u2)                                    \
  nonstatic_field(ConstMethod,                 _max_locals,                                   u2)                                    \
  nonstatic_field(ConstMethod,                 _size_of_parameters,                           u2)                                    \
  nonstatic_field(ConstMethod,                 _num_stack_arg_slots,                          u2)                                    \
  nonstatic_field(ObjArrayKlass,               _element_klass,                                Klass*)                                \
  nonstatic_field(ObjArrayKlass,               _bottom_klass,                                 Klass*)                                \
  volatile_nonstatic_field(Symbol,             _hash_and_refcount,                            unsigned int)                          \
  nonstatic_field(Symbol,                      _length,                                       u2)                                    \
  unchecked_nonstatic_field(Symbol,            _body,                                         sizeof(u1)) /* NOTE: no type */        \
  nonstatic_field(Symbol,                      _body[0],                                      u1)                                    \
  nonstatic_field(TypeArrayKlass,              _max_length,                                   jint)                                  \
  nonstatic_field(OopHandle,                   _obj,                                          oop*)                                  \
  nonstatic_field(Annotations,                 _class_annotations,                            Array<u1>*)                            \
  nonstatic_field(Annotations,                 _fields_annotations,                           Array<Array<u1>*>*)                    \
  nonstatic_field(Annotations,                 _class_type_annotations,                       Array<u1>*)                            \
  nonstatic_field(Annotations,                 _fields_type_annotations,                      Array<Array<u1>*>*)                    \
                                                                                                                                     \
  /*****************************/                                                                                                    \
  /* Method related structures */                                                                                                    \
  /*****************************/                                                                                                    \
                                                                                                                                     \
  nonstatic_field(CheckedExceptionElement,     class_cp_index,                                u2)                                    \
  nonstatic_field(LocalVariableTableElement,   start_bci,                                     u2)                                    \
  nonstatic_field(LocalVariableTableElement,   length,                                        u2)                                    \
  nonstatic_field(LocalVariableTableElement,   name_cp_index,                                 u2)                                    \
  nonstatic_field(LocalVariableTableElement,   descriptor_cp_index,                           u2)                                    \
  nonstatic_field(LocalVariableTableElement,   signature_cp_index,                            u2)                                    \
  nonstatic_field(LocalVariableTableElement,   slot,                                          u2)                                    \
  nonstatic_field(ExceptionTableElement,       start_pc,                                      u2)                                    \
  nonstatic_field(ExceptionTableElement,       end_pc,                                        u2)                                    \
  nonstatic_field(ExceptionTableElement,       handler_pc,                                    u2)                                    \
  nonstatic_field(ExceptionTableElement,       catch_type_index,                              u2)                                    \
  JVMTI_ONLY(nonstatic_field(BreakpointInfo,   _orig_bytecode,                                Bytecodes::Code))                      \
  JVMTI_ONLY(nonstatic_field(BreakpointInfo,   _bci,                                          int))                                  \
  JVMTI_ONLY(nonstatic_field(BreakpointInfo,   _name_index,                                   u2))                                   \
  JVMTI_ONLY(nonstatic_field(BreakpointInfo,   _signature_index,                              u2))                                   \
  JVMTI_ONLY(nonstatic_field(BreakpointInfo,   _next,                                         BreakpointInfo*))                      \
  /***********/                                                                                                                      \
  /* JNI IDs */                                                                                                                      \
  /***********/                                                                                                                      \
                                                                                                                                     \
  nonstatic_field(JNIid,                       _holder,                                       Klass*)                                \
  nonstatic_field(JNIid,                       _next,                                         JNIid*)                                \
  nonstatic_field(JNIid,                       _offset,                                       int)                                   \
                                                                                                                                     \
  /************/                                                                                                                     \
  /* Universe */                                                                                                                     \
  /************/                                                                                                                     \
     static_field(Universe,                    _collectedHeap,                                CollectedHeap*)                        \
  /******************/                                                                                                               \
  /* CompressedOops */                                                                                                               \
  /******************/                                                                                                               \
                                                                                                                                     \
     static_field(CompressedOops,              _base,                                         address)                               \
     static_field(CompressedOops,              _shift,                                        int)                                   \
     static_field(CompressedOops,              _use_implicit_null_checks,                     bool)                                  \
                                                                                                                                     \
  /***************************/                                                                                                      \
  /* CompressedKlassPointers */                                                                                                      \
  /***************************/                                                                                                      \
                                                                                                                                     \
     static_field(CompressedKlassPointers,     _base,                                         address)                               \
     static_field(CompressedKlassPointers,     _shift,                                        int)                                   \
                                                                                                                                     \
  /**********/                                                                                                                       \
  /* Memory */                                                                                                                       \
  /**********/                                                                                                                       \
                                                                                                                                     \
     static_field(MetaspaceObj,                _shared_metaspace_base,                        void*)                                 \
     static_field(MetaspaceObj,                _shared_metaspace_top,                         void*)                                 \
  nonstatic_field(ThreadLocalAllocBuffer,      _start,                                        HeapWord*)                             \
  nonstatic_field(ThreadLocalAllocBuffer,      _top,                                          HeapWord*)                             \
  nonstatic_field(ThreadLocalAllocBuffer,      _end,                                          HeapWord*)                             \
  nonstatic_field(ThreadLocalAllocBuffer,      _pf_top,                                       HeapWord*)                             \
  nonstatic_field(ThreadLocalAllocBuffer,      _desired_size,                                 size_t)                                \
  nonstatic_field(ThreadLocalAllocBuffer,      _refill_waste_limit,                           size_t)                                \
     static_field(ThreadLocalAllocBuffer,      _reserve_for_allocation_prefetch,              int)                                   \
     static_field(ThreadLocalAllocBuffer,      _target_refills,                               unsigned)                              \
  nonstatic_field(ThreadLocalAllocBuffer,      _number_of_refills,                            unsigned)                              \
  nonstatic_field(ThreadLocalAllocBuffer,      _refill_waste,                                 unsigned)                              \
  nonstatic_field(ThreadLocalAllocBuffer,      _gc_waste,                                     unsigned)                              \
  nonstatic_field(ThreadLocalAllocBuffer,      _slow_allocations,                             unsigned)                              \
  nonstatic_field(VirtualSpace,                _low_boundary,                                 char*)                                 \
  nonstatic_field(VirtualSpace,                _high_boundary,                                char*)                                 \
  nonstatic_field(VirtualSpace,                _low,                                          char*)                                 \
  nonstatic_field(VirtualSpace,                _high,                                         char*)                                 \
  nonstatic_field(VirtualSpace,                _lower_high,                                   char*)                                 \
  nonstatic_field(VirtualSpace,                _middle_high,                                  char*)                                 \
  nonstatic_field(VirtualSpace,                _upper_high,                                   char*)                                 \
                                                                                                                                     \
  /************************/                                                                                                         \
  /* PerfMemory - jvmstat */                                                                                                         \
  /************************/                                                                                                         \
                                                                                                                                     \
  nonstatic_field(PerfDataPrologue,            magic,                                         jint)                                  \
  nonstatic_field(PerfDataPrologue,            byte_order,                                    jbyte)                                 \
  nonstatic_field(PerfDataPrologue,            major_version,                                 jbyte)                                 \
  nonstatic_field(PerfDataPrologue,            minor_version,                                 jbyte)                                 \
  nonstatic_field(PerfDataPrologue,            accessible,                                    jbyte)                                 \
  nonstatic_field(PerfDataPrologue,            used,                                          jint)                                  \
  nonstatic_field(PerfDataPrologue,            overflow,                                      jint)                                  \
  nonstatic_field(PerfDataPrologue,            mod_time_stamp,                                jlong)                                 \
  nonstatic_field(PerfDataPrologue,            entry_offset,                                  jint)                                  \
  nonstatic_field(PerfDataPrologue,            num_entries,                                   jint)                                  \
                                                                                                                                     \
  nonstatic_field(PerfDataEntry,               entry_length,                                  jint)                                  \
  nonstatic_field(PerfDataEntry,               name_offset,                                   jint)                                  \
  nonstatic_field(PerfDataEntry,               vector_length,                                 jint)                                  \
  nonstatic_field(PerfDataEntry,               data_type,                                     jbyte)                                 \
  nonstatic_field(PerfDataEntry,               flags,                                         jbyte)                                 \
  nonstatic_field(PerfDataEntry,               data_units,                                    jbyte)                                 \
  nonstatic_field(PerfDataEntry,               data_variability,                              jbyte)                                 \
  nonstatic_field(PerfDataEntry,               data_offset,                                   jint)                                  \
                                                                                                                                     \
     static_field(PerfMemory,                  _start,                                        char*)                                 \
     static_field(PerfMemory,                  _end,                                          char*)                                 \
     static_field(PerfMemory,                  _top,                                          char*)                                 \
     static_field(PerfMemory,                  _capacity,                                     size_t)                                \
     static_field(PerfMemory,                  _prologue,                                     PerfDataPrologue*)                     \
     volatile_static_field(PerfMemory,         _initialized,                                  int)                                   \
                                                                                                                                     \
  /********************/                                                                                                             \
  /* VM Classes       */                                                                                                             \
  /********************/                                                                                                             \
                                                                                                                                     \
     static_field(vmClasses,                   VM_CLASS_AT(Object_klass),                        InstanceKlass*)                     \
     static_field(vmClasses,                   VM_CLASS_AT(String_klass),                        InstanceKlass*)                     \
     static_field(vmClasses,                   VM_CLASS_AT(Class_klass),                         InstanceKlass*)                     \
     static_field(vmClasses,                   VM_CLASS_AT(ClassLoader_klass),                   InstanceKlass*)                     \
     static_field(vmClasses,                   VM_CLASS_AT(System_klass),                        InstanceKlass*)                     \
     static_field(vmClasses,                   VM_CLASS_AT(Thread_klass),                        InstanceKlass*)                     \
     static_field(vmClasses,                   VM_CLASS_AT(Thread_FieldHolder_klass),            InstanceKlass*)                     \
     static_field(vmClasses,                   VM_CLASS_AT(ThreadGroup_klass),                   InstanceKlass*)                     \
     static_field(vmClasses,                   VM_CLASS_AT(MethodHandle_klass),                  InstanceKlass*)                     \
                                                                                                                                     \
  /*************/                                                                                                                    \
  /* vmSymbols */                                                                                                                    \
  /*************/                                                                                                                    \
                                                                                                                                     \
     static_field(Symbol,                      _vm_symbols[0],                                Symbol*)                               \
                                                                                                                                     \
  /*******************/                                                                                                              \
  /* ClassLoaderData */                                                                                                              \
  /*******************/                                                                                                              \
  nonstatic_field(ClassLoaderData,             _class_loader,                                 OopHandle)                             \
  nonstatic_field(ClassLoaderData,             _next,                                         ClassLoaderData*)                      \
  volatile_nonstatic_field(ClassLoaderData,    _klasses,                                      Klass*)                                \
  nonstatic_field(ClassLoaderData,             _has_class_mirror_holder,                      bool)                                  \
                                                                                                                                     \
  volatile_static_field(ClassLoaderDataGraph, _head,                                          ClassLoaderData*)                      \
                                                                                                                                     \
  /**********/                                                                                                                       \
  /* Arrays */                                                                                                                       \
  /**********/                                                                                                                       \
                                                                                                                                     \
  nonstatic_field(Array<Klass*>,               _length,                                       int)                                   \
  nonstatic_field(Array<Klass*>,               _data[0],                                      Klass*)                                \
  nonstatic_field(Array<ResolvedFieldEntry>,   _length,                                       int)                                   \
  nonstatic_field(Array<ResolvedFieldEntry>,   _data[0],                                      ResolvedFieldEntry)                    \
  nonstatic_field(Array<ResolvedMethodEntry>,  _length,                                       int)                                   \
  nonstatic_field(Array<ResolvedMethodEntry>,  _data[0],                                      ResolvedMethodEntry)                   \
  nonstatic_field(Array<ResolvedIndyEntry>,    _length,                                       int)                                   \
  nonstatic_field(Array<ResolvedIndyEntry>,    _data[0],                                      ResolvedIndyEntry)                     \
                                                                                                                                     \
  /*******************/                                                                                                              \
  /* GrowableArrays  */                                                                                                              \
  /*******************/                                                                                                              \
                                                                                                                                     \
  nonstatic_field(GrowableArrayBase,           _len,                                          int)                                   \
  nonstatic_field(GrowableArrayBase,           _capacity,                                     int)                                   \
  nonstatic_field(GrowableArray<int>,          _data,                                         int*)                                  \
                                                                                                                                     \
  /********************************/                                                                                                 \
  /* CodeCache (NOTE: incomplete) */                                                                                                 \
  /********************************/                                                                                                 \
                                                                                                                                     \
     static_field(CodeCache,                   _heaps,                                        GrowableArray<CodeHeap*>*)             \
     static_field(CodeCache,                   _low_bound,                                    address)                               \
     static_field(CodeCache,                   _high_bound,                                   address)                               \
                                                                                                                                     \
  /*******************************/                                                                                                  \
  /* CodeHeap (NOTE: incomplete) */                                                                                                  \
  /*******************************/                                                                                                  \
                                                                                                                                     \
  nonstatic_field(CodeHeap,                    _memory,                                       VirtualSpace)                          \
  nonstatic_field(CodeHeap,                    _segmap,                                       VirtualSpace)                          \
  nonstatic_field(CodeHeap,                    _log2_segment_size,                            int)                                   \
  nonstatic_field(HeapBlock,                   _header,                                       HeapBlock::Header)                     \
  nonstatic_field(HeapBlock::Header,           _length,                                       uint32_t)                              \
  nonstatic_field(HeapBlock::Header,           _used,                                         bool)                                  \
                                                                                                                                     \
  /**********************************/                                                                                               \
  /* Interpreter (NOTE: incomplete) */                                                                                               \
  /**********************************/                                                                                               \
                                                                                                                                     \
     static_field(AbstractInterpreter,         _code,                                         StubQueue*)                            \
                                                                                                                                     \
  /****************************/                                                                                                     \
  /* Stubs (NOTE: incomplete) */                                                                                                     \
  /****************************/                                                                                                     \
                                                                                                                                     \
  nonstatic_field(StubQueue,                   _stub_buffer,                                  address)                               \
  nonstatic_field(StubQueue,                   _buffer_limit,                                 int)                                   \
  nonstatic_field(StubQueue,                   _queue_begin,                                  int)                                   \
  nonstatic_field(StubQueue,                   _queue_end,                                    int)                                   \
  nonstatic_field(StubQueue,                   _number_of_stubs,                              int)                                   \
  nonstatic_field(InterpreterCodelet,          _size,                                         int)                                   \
  nonstatic_field(InterpreterCodelet,          _description,                                  const char*)                           \
  nonstatic_field(InterpreterCodelet,          _bytecode,                                     Bytecodes::Code)                       \
                                                                                                                                     \
  /***********************************/                                                                                              \
  /* StubRoutine for stack walking.  */                                                                                              \
  /***********************************/                                                                                              \
                                                                                                                                     \
     static_field(StubRoutines,                _call_stub_return_address,                     address)                               \
                                                                                                                                     \
  /***************************************/                                                                                          \
  /* PcDesc and other compiled code info */                                                                                          \
  /***************************************/                                                                                          \
                                                                                                                                     \
  nonstatic_field(PcDesc,                      _pc_offset,                                    int)                                   \
  nonstatic_field(PcDesc,                      _scope_decode_offset,                          int)                                   \
  nonstatic_field(PcDesc,                      _obj_decode_offset,                            int)                                   \
  nonstatic_field(PcDesc,                      _flags,                                        int)                                   \
                                                                                                                                     \
  /***************************************************/                                                                              \
  /* CodeBlobs (NOTE: incomplete, but only a little) */                                                                              \
  /***************************************************/                                                                              \
                                                                                                                                     \
  nonstatic_field(CodeBlob,                    _name,                                         const char*)                           \
  nonstatic_field(CodeBlob,                    _size,                                         int)                                   \
  nonstatic_field(CodeBlob,                    _kind,                                         CodeBlobKind)                          \
  nonstatic_field(CodeBlob,                    _header_size,                                  u2)                                    \
  nonstatic_field(CodeBlob,                    _relocation_size,                              int)                                   \
  nonstatic_field(CodeBlob,                    _content_offset,                               int)                                   \
  nonstatic_field(CodeBlob,                    _code_offset,                                  int)                                   \
  nonstatic_field(CodeBlob,                    _frame_complete_offset,                        int16_t)                               \
  nonstatic_field(CodeBlob,                    _data_offset,                                  int)                                   \
  nonstatic_field(CodeBlob,                    _frame_size,                                   int)                                   \
  nonstatic_field(CodeBlob,                    _oop_maps,                                     ImmutableOopMapSet*)                   \
  nonstatic_field(CodeBlob,                    _caller_must_gc_arguments,                     bool)                                  \
  nonstatic_field(CodeBlob,                    _mutable_data,                                 address)                               \
  nonstatic_field(CodeBlob,                    _mutable_data_size,                            int)                                   \
                                                                                                                                     \
  nonstatic_field(DeoptimizationBlob,          _unpack_offset,                                int)                                   \
                                                                                                                                     \
  /*****************************************************/                                                                            \
  /* UpcallStubs (NOTE: incomplete, but only a little) */                                                                            \
  /*****************************************************/                                                                            \
                                                                                                                                     \
  nonstatic_field(UpcallStub,                  _frame_data_offset,                            ByteSize)                              \
                                                                                                                                     \
  /**************************************************/                                                                               \
  /* NMethods (NOTE: incomplete, but only a little) */                                                                               \
  /**************************************************/                                                                               \
                                                                                                                                     \
  nonstatic_field(nmethod,                     _method,                                       Method*)                               \
  nonstatic_field(nmethod,                     _entry_bci,                                    int)                                   \
  nonstatic_field(nmethod,                     _osr_link,                                     nmethod*)                              \
  nonstatic_field(nmethod,                     _state,                                        volatile signed char)                  \
  nonstatic_field(nmethod,                     _exception_offset,                             int)                                   \
  nonstatic_field(nmethod,                     _deopt_handler_offset,                         int)                                   \
  nonstatic_field(nmethod,                     _deopt_mh_handler_offset,                      int)                                   \
  nonstatic_field(nmethod,                     _orig_pc_offset,                               int)                                   \
  nonstatic_field(nmethod,                     _stub_offset,                                  int)                                   \
  nonstatic_field(nmethod,                     _scopes_pcs_offset,                            int)                                   \
  nonstatic_field(nmethod,                     _scopes_data_offset,                           int)                                   \
  nonstatic_field(nmethod,                     _handler_table_offset,                         u2)                                    \
  nonstatic_field(nmethod,                     _nul_chk_table_offset,                         u2)                                    \
  nonstatic_field(nmethod,                     _entry_offset,                                 u2)                                    \
  nonstatic_field(nmethod,                     _verified_entry_offset,                        u2)                                    \
  nonstatic_field(nmethod,                     _osr_entry_point,                              address)                               \
  nonstatic_field(nmethod,                     _immutable_data,                               address)                               \
  nonstatic_field(nmethod,                     _immutable_data_size,                          int)                                   \
  nonstatic_field(nmethod,                     _compile_id,                                   int)                                   \
  nonstatic_field(nmethod,                     _comp_level,                                   CompLevel)                             \
  volatile_nonstatic_field(nmethod,            _exception_cache,                              ExceptionCache*)                       \
                                                                                                                                     \
  nonstatic_field(Deoptimization::UnrollBlock, _size_of_deoptimized_frame,                    int)                                   \
  nonstatic_field(Deoptimization::UnrollBlock, _caller_adjustment,                            int)                                   \
  nonstatic_field(Deoptimization::UnrollBlock, _number_of_frames,                             int)                                   \
  nonstatic_field(Deoptimization::UnrollBlock, _total_frame_sizes,                            int)                                   \
  nonstatic_field(Deoptimization::UnrollBlock, _unpack_kind,                                  int)                                   \
  nonstatic_field(Deoptimization::UnrollBlock, _frame_sizes,                                  intptr_t*)                             \
  nonstatic_field(Deoptimization::UnrollBlock, _frame_pcs,                                    address*)                              \
  nonstatic_field(Deoptimization::UnrollBlock, _register_block,                               intptr_t*)                             \
  nonstatic_field(Deoptimization::UnrollBlock, _return_type,                                  BasicType)                             \
  nonstatic_field(Deoptimization::UnrollBlock, _initial_info,                                 intptr_t)                              \
  nonstatic_field(Deoptimization::UnrollBlock, _caller_actual_parameters,                     int)                                   \
                                                                                                                                     \
  /********************************/                                                                                                 \
  /* JavaCalls (NOTE: incomplete) */                                                                                                 \
  /********************************/                                                                                                 \
                                                                                                                                     \
  nonstatic_field(JavaCallWrapper,             _anchor,                                       JavaFrameAnchor)                       \
  /********************************/                                                                                                 \
  /* JavaFrameAnchor (NOTE: incomplete) */                                                                                           \
  /********************************/                                                                                                 \
  volatile_nonstatic_field(JavaFrameAnchor,    _last_Java_sp,                                 intptr_t*)                             \
  volatile_nonstatic_field(JavaFrameAnchor,    _last_Java_pc,                                 address)                               \
                                                                                                                                     \
  /******************************/                                                                                                   \
  /* Threads (NOTE: incomplete) */                                                                                                   \
  /******************************/                                                                                                   \
                                                                                                                                     \
  static_field(Threads,                     _number_of_threads,                               int)                                   \
  static_field(Threads,                     _number_of_non_daemon_threads,                    int)                                   \
  static_field(Threads,                     _return_code,                                     int)                                   \
                                                                                                                                     \
  volatile_static_field(ThreadsSMRSupport, _java_thread_list,                                 ThreadsList*)                          \
  nonstatic_field(ThreadsList,                 _length,                                       const uint)                            \
  nonstatic_field(ThreadsList,                 _threads,                                      JavaThread *const *const)              \
                                                                                                                                     \
  nonstatic_field(ThreadShadow,                _pending_exception,                            oop)                                   \
  nonstatic_field(ThreadShadow,                _exception_file,                               const char*)                           \
  nonstatic_field(ThreadShadow,                _exception_line,                               int)                                   \
  nonstatic_field(Thread,                      _tlab,                                         ThreadLocalAllocBuffer)                \
  nonstatic_field(Thread,                      _allocated_bytes,                              jlong)                                 \
  nonstatic_field(JavaThread,                  _lock_stack,                                   LockStack)                             \
  nonstatic_field(LockStack,                   _top,                                          uint32_t)                              \
  nonstatic_field(LockStack,                   _base[0],                                      oop)                                   \
  nonstatic_field(NamedThread,                 _name,                                         char*)                                 \
  nonstatic_field(NamedThread,                 _processed_thread,                             Thread*)                               \
  nonstatic_field(JavaThread,                  _threadObj,                                    OopHandle)                             \
  nonstatic_field(JavaThread,                  _vthread,                                      OopHandle)                             \
  nonstatic_field(JavaThread,                  _jvmti_vthread,                                OopHandle)                             \
  nonstatic_field(JavaThread,                  _scopedValueCache,                              OopHandle)                            \
  nonstatic_field(JavaThread,                  _anchor,                                       JavaFrameAnchor)                       \
  volatile_nonstatic_field(JavaThread,         _current_pending_monitor,                      ObjectMonitor*)                        \
  nonstatic_field(JavaThread,                  _current_pending_monitor_is_from_java,         bool)                                  \
  volatile_nonstatic_field(JavaThread,         _current_waiting_monitor,                      ObjectMonitor*)                        \
  volatile_nonstatic_field(JavaThread,         _suspend_flags,                                uint32_t)                              \
  volatile_nonstatic_field(JavaThread,         _exception_oop,                                oop)                                   \
  volatile_nonstatic_field(JavaThread,         _exception_pc,                                 address)                               \
  volatile_nonstatic_field(JavaThread,         _is_method_handle_return,                      int)                                   \
  nonstatic_field(JavaThread,                  _saved_exception_pc,                           address)                               \
  volatile_nonstatic_field(JavaThread,         _thread_state,                                 JavaThreadState)                       \
  nonstatic_field(JavaThread,                  _stack_base,                                   address)                               \
  nonstatic_field(JavaThread,                  _stack_size,                                   size_t)                                \
  nonstatic_field(JavaThread,                  _vframe_array_head,                            vframeArray*)                          \
  nonstatic_field(JavaThread,                  _vframe_array_last,                            vframeArray*)                          \
  nonstatic_field(JavaThread,                  _active_handles,                               JNIHandleBlock*)                       \
  nonstatic_field(JavaThread,                  _monitor_owner_id,                             int64_t)                               \
  volatile_nonstatic_field(JavaThread,         _terminated,                                   JavaThread::TerminatedTypes)           \
  nonstatic_field(Thread,                      _osthread,                                     OSThread*)                             \
                                                                                                                                     \
  /************/                                                                                                                     \
  /* OSThread */                                                                                                                     \
  /************/                                                                                                                     \
                                                                                                                                     \
  volatile_nonstatic_field(OSThread,           _state,                                        ThreadState)                           \
                                                                                                                                     \
  /************************/                                                                                                         \
  /* ImmutableOopMap      */                                                                                                         \
  /************************/                                                                                                         \
                                                                                                                                     \
  nonstatic_field(ImmutableOopMapSet,          _count,                                        int)                                   \
  nonstatic_field(ImmutableOopMapSet,          _size,                                         int)                                   \
                                                                                                                                     \
  nonstatic_field(ImmutableOopMapPair,         _pc_offset,                                    int)                                   \
  nonstatic_field(ImmutableOopMapPair,         _oopmap_offset,                                int)                                   \
                                                                                                                                     \
  nonstatic_field(ImmutableOopMap,             _count,                                        int)                                   \
                                                                                                                                     \
  /*********************************/                                                                                                \
  /* JNIHandles and JNIHandleBlock */                                                                                                \
  /*********************************/                                                                                                \
  static_field(JNIHandles,                     _global_handles,                               OopStorage*)                           \
  static_field(JNIHandles,                     _weak_global_handles,                          OopStorage*)                           \
  unchecked_nonstatic_field(JNIHandleBlock,    _handles,       JNIHandleBlock::block_size_in_oops * sizeof(Oop)) /* Note: no type */ \
  nonstatic_field(JNIHandleBlock,              _top,                                          int)                                   \
  nonstatic_field(JNIHandleBlock,              _next,                                         JNIHandleBlock*)                       \
                                                                                                                                     \
  /********************/                                                                                                             \
  /* CompressedStream */                                                                                                             \
  /********************/                                                                                                             \
                                                                                                                                     \
  nonstatic_field(CompressedStream,            _buffer,                                       u_char*)                               \
  nonstatic_field(CompressedStream,            _position,                                     int)                                   \
                                                                                                                                     \
  /*********************************/                                                                                                \
  /* VMRegImpl (NOTE: incomplete) */                                                                                                 \
  /*********************************/                                                                                                \
                                                                                                                                     \
     static_field(VMRegImpl,                   regName[0],                                    const char*)                           \
     static_field(VMRegImpl,                   stack0,                                        VMReg)                                 \
                                                                                                                                     \
  /************/                                                                                                                     \
  /* Monitors */                                                                                                                     \
  /************/                                                                                                                     \
                                                                                                                                     \
  volatile_nonstatic_field(ObjectMonitor,      _metadata,                                     uintptr_t)                             \
  unchecked_nonstatic_field(ObjectMonitor,     _object,                                       sizeof(void *)) /* NOTE: no type */    \
  volatile_nonstatic_field(ObjectMonitor,      _owner,                                        int64_t)                               \
  volatile_nonstatic_field(ObjectMonitor,      _stack_locker,                                 BasicLock*)                            \
  volatile_nonstatic_field(ObjectMonitor,      _next_om,                                      ObjectMonitor*)                        \
  volatile_nonstatic_field(BasicLock,          _metadata,                                     uintptr_t)                             \
  nonstatic_field(ObjectMonitor,               _contentions,                                  int)                                   \
  volatile_nonstatic_field(ObjectMonitor,      _waiters,                                      int)                                   \
  volatile_nonstatic_field(ObjectMonitor,      _recursions,                                   intx)                                  \
  nonstatic_field(BasicObjectLock,             _lock,                                         BasicLock)                             \
  nonstatic_field(BasicObjectLock,             _obj,                                          oop)                                   \
  static_field(ObjectSynchronizer,             _in_use_list,                                  MonitorList)                           \
  volatile_nonstatic_field(MonitorList,        _head,                                         ObjectMonitor*)                        \
                                                                                                                                     \
  /*********************/                                                                                                            \
  /* -XX flags         */                                                                                                            \
  /*********************/                                                                                                            \
                                                                                                                                     \
  nonstatic_field(JVMFlag,                     _type,                                         int)                                   \
  nonstatic_field(JVMFlag,                     _name,                                         const char*)                           \
  unchecked_nonstatic_field(JVMFlag,           _addr,                                         sizeof(void*)) /* NOTE: no type */     \
  nonstatic_field(JVMFlag,                     _flags,                                        JVMFlag::Flags)                        \
     static_field(JVMFlag,                     flags,                                         JVMFlag*)                              \
     static_field(JVMFlag,                     numFlags,                                      size_t)                                \
                                                                                                                                     \
  /*************************/                                                                                                        \
  /* JDK / VM version info */                                                                                                        \
  /*************************/                                                                                                        \
                                                                                                                                     \
     static_field(Abstract_VM_Version,         _s_vm_release,                                 const char*)                           \
     static_field(Abstract_VM_Version,         _s_internal_vm_info_string,                    const char*)                           \
     static_field(Abstract_VM_Version,         _features,                                     uint64_t)                              \
     static_field(Abstract_VM_Version,         _features_string,                              const char*)                           \
     static_field(Abstract_VM_Version,         _cpu_info_string,                              const char*)                           \
     static_field(Abstract_VM_Version,         _vm_major_version,                             int)                                   \
     static_field(Abstract_VM_Version,         _vm_minor_version,                             int)                                   \
     static_field(Abstract_VM_Version,         _vm_security_version,                          int)                                   \
     static_field(Abstract_VM_Version,         _vm_build_number,                              int)                                   \
                                                                                                                                     \
  /*************************/                                                                                                        \
  /* JVMTI */                                                                                                                        \
  /*************************/                                                                                                        \
                                                                                                                                     \
  JVMTI_STRUCTS(static_field)                                                                                                        \
                                                                                                                                     \
  /*************/                                                                                                                    \
  /* Arguments */                                                                                                                    \
  /*************/                                                                                                                    \
                                                                                                                                     \
     static_field(Arguments,                   _jvm_flags_array,                              char**)                                \
     static_field(Arguments,                   _num_jvm_flags,                                int)                                   \
     static_field(Arguments,                   _jvm_args_array,                               char**)                                \
     static_field(Arguments,                   _num_jvm_args,                                 int)                                   \
     static_field(Arguments,                   _java_command,                                 char*)                                 \
                                                                                                                                     \
  /************/                                                                                                                     \
  /* Array<T> */                                                                                                                     \
  /************/                                                                                                                     \
                                                                                                                                     \
  nonstatic_field(Array<int>,                          _length,                               int)                                   \
  unchecked_nonstatic_field(Array<int>,                _data,                                 sizeof(int))                           \
  unchecked_nonstatic_field(Array<u1>,                 _data,                                 sizeof(u1))                            \
  unchecked_nonstatic_field(Array<u2>,                 _data,                                 sizeof(u2))                            \
  unchecked_nonstatic_field(Array<Method*>,            _data,                                 sizeof(Method*))                       \
  unchecked_nonstatic_field(Array<Klass*>,             _data,                                 sizeof(Klass*))                        \
  unchecked_nonstatic_field(Array<ResolvedFieldEntry>, _data,                                 sizeof(ResolvedFieldEntry))            \
  unchecked_nonstatic_field(Array<ResolvedMethodEntry>,_data,                                 sizeof(ResolvedMethodEntry))           \
  unchecked_nonstatic_field(Array<ResolvedIndyEntry>,  _data,                                 sizeof(ResolvedIndyEntry))             \
  unchecked_nonstatic_field(Array<Array<u1>*>,         _data,                                 sizeof(Array<u1>*))                    \
                                                                                                                                     \
  /*********************************/                                                                                                \
  /* java_lang_Class fields        */                                                                                                \
  /*********************************/                                                                                                \
                                                                                                                                     \
     static_field(java_lang_Class,             _klass_offset,                                 int)                                   \
     static_field(java_lang_Class,             _array_klass_offset,                           int)                                   \
     static_field(java_lang_Class,             _oop_size_offset,                              int)                                   \
     static_field(java_lang_Class,             _static_oop_field_count_offset,                int)                                   \
                                                                                                                                     \
  /********************************************/                                                                                     \
  /* FileMapInfo fields (CDS archive related) */                                                                                     \
  /********************************************/                                                                                     \
                                                                                                                                     \
  CDS_ONLY(nonstatic_field(FileMapInfo,        _header,                   FileMapHeader*))                                           \
  CDS_ONLY(   static_field(FileMapInfo,        _current_info,             FileMapInfo*))                                             \
  CDS_ONLY(nonstatic_field(FileMapHeader,      _regions[0],               CDSFileMapRegion))                                         \
  CDS_ONLY(nonstatic_field(FileMapHeader,      _cloned_vtables_offset,    size_t))                                                   \
  CDS_ONLY(nonstatic_field(FileMapHeader,      _mapped_base_address,      char*))                                                    \
  CDS_ONLY(nonstatic_field(CDSFileMapRegion,   _mapped_base,              char*))                                                    \
  CDS_ONLY(nonstatic_field(CDSFileMapRegion,   _used,                     size_t))                                                   \
                                                                                                                                     \
  /******************/                                                                                                               \
  /* VMError fields */                                                                                                               \
  /******************/                                                                                                               \
                                                                                                                                     \
     static_field(VMError,                     _thread,                                       Thread*)                               \
                                                                                                                                     \
  /************************/                                                                                                         \
  /* Miscellaneous fields */                                                                                                         \
  /************************/                                                                                                         \
                                                                                                                                     \
  nonstatic_field(CompileTask,                 _method,                                       Method*)                               \
  nonstatic_field(CompileTask,                 _osr_bci,                                      int)                                   \
  nonstatic_field(CompileTask,                 _comp_level,                                   int)                                   \
  nonstatic_field(CompileTask,                 _compile_id,                                   int)                                   \
  nonstatic_field(CompileTask,                 _num_inlined_bytecodes,                        int)                                   \
  nonstatic_field(CompileTask,                 _next,                                         CompileTask*)                          \
  nonstatic_field(CompileTask,                 _prev,                                         CompileTask*)                          \
                                                                                                                                     \
  nonstatic_field(vframeArray,                 _original,                                     frame)                                 \
  nonstatic_field(vframeArray,                 _caller,                                       frame)                                 \
  nonstatic_field(vframeArray,                 _frames,                                       int)                                   \
                                                                                                                                     \
  nonstatic_field(vframeArrayElement,          _frame,                                        frame)                                 \
  nonstatic_field(vframeArrayElement,          _bci,                                          int)                                   \
  nonstatic_field(vframeArrayElement,          _method,                                       Method*)                               \
                                                                                                                                     \
  nonstatic_field(AccessFlags,                 _flags,                                        u2)                                    \
  nonstatic_field(elapsedTimer,                _counter,                                      jlong)                                 \
  nonstatic_field(elapsedTimer,                _active,                                       bool)                                  \
  nonstatic_field(InvocationCounter,           _counter,                                      unsigned int)                          \
                                                                                                                                     \
  nonstatic_field(UpcallStub::FrameData,       jfa,                                           JavaFrameAnchor)                       \
                                                                                                                                     \
  nonstatic_field(Mutex,                       _name,                                         const char*)                           \
  static_field(Mutex,                          _mutex_array,                                  Mutex**)                               \
  static_field(Mutex,                          _num_mutex,                                    int)                                   \
  volatile_nonstatic_field(Mutex,              _owner,                                        Thread*)

//--------------------------------------------------------------------------------
// VM_TYPES
//
// This list must enumerate at least all of the types in the above
// list. For the types in the above list, the entry below must have
// exactly the same spacing since string comparisons are done in the
// code which verifies the consistency of these tables (in the debug
// build).
//
// In addition to the above types, this list is required to enumerate
// the JNI's java types, which are used to indicate the size of Java
// fields in this VM to the SA. Further, oop types are currently
// distinguished by name (i.e., ends with "oop") over in the SA.
//
// The declare_toplevel_type macro should be used to declare types
// which do not have a superclass.
//
// The declare_integer_type and declare_unsigned_integer_type macros
// are required in order to properly identify C integer types over in
// the SA. They should be used for any type which is otherwise opaque
// and which it is necessary to coerce into an integer value. This
// includes, for example, the type uintptr_t. Note that while they
// will properly identify the type's size regardless of the platform,
// since it is does not seem possible to deduce or check signedness at
// compile time using the pointer comparison tricks, it is currently
// required that the given types have the same signedness across all
// platforms.
//
// NOTE that there are platform-specific additions to this table in
// vmStructs_<os>_<cpu>.hpp.

#define VM_TYPES(declare_type,                                            \
                 declare_toplevel_type,                                   \
                 declare_oop_type,                                        \
                 declare_integer_type,                                    \
                 declare_unsigned_integer_type)                           \
                                                                          \
  /*************************************************************/         \
  /* Java primitive types -- required by the SA implementation */         \
  /* in order to determine the size of Java fields in this VM  */         \
  /* (the implementation looks up these names specifically)    */         \
  /* NOTE: since we fetch these sizes from the remote VM, we   */         \
  /* have a bootstrapping sequence during which it is not      */         \
  /* valid to fetch Java values from the remote process, only  */         \
  /* C integer values (of known size). NOTE also that we do    */         \
  /* NOT include "Java unsigned" types like juint here; since  */         \
  /* Java does not have unsigned primitive types, those can    */         \
  /* not be mapped directly and are considered to be C integer */         \
  /* types in this system (see the "other types" section,      */         \
  /* below.)                                                   */         \
  /*************************************************************/         \
                                                                          \
  declare_toplevel_type(jboolean)                                         \
  declare_toplevel_type(jbyte)                                            \
  declare_toplevel_type(jchar)                                            \
  declare_toplevel_type(jdouble)                                          \
  declare_toplevel_type(jfloat)                                           \
  declare_toplevel_type(jint)                                             \
  declare_toplevel_type(jlong)                                            \
  declare_toplevel_type(jshort)                                           \
                                                                          \
  /*********************************************************************/ \
  /* C integer types. User-defined typedefs (like "size_t" or          */ \
  /* "intptr_t") are guaranteed to be present with the same names over */ \
  /* in the SA's type database. Names like "unsigned short" are not    */ \
  /* guaranteed to be visible through the SA's type database lookup    */ \
  /* mechanism, though they will have a Type object created for them   */ \
  /* and are valid types for Fields.                                   */ \
  /*********************************************************************/ \
  declare_integer_type(bool)                                              \
  declare_integer_type(short)                                             \
  declare_integer_type(int)                                               \
  declare_integer_type(long)                                              \
  declare_integer_type(char)                                              \
  declare_integer_type(volatile signed char)                              \
  declare_unsigned_integer_type(unsigned char)                            \
  declare_unsigned_integer_type(u_char)                                   \
  declare_unsigned_integer_type(unsigned int)                             \
  declare_unsigned_integer_type(uint)                                     \
  declare_unsigned_integer_type(volatile uint)                            \
  declare_unsigned_integer_type(unsigned short)                           \
  declare_unsigned_integer_type(jushort)                                  \
  declare_unsigned_integer_type(unsigned long)                            \
  /* The compiler thinks this is a different type than */                 \
  /* unsigned short on Win32 */                                           \
  declare_unsigned_integer_type(u1)                                       \
  declare_unsigned_integer_type(u2)                                       \
  declare_unsigned_integer_type(u4)                                       \
  declare_unsigned_integer_type(u8)                                       \
  declare_unsigned_integer_type(unsigned)                                 \
                                                                          \
  /*****************************/                                         \
  /* C primitive pointer types */                                         \
  /*****************************/                                         \
                                                                          \
  declare_toplevel_type(void*)                                            \
  declare_toplevel_type(int*)                                             \
  declare_toplevel_type(char*)                                            \
  declare_toplevel_type(char**)                                           \
  declare_toplevel_type(u_char*)                                          \
  declare_toplevel_type(unsigned char*)                                   \
  declare_toplevel_type(volatile unsigned char*)                          \
                                                                          \
  /*******************************************************************/   \
  /* Types which it will be handy to have available over in the SA   */   \
  /* in order to do platform-independent address -> integer coercion */   \
  /* (note: these will be looked up by name)                         */   \
  /*******************************************************************/   \
                                                                          \
  declare_unsigned_integer_type(size_t)                                   \
  declare_integer_type(ssize_t)                                           \
  declare_integer_type(intx)                                              \
  declare_integer_type(intptr_t)                                          \
  declare_integer_type(int16_t)                                           \
  declare_integer_type(int64_t)                                           \
  declare_unsigned_integer_type(uintx)                                    \
  declare_unsigned_integer_type(uintptr_t)                                \
  declare_unsigned_integer_type(uint8_t)                                  \
  declare_unsigned_integer_type(uint32_t)                                 \
  declare_unsigned_integer_type(uint64_t)                                 \
                                                                          \
  /******************************************/                            \
  /* OopDesc hierarchy (NOTE: some missing) */                            \
  /******************************************/                            \
                                                                          \
  declare_toplevel_type(oopDesc)                                          \
    declare_type(arrayOopDesc, oopDesc)                                   \
      declare_type(objArrayOopDesc, arrayOopDesc)                         \
    declare_type(instanceOopDesc, oopDesc)                                \
                                                                          \
  /**************************************************/                    \
  /* MetadataOopDesc hierarchy (NOTE: some missing) */                    \
  /**************************************************/                    \
                                                                          \
  declare_toplevel_type(MetaspaceObj)                                     \
    declare_type(Metadata, MetaspaceObj)                                  \
    declare_type(Klass, Metadata)                                         \
           declare_type(ArrayKlass, Klass)                                \
           declare_type(ObjArrayKlass, ArrayKlass)                        \
           declare_type(TypeArrayKlass, ArrayKlass)                       \
      declare_type(InstanceKlass, Klass)                                  \
        declare_type(InstanceClassLoaderKlass, InstanceKlass)             \
        declare_type(InstanceMirrorKlass, InstanceKlass)                  \
        declare_type(InstanceRefKlass, InstanceKlass)                     \
        declare_type(InstanceStackChunkKlass, InstanceKlass)              \
    declare_type(ConstantPool, Metadata)                                  \
    declare_type(ConstantPoolCache, MetaspaceObj)                         \
    declare_type(MethodData, Metadata)                                    \
    declare_type(Method, Metadata)                                        \
    declare_type(MethodCounters, MetaspaceObj)                            \
    declare_type(ConstMethod, MetaspaceObj)                               \
    declare_type(Annotations, MetaspaceObj)                               \
                                                                          \
  declare_toplevel_type(MethodData::CompilerCounters)                     \
                                                                          \
  declare_toplevel_type(narrowKlass)                                      \
                                                                          \
  declare_toplevel_type(vtableEntry)                                      \
                                                                          \
           declare_toplevel_type(Symbol)                                  \
           declare_toplevel_type(Symbol*)                                 \
  declare_toplevel_type(volatile Metadata*)                               \
                                                                          \
  declare_toplevel_type(DataLayout)                                       \
                                                                          \
  /********/                                                              \
  /* Oops */                                                              \
  /********/                                                              \
                                                                          \
  declare_oop_type(objArrayOop)                                           \
  declare_oop_type(oop)                                                   \
  declare_oop_type(narrowOop)                                             \
  declare_oop_type(typeArrayOop)                                          \
                                                                          \
  declare_toplevel_type(OopHandle)                                        \
                                                                          \
  /**********************************/                                    \
  /* Method related data structures */                                    \
  /**********************************/                                    \
                                                                          \
  declare_toplevel_type(CheckedExceptionElement)                          \
  declare_toplevel_type(LocalVariableTableElement)                        \
  declare_toplevel_type(ExceptionTableElement)                            \
  declare_toplevel_type(MethodParametersElement)                          \
                                                                          \
  declare_toplevel_type(ClassLoaderData)                                  \
  declare_toplevel_type(ClassLoaderDataGraph)                             \
                                                                          \
  /************************/                                              \
  /* PerfMemory - jvmstat */                                              \
  /************************/                                              \
                                                                          \
  declare_toplevel_type(PerfDataPrologue)                                 \
  declare_toplevel_type(PerfDataPrologue*)                                \
  declare_toplevel_type(PerfDataEntry)                                    \
  declare_toplevel_type(PerfMemory)                                       \
  declare_type(PerfData, CHeapObj<mtInternal>)                            \
                                                                          \
  /********************/                                                  \
  /* VM Classes       */                                                  \
  /********************/                                                  \
                                                                          \
  declare_toplevel_type(vmClasses)                                        \
  declare_toplevel_type(vmSymbols)                                        \
                                                                          \
  declare_toplevel_type(GrowableArrayBase)                                \
  declare_toplevel_type(GrowableArray<int>)                               \
                                                                          \
  /***********************************************************/           \
  /* Thread hierarchy (needed for run-time type information) */           \
  /***********************************************************/           \
                                                                          \
  declare_toplevel_type(Threads)                                          \
  declare_toplevel_type(ThreadShadow)                                     \
    declare_type(Thread, ThreadShadow)                                    \
      declare_type(NonJavaThread, Thread)                                 \
        declare_type(NamedThread, NonJavaThread)                          \
        declare_type(WatcherThread, NonJavaThread)                        \
        declare_type(AsyncLogWriter, NonJavaThread)                       \
      declare_type(JavaThread, Thread)                                    \
        declare_type(JvmtiAgentThread, JavaThread)                        \
        declare_type(MonitorDeflationThread, JavaThread)                  \
        declare_type(ServiceThread, JavaThread)                           \
        declare_type(NotificationThread, JavaThread)                      \
        declare_type(CompilerThread, JavaThread)                          \
        declare_type(TrainingReplayThread, JavaThread)                    \
        declare_type(StringDedupThread, JavaThread)                       \
        declare_type(AttachListenerThread, JavaThread)                    \
        declare_type(JfrRecorderThread, JavaThread)                       \
        DEBUG_ONLY(COMPILER2_OR_JVMCI_PRESENT(                            \
          declare_type(DeoptimizeObjectsALotThread, JavaThread)))         \
  declare_toplevel_type(OSThread)                                         \
  declare_toplevel_type(JavaFrameAnchor)                                  \
                                                                          \
  declare_toplevel_type(ThreadsSMRSupport)                                \
  declare_toplevel_type(ThreadsList)                                      \
  declare_toplevel_type(LockStack)                                        \
                                                                          \
  /***************/                                                       \
  /* Interpreter */                                                       \
  /***************/                                                       \
                                                                          \
  declare_toplevel_type(AbstractInterpreter)                              \
                                                                          \
  /*********/                                                             \
  /* Stubs */                                                             \
  /*********/                                                             \
                                                                          \
  declare_toplevel_type(StubQueue)                                        \
  declare_toplevel_type(StubRoutines)                                     \
  declare_toplevel_type(Stub)                                             \
           declare_type(InterpreterCodelet, Stub)                         \
                                                                          \
  /*************/                                                         \
  /* JavaCalls */                                                         \
  /*************/                                                         \
                                                                          \
  declare_toplevel_type(JavaCallWrapper)                                  \
                                                                          \
  /*************/                                                         \
  /* CodeCache */                                                         \
  /*************/                                                         \
                                                                          \
  declare_toplevel_type(CodeCache)                                        \
                                                                          \
  /************/                                                          \
  /* CodeHeap */                                                          \
  /************/                                                          \
                                                                          \
  declare_toplevel_type(CodeHeap)                                         \
  declare_toplevel_type(CodeHeap*)                                        \
  declare_toplevel_type(HeapBlock)                                        \
  declare_toplevel_type(HeapBlock::Header)                                \
           declare_type(FreeBlock, HeapBlock)                             \
                                                                          \
  /*************************************************************/         \
  /* CodeBlob hierarchy (needed for run-time type information) */         \
  /*************************************************************/         \
                                                                          \
  declare_toplevel_type(CodeBlob)                                         \
  declare_type(RuntimeBlob,              CodeBlob)                        \
  declare_type(BufferBlob,               RuntimeBlob)                     \
  declare_type(AdapterBlob,              BufferBlob)                      \
  declare_type(MethodHandlesAdapterBlob, BufferBlob)                      \
  declare_type(VtableBlob,               BufferBlob)                      \
  declare_type(nmethod,                  CodeBlob)                        \
  declare_type(RuntimeStub,              RuntimeBlob)                     \
  declare_type(SingletonBlob,            RuntimeBlob)                     \
  declare_type(UpcallStub,               RuntimeBlob)                     \
  declare_type(SafepointBlob,            SingletonBlob)                   \
  declare_type(DeoptimizationBlob,       SingletonBlob)                   \
  COMPILER2_PRESENT(declare_type(ExceptionBlob,    SingletonBlob))        \
  COMPILER2_PRESENT(declare_type(UncommonTrapBlob, RuntimeBlob))          \
                                                                          \
  /***************************************/                               \
  /* PcDesc and other compiled code info */                               \
  /***************************************/                               \
                                                                          \
  declare_toplevel_type(PcDesc)                                           \
  declare_toplevel_type(ExceptionCache)                                   \
  declare_toplevel_type(PcDescCache)                                      \
  declare_toplevel_type(Dependencies)                                     \
  declare_toplevel_type(CompileTask)                                      \
  declare_toplevel_type(Deoptimization)                                   \
  declare_toplevel_type(Deoptimization::UnrollBlock)                      \
                                                                          \
  /************************/                                              \
  /* ImmutableOopMap      */                                              \
  /************************/                                              \
                                                                          \
  declare_toplevel_type(ImmutableOopMapSet)                               \
  declare_toplevel_type(ImmutableOopMapPair)                              \
  declare_toplevel_type(ImmutableOopMap)                                  \
                                                                          \
  /********************/                                                  \
  /* CompressedStream */                                                  \
  /********************/                                                  \
                                                                          \
  declare_toplevel_type(CompressedStream)                                 \
                                                                          \
  /**************/                                                        \
  /* VMRegImpl  */                                                        \
  /**************/                                                        \
                                                                          \
  declare_toplevel_type(VMRegImpl)                                        \
                                                                          \
  /*********************************/                                     \
  /* JNIHandles and JNIHandleBlock */                                     \
  /*********************************/                                     \
                                                                          \
  declare_toplevel_type(JNIHandles)                                       \
  declare_toplevel_type(JNIHandleBlock)                                   \
  declare_toplevel_type(jobject)                                          \
                                                                          \
  /**************/                                                        \
  /* OopStorage */                                                        \
  /**************/                                                        \
                                                                          \
  declare_toplevel_type(OopStorage)                                       \
                                                                          \
  /************/                                                          \
  /* Monitors */                                                          \
  /************/                                                          \
                                                                          \
  declare_toplevel_type(ObjectMonitor)                                    \
  declare_toplevel_type(MonitorList)                                      \
  declare_toplevel_type(ObjectSynchronizer)                               \
  declare_toplevel_type(BasicLock)                                        \
  declare_toplevel_type(BasicObjectLock)                                  \
                                                                          \
  /********************/                                                  \
  /* -XX flags        */                                                  \
  /********************/                                                  \
                                                                          \
  declare_toplevel_type(JVMFlag)                                          \
  declare_toplevel_type(JVMFlag*)                                         \
                                                                          \
  /********************/                                                  \
  /* JVMTI            */                                                  \
  /********************/                                                  \
                                                                          \
  declare_toplevel_type(JvmtiExport)                                      \
                                                                          \
  /********************/                                                  \
  /* JDK/VM version   */                                                  \
  /********************/                                                  \
                                                                          \
  declare_toplevel_type(Abstract_VM_Version)                              \
  declare_toplevel_type(VM_Version)                                       \
                                                                          \
  /*************/                                                         \
  /* Arguments */                                                         \
  /*************/                                                         \
                                                                          \
  declare_toplevel_type(Arguments)                                        \
                                                                          \
  /***********/                                                           \
  /* VMError */                                                           \
  /***********/                                                           \
                                                                          \
  declare_toplevel_type(VMError)                                          \
                                                                          \
  /***************/                                                       \
  /* Other types */                                                       \
  /***************/                                                       \
                                                                          \
  /* all enum types */                                                    \
                                                                          \
   declare_integer_type(Bytecodes::Code)                                  \
   declare_integer_type(InstanceKlass::ClassState)                        \
   declare_integer_type(JavaThreadState)                                  \
   declare_integer_type(ThreadState)                                      \
   declare_integer_type(Location::Type)                                   \
   declare_integer_type(Location::Where)                                  \
   declare_integer_type(JVMFlag::Flags)                                   \
                                                                          \
   declare_toplevel_type(CHeapObj<mtInternal>)                            \
            declare_type(Array<int>, MetaspaceObj)                        \
            declare_type(Array<u1>, MetaspaceObj)                         \
            declare_type(Array<u2>, MetaspaceObj)                         \
            declare_type(Array<Klass*>, MetaspaceObj)                     \
            declare_type(Array<Method*>, MetaspaceObj)                    \
            declare_type(Array<ResolvedFieldEntry>, MetaspaceObj)         \
            declare_type(Array<ResolvedMethodEntry>, MetaspaceObj)        \
            declare_type(Array<ResolvedIndyEntry>, MetaspaceObj)          \
            declare_type(Array<Array<u1>*>, MetaspaceObj)                 \
                                                                          \
   declare_toplevel_type(BitMap)                                          \
            declare_type(BitMapView, BitMap)                              \
                                                                          \
  declare_integer_type(markWord)                                          \
  declare_integer_type(AccessFlags)  /* FIXME: wrong type (not integer) */\
  declare_toplevel_type(address)      /* FIXME: should this be an integer type? */\
  declare_integer_type(BasicType)   /* FIXME: wrong type (not integer) */ \
                                                                          \
  declare_integer_type(CompLevel)                                         \
  declare_integer_type(ByteSize)                                          \
  declare_integer_type(CodeBlobKind)                                      \
  JVMTI_ONLY(declare_toplevel_type(BreakpointInfo))                       \
  JVMTI_ONLY(declare_toplevel_type(BreakpointInfo*))                      \
  declare_toplevel_type(CodeBlob*)                                        \
  declare_toplevel_type(RuntimeBlob*)                                     \
  declare_toplevel_type(CompressedWriteStream*)                           \
  declare_toplevel_type(ResolvedFieldEntry)                               \
  declare_toplevel_type(ResolvedMethodEntry)                              \
  declare_toplevel_type(ResolvedIndyEntry)                                \
  declare_toplevel_type(elapsedTimer)                                     \
  declare_toplevel_type(frame)                                            \
  declare_toplevel_type(intptr_t*)                                        \
   declare_unsigned_integer_type(InvocationCounter) /* FIXME: wrong type (not integer) */ \
  declare_toplevel_type(JavaThread*)                                      \
  declare_toplevel_type(JavaThread *const *const)                         \
  declare_toplevel_type(java_lang_Class)                                  \
  declare_integer_type(JavaThread::TerminatedTypes)                       \
  declare_toplevel_type(jbyte*)                                           \
  declare_toplevel_type(jbyte**)                                          \
  declare_toplevel_type(jint*)                                            \
  declare_unsigned_integer_type(juint)                                    \
  declare_unsigned_integer_type(julong)                                   \
  declare_toplevel_type(JNIHandleBlock*)                                  \
  declare_toplevel_type(JNIid)                                            \
  declare_toplevel_type(JNIid*)                                           \
  declare_toplevel_type(jmethodID*)                                       \
  declare_toplevel_type(Mutex)                                            \
  declare_toplevel_type(Mutex*)                                           \
  declare_toplevel_type(nmethod*)                                         \
  declare_toplevel_type(ObjectMonitor*)                                   \
  declare_toplevel_type(oop*)                                             \
  declare_toplevel_type(OopMapCache*)                                     \
  declare_toplevel_type(VMReg)                                            \
  declare_toplevel_type(OSThread*)                                        \
   declare_integer_type(ReferenceType)                                    \
  declare_toplevel_type(StubQueue*)                                       \
  declare_toplevel_type(Thread*)                                          \
  declare_toplevel_type(Universe)                                         \
  declare_toplevel_type(CompressedOops)                                   \
  declare_toplevel_type(CompressedKlassPointers)                          \
  declare_toplevel_type(os)                                               \
  declare_toplevel_type(vframeArray)                                      \
  declare_toplevel_type(vframeArrayElement)                               \
  declare_toplevel_type(Annotations*)                                     \
  declare_toplevel_type(OopMapValue)                                      \
  declare_type(FileMapInfo, CHeapObj<mtInternal>)                         \
  declare_toplevel_type(FileMapHeader)                                    \
  declare_toplevel_type(CDSFileMapRegion)                                 \
  declare_toplevel_type(UpcallStub::FrameData)                            \
                                                                          \
  /************/                                                          \
  /* GC types */                                                          \
  /************/                                                          \
                                                                          \
  VM_TYPES_GC(declare_type,                                               \
              declare_toplevel_type,                                      \
              declare_integer_type)

//--------------------------------------------------------------------------------
// VM_INT_CONSTANTS
//
// This table contains integer constants required over in the
// serviceability agent. The "declare_constant" macro is used for all
// enums, etc., while "declare_preprocessor_constant" must be used for
// all #defined constants.

#define VM_INT_CONSTANTS(declare_constant,                                \
                         declare_constant_with_value,                     \
                         declare_preprocessor_constant)                   \
                                                                          \
  /****************/                                                      \
  /* GC constants */                                                      \
  /****************/                                                      \
                                                                          \
  VM_INT_CONSTANTS_GC(declare_constant,                                   \
                      declare_constant_with_value)                        \
                                                                          \
  /******************/                                                    \
  /* Useful globals */                                                    \
  /******************/                                                    \
                                                                          \
  declare_preprocessor_constant("ASSERT", DEBUG_ONLY(1) NOT_DEBUG(0))     \
  declare_preprocessor_constant("COMPILER2", COMPILER2_PRESENT(1) NOT_COMPILER2(0)) \
                                                                          \
  /****************/                                                      \
  /* Object sizes */                                                      \
  /****************/                                                      \
                                                                          \
  declare_constant(oopSize)                                               \
  declare_constant(LogBytesPerWord)                                       \
  declare_constant(BytesPerWord)                                          \
  declare_constant(BytesPerLong)                                          \
                                                                          \
  declare_constant(HeapWordSize)                                          \
  declare_constant(LogHeapWordSize)                                       \
                                                                          \
                                                                          \
  /************************/                                              \
  /* PerfMemory - jvmstat */                                              \
  /************************/                                              \
                                                                          \
  declare_preprocessor_constant("PERFDATA_MAJOR_VERSION", PERFDATA_MAJOR_VERSION) \
  declare_preprocessor_constant("PERFDATA_MINOR_VERSION", PERFDATA_MINOR_VERSION) \
  declare_preprocessor_constant("PERFDATA_BIG_ENDIAN", PERFDATA_BIG_ENDIAN)       \
  declare_preprocessor_constant("PERFDATA_LITTLE_ENDIAN", PERFDATA_LITTLE_ENDIAN) \
                                                                          \
                                                                          \
  /************************************************************/          \
  /* HotSpot specific JVM_ACC constants from global anon enum */          \
  /************************************************************/          \
                                                                          \
  declare_constant(JVM_CONSTANT_Utf8)                                     \
  declare_constant(JVM_CONSTANT_Unicode)                                  \
  declare_constant(JVM_CONSTANT_Integer)                                  \
  declare_constant(JVM_CONSTANT_Float)                                    \
  declare_constant(JVM_CONSTANT_Long)                                     \
  declare_constant(JVM_CONSTANT_Double)                                   \
  declare_constant(JVM_CONSTANT_Class)                                    \
  declare_constant(JVM_CONSTANT_String)                                   \
  declare_constant(JVM_CONSTANT_Fieldref)                                 \
  declare_constant(JVM_CONSTANT_Methodref)                                \
  declare_constant(JVM_CONSTANT_InterfaceMethodref)                       \
  declare_constant(JVM_CONSTANT_NameAndType)                              \
  declare_constant(JVM_CONSTANT_MethodHandle)                             \
  declare_constant(JVM_CONSTANT_MethodType)                               \
  declare_constant(JVM_CONSTANT_Dynamic)                                  \
  declare_constant(JVM_CONSTANT_InvokeDynamic)                            \
  declare_constant(JVM_CONSTANT_Module)                                   \
  declare_constant(JVM_CONSTANT_Package)                                  \
  declare_constant(JVM_CONSTANT_ExternalMax)                              \
                                                                          \
  declare_constant(JVM_CONSTANT_Invalid)                                  \
  declare_constant(JVM_CONSTANT_InternalMin)                              \
  declare_constant(JVM_CONSTANT_UnresolvedClass)                          \
  declare_constant(JVM_CONSTANT_ClassIndex)                               \
  declare_constant(JVM_CONSTANT_StringIndex)                              \
  declare_constant(JVM_CONSTANT_UnresolvedClassInError)                   \
  declare_constant(JVM_CONSTANT_MethodHandleInError)                      \
  declare_constant(JVM_CONSTANT_MethodTypeInError)                        \
  declare_constant(JVM_CONSTANT_DynamicInError)                           \
  declare_constant(JVM_CONSTANT_InternalMax)                              \
                                                                          \
  /*******************/                                                   \
  /* JavaThreadState */                                                   \
  /*******************/                                                   \
                                                                          \
  declare_constant(_thread_uninitialized)                                 \
  declare_constant(_thread_new)                                           \
  declare_constant(_thread_new_trans)                                     \
  declare_constant(_thread_in_native)                                     \
  declare_constant(_thread_in_native_trans)                               \
  declare_constant(_thread_in_vm)                                         \
  declare_constant(_thread_in_vm_trans)                                   \
  declare_constant(_thread_in_Java)                                       \
  declare_constant(_thread_in_Java_trans)                                 \
  declare_constant(_thread_blocked)                                       \
  declare_constant(_thread_blocked_trans)                                 \
  declare_constant(JavaThread::_not_terminated)                           \
  declare_constant(JavaThread::_thread_exiting)                           \
                                                                          \
  /*******************/                                                   \
  /* JavaThreadState */                                                   \
  /*******************/                                                   \
                                                                          \
  declare_constant(ALLOCATED)                                             \
  declare_constant(INITIALIZED)                                           \
  declare_constant(RUNNABLE)                                              \
  declare_constant(MONITOR_WAIT)                                          \
  declare_constant(CONDVAR_WAIT)                                          \
  declare_constant(OBJECT_WAIT)                                           \
  declare_constant(BREAKPOINTED)                                          \
  declare_constant(SLEEPING)                                              \
  declare_constant(ZOMBIE)                                                \
                                                                          \
  /******************************/                                        \
  /* Klass misc. enum constants */                                        \
  /******************************/                                        \
                                                                          \
  declare_constant(Klass::_primary_super_limit)                           \
  declare_constant(Klass::_lh_neutral_value)                              \
  declare_constant(Klass::_lh_instance_slow_path_bit)                     \
  declare_constant(Klass::_lh_log2_element_size_shift)                    \
  declare_constant(Klass::_lh_log2_element_size_mask)                     \
  declare_constant(Klass::_lh_element_type_shift)                         \
  declare_constant(Klass::_lh_element_type_mask)                          \
  declare_constant(Klass::_lh_header_size_shift)                          \
  declare_constant(Klass::_lh_header_size_mask)                           \
  declare_constant(Klass::_lh_array_tag_shift)                            \
  declare_constant(Klass::_lh_array_tag_type_value)                       \
  declare_constant(Klass::_lh_array_tag_obj_value)                        \
                                                                          \
  declare_constant(Method::nonvirtual_vtable_index)                       \
  declare_constant(Method::extra_stack_entries_for_jsr292)                \
                                                                          \
  /********************************/                                      \
  /* ConstMethod anon-enum */                                             \
  /********************************/                                      \
                                                                          \
  declare_constant(ConstMethodFlags::_misc_has_linenumber_table)          \
  declare_constant(ConstMethodFlags::_misc_has_checked_exceptions)        \
  declare_constant(ConstMethodFlags::_misc_has_localvariable_table)       \
  declare_constant(ConstMethodFlags::_misc_has_exception_table)           \
  declare_constant(ConstMethodFlags::_misc_has_generic_signature)         \
  declare_constant(ConstMethodFlags::_misc_has_method_parameters)         \
  declare_constant(ConstMethodFlags::_misc_has_method_annotations)        \
  declare_constant(ConstMethodFlags::_misc_has_parameter_annotations)     \
  declare_constant(ConstMethodFlags::_misc_has_default_annotations)       \
  declare_constant(ConstMethodFlags::_misc_has_type_annotations)          \
                                                                          \
  /**************/                                                        \
  /* DataLayout */                                                        \
  /**************/                                                        \
                                                                          \
  declare_constant(DataLayout::cell_size)                                 \
  declare_constant(DataLayout::no_tag)                                    \
  declare_constant(DataLayout::bit_data_tag)                              \
  declare_constant(DataLayout::counter_data_tag)                          \
  declare_constant(DataLayout::jump_data_tag)                             \
  declare_constant(DataLayout::receiver_type_data_tag)                    \
  declare_constant(DataLayout::virtual_call_data_tag)                     \
  declare_constant(DataLayout::ret_data_tag)                              \
  declare_constant(DataLayout::branch_data_tag)                           \
  declare_constant(DataLayout::multi_branch_data_tag)                     \
  declare_constant(DataLayout::arg_info_data_tag)                         \
  declare_constant(DataLayout::call_type_data_tag)                        \
  declare_constant(DataLayout::virtual_call_type_data_tag)                \
  declare_constant(DataLayout::parameters_type_data_tag)                  \
  declare_constant(DataLayout::speculative_trap_data_tag)                 \
                                                                          \
  /*************************************/                                 \
  /* InstanceKlass enum                */                                 \
  /*************************************/                                 \
                                                                          \
                                                                          \
                                                                          \
  /************************************************/                      \
  /* InstanceKlass InnerClassAttributeOffset enum */                      \
  /************************************************/                      \
                                                                          \
  declare_constant(InstanceKlass::inner_class_inner_class_info_offset)    \
  declare_constant(InstanceKlass::inner_class_outer_class_info_offset)    \
  declare_constant(InstanceKlass::inner_class_inner_name_offset)          \
  declare_constant(InstanceKlass::inner_class_access_flags_offset)        \
  declare_constant(InstanceKlass::inner_class_next_offset)                \
                                                                          \
  /*****************************************************/                 \
  /* InstanceKlass EnclosingMethodAttributeOffset enum */                 \
  /*****************************************************/                 \
                                                                          \
  declare_constant(InstanceKlass::enclosing_method_attribute_size)        \
                                                                          \
  /*********************************/                                     \
  /* InstanceKlass ClassState enum */                                     \
  /*********************************/                                     \
                                                                          \
  declare_constant(InstanceKlass::allocated)                              \
  declare_constant(InstanceKlass::loaded)                                 \
  declare_constant(InstanceKlass::linked)                                 \
  declare_constant(InstanceKlass::being_initialized)                      \
  declare_constant(InstanceKlass::fully_initialized)                      \
  declare_constant(InstanceKlass::initialization_error)                   \
                                                                          \
  /*********************************/                                     \
  /* Symbol* - symbol max length */                                       \
  /*********************************/                                     \
                                                                          \
  declare_constant(Symbol::max_symbol_length)                             \
                                                                          \
  /******************************************************/                \
  /* BSMAttributeEntry* - layout enum for InvokeDynamic */                \
  /******************************************************/                \
                                                                          \
  declare_constant(BSMAttributeEntry::_bsmi_offset)                       \
  declare_constant(BSMAttributeEntry::_argc_offset)                       \
  declare_constant(BSMAttributeEntry::_argv_offset)                       \
                                                                          \
  /***************************************/                               \
  /* JavaThreadStatus enum               */                               \
  /***************************************/                               \
                                                                          \
  declare_constant(JavaThreadStatus::NEW)                                 \
  declare_constant(JavaThreadStatus::RUNNABLE)                            \
  declare_constant(JavaThreadStatus::SLEEPING)                            \
  declare_constant(JavaThreadStatus::IN_OBJECT_WAIT)                      \
  declare_constant(JavaThreadStatus::IN_OBJECT_WAIT_TIMED)                \
  declare_constant(JavaThreadStatus::PARKED)                              \
  declare_constant(JavaThreadStatus::PARKED_TIMED)                        \
  declare_constant(JavaThreadStatus::BLOCKED_ON_MONITOR_ENTER)            \
  declare_constant(JavaThreadStatus::TERMINATED)                          \
                                                                          \
                                                                          \
  /******************************/                                        \
  /* FieldFlags enum            */                                        \
  /******************************/                                        \
                                                                          \
  declare_constant(FieldInfo::FieldFlags::_ff_initialized)                \
  declare_constant(FieldInfo::FieldFlags::_ff_injected)                   \
  declare_constant(FieldInfo::FieldFlags::_ff_generic)                    \
  declare_constant(FieldInfo::FieldFlags::_ff_stable)                     \
  declare_constant(FieldInfo::FieldFlags::_ff_contended)                  \
                                                                          \
  /******************************/                                        \
  /* Debug info                 */                                        \
  /******************************/                                        \
                                                                          \
  declare_constant(Location::OFFSET_MASK)                                 \
  declare_constant(Location::OFFSET_SHIFT)                                \
  declare_constant(Location::TYPE_MASK)                                   \
  declare_constant(Location::TYPE_SHIFT)                                  \
  declare_constant(Location::WHERE_MASK)                                  \
  declare_constant(Location::WHERE_SHIFT)                                 \
                                                                          \
  /* constants from Location::Type enum  */                               \
                                                                          \
  declare_constant(Location::normal)                                      \
  declare_constant(Location::oop)                                         \
  declare_constant(Location::narrowoop)                                   \
  declare_constant(Location::int_in_long)                                 \
  declare_constant(Location::lng)                                         \
  declare_constant(Location::float_in_dbl)                                \
  declare_constant(Location::dbl)                                         \
  declare_constant(Location::addr)                                        \
  declare_constant(Location::invalid)                                     \
                                                                          \
  /* constants from Location::Where enum */                               \
                                                                          \
  declare_constant(Location::on_stack)                                    \
  declare_constant(Location::in_register)                                 \
                                                                          \
  declare_constant(Deoptimization::Reason_many)                           \
  declare_constant(Deoptimization::Reason_none)                           \
  declare_constant(Deoptimization::Reason_null_check)                     \
  declare_constant(Deoptimization::Reason_null_assert)                    \
  declare_constant(Deoptimization::Reason_range_check)                    \
  declare_constant(Deoptimization::Reason_class_check)                    \
  declare_constant(Deoptimization::Reason_array_check)                    \
  declare_constant(Deoptimization::Reason_intrinsic)                      \
  declare_constant(Deoptimization::Reason_bimorphic)                      \
  declare_constant(Deoptimization::Reason_profile_predicate)              \
  declare_constant(Deoptimization::Reason_unloaded)                       \
  declare_constant(Deoptimization::Reason_uninitialized)                  \
  declare_constant(Deoptimization::Reason_initialized)                    \
  declare_constant(Deoptimization::Reason_unreached)                      \
  declare_constant(Deoptimization::Reason_unhandled)                      \
  declare_constant(Deoptimization::Reason_constraint)                     \
  declare_constant(Deoptimization::Reason_div0_check)                     \
  declare_constant(Deoptimization::Reason_age)                            \
  declare_constant(Deoptimization::Reason_predicate)                      \
  declare_constant(Deoptimization::Reason_loop_limit_check)               \
  declare_constant(Deoptimization::Reason_short_running_long_loop)        \
  declare_constant(Deoptimization::Reason_auto_vectorization_check)       \
  declare_constant(Deoptimization::Reason_speculate_class_check)          \
  declare_constant(Deoptimization::Reason_speculate_null_check)           \
  declare_constant(Deoptimization::Reason_speculate_null_assert)          \
  declare_constant(Deoptimization::Reason_unstable_if)                    \
  declare_constant(Deoptimization::Reason_unstable_fused_if)              \
  declare_constant(Deoptimization::Reason_receiver_constraint)            \
  NOT_ZERO(JVMCI_ONLY(declare_constant(Deoptimization::Reason_transfer_to_interpreter)))        \
  NOT_ZERO(JVMCI_ONLY(declare_constant(Deoptimization::Reason_not_compiled_exception_handler))) \
  NOT_ZERO(JVMCI_ONLY(declare_constant(Deoptimization::Reason_unresolved)))                     \
  NOT_ZERO(JVMCI_ONLY(declare_constant(Deoptimization::Reason_jsr_mismatch)))                   \
  declare_constant(Deoptimization::Reason_tenured)                        \
  declare_constant(Deoptimization::Reason_LIMIT)                          \
  declare_constant(Deoptimization::Reason_RECORDED_LIMIT)                 \
                                                                          \
  declare_constant(Deoptimization::Action_none)                           \
  declare_constant(Deoptimization::Action_maybe_recompile)                \
  declare_constant(Deoptimization::Action_reinterpret)                    \
  declare_constant(Deoptimization::Action_make_not_entrant)               \
  declare_constant(Deoptimization::Action_make_not_compilable)            \
  declare_constant(Deoptimization::Action_LIMIT)                          \
                                                                          \
  declare_constant(Deoptimization::Unpack_deopt)                          \
  declare_constant(Deoptimization::Unpack_exception)                      \
  declare_constant(Deoptimization::Unpack_uncommon_trap)                  \
  declare_constant(Deoptimization::Unpack_reexecute)                      \
                                                                          \
  declare_constant(Deoptimization::_action_bits)                          \
  declare_constant(Deoptimization::_reason_bits)                          \
  declare_constant(Deoptimization::_debug_id_bits)                        \
  declare_constant(Deoptimization::_action_shift)                         \
  declare_constant(Deoptimization::_reason_shift)                         \
  declare_constant(Deoptimization::_debug_id_shift)                       \
                                                                          \
  /******************************************/                            \
  /* BasicType enum (globalDefinitions.hpp) */                            \
  /******************************************/                            \
                                                                          \
  declare_constant(T_BOOLEAN)                                             \
  declare_constant(T_CHAR)                                                \
  declare_constant(T_FLOAT)                                               \
  declare_constant(T_DOUBLE)                                              \
  declare_constant(T_BYTE)                                                \
  declare_constant(T_SHORT)                                               \
  declare_constant(T_INT)                                                 \
  declare_constant(T_LONG)                                                \
  declare_constant(T_OBJECT)                                              \
  declare_constant(T_ARRAY)                                               \
  declare_constant(T_VOID)                                                \
  declare_constant(T_ADDRESS)                                             \
  declare_constant(T_NARROWOOP)                                           \
  declare_constant(T_METADATA)                                            \
  declare_constant(T_NARROWKLASS)                                         \
  declare_constant(T_CONFLICT)                                            \
  declare_constant(T_ILLEGAL)                                             \
                                                                          \
  /**********************************************/                        \
  /* BasicTypeSize enum (globalDefinitions.hpp) */                        \
  /**********************************************/                        \
                                                                          \
  declare_constant(T_BOOLEAN_size)                                        \
  declare_constant(T_CHAR_size)                                           \
  declare_constant(T_FLOAT_size)                                          \
  declare_constant(T_DOUBLE_size)                                         \
  declare_constant(T_BYTE_size)                                           \
  declare_constant(T_SHORT_size)                                          \
  declare_constant(T_INT_size)                                            \
  declare_constant(T_LONG_size)                                           \
  declare_constant(T_OBJECT_size)                                         \
  declare_constant(T_ARRAY_size)                                          \
  declare_constant(T_NARROWOOP_size)                                      \
  declare_constant(T_NARROWKLASS_size)                                    \
  declare_constant(T_VOID_size)                                           \
                                                                          \
  /**********************************************/                        \
  /* LockingMode enum (globalDefinitions.hpp) */                          \
  /**********************************************/                        \
                                                                          \
  declare_constant(LM_MONITOR)                                            \
  declare_constant(LM_LEGACY)                                             \
  declare_constant(LM_LIGHTWEIGHT)                                        \
                                                                          \
  /*********************************************/                         \
  /* MethodCompilation (globalDefinitions.hpp) */                         \
  /*********************************************/                         \
                                                                          \
  declare_constant(InvocationEntryBci)                                    \
                                                                          \
  /*************/                                                         \
  /* CompLevel */                                                         \
  /*************/                                                         \
                                                                          \
  declare_constant(CompLevel_any)                                         \
  declare_constant(CompLevel_all)                                         \
  declare_constant(CompLevel_none)                                        \
  declare_constant(CompLevel_simple)                                      \
  declare_constant(CompLevel_limited_profile)                             \
  declare_constant(CompLevel_full_profile)                                \
  declare_constant(CompLevel_full_optimization)                           \
                                                                          \
  /****************/                                                      \
  /* CodeBlobKind */                                                      \
  /****************/                                                      \
                                                                          \
  declare_constant(CodeBlobKind::Nmethod)                                 \
  declare_constant(CodeBlobKind::Buffer)                                  \
  declare_constant(CodeBlobKind::Adapter)                                 \
  declare_constant(CodeBlobKind::Vtable)                                  \
  declare_constant(CodeBlobKind::MHAdapter)                               \
  declare_constant(CodeBlobKind::RuntimeStub)                             \
  declare_constant(CodeBlobKind::Deoptimization)                          \
  declare_constant(CodeBlobKind::Safepoint)                               \
  COMPILER2_PRESENT(declare_constant(CodeBlobKind::Exception))            \
  COMPILER2_PRESENT(declare_constant(CodeBlobKind::UncommonTrap))         \
  declare_constant(CodeBlobKind::Upcall)                                  \
  declare_constant(CodeBlobKind::Number_Of_Kinds)                         \
                                                                          \
  /***************/                                                       \
  /* OopMapValue */                                                       \
  /***************/                                                       \
                                                                          \
  declare_constant(OopMapValue::type_bits)                                \
  declare_constant(OopMapValue::register_bits)                            \
  declare_constant(OopMapValue::type_shift)                               \
  declare_constant(OopMapValue::register_shift)                           \
  declare_constant(OopMapValue::type_mask)                                \
  declare_constant(OopMapValue::type_mask_in_place)                       \
  declare_constant(OopMapValue::register_mask)                            \
  declare_constant(OopMapValue::register_mask_in_place)                   \
  declare_constant(OopMapValue::unused_value)                             \
  declare_constant(OopMapValue::oop_value)                                \
  declare_constant(OopMapValue::narrowoop_value)                          \
  declare_constant(OopMapValue::callee_saved_value)                       \
  declare_constant(OopMapValue::derived_oop_value)                        \
                                                                          \
  /******************/                                                    \
  /* JNIHandleBlock */                                                    \
  /******************/                                                    \
                                                                          \
  declare_constant(JNIHandleBlock::block_size_in_oops)                    \
                                                                          \
  /**********************/                                                \
  /* PcDesc             */                                                \
  /**********************/                                                \
                                                                          \
  declare_constant(PcDesc::PCDESC_reexecute)                              \
  declare_constant(PcDesc::PCDESC_is_method_handle_invoke)                \
  declare_constant(PcDesc::PCDESC_return_oop)                             \
                                                                          \
  /**********************/                                                \
  /* frame              */                                                \
  /**********************/                                                \
  NOT_ZERO(PPC64_ONLY(declare_constant(frame::entry_frame_locals_size)))  \
                                                                          \
  declare_constant(frame::pc_return_offset)                               \
                                                                          \
  /*************/                                                         \
  /* vmSymbols */                                                         \
  /*************/                                                         \
                                                                          \
  declare_constant(vmSymbols::FIRST_SID)                                  \
  declare_constant(vmSymbols::SID_LIMIT)                                  \
                                                                          \
  /****************/                                                      \
  /* vmIntrinsics */                                                      \
  /****************/                                                      \
                                                                          \
  declare_constant(vmIntrinsics::_invokeBasic)                            \
  declare_constant(vmIntrinsics::_linkToVirtual)                          \
  declare_constant(vmIntrinsics::_linkToStatic)                           \
  declare_constant(vmIntrinsics::_linkToSpecial)                          \
  declare_constant(vmIntrinsics::_linkToInterface)                        \
  declare_constant(vmIntrinsics::_linkToNative)                           \
                                                                          \
  /********************************/                                      \
  /* Calling convention constants */                                      \
  /********************************/                                      \
                                                                          \
  declare_constant(ConcreteRegisterImpl::number_of_registers)             \
  declare_preprocessor_constant("REG_COUNT", REG_COUNT)                   \
  COMPILER2_PRESENT(declare_preprocessor_constant("SAVED_ON_ENTRY_REG_COUNT", SAVED_ON_ENTRY_REG_COUNT)) \
  COMPILER2_PRESENT(declare_preprocessor_constant("C_SAVED_ON_ENTRY_REG_COUNT", C_SAVED_ON_ENTRY_REG_COUNT)) \
                                                                          \
  /************/                                                          \
  /* PerfData */                                                          \
  /************/                                                          \
                                                                          \
  /***********************/                                               \
  /* PerfData Units enum */                                               \
  /***********************/                                               \
                                                                          \
  declare_constant(PerfData::U_None)                                      \
  declare_constant(PerfData::U_Bytes)                                     \
  declare_constant(PerfData::U_Ticks)                                     \
  declare_constant(PerfData::U_Events)                                    \
  declare_constant(PerfData::U_String)                                    \
  declare_constant(PerfData::U_Hertz)                                     \
                                                                          \
  /****************/                                                      \
  /* JVMCI */                                                             \
  /****************/                                                      \
                                                                          \
  declare_preprocessor_constant("INCLUDE_JVMCI", INCLUDE_JVMCI)           \
                                                                          \
  /****************/                                                      \
  /*  VMRegImpl   */                                                      \
  /****************/                                                      \
  declare_constant(VMRegImpl::stack_slot_size)                            \
                                                                          \
  /******************************/                                        \
  /*  -XX flags (value origin)  */                                        \
  /******************************/                                        \
  declare_constant(JVMFlagOrigin::DEFAULT)                                \
  declare_constant(JVMFlagOrigin::COMMAND_LINE)                           \
  declare_constant(JVMFlagOrigin::ENVIRON_VAR)                            \
  declare_constant(JVMFlagOrigin::CONFIG_FILE)                            \
  declare_constant(JVMFlagOrigin::MANAGEMENT)                             \
  declare_constant(JVMFlagOrigin::ERGONOMIC)                              \
  declare_constant(JVMFlagOrigin::ATTACH_ON_DEMAND)                       \
  declare_constant(JVMFlagOrigin::INTERNAL)                               \
  declare_constant(JVMFlagOrigin::JIMAGE_RESOURCE)                        \
  declare_constant(JVMFlag::VALUE_ORIGIN_MASK)                            \
  declare_constant(JVMFlag::WAS_SET_ON_COMMAND_LINE)

//--------------------------------------------------------------------------------
// VM_LONG_CONSTANTS
//
// This table contains long constants required over in the
// serviceability agent. The "declare_constant" macro is used for all
// enums, etc., while "declare_preprocessor_constant" must be used for
// all #defined constants.

#define VM_LONG_CONSTANTS(declare_constant, declare_preprocessor_constant) \
                                                                          \
  /****************/                                                      \
  /* GC constants */                                                      \
  /****************/                                                      \
                                                                          \
  VM_LONG_CONSTANTS_GC(declare_constant)                                  \
                                                                          \
  /*********************/                                                 \
  /* markWord constants */                                                \
  /*********************/                                                 \
                                                                          \
  /* Note: some of these are declared as long constants just for */       \
  /* consistency. The mask constants are the only ones requiring */       \
  /* 64 bits (on 64-bit platforms). */                                    \
                                                                          \
  declare_constant(markWord::age_bits)                                    \
  declare_constant(markWord::lock_bits)                                   \
  declare_constant(markWord::max_hash_bits)                               \
  declare_constant(markWord::hash_bits)                                   \
                                                                          \
  declare_constant(markWord::lock_shift)                                  \
  declare_constant(markWord::age_shift)                                   \
  declare_constant(markWord::hash_shift)                                  \
  LP64_ONLY(declare_constant(markWord::klass_shift))                      \
                                                                          \
  declare_constant(markWord::lock_mask)                                   \
  declare_constant(markWord::lock_mask_in_place)                          \
  declare_constant(markWord::age_mask)                                    \
  declare_constant(markWord::age_mask_in_place)                           \
  declare_constant(markWord::hash_mask)                                   \
  declare_constant(markWord::hash_mask_in_place)                          \
                                                                          \
  declare_constant(markWord::locked_value)                                \
  declare_constant(markWord::unlocked_value)                              \
  declare_constant(markWord::monitor_value)                               \
  declare_constant(markWord::marked_value)                                \
                                                                          \
  declare_constant(markWord::no_hash)                                     \
  declare_constant(markWord::no_hash_in_place)                            \
  declare_constant(markWord::no_lock_in_place)                            \
  declare_constant(markWord::max_age)                                     \
                                                                          \
  /* InvocationCounter constants */                                       \
  declare_constant(InvocationCounter::count_increment)                    \
  declare_constant(InvocationCounter::count_shift)                        \
                                                                          \
  /* ObjectMonitor constants */                                           \
  declare_constant(ObjectMonitor::NO_OWNER)                               \
  declare_constant(ObjectMonitor::ANONYMOUS_OWNER)                        \
  declare_constant(ObjectMonitor::DEFLATER_MARKER)                        \

//--------------------------------------------------------------------------------
//

// Generate and check a nonstatic field in non-product builds
#ifndef PRODUCT
# define GENERATE_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY(a, b, c) GENERATE_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define CHECK_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)    CHECK_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define ENSURE_NONPRODUCT_FIELD_TYPE_PRESENT(a, b, c)          ENSURE_FIELD_TYPE_PRESENT(a, b, c)
#else
# define GENERATE_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define CHECK_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define ENSURE_NONPRODUCT_FIELD_TYPE_PRESENT(a, b, c)
#endif /* PRODUCT */

//
// Instantiation of VMStructEntries, VMTypeEntries and VMIntConstantEntries
//

// These initializers are allowed to access private fields in classes
// as long as class VMStructs is a friend
VMStructEntry VMStructs::localHotSpotVMStructs[] = {

  VM_STRUCTS(GENERATE_NONSTATIC_VM_STRUCT_ENTRY,
             GENERATE_STATIC_VM_STRUCT_ENTRY,
             GENERATE_VOLATILE_STATIC_VM_STRUCT_ENTRY,
             GENERATE_UNCHECKED_NONSTATIC_VM_STRUCT_ENTRY,
             GENERATE_NONSTATIC_VM_STRUCT_ENTRY,
             GENERATE_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY)


  VM_STRUCTS_OS(GENERATE_NONSTATIC_VM_STRUCT_ENTRY,
                GENERATE_STATIC_VM_STRUCT_ENTRY,
                GENERATE_UNCHECKED_NONSTATIC_VM_STRUCT_ENTRY,
                GENERATE_NONSTATIC_VM_STRUCT_ENTRY,
                GENERATE_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY)

  VM_STRUCTS_CPU(GENERATE_NONSTATIC_VM_STRUCT_ENTRY,
                 GENERATE_STATIC_VM_STRUCT_ENTRY,
                 GENERATE_UNCHECKED_NONSTATIC_VM_STRUCT_ENTRY,
                 GENERATE_NONSTATIC_VM_STRUCT_ENTRY,
                 GENERATE_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY)

  GENERATE_VM_STRUCT_LAST_ENTRY()
};

size_t VMStructs::localHotSpotVMStructsLength() {
  return sizeof(localHotSpotVMStructs) / sizeof(VMStructEntry);
}

VMTypeEntry VMStructs::localHotSpotVMTypes[] = {

  VM_TYPES(GENERATE_VM_TYPE_ENTRY,
           GENERATE_TOPLEVEL_VM_TYPE_ENTRY,
           GENERATE_OOP_VM_TYPE_ENTRY,
           GENERATE_INTEGER_VM_TYPE_ENTRY,
           GENERATE_UNSIGNED_INTEGER_VM_TYPE_ENTRY)

  VM_TYPES_OS(GENERATE_VM_TYPE_ENTRY,
              GENERATE_TOPLEVEL_VM_TYPE_ENTRY,
              GENERATE_OOP_VM_TYPE_ENTRY,
              GENERATE_INTEGER_VM_TYPE_ENTRY,
              GENERATE_UNSIGNED_INTEGER_VM_TYPE_ENTRY)

  VM_TYPES_CPU(GENERATE_VM_TYPE_ENTRY,
               GENERATE_TOPLEVEL_VM_TYPE_ENTRY,
               GENERATE_OOP_VM_TYPE_ENTRY,
               GENERATE_INTEGER_VM_TYPE_ENTRY,
               GENERATE_UNSIGNED_INTEGER_VM_TYPE_ENTRY)

  GENERATE_VM_TYPE_LAST_ENTRY()
};

size_t VMStructs::localHotSpotVMTypesLength() {
  return sizeof(localHotSpotVMTypes) / sizeof(VMTypeEntry);
}

VMIntConstantEntry VMStructs::localHotSpotVMIntConstants[] = {

  VM_INT_CONSTANTS(GENERATE_VM_INT_CONSTANT_ENTRY,
                   GENERATE_VM_INT_CONSTANT_WITH_VALUE_ENTRY,
                   GENERATE_PREPROCESSOR_VM_INT_CONSTANT_ENTRY)

  VM_INT_CONSTANTS_OS(GENERATE_VM_INT_CONSTANT_ENTRY,
                      GENERATE_PREPROCESSOR_VM_INT_CONSTANT_ENTRY)

  VM_INT_CONSTANTS_CPU(GENERATE_VM_INT_CONSTANT_ENTRY,
                       GENERATE_PREPROCESSOR_VM_INT_CONSTANT_ENTRY)

#ifdef VM_INT_CPU_FEATURE_CONSTANTS
  VM_INT_CPU_FEATURE_CONSTANTS
#endif

  GENERATE_VM_INT_CONSTANT_LAST_ENTRY()
};

size_t VMStructs::localHotSpotVMIntConstantsLength() {
  return sizeof(localHotSpotVMIntConstants) / sizeof(VMIntConstantEntry);
}

VMLongConstantEntry VMStructs::localHotSpotVMLongConstants[] = {

  VM_LONG_CONSTANTS(GENERATE_VM_LONG_CONSTANT_ENTRY,
                    GENERATE_PREPROCESSOR_VM_LONG_CONSTANT_ENTRY)

  VM_LONG_CONSTANTS_OS(GENERATE_VM_LONG_CONSTANT_ENTRY,
                       GENERATE_PREPROCESSOR_VM_LONG_CONSTANT_ENTRY)

  VM_LONG_CONSTANTS_CPU(GENERATE_VM_LONG_CONSTANT_ENTRY,
                        GENERATE_PREPROCESSOR_VM_LONG_CONSTANT_ENTRY)

#ifdef VM_LONG_CPU_FEATURE_CONSTANTS
  VM_LONG_CPU_FEATURE_CONSTANTS
#endif

  GENERATE_VM_LONG_CONSTANT_LAST_ENTRY()
};

size_t VMStructs::localHotSpotVMLongConstantsLength() {
  return sizeof(localHotSpotVMLongConstants) / sizeof(VMLongConstantEntry);
}

extern "C" {

#define STRIDE(array) ((char*)&array[1] - (char*)&array[0])

JNIEXPORT VMStructEntry* gHotSpotVMStructs = VMStructs::localHotSpotVMStructs;
JNIEXPORT uint64_t gHotSpotVMStructEntryTypeNameOffset = offset_of(VMStructEntry, typeName);
JNIEXPORT uint64_t gHotSpotVMStructEntryFieldNameOffset = offset_of(VMStructEntry, fieldName);
JNIEXPORT uint64_t gHotSpotVMStructEntryTypeStringOffset = offset_of(VMStructEntry, typeString);
JNIEXPORT uint64_t gHotSpotVMStructEntryIsStaticOffset = offset_of(VMStructEntry, isStatic);
JNIEXPORT uint64_t gHotSpotVMStructEntryOffsetOffset = offset_of(VMStructEntry, offset);
JNIEXPORT uint64_t gHotSpotVMStructEntryAddressOffset = offset_of(VMStructEntry, address);
JNIEXPORT uint64_t gHotSpotVMStructEntryArrayStride = STRIDE(gHotSpotVMStructs);

JNIEXPORT VMTypeEntry* gHotSpotVMTypes = VMStructs::localHotSpotVMTypes;
JNIEXPORT uint64_t gHotSpotVMTypeEntryTypeNameOffset = offset_of(VMTypeEntry, typeName);
JNIEXPORT uint64_t gHotSpotVMTypeEntrySuperclassNameOffset = offset_of(VMTypeEntry, superclassName);
JNIEXPORT uint64_t gHotSpotVMTypeEntryIsOopTypeOffset = offset_of(VMTypeEntry, isOopType);
JNIEXPORT uint64_t gHotSpotVMTypeEntryIsIntegerTypeOffset = offset_of(VMTypeEntry, isIntegerType);
JNIEXPORT uint64_t gHotSpotVMTypeEntryIsUnsignedOffset = offset_of(VMTypeEntry, isUnsigned);
JNIEXPORT uint64_t gHotSpotVMTypeEntrySizeOffset = offset_of(VMTypeEntry, size);
JNIEXPORT uint64_t gHotSpotVMTypeEntryArrayStride = STRIDE(gHotSpotVMTypes);

JNIEXPORT VMIntConstantEntry* gHotSpotVMIntConstants = VMStructs::localHotSpotVMIntConstants;
JNIEXPORT uint64_t gHotSpotVMIntConstantEntryNameOffset = offset_of(VMIntConstantEntry, name);
JNIEXPORT uint64_t gHotSpotVMIntConstantEntryValueOffset = offset_of(VMIntConstantEntry, value);
JNIEXPORT uint64_t gHotSpotVMIntConstantEntryArrayStride = STRIDE(gHotSpotVMIntConstants);

JNIEXPORT VMLongConstantEntry* gHotSpotVMLongConstants = VMStructs::localHotSpotVMLongConstants;
JNIEXPORT uint64_t gHotSpotVMLongConstantEntryNameOffset = offset_of(VMLongConstantEntry, name);
JNIEXPORT uint64_t gHotSpotVMLongConstantEntryValueOffset = offset_of(VMLongConstantEntry, value);
JNIEXPORT uint64_t gHotSpotVMLongConstantEntryArrayStride = STRIDE(gHotSpotVMLongConstants);
} // "C"

#ifdef ASSERT
// This is used both to check the types of referenced fields and
// to ensure that all of the field types are present.
void VMStructs::init() {
  VM_STRUCTS(CHECK_NONSTATIC_VM_STRUCT_ENTRY,
             CHECK_STATIC_VM_STRUCT_ENTRY,
             CHECK_VOLATILE_STATIC_VM_STRUCT_ENTRY,
             CHECK_NO_OP,
             CHECK_VOLATILE_NONSTATIC_VM_STRUCT_ENTRY,
             CHECK_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY)

  VM_STRUCTS_CPU(CHECK_NONSTATIC_VM_STRUCT_ENTRY,
                 CHECK_STATIC_VM_STRUCT_ENTRY,
                 CHECK_NO_OP,
                 CHECK_VOLATILE_NONSTATIC_VM_STRUCT_ENTRY,
                 CHECK_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY)

  VM_TYPES(CHECK_VM_TYPE_ENTRY,
           CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
           CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
           CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
           CHECK_SINGLE_ARG_VM_TYPE_NO_OP)


  VM_TYPES_CPU(CHECK_VM_TYPE_ENTRY,
               CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
               CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
               CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
               CHECK_SINGLE_ARG_VM_TYPE_NO_OP)

  //
  // Split VM_STRUCTS() invocation into two parts to allow MS VC++ 6.0
  // to build with the source mounted over SNC3.2. Symptom was that
  // debug build failed with an internal compiler error. Has been seen
  // mounting sources from Solaris 2.6 and 2.7 hosts, but so far not
  // 2.8 hosts. Appears to occur because line is too long.
  //
  // If an assertion failure is triggered here it means that an entry
  // in VMStructs::localHotSpotVMStructs[] was not found in
  // VMStructs::localHotSpotVMTypes[]. (The assertion itself had to be
  // made less descriptive because of this above bug -- see the
  // definition of ENSURE_FIELD_TYPE_PRESENT.)
  //
  // NOTE: taken out because this was just not working on everyone's
  // Solstice NFS setup. If everyone switches to local workspaces on
  // Win32, we can put this back in.
#ifndef _WINDOWS
  VM_STRUCTS(ENSURE_FIELD_TYPE_PRESENT,
             CHECK_NO_OP,
             CHECK_NO_OP,
             CHECK_NO_OP,
             CHECK_NO_OP,
             CHECK_NO_OP)

  VM_STRUCTS(CHECK_NO_OP,
             ENSURE_FIELD_TYPE_PRESENT,
             ENSURE_FIELD_TYPE_PRESENT,
             CHECK_NO_OP,
             ENSURE_FIELD_TYPE_PRESENT,
             ENSURE_NONPRODUCT_FIELD_TYPE_PRESENT)

  VM_STRUCTS_CPU(ENSURE_FIELD_TYPE_PRESENT,
                 ENSURE_FIELD_TYPE_PRESENT,
                 CHECK_NO_OP,
                 ENSURE_FIELD_TYPE_PRESENT,
                 ENSURE_NONPRODUCT_FIELD_TYPE_PRESENT)
#endif // !_WINDOWS
}

static int recursiveFindType(VMTypeEntry* origtypes, const char* typeName, bool isRecurse) {
  {
    VMTypeEntry* types = origtypes;
    while (types->typeName != nullptr) {
      if (strcmp(typeName, types->typeName) == 0) {
        // Found it
        return 1;
      }
      ++types;
    }
  }
  // Search for the base type by peeling off const and *
  size_t len = strlen(typeName);
  if (typeName[len-1] == '*') {
    char * s = NEW_C_HEAP_ARRAY(char, len, mtInternal);
    strncpy(s, typeName, len - 1);
    s[len-1] = '\0';
    // tty->print_cr("checking \"%s\" for \"%s\"", s, typeName);
    if (recursiveFindType(origtypes, s, true) == 1) {
      FREE_C_HEAP_ARRAY(char, s);
      return 1;
    }
    FREE_C_HEAP_ARRAY(char, s);
  }
  const char* start = nullptr;
  if (strstr(typeName, "GrowableArray<") == typeName) {
    start = typeName + strlen("GrowableArray<");
  } else if (strstr(typeName, "Array<") == typeName) {
    start = typeName + strlen("Array<");
  }
  if (start != nullptr) {
    const char * end = strrchr(typeName, '>');
    int len = pointer_delta_as_int(end, start) + 1;
    char * s = NEW_C_HEAP_ARRAY(char, len, mtInternal);
    strncpy(s, start, len - 1);
    s[len-1] = '\0';
    // tty->print_cr("checking \"%s\" for \"%s\"", s, typeName);
    if (recursiveFindType(origtypes, s, true) == 1) {
      FREE_C_HEAP_ARRAY(char, s);
      return 1;
    }
    FREE_C_HEAP_ARRAY(char, s);
  }
  if (strstr(typeName, "const ") == typeName) {
    const char * s = typeName + strlen("const ");
    // tty->print_cr("checking \"%s\" for \"%s\"", s, typeName);
    if (recursiveFindType(origtypes, s, true) == 1) {
      return 1;
    }
  }
  if (strstr(typeName, " const") == typeName + len - 6) {
    char * s = os::strdup_check_oom(typeName);
    s[len - 6] = '\0';
    // tty->print_cr("checking \"%s\" for \"%s\"", s, typeName);
    if (recursiveFindType(origtypes, s, true) == 1) {
      os::free(s);
      return 1;
    }
    os::free(s);
  }
  if (!isRecurse) {
    tty->print_cr("type \"%s\" not found", typeName);
  }
  return 0;
}

int VMStructs::findType(const char* typeName) {
  VMTypeEntry* types = gHotSpotVMTypes;

  return recursiveFindType(types, typeName, false);
}

void vmStructs_init() {
  VMStructs::init();
}
#endif // ASSERT

