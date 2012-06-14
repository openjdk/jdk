/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/dictionary.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/loaderConstraints.hpp"
#include "classfile/placeholders.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "ci/ciField.hpp"
#include "ci/ciInstance.hpp"
#include "ci/ciObjArrayKlass.hpp"
#include "ci/ciMethodData.hpp"
#include "ci/ciSymbol.hpp"
#include "code/codeBlob.hpp"
#include "code/codeCache.hpp"
#include "code/compressedStream.hpp"
#include "code/location.hpp"
#include "code/nmethod.hpp"
#include "code/pcDesc.hpp"
#include "code/stubs.hpp"
#include "code/vmreg.hpp"
#include "compiler/oopMap.hpp"
#include "compiler/compileBroker.hpp"
#include "gc_implementation/shared/immutableSpace.hpp"
#include "gc_implementation/shared/markSweep.hpp"
#include "gc_implementation/shared/mutableSpace.hpp"
#include "gc_interface/collectedHeap.hpp"
#include "interpreter/bytecodeInterpreter.hpp"
#include "interpreter/bytecodes.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/cardTableRS.hpp"
#include "memory/compactPermGen.hpp"
#include "memory/defNewGeneration.hpp"
#include "memory/freeBlockDictionary.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/generation.hpp"
#include "memory/generationSpec.hpp"
#include "memory/heap.hpp"
#include "memory/permGen.hpp"
#include "memory/space.hpp"
#include "memory/tenuredGeneration.hpp"
#include "memory/universe.hpp"
#include "memory/watermark.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/arrayKlassKlass.hpp"
#include "oops/arrayOop.hpp"
#include "oops/compiledICHolderKlass.hpp"
#include "oops/compiledICHolderOop.hpp"
#include "oops/constMethodKlass.hpp"
#include "oops/constMethodOop.hpp"
#include "oops/constantPoolKlass.hpp"
#include "oops/constantPoolOop.hpp"
#include "oops/cpCacheKlass.hpp"
#include "oops/cpCacheOop.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/instanceKlassKlass.hpp"
#include "oops/instanceOop.hpp"
#include "oops/klass.hpp"
#include "oops/klassOop.hpp"
#include "oops/markOop.hpp"
#include "oops/methodDataKlass.hpp"
#include "oops/methodDataOop.hpp"
#include "oops/methodKlass.hpp"
#include "oops/methodOop.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayKlassKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "oops/typeArrayKlass.hpp"
#include "oops/typeArrayKlassKlass.hpp"
#include "oops/typeArrayOop.hpp"
#include "prims/jvmtiAgentThread.hpp"
#include "runtime/arguments.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/vframeArray.hpp"
#include "runtime/globals.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/perfMemory.hpp"
#include "runtime/serviceThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/virtualspace.hpp"
#include "runtime/vmStructs.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/hashtable.hpp"
#ifdef TARGET_ARCH_x86
# include "vmStructs_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "vmStructs_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "vmStructs_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "vmStructs_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "vmStructs_ppc.hpp"
#endif
#ifdef TARGET_OS_FAMILY_linux
# include "thread_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "thread_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "thread_windows.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "thread_bsd.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_x86
# include "vmStructs_linux_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_sparc
# include "vmStructs_linux_sparc.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_zero
# include "vmStructs_linux_zero.hpp"
#endif
#ifdef TARGET_OS_ARCH_solaris_x86
# include "vmStructs_solaris_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_solaris_sparc
# include "vmStructs_solaris_sparc.hpp"
#endif
#ifdef TARGET_OS_ARCH_windows_x86
# include "vmStructs_windows_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_arm
# include "vmStructs_linux_arm.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_ppc
# include "vmStructs_linux_ppc.hpp"
#endif
#ifdef TARGET_OS_ARCH_bsd_x86
# include "vmStructs_bsd_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_bsd_zero
# include "vmStructs_bsd_zero.hpp"
#endif
#ifndef SERIALGC
#include "gc_implementation/concurrentMarkSweep/cmsPermGen.hpp"
#include "gc_implementation/concurrentMarkSweep/compactibleFreeListSpace.hpp"
#include "gc_implementation/concurrentMarkSweep/concurrentMarkSweepGeneration.hpp"
#include "gc_implementation/concurrentMarkSweep/concurrentMarkSweepThread.hpp"
#include "gc_implementation/concurrentMarkSweep/vmStructs_cms.hpp"
#include "gc_implementation/parNew/parNewGeneration.hpp"
#include "gc_implementation/parNew/vmStructs_parNew.hpp"
#include "gc_implementation/parallelScavenge/asPSOldGen.hpp"
#include "gc_implementation/parallelScavenge/asPSYoungGen.hpp"
#include "gc_implementation/parallelScavenge/parallelScavengeHeap.hpp"
#include "gc_implementation/parallelScavenge/psOldGen.hpp"
#include "gc_implementation/parallelScavenge/psPermGen.hpp"
#include "gc_implementation/parallelScavenge/psVirtualspace.hpp"
#include "gc_implementation/parallelScavenge/psYoungGen.hpp"
#include "gc_implementation/parallelScavenge/vmStructs_parallelgc.hpp"
#include "gc_implementation/g1/vmStructs_g1.hpp"
#endif
#ifdef COMPILER2
#include "opto/addnode.hpp"
#include "opto/block.hpp"
#include "opto/callnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/chaitin.hpp"
#include "opto/divnode.hpp"
#include "opto/locknode.hpp"
#include "opto/loopnode.hpp"
#include "opto/machnode.hpp"
#include "opto/matcher.hpp"
#include "opto/mulnode.hpp"
#include "opto/phaseX.hpp"
#include "opto/parse.hpp"
#include "opto/regalloc.hpp"
#include "opto/rootnode.hpp"
#include "opto/subnode.hpp"
#include "opto/vectornode.hpp"
#ifdef TARGET_ARCH_MODEL_x86_32
# include "adfiles/adGlobals_x86_32.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_x86_64
# include "adfiles/adGlobals_x86_64.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_sparc
# include "adfiles/adGlobals_sparc.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_zero
# include "adfiles/adGlobals_zero.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_arm
# include "adfiles/adGlobals_arm.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_ppc
# include "adfiles/adGlobals_ppc.hpp"
#endif
#endif

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

// whole purpose of this function is to work around bug c++/27724 in gcc 4.1.1
// with optimization turned on it doesn't affect produced code
static inline uint64_t cast_uint64_t(size_t x)
{
  return x;
}


//--------------------------------------------------------------------------------
// VM_STRUCTS
//
// This list enumerates all of the fields the serviceability agent
// needs to know about. Be sure to see also the type table below this one.
// NOTE that there are platform-specific additions to this table in
// vmStructs_<os>_<cpu>.hpp.

#define VM_STRUCTS(nonstatic_field, \
                   static_field, \
                   unchecked_nonstatic_field, \
                   volatile_nonstatic_field, \
                   nonproduct_nonstatic_field, \
                   c1_nonstatic_field, \
                   c2_nonstatic_field, \
                   unchecked_c1_static_field, \
                   unchecked_c2_static_field, \
                   last_entry) \
                                                                                                                                     \
  /******************************************************************/                                                               \
  /* OopDesc and Klass hierarchies (NOTE: methodDataOop incomplete) */                                                               \
  /******************************************************************/                                                               \
                                                                                                                                     \
  volatile_nonstatic_field(oopDesc,            _mark,                                         markOop)                               \
  volatile_nonstatic_field(oopDesc,            _metadata._klass,                              wideKlassOop)                          \
  volatile_nonstatic_field(oopDesc,            _metadata._compressed_klass,                   narrowOop)                             \
     static_field(oopDesc,                     _bs,                                           BarrierSet*)                           \
  nonstatic_field(arrayKlass,                  _dimension,                                    int)                                   \
  volatile_nonstatic_field(arrayKlass,         _higher_dimension,                             klassOop)                              \
  volatile_nonstatic_field(arrayKlass,         _lower_dimension,                              klassOop)                              \
  nonstatic_field(arrayKlass,                  _vtable_len,                                   int)                                   \
  nonstatic_field(arrayKlass,                  _alloc_size,                                   juint)                                 \
  nonstatic_field(arrayKlass,                  _component_mirror,                             oop)                                   \
  nonstatic_field(compiledICHolderKlass,       _alloc_size,                                   juint)                                 \
  nonstatic_field(compiledICHolderOopDesc,     _holder_method,                                methodOop)                             \
  nonstatic_field(compiledICHolderOopDesc,     _holder_klass,                                 klassOop)                              \
  nonstatic_field(constantPoolOopDesc,         _tags,                                         typeArrayOop)                          \
  nonstatic_field(constantPoolOopDesc,         _cache,                                        constantPoolCacheOop)                  \
  nonstatic_field(constantPoolOopDesc,         _pool_holder,                                  klassOop)                              \
  nonstatic_field(constantPoolOopDesc,         _operands,                                     typeArrayOop)                          \
  nonstatic_field(constantPoolOopDesc,         _length,                                       int)                                   \
  nonstatic_field(constantPoolCacheOopDesc,    _length,                                       int)                                   \
  nonstatic_field(constantPoolCacheOopDesc,    _constant_pool,                                constantPoolOop)                       \
  nonstatic_field(instanceKlass,               _array_klasses,                                klassOop)                              \
  nonstatic_field(instanceKlass,               _methods,                                      objArrayOop)                           \
  nonstatic_field(instanceKlass,               _method_ordering,                              typeArrayOop)                          \
  nonstatic_field(instanceKlass,               _local_interfaces,                             objArrayOop)                           \
  nonstatic_field(instanceKlass,               _transitive_interfaces,                        objArrayOop)                           \
  nonstatic_field(instanceKlass,               _fields,                                       typeArrayOop)                          \
  nonstatic_field(instanceKlass,               _java_fields_count,                            u2)                                    \
  nonstatic_field(instanceKlass,               _constants,                                    constantPoolOop)                       \
  nonstatic_field(instanceKlass,               _class_loader,                                 oop)                                   \
  nonstatic_field(instanceKlass,               _protection_domain,                            oop)                                   \
  nonstatic_field(instanceKlass,               _signers,                                      objArrayOop)                           \
  nonstatic_field(instanceKlass,               _source_file_name,                             Symbol*)                               \
  nonstatic_field(instanceKlass,               _source_debug_extension,                       Symbol*)                               \
  nonstatic_field(instanceKlass,               _inner_classes,                                typeArrayOop)                          \
  nonstatic_field(instanceKlass,               _nonstatic_field_size,                         int)                                   \
  nonstatic_field(instanceKlass,               _static_field_size,                            int)                                   \
  nonstatic_field(instanceKlass,               _static_oop_field_count,                       u2)                                   \
  nonstatic_field(instanceKlass,               _nonstatic_oop_map_size,                       int)                                   \
  nonstatic_field(instanceKlass,               _is_marked_dependent,                          bool)                                  \
  nonstatic_field(instanceKlass,               _minor_version,                                u2)                                    \
  nonstatic_field(instanceKlass,               _major_version,                                u2)                                    \
  nonstatic_field(instanceKlass,               _init_state,                                   u1)                                    \
  nonstatic_field(instanceKlass,               _init_thread,                                  Thread*)                               \
  nonstatic_field(instanceKlass,               _vtable_len,                                   int)                                   \
  nonstatic_field(instanceKlass,               _itable_len,                                   int)                                   \
  nonstatic_field(instanceKlass,               _reference_type,                               u1)                                    \
  volatile_nonstatic_field(instanceKlass,      _oop_map_cache,                                OopMapCache*)                          \
  nonstatic_field(instanceKlass,               _jni_ids,                                      JNIid*)                                \
  nonstatic_field(instanceKlass,               _osr_nmethods_head,                            nmethod*)                              \
  nonstatic_field(instanceKlass,               _breakpoints,                                  BreakpointInfo*)                       \
  nonstatic_field(instanceKlass,               _generic_signature,                            Symbol*)                               \
  nonstatic_field(instanceKlass,               _methods_jmethod_ids,                          jmethodID*)                            \
  nonstatic_field(instanceKlass,               _methods_cached_itable_indices,                int*)                                  \
  volatile_nonstatic_field(instanceKlass,      _idnum_allocated_count,                        u2)                                    \
  nonstatic_field(instanceKlass,               _class_annotations,                            typeArrayOop)                          \
  nonstatic_field(instanceKlass,               _fields_annotations,                           objArrayOop)                           \
  nonstatic_field(instanceKlass,               _methods_annotations,                          objArrayOop)                           \
  nonstatic_field(instanceKlass,               _methods_parameter_annotations,                objArrayOop)                           \
  nonstatic_field(instanceKlass,               _methods_default_annotations,                  objArrayOop)                           \
  nonstatic_field(instanceKlass,               _dependencies,                                 nmethodBucket*)                        \
  nonstatic_field(nmethodBucket,               _nmethod,                                      nmethod*)                              \
  nonstatic_field(nmethodBucket,               _count,                                        int)                                   \
  nonstatic_field(nmethodBucket,               _next,                                         nmethodBucket*)                        \
  nonstatic_field(Klass,                       _super_check_offset,                           juint)                                 \
  nonstatic_field(Klass,                       _secondary_super_cache,                        klassOop)                              \
  nonstatic_field(Klass,                       _secondary_supers,                             objArrayOop)                           \
  nonstatic_field(Klass,                       _primary_supers[0],                            klassOop)                              \
  nonstatic_field(Klass,                       _java_mirror,                                  oop)                                   \
  nonstatic_field(Klass,                       _modifier_flags,                               jint)                                  \
  nonstatic_field(Klass,                       _super,                                        klassOop)                              \
  nonstatic_field(Klass,                       _layout_helper,                                jint)                                  \
  nonstatic_field(Klass,                       _name,                                         Symbol*)                               \
  nonstatic_field(Klass,                       _access_flags,                                 AccessFlags)                           \
  nonstatic_field(Klass,                       _subklass,                                     klassOop)                              \
  nonstatic_field(Klass,                       _next_sibling,                                 klassOop)                              \
  nonproduct_nonstatic_field(Klass,            _verify_count,                                 int)                                   \
  nonstatic_field(Klass,                       _alloc_count,                                  juint)                                 \
  nonstatic_field(klassKlass,                  _alloc_size,                                   juint)                                 \
  nonstatic_field(methodKlass,                 _alloc_size,                                   juint)                                 \
  nonstatic_field(methodDataOopDesc,           _size,                                         int)                                   \
  nonstatic_field(methodDataOopDesc,           _method,                                       methodOop)                             \
  nonstatic_field(methodDataOopDesc,           _data_size,                                    int)                                   \
  nonstatic_field(methodDataOopDesc,           _data[0],                                      intptr_t)                              \
  nonstatic_field(methodDataOopDesc,           _nof_decompiles,                               uint)                                  \
  nonstatic_field(methodDataOopDesc,           _nof_overflow_recompiles,                      uint)                                  \
  nonstatic_field(methodDataOopDesc,           _nof_overflow_traps,                           uint)                                  \
  nonstatic_field(methodDataOopDesc,           _eflags,                                       intx)                                  \
  nonstatic_field(methodDataOopDesc,           _arg_local,                                    intx)                                  \
  nonstatic_field(methodDataOopDesc,           _arg_stack,                                    intx)                                  \
  nonstatic_field(methodDataOopDesc,           _arg_returned,                                 intx)                                  \
  nonstatic_field(methodOopDesc,               _constMethod,                                  constMethodOop)                        \
  nonstatic_field(methodOopDesc,               _method_data,                                  methodDataOop)                         \
  nonstatic_field(methodOopDesc,               _interpreter_invocation_count,                 int)                                   \
  nonstatic_field(methodOopDesc,               _access_flags,                                 AccessFlags)                           \
  nonstatic_field(methodOopDesc,               _vtable_index,                                 int)                                   \
  nonstatic_field(methodOopDesc,               _method_size,                                  u2)                                    \
  nonstatic_field(methodOopDesc,               _max_stack,                                    u2)                                    \
  nonstatic_field(methodOopDesc,               _max_locals,                                   u2)                                    \
  nonstatic_field(methodOopDesc,               _size_of_parameters,                           u2)                                    \
  nonstatic_field(methodOopDesc,               _interpreter_throwout_count,                   u2)                                    \
  nonstatic_field(methodOopDesc,               _number_of_breakpoints,                        u2)                                    \
  nonstatic_field(methodOopDesc,               _invocation_counter,                           InvocationCounter)                     \
  nonstatic_field(methodOopDesc,               _backedge_counter,                             InvocationCounter)                     \
  nonproduct_nonstatic_field(methodOopDesc,    _compiled_invocation_count,                    int)                                   \
  volatile_nonstatic_field(methodOopDesc,      _code,                                         nmethod*)                              \
  nonstatic_field(methodOopDesc,               _i2i_entry,                                    address)                               \
  nonstatic_field(methodOopDesc,               _adapter,                                      AdapterHandlerEntry*)                  \
  volatile_nonstatic_field(methodOopDesc,      _from_compiled_entry,                          address)                               \
  volatile_nonstatic_field(methodOopDesc,      _from_interpreted_entry,                       address)                               \
  volatile_nonstatic_field(constMethodOopDesc, _fingerprint,                                  uint64_t)                              \
  nonstatic_field(constMethodOopDesc,          _constants,                                    constantPoolOop)                       \
  nonstatic_field(constMethodOopDesc,          _stackmap_data,                                typeArrayOop)                          \
  nonstatic_field(constMethodOopDesc,          _exception_table,                              typeArrayOop)                          \
  nonstatic_field(constMethodOopDesc,          _constMethod_size,                             int)                                   \
  nonstatic_field(constMethodOopDesc,          _interpreter_kind,                             jbyte)                                 \
  nonstatic_field(constMethodOopDesc,          _flags,                                        jbyte)                                 \
  nonstatic_field(constMethodOopDesc,          _code_size,                                    u2)                                    \
  nonstatic_field(constMethodOopDesc,          _name_index,                                   u2)                                    \
  nonstatic_field(constMethodOopDesc,          _signature_index,                              u2)                                    \
  nonstatic_field(constMethodOopDesc,          _method_idnum,                                 u2)                                    \
  nonstatic_field(constMethodOopDesc,          _generic_signature_index,                      u2)                                    \
  nonstatic_field(objArrayKlass,               _element_klass,                                klassOop)                              \
  nonstatic_field(objArrayKlass,               _bottom_klass,                                 klassOop)                              \
  volatile_nonstatic_field(Symbol,             _refcount,                                     int)                                   \
  nonstatic_field(Symbol,                      _identity_hash,                                int)                                   \
  nonstatic_field(Symbol,                      _length,                                       unsigned short)                        \
  unchecked_nonstatic_field(Symbol,            _body,                                         sizeof(jbyte)) /* NOTE: no type */     \
  nonstatic_field(typeArrayKlass,              _max_length,                                   int)                                   \
                                                                                                                                     \
  /***********************/                                                                                                          \
  /* Constant Pool Cache */                                                                                                          \
  /***********************/                                                                                                          \
                                                                                                                                     \
  volatile_nonstatic_field(ConstantPoolCacheEntry,      _indices,                                      intx)                         \
  volatile_nonstatic_field(ConstantPoolCacheEntry,      _f1,                                           oop)                          \
  volatile_nonstatic_field(ConstantPoolCacheEntry,      _f2,                                           intx)                         \
  volatile_nonstatic_field(ConstantPoolCacheEntry,      _flags,                                        intx)                         \
                                                                                                                                     \
  /********************************/                                                                                                 \
  /* MethodOop-related structures */                                                                                                 \
  /********************************/                                                                                                 \
                                                                                                                                     \
  nonstatic_field(CheckedExceptionElement,     class_cp_index,                                u2)                                    \
  nonstatic_field(LocalVariableTableElement,   start_bci,                                     u2)                                    \
  nonstatic_field(LocalVariableTableElement,   length,                                        u2)                                    \
  nonstatic_field(LocalVariableTableElement,   name_cp_index,                                 u2)                                    \
  nonstatic_field(LocalVariableTableElement,   descriptor_cp_index,                           u2)                                    \
  nonstatic_field(LocalVariableTableElement,   signature_cp_index,                            u2)                                    \
  nonstatic_field(LocalVariableTableElement,   slot,                                          u2)                                    \
  nonstatic_field(BreakpointInfo,              _orig_bytecode,                                Bytecodes::Code)                       \
  nonstatic_field(BreakpointInfo,              _bci,                                          int)                                   \
  nonstatic_field(BreakpointInfo,              _name_index,                                   u2)                                    \
  nonstatic_field(BreakpointInfo,              _signature_index,                              u2)                                    \
  nonstatic_field(BreakpointInfo,              _next,                                         BreakpointInfo*)                       \
  /***********/                                                                                                                      \
  /* JNI IDs */                                                                                                                      \
  /***********/                                                                                                                      \
                                                                                                                                     \
  nonstatic_field(JNIid,                       _holder,                                       klassOop)                              \
  nonstatic_field(JNIid,                       _next,                                         JNIid*)                                \
  nonstatic_field(JNIid,                       _offset,                                       int)                                   \
  /************/                                                                                                                     \
  /* Universe */                                                                                                                     \
  /************/                                                                                                                     \
                                                                                                                                     \
     static_field(Universe,                    _boolArrayKlassObj,                            klassOop)                              \
     static_field(Universe,                    _byteArrayKlassObj,                            klassOop)                              \
     static_field(Universe,                    _charArrayKlassObj,                            klassOop)                              \
     static_field(Universe,                    _intArrayKlassObj,                             klassOop)                              \
     static_field(Universe,                    _shortArrayKlassObj,                           klassOop)                              \
     static_field(Universe,                    _longArrayKlassObj,                            klassOop)                              \
     static_field(Universe,                    _singleArrayKlassObj,                          klassOop)                              \
     static_field(Universe,                    _doubleArrayKlassObj,                          klassOop)                              \
     static_field(Universe,                    _methodKlassObj,                               klassOop)                              \
     static_field(Universe,                    _constMethodKlassObj,                          klassOop)                              \
     static_field(Universe,                    _methodDataKlassObj,                           klassOop)                              \
     static_field(Universe,                    _klassKlassObj,                                klassOop)                              \
     static_field(Universe,                    _arrayKlassKlassObj,                           klassOop)                              \
     static_field(Universe,                    _objArrayKlassKlassObj,                        klassOop)                              \
     static_field(Universe,                    _typeArrayKlassKlassObj,                       klassOop)                              \
     static_field(Universe,                    _instanceKlassKlassObj,                        klassOop)                              \
     static_field(Universe,                    _constantPoolKlassObj,                         klassOop)                              \
     static_field(Universe,                    _constantPoolCacheKlassObj,                    klassOop)                              \
     static_field(Universe,                    _compiledICHolderKlassObj,                     klassOop)                              \
     static_field(Universe,                    _systemObjArrayKlassObj,                       klassOop)                              \
     static_field(Universe,                    _mirrors[0],                                   oop)                                   \
     static_field(Universe,                    _main_thread_group,                            oop)                                   \
     static_field(Universe,                    _system_thread_group,                          oop)                                   \
     static_field(Universe,                    _the_empty_byte_array,                         typeArrayOop)                          \
     static_field(Universe,                    _the_empty_short_array,                        typeArrayOop)                          \
     static_field(Universe,                    _the_empty_int_array,                          typeArrayOop)                          \
     static_field(Universe,                    _the_empty_system_obj_array,                   objArrayOop)                           \
     static_field(Universe,                    _the_empty_class_klass_array,                  objArrayOop)                           \
     static_field(Universe,                    _out_of_memory_error_java_heap,                oop)                                   \
     static_field(Universe,                    _out_of_memory_error_perm_gen,                 oop)                                   \
     static_field(Universe,                    _out_of_memory_error_array_size,               oop)                                   \
     static_field(Universe,                    _out_of_memory_error_gc_overhead_limit,        oop)                                   \
     static_field(Universe,                    _null_ptr_exception_instance,                  oop)                                   \
     static_field(Universe,                    _arithmetic_exception_instance,                oop)                                   \
     static_field(Universe,                    _vm_exception,                                 oop)                                   \
     static_field(Universe,                    _collectedHeap,                                CollectedHeap*)                        \
     static_field(Universe,                    _base_vtable_size,                             int)                                   \
     static_field(Universe,                    _bootstrapping,                                bool)                                  \
     static_field(Universe,                    _fully_initialized,                            bool)                                  \
     static_field(Universe,                    _verify_count,                                 int)                                   \
     static_field(Universe,                    _narrow_oop._base,                             address)                               \
     static_field(Universe,                    _narrow_oop._shift,                            int)                                   \
     static_field(Universe,                    _narrow_oop._use_implicit_null_checks,         bool)                                  \
                                                                                                                                     \
  /**********************************************************************************/                                               \
  /* Generation and Space hierarchies                                               */                                               \
  /**********************************************************************************/                                               \
                                                                                                                                     \
  unchecked_nonstatic_field(ageTable,          sizes,                                         sizeof(ageTable::sizes))               \
                                                                                                                                     \
  nonstatic_field(BarrierSet,                  _max_covered_regions,                          int)                                   \
  nonstatic_field(BlockOffsetTable,            _bottom,                                       HeapWord*)                             \
  nonstatic_field(BlockOffsetTable,            _end,                                          HeapWord*)                             \
                                                                                                                                     \
  nonstatic_field(BlockOffsetSharedArray,      _reserved,                                     MemRegion)                             \
  nonstatic_field(BlockOffsetSharedArray,      _end,                                          HeapWord*)                             \
  nonstatic_field(BlockOffsetSharedArray,      _vs,                                           VirtualSpace)                          \
  nonstatic_field(BlockOffsetSharedArray,      _offset_array,                                 u_char*)                               \
                                                                                                                                     \
  nonstatic_field(BlockOffsetArray,            _array,                                        BlockOffsetSharedArray*)               \
  nonstatic_field(BlockOffsetArray,            _sp,                                           Space*)                                \
  nonstatic_field(BlockOffsetArrayContigSpace, _next_offset_threshold,                        HeapWord*)                             \
  nonstatic_field(BlockOffsetArrayContigSpace, _next_offset_index,                            size_t)                                \
                                                                                                                                     \
  nonstatic_field(BlockOffsetArrayNonContigSpace, _unallocated_block,                         HeapWord*)                             \
                                                                                                                                     \
  nonstatic_field(CardGeneration,              _rs,                                           GenRemSet*)                            \
  nonstatic_field(CardGeneration,              _bts,                                          BlockOffsetSharedArray*)               \
                                                                                                                                     \
  nonstatic_field(CardTableModRefBS,           _whole_heap,                                   const MemRegion)                       \
  nonstatic_field(CardTableModRefBS,           _guard_index,                                  const size_t)                          \
  nonstatic_field(CardTableModRefBS,           _last_valid_index,                             const size_t)                          \
  nonstatic_field(CardTableModRefBS,           _page_size,                                    const size_t)                          \
  nonstatic_field(CardTableModRefBS,           _byte_map_size,                                const size_t)                          \
  nonstatic_field(CardTableModRefBS,           _byte_map,                                     jbyte*)                                \
  nonstatic_field(CardTableModRefBS,           _cur_covered_regions,                          int)                                   \
  nonstatic_field(CardTableModRefBS,           _covered,                                      MemRegion*)                            \
  nonstatic_field(CardTableModRefBS,           _committed,                                    MemRegion*)                            \
  nonstatic_field(CardTableModRefBS,           _guard_region,                                 MemRegion)                             \
  nonstatic_field(CardTableModRefBS,           byte_map_base,                                 jbyte*)                                \
                                                                                                                                     \
  nonstatic_field(CardTableRS,                 _ct_bs,                                        CardTableModRefBSForCTRS*)             \
                                                                                                                                     \
  nonstatic_field(CollectedHeap,               _reserved,                                     MemRegion)                             \
  nonstatic_field(SharedHeap,                  _perm_gen,                                     PermGen*)                              \
  nonstatic_field(CollectedHeap,               _barrier_set,                                  BarrierSet*)                           \
  nonstatic_field(CollectedHeap,               _defer_initial_card_mark,                      bool)                                  \
  nonstatic_field(CollectedHeap,               _is_gc_active,                                 bool)                                  \
  nonstatic_field(CompactibleSpace,            _compaction_top,                               HeapWord*)                             \
  nonstatic_field(CompactibleSpace,            _first_dead,                                   HeapWord*)                             \
  nonstatic_field(CompactibleSpace,            _end_of_live,                                  HeapWord*)                             \
                                                                                                                                     \
  nonstatic_field(CompactingPermGen,           _gen,                                          OneContigSpaceCardGeneration*)         \
                                                                                                                                     \
  nonstatic_field(ContiguousSpace,             _top,                                          HeapWord*)                             \
  nonstatic_field(ContiguousSpace,             _concurrent_iteration_safe_limit,              HeapWord*)                             \
  nonstatic_field(ContiguousSpace,             _saved_mark_word,                              HeapWord*)                             \
                                                                                                                                     \
  nonstatic_field(DefNewGeneration,            _next_gen,                                     Generation*)                           \
  nonstatic_field(DefNewGeneration,            _tenuring_threshold,                           int)                                   \
  nonstatic_field(DefNewGeneration,            _age_table,                                    ageTable)                              \
  nonstatic_field(DefNewGeneration,            _eden_space,                                   EdenSpace*)                            \
  nonstatic_field(DefNewGeneration,            _from_space,                                   ContiguousSpace*)                      \
  nonstatic_field(DefNewGeneration,            _to_space,                                     ContiguousSpace*)                      \
                                                                                                                                     \
  nonstatic_field(EdenSpace,                   _gen,                                          DefNewGeneration*)                     \
                                                                                                                                     \
  nonstatic_field(Generation,                  _reserved,                                     MemRegion)                             \
  nonstatic_field(Generation,                  _virtual_space,                                VirtualSpace)                          \
  nonstatic_field(Generation,                  _level,                                        int)                                   \
  nonstatic_field(Generation,                  _stat_record,                                  Generation::StatRecord)                \
                                                                                                                                     \
  nonstatic_field(Generation::StatRecord,      invocations,                                   int)                                   \
  nonstatic_field(Generation::StatRecord,      accumulated_time,                              elapsedTimer)                          \
                                                                                                                                     \
  nonstatic_field(GenerationSpec,              _name,                                         Generation::Name)                      \
  nonstatic_field(GenerationSpec,              _init_size,                                    size_t)                                \
  nonstatic_field(GenerationSpec,              _max_size,                                     size_t)                                \
                                                                                                                                     \
    static_field(GenCollectedHeap,             _gch,                                          GenCollectedHeap*)                     \
 nonstatic_field(GenCollectedHeap,             _n_gens,                                       int)                                   \
 unchecked_nonstatic_field(GenCollectedHeap,   _gens,                                         sizeof(GenCollectedHeap::_gens)) /* NOTE: no type */ \
  nonstatic_field(GenCollectedHeap,            _gen_specs,                                    GenerationSpec**)                      \
                                                                                                                                     \
  nonstatic_field(HeapWord,                    i,                                             char*)                                 \
                                                                                                                                     \
  nonstatic_field(MemRegion,                   _start,                                        HeapWord*)                             \
  nonstatic_field(MemRegion,                   _word_size,                                    size_t)                                \
                                                                                                                                     \
  nonstatic_field(OffsetTableContigSpace,      _offsets,                                      BlockOffsetArray)                      \
                                                                                                                                     \
  nonstatic_field(OneContigSpaceCardGeneration, _min_heap_delta_bytes,                        size_t)                                \
  nonstatic_field(OneContigSpaceCardGeneration, _the_space,                                   ContiguousSpace*)                      \
  nonstatic_field(OneContigSpaceCardGeneration, _last_gc,                                     WaterMark)                             \
                                                                                                                                     \
  nonstatic_field(CompactingPermGenGen,        _ro_vs,                                        VirtualSpace)                          \
  nonstatic_field(CompactingPermGenGen,        _rw_vs,                                        VirtualSpace)                          \
  nonstatic_field(CompactingPermGenGen,        _md_vs,                                        VirtualSpace)                          \
  nonstatic_field(CompactingPermGenGen,        _mc_vs,                                        VirtualSpace)                          \
  nonstatic_field(CompactingPermGenGen,        _ro_space,                                     OffsetTableContigSpace*)               \
  nonstatic_field(CompactingPermGenGen,        _rw_space,                                     OffsetTableContigSpace*)               \
     static_field(CompactingPermGenGen,        unshared_bottom,                               HeapWord*)                             \
     static_field(CompactingPermGenGen,        unshared_end,                                  HeapWord*)                             \
     static_field(CompactingPermGenGen,        shared_bottom,                                 HeapWord*)                             \
     static_field(CompactingPermGenGen,        readonly_bottom,                               HeapWord*)                             \
     static_field(CompactingPermGenGen,        readonly_end,                                  HeapWord*)                             \
     static_field(CompactingPermGenGen,        readwrite_bottom,                              HeapWord*)                             \
     static_field(CompactingPermGenGen,        readwrite_end,                                 HeapWord*)                             \
     static_field(CompactingPermGenGen,        miscdata_bottom,                               HeapWord*)                             \
     static_field(CompactingPermGenGen,        miscdata_end,                                  HeapWord*)                             \
     static_field(CompactingPermGenGen,        misccode_bottom,                               HeapWord*)                             \
     static_field(CompactingPermGenGen,        misccode_end,                                  HeapWord*)                             \
     static_field(CompactingPermGenGen,        shared_end,                                    HeapWord*)                             \
                                                                                                                                     \
  nonstatic_field(PermGen,                     _capacity_expansion_limit,                     size_t)                                \
                                                                                                                                     \
  nonstatic_field(PermanentGenerationSpec,     _name,                                         PermGen::Name)                         \
  nonstatic_field(PermanentGenerationSpec,     _init_size,                                    size_t)                                \
  nonstatic_field(PermanentGenerationSpec,     _max_size,                                     size_t)                                \
                                                                                                                                     \
  nonstatic_field(Space,                       _bottom,                                       HeapWord*)                             \
  nonstatic_field(Space,                       _end,                                          HeapWord*)                             \
                                                                                                                                     \
  nonstatic_field(TenuredGeneration,           _shrink_factor,                                size_t)                                \
  nonstatic_field(TenuredGeneration,           _capacity_at_prologue,                         size_t)                                \
  nonstatic_field(ThreadLocalAllocBuffer,      _start,                                        HeapWord*)                             \
  nonstatic_field(ThreadLocalAllocBuffer,      _top,                                          HeapWord*)                             \
  nonstatic_field(ThreadLocalAllocBuffer,      _end,                                          HeapWord*)                             \
  nonstatic_field(ThreadLocalAllocBuffer,      _desired_size,                                 size_t)                                \
  nonstatic_field(ThreadLocalAllocBuffer,      _refill_waste_limit,                           size_t)                                \
     static_field(ThreadLocalAllocBuffer,      _target_refills,                               unsigned)                              \
  nonstatic_field(VirtualSpace,                _low_boundary,                                 char*)                                 \
  nonstatic_field(VirtualSpace,                _high_boundary,                                char*)                                 \
  nonstatic_field(VirtualSpace,                _low,                                          char*)                                 \
  nonstatic_field(VirtualSpace,                _high,                                         char*)                                 \
  nonstatic_field(VirtualSpace,                _lower_high,                                   char*)                                 \
  nonstatic_field(VirtualSpace,                _middle_high,                                  char*)                                 \
  nonstatic_field(VirtualSpace,                _upper_high,                                   char*)                                 \
  nonstatic_field(WaterMark,                   _point,                                        HeapWord*)                             \
  nonstatic_field(WaterMark,                   _space,                                        Space*)                                \
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
     static_field(PerfMemory,                  _initialized,                                  jint)                                  \
                                                                                                                                     \
  /***************/                                                                                                                  \
  /* SymbolTable */                                                                                                                  \
  /***************/                                                                                                                  \
                                                                                                                                     \
     static_field(SymbolTable,                  _the_table,                                   SymbolTable*)                          \
                                                                                                                                     \
  /***************/                                                                                                                  \
  /* StringTable */                                                                                                                  \
  /***************/                                                                                                                  \
                                                                                                                                     \
     static_field(StringTable,                  _the_table,                                   StringTable*)                          \
                                                                                                                                     \
  /********************/                                                                                                             \
  /* SystemDictionary */                                                                                                             \
  /********************/                                                                                                             \
                                                                                                                                     \
      static_field(SystemDictionary,            _dictionary,                                   Dictionary*)                          \
      static_field(SystemDictionary,            _placeholders,                                 PlaceholderTable*)                    \
      static_field(SystemDictionary,            _shared_dictionary,                            Dictionary*)                          \
      static_field(SystemDictionary,            _system_loader_lock_obj,                       oop)                                  \
      static_field(SystemDictionary,            _loader_constraints,                           LoaderConstraintTable*)               \
      static_field(SystemDictionary,            WK_KLASS(Object_klass),                        klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(String_klass),                        klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(Class_klass),                         klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(Cloneable_klass),                     klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(ClassLoader_klass),                   klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(Serializable_klass),                  klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(System_klass),                        klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(Throwable_klass),                     klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(ThreadDeath_klass),                   klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(Error_klass),                         klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(Exception_klass),                     klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(RuntimeException_klass),              klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(ClassNotFoundException_klass),        klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(NoClassDefFoundError_klass),          klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(LinkageError_klass),                  klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(ClassCastException_klass),            klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(ArrayStoreException_klass),           klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(VirtualMachineError_klass),           klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(OutOfMemoryError_klass),              klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(StackOverflowError_klass),            klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(ProtectionDomain_klass),              klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(AccessControlContext_klass),          klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(Reference_klass),                     klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(SoftReference_klass),                 klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(WeakReference_klass),                 klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(FinalReference_klass),                klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(PhantomReference_klass),              klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(Finalizer_klass),                     klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(Thread_klass),                        klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(ThreadGroup_klass),                   klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(Properties_klass),                    klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(StringBuffer_klass),                  klassOop)                             \
      static_field(SystemDictionary,            WK_KLASS(MethodHandle_klass),                  klassOop)                             \
      static_field(SystemDictionary,            _box_klasses[0],                               klassOop)                             \
      static_field(SystemDictionary,            _java_system_loader,                           oop)                                  \
                                                                                                                                     \
  /*************/                                                                                                                    \
  /* vmSymbols */                                                                                                                    \
  /*************/                                                                                                                    \
                                                                                                                                     \
      static_field(vmSymbols,                   _symbols[0],                                  Symbol*)                               \
                                                                                                                                     \
  /*******************/                                                                                                              \
  /* HashtableBucket */                                                                                                              \
  /*******************/                                                                                                              \
                                                                                                                                     \
  nonstatic_field(HashtableBucket,             _entry,                                        BasicHashtableEntry*)                  \
                                                                                                                                     \
  /******************/                                                                                                               \
  /* HashtableEntry */                                                                                                               \
  /******************/                                                                                                               \
                                                                                                                                     \
  nonstatic_field(BasicHashtableEntry,         _next,                                         BasicHashtableEntry*)                  \
  nonstatic_field(BasicHashtableEntry,         _hash,                                         unsigned int)                          \
  nonstatic_field(HashtableEntry<intptr_t>,    _literal,                                      intptr_t) \
                                                                                                                                     \
  /*************/                                                                                                                    \
  /* Hashtable */                                                                                                                    \
  /*************/                                                                                                                    \
                                                                                                                                     \
  nonstatic_field(BasicHashtable,              _table_size,                                   int)                                   \
  nonstatic_field(BasicHashtable,              _buckets,                                      HashtableBucket*)                      \
  nonstatic_field(BasicHashtable,              _free_list,                                    BasicHashtableEntry*)                  \
  nonstatic_field(BasicHashtable,              _first_free_entry,                             char*)                                 \
  nonstatic_field(BasicHashtable,              _end_block,                                    char*)                                 \
  nonstatic_field(BasicHashtable,              _entry_size,                                   int)                                   \
                                                                                                                                     \
  /*******************/                                                                                                              \
  /* DictionaryEntry */                                                                                                              \
  /*******************/                                                                                                              \
                                                                                                                                     \
  nonstatic_field(DictionaryEntry,             _loader,                                       oop)                                   \
  nonstatic_field(DictionaryEntry,             _pd_set,                                       ProtectionDomainEntry*)                \
                                                                                                                                     \
  /********************/                                                                                                             \
                                                                                                                                     \
  nonstatic_field(PlaceholderEntry,            _loader,                                       oop)                                   \
                                                                                                                                     \
  /**************************/                                                                                                       \
  /* ProctectionDomainEntry */                                                                                                       \
  /**************************/                                                                                                       \
                                                                                                                                     \
  nonstatic_field(ProtectionDomainEntry,       _next,                                         ProtectionDomainEntry*)                \
  nonstatic_field(ProtectionDomainEntry,       _protection_domain,                            oop)                                   \
                                                                                                                                     \
  /*************************/                                                                                                        \
  /* LoaderConstraintEntry */                                                                                                        \
  /*************************/                                                                                                        \
                                                                                                                                     \
  nonstatic_field(LoaderConstraintEntry,       _name,                                         Symbol*)                               \
  nonstatic_field(LoaderConstraintEntry,       _num_loaders,                                  int)                                   \
  nonstatic_field(LoaderConstraintEntry,       _max_loaders,                                  int)                                   \
  nonstatic_field(LoaderConstraintEntry,       _loaders,                                      oop*)                                  \
                                                                                                                                     \
  /*******************/                                                                                                              \
  /* GrowableArrays  */                                                                                                              \
  /*******************/                                                                                                              \
                                                                                                                                     \
  nonstatic_field(GenericGrowableArray,        _len,                                          int)                                   \
  nonstatic_field(GenericGrowableArray,        _max,                                          int)                                   \
  nonstatic_field(GenericGrowableArray,        _arena,                                        Arena*)                                \
  nonstatic_field(GrowableArray<int>,               _data,                                         int*) \
                                                                                                                                     \
  /********************************/                                                                                                 \
  /* CodeCache (NOTE: incomplete) */                                                                                                 \
  /********************************/                                                                                                 \
                                                                                                                                     \
     static_field(CodeCache,                   _heap,                                         CodeHeap*)                             \
     static_field(CodeCache,                   _scavenge_root_nmethods,                       nmethod*)                              \
                                                                                                                                     \
  /*******************************/                                                                                                  \
  /* CodeHeap (NOTE: incomplete) */                                                                                                  \
  /*******************************/                                                                                                  \
                                                                                                                                     \
  nonstatic_field(CodeHeap,                    _memory,                                       VirtualSpace)                          \
  nonstatic_field(CodeHeap,                    _segmap,                                       VirtualSpace)                          \
  nonstatic_field(CodeHeap,                    _log2_segment_size,                            int)                                   \
  nonstatic_field(HeapBlock,                   _header,                                       HeapBlock::Header)                     \
  nonstatic_field(HeapBlock::Header,           _length,                                       size_t)                                \
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
  /* StubRoutines (NOTE: incomplete) */                                                                                              \
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
  X86_ONLY(nonstatic_field(MethodHandles::RicochetFrame, _sender_pc,                                     address))                   \
  X86_ONLY(nonstatic_field(MethodHandles::RicochetFrame, _exact_sender_sp,                              intptr_t*))                  \
  X86_ONLY(nonstatic_field(MethodHandles::RicochetFrame, _sender_link,                                  intptr_t*))                  \
  X86_ONLY(nonstatic_field(MethodHandles::RicochetFrame, _saved_args_base,                              intptr_t*))                  \
                                                                                                                                     \
     static_field(SharedRuntime,               _ricochet_blob,                                RicochetBlob*)                         \
                                                                                                                                     \
  nonstatic_field(CodeBlob,                    _name,                                         const char*)                           \
  nonstatic_field(CodeBlob,                    _size,                                         int)                                   \
  nonstatic_field(CodeBlob,                    _header_size,                                  int)                                   \
  nonstatic_field(CodeBlob,                    _relocation_size,                              int)                                   \
  nonstatic_field(CodeBlob,                    _content_offset,                               int)                                   \
  nonstatic_field(CodeBlob,                    _code_offset,                                  int)                                   \
  nonstatic_field(CodeBlob,                    _frame_complete_offset,                        int)                                   \
  nonstatic_field(CodeBlob,                    _data_offset,                                  int)                                   \
  nonstatic_field(CodeBlob,                    _frame_size,                                   int)                                   \
  nonstatic_field(CodeBlob,                    _oop_maps,                                     OopMapSet*)                            \
                                                                                                                                     \
  nonstatic_field(RuntimeStub,                 _caller_must_gc_arguments,                     bool)                                  \
                                                                                                                                     \
  /**************************************************/                                                                               \
  /* NMethods (NOTE: incomplete, but only a little) */                                                                               \
  /**************************************************/                                                                               \
                                                                                                                                     \
  nonstatic_field(nmethod,             _method,                                       methodOop)                             \
  nonstatic_field(nmethod,             _entry_bci,                                    int)                                   \
  nonstatic_field(nmethod,             _osr_link,                                     nmethod*)                              \
  nonstatic_field(nmethod,             _scavenge_root_link,                           nmethod*)                              \
  nonstatic_field(nmethod,             _scavenge_root_state,                          jbyte)                                 \
  nonstatic_field(nmethod,             _state,                                        unsigned char)                         \
  nonstatic_field(nmethod,             _exception_offset,                             int)                                   \
  nonstatic_field(nmethod,             _deoptimize_offset,                            int)                                   \
  nonstatic_field(nmethod,             _deoptimize_mh_offset,                         int)                                   \
  nonstatic_field(nmethod,             _orig_pc_offset,                               int)                                   \
  nonstatic_field(nmethod,             _stub_offset,                                  int)                                   \
  nonstatic_field(nmethod,             _consts_offset,                                int)                                   \
  nonstatic_field(nmethod,             _oops_offset,                                  int)                                   \
  nonstatic_field(nmethod,             _scopes_data_offset,                           int)                                   \
  nonstatic_field(nmethod,             _scopes_pcs_offset,                            int)                                   \
  nonstatic_field(nmethod,             _dependencies_offset,                          int)                                   \
  nonstatic_field(nmethod,             _handler_table_offset,                         int)                                   \
  nonstatic_field(nmethod,             _nul_chk_table_offset,                         int)                                   \
  nonstatic_field(nmethod,             _nmethod_end_offset,                           int)                                   \
  nonstatic_field(nmethod,             _entry_point,                                  address)                               \
  nonstatic_field(nmethod,             _verified_entry_point,                         address)                               \
  nonstatic_field(nmethod,             _osr_entry_point,                              address)                               \
  nonstatic_field(nmethod,             _lock_count,                                   jint)                                  \
  nonstatic_field(nmethod,             _stack_traversal_mark,                         long)                                  \
  nonstatic_field(nmethod,             _compile_id,                                   int)                                   \
  nonstatic_field(nmethod,             _exception_cache,                              ExceptionCache*)                       \
  nonstatic_field(nmethod,             _marked_for_deoptimization,                    bool)                                  \
                                                                                                                                     \
  nonstatic_field(RicochetBlob,        _bounce_offset,                                int)                                           \
  nonstatic_field(RicochetBlob,        _exception_offset,                             int)                                           \
                                                                                                                                     \
  unchecked_c2_static_field(Deoptimization,         _trap_reason_name,                   void*)                                         \
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
     static_field(Threads,                     _thread_list,                                  JavaThread*)                           \
     static_field(Threads,                     _number_of_threads,                            int)                                   \
     static_field(Threads,                     _number_of_non_daemon_threads,                 int)                                   \
     static_field(Threads,                     _return_code,                                  int)                                   \
                                                                                                                                     \
  nonstatic_field(ThreadShadow,                _pending_exception,                            oop)                                   \
  nonstatic_field(ThreadShadow,                _exception_file,                               const char*)                           \
  nonstatic_field(ThreadShadow,                _exception_line,                               int)                                   \
   volatile_nonstatic_field(Thread,            _suspend_flags,                                uint32_t)                              \
  nonstatic_field(Thread,                      _active_handles,                               JNIHandleBlock*)                       \
  nonstatic_field(Thread,                      _tlab,                                         ThreadLocalAllocBuffer)                \
  nonstatic_field(Thread,                      _current_pending_monitor,                      ObjectMonitor*)                        \
  nonstatic_field(Thread,                      _current_pending_monitor_is_from_java,         bool)                                  \
  nonstatic_field(Thread,                      _current_waiting_monitor,                      ObjectMonitor*)                        \
  nonstatic_field(NamedThread,                 _name,                                         char*)                                 \
  nonstatic_field(NamedThread,                 _processed_thread,                             JavaThread*)                           \
  nonstatic_field(JavaThread,                  _next,                                         JavaThread*)                           \
  nonstatic_field(JavaThread,                  _threadObj,                                    oop)                                   \
  nonstatic_field(JavaThread,                  _anchor,                                       JavaFrameAnchor)                       \
  nonstatic_field(JavaThread,                  _vm_result,                                    oop)                                   \
  nonstatic_field(JavaThread,                  _vm_result_2,                                  oop)                                   \
  nonstatic_field(JavaThread,                  _pending_async_exception,                      oop)                                   \
  volatile_nonstatic_field(JavaThread,         _exception_oop,                                oop)                                   \
  volatile_nonstatic_field(JavaThread,         _exception_pc,                                 address)                               \
  nonstatic_field(JavaThread,                  _is_compiling,                                 bool)                                  \
  nonstatic_field(JavaThread,                  _special_runtime_exit_condition,               JavaThread::AsyncRequests)             \
  nonstatic_field(JavaThread,                  _saved_exception_pc,                           address)                               \
   volatile_nonstatic_field(JavaThread,        _thread_state,                                 JavaThreadState)                       \
  nonstatic_field(JavaThread,                  _osthread,                                     OSThread*)                             \
  nonstatic_field(JavaThread,                  _stack_base,                                   address)                               \
  nonstatic_field(JavaThread,                  _stack_size,                                   size_t)                                \
  nonstatic_field(JavaThread,                  _vframe_array_head,                            vframeArray*)                          \
  nonstatic_field(JavaThread,                  _vframe_array_last,                            vframeArray*)                          \
  nonstatic_field(Thread,                      _resource_area,                                ResourceArea*)                         \
  nonstatic_field(CompilerThread,              _env,                                          ciEnv*)                                \
                                                                                                                                     \
  /************/                                                                                                                     \
  /* OSThread */                                                                                                                     \
  /************/                                                                                                                     \
                                                                                                                                     \
  volatile_nonstatic_field(OSThread,           _interrupted,                                  jint)                                  \
                                                                                                                                     \
  /************************/                                                                                                         \
  /* OopMap and OopMapSet */                                                                                                         \
  /************************/                                                                                                         \
                                                                                                                                     \
  nonstatic_field(OopMap,                      _pc_offset,                                    int)                                   \
  nonstatic_field(OopMap,                      _omv_count,                                    int)                                   \
  nonstatic_field(OopMap,                      _omv_data_size,                                int)                                   \
  nonstatic_field(OopMap,                      _omv_data,                                     unsigned char*)                        \
  nonstatic_field(OopMap,                      _write_stream,                                 CompressedWriteStream*)                \
  nonstatic_field(OopMapSet,                   _om_count,                                     int)                                   \
  nonstatic_field(OopMapSet,                   _om_size,                                      int)                                   \
  nonstatic_field(OopMapSet,                   _om_data,                                      OopMap**)                              \
                                                                                                                                     \
  /*********************************/                                                                                                \
  /* JNIHandles and JNIHandleBlock */                                                                                                \
  /*********************************/                                                                                                \
     static_field(JNIHandles,                  _global_handles,                               JNIHandleBlock*)                       \
     static_field(JNIHandles,                  _weak_global_handles,                          JNIHandleBlock*)                       \
     static_field(JNIHandles,                  _deleted_handle,                               oop)                                   \
                                                                                                                                     \
  unchecked_nonstatic_field(JNIHandleBlock,    _handles,                                      JNIHandleBlock::block_size_in_oops * sizeof(Oop)) /* Note: no type */ \
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
  /*******************************/                                                                                                  \
  /* Runtime1 (NOTE: incomplete) */                                                                                                  \
  /*******************************/                                                                                                  \
                                                                                                                                     \
  unchecked_c1_static_field(Runtime1,          _blobs,                                 sizeof(Runtime1::_blobs)) /* NOTE: no type */ \
                                                                                                                                     \
  /**************/                                                                                                                   \
  /* allocation */                                                                                                                   \
  /**************/                                                                                                                   \
                                                                                                                                     \
  nonstatic_field(Chunk, _next, Chunk*)                                                                                              \
  nonstatic_field(Chunk, _len, const size_t)                                                                                         \
                                                                                                                                     \
  nonstatic_field(Arena, _first, Chunk*)                                                                                             \
  nonstatic_field(Arena, _chunk, Chunk*)                                                                                             \
  nonstatic_field(Arena, _hwm, char*)                                                                                                \
  nonstatic_field(Arena, _max, char*)                                                                                                \
                                                                                                                                     \
  /************/                                                                                                                     \
  /* CI */                                                                                                                           \
  /************/                                                                                                                     \
                                                                                                                                     \
 nonstatic_field(ciEnv,               _system_dictionary_modification_counter, int)                                                  \
 nonstatic_field(ciEnv,               _compiler_data, void*)                                                                         \
 nonstatic_field(ciEnv,               _failure_reason, const char*)                                                                  \
 nonstatic_field(ciEnv,               _factory, ciObjectFactory*)                                                                    \
 nonstatic_field(ciEnv,               _dependencies, Dependencies*)                                                                  \
 nonstatic_field(ciEnv,               _task, CompileTask*)                                                                           \
 nonstatic_field(ciEnv,               _arena, Arena*)                                                                                \
                                                                                                                                     \
 nonstatic_field(ciObject,    _handle, jobject)                                                                                      \
 nonstatic_field(ciObject,    _klass, ciKlass*)                                                                                      \
 nonstatic_field(ciObject,    _ident, uint)                                                                                          \
                                                                                                                                     \
 nonstatic_field(ciSymbol,    _ident, uint)                                                                                          \
 nonstatic_field(ciSymbol,    _symbol, Symbol*)                                                                                      \
                                                                                                                                     \
 nonstatic_field(ciType,    _basic_type, BasicType)                                                                                  \
                                                                                                                                     \
 nonstatic_field(ciKlass,   _name, ciSymbol*)                                                                                        \
                                                                                                                                     \
 nonstatic_field(ciArrayKlass,   _dimension, jint)                                                                                   \
                                                                                                                                     \
 nonstatic_field(ciObjArrayKlass, _element_klass, ciKlass*)                                                                          \
 nonstatic_field(ciObjArrayKlass, _base_element_klass, ciKlass*)                                                                     \
                                                                                                                                     \
 nonstatic_field(ciInstanceKlass,   _init_state, instanceKlass::ClassState)                                                          \
 nonstatic_field(ciInstanceKlass,   _is_shared,  bool)                                                                               \
                                                                                                                                     \
 nonstatic_field(ciMethod,     _interpreter_invocation_count, int)                                                                   \
 nonstatic_field(ciMethod,     _interpreter_throwout_count, int)                                                                     \
                                                                                                                                     \
 nonstatic_field(ciMethodData, _data_size, int)                                                                                      \
 nonstatic_field(ciMethodData, _state, u_char)                                                                                       \
 nonstatic_field(ciMethodData, _extra_data_size, int)                                                                                \
 nonstatic_field(ciMethodData, _data, intptr_t*)                                                                                     \
 nonstatic_field(ciMethodData, _hint_di, int)                                                                                        \
 nonstatic_field(ciMethodData, _eflags, intx)                                                                                        \
 nonstatic_field(ciMethodData, _arg_local, intx)                                                                                     \
 nonstatic_field(ciMethodData, _arg_stack, intx)                                                                                     \
 nonstatic_field(ciMethodData, _arg_returned, intx)                                                                                  \
 nonstatic_field(ciMethodData, _current_mileage, int)                                                                                \
 nonstatic_field(ciMethodData, _orig, methodDataOopDesc)                                                                             \
                                                                                                                                     \
 nonstatic_field(ciField,     _holder, ciInstanceKlass*)                                                                             \
 nonstatic_field(ciField,     _name, ciSymbol*)                                                                                      \
 nonstatic_field(ciField,     _signature, ciSymbol*)                                                                                 \
 nonstatic_field(ciField,     _offset, int)                                                                                          \
 nonstatic_field(ciField,     _is_constant, bool)                                                                                    \
 nonstatic_field(ciField,     _constant_value, ciConstant)                                                                           \
                                                                                                                                     \
 nonstatic_field(ciObjectFactory,     _ci_objects, GrowableArray<ciObject*>*)                                                        \
 nonstatic_field(ciObjectFactory,     _symbols, GrowableArray<ciSymbol*>*)                                                           \
 nonstatic_field(ciObjectFactory,     _unloaded_methods, GrowableArray<ciMethod*>*)                                                  \
                                                                                                                                     \
 nonstatic_field(ciConstant,     _type, BasicType)                                                                                   \
 nonstatic_field(ciConstant,     _value._int, jint)                                                                                  \
 nonstatic_field(ciConstant,     _value._long, jlong)                                                                                \
 nonstatic_field(ciConstant,     _value._float, jfloat)                                                                              \
 nonstatic_field(ciConstant,     _value._double, jdouble)                                                                            \
 nonstatic_field(ciConstant,     _value._object, ciObject*)                                                                          \
                                                                                                                                     \
  /************/                                                                                                                     \
  /* Monitors */                                                                                                                     \
  /************/                                                                                                                     \
                                                                                                                                     \
  volatile_nonstatic_field(ObjectMonitor,      _header,                                       markOop)                               \
  unchecked_nonstatic_field(ObjectMonitor,     _object,                                       sizeof(void *)) /* NOTE: no type */    \
  unchecked_nonstatic_field(ObjectMonitor,     _owner,                                        sizeof(void *)) /* NOTE: no type */    \
  volatile_nonstatic_field(ObjectMonitor,      _count,                                        intptr_t)                              \
  volatile_nonstatic_field(ObjectMonitor,      _waiters,                                      intptr_t)                              \
  volatile_nonstatic_field(ObjectMonitor,      _recursions,                                   intptr_t)                              \
  nonstatic_field(ObjectMonitor,               FreeNext,                                      ObjectMonitor*)                        \
  volatile_nonstatic_field(BasicLock,          _displaced_header,                             markOop)                               \
  nonstatic_field(BasicObjectLock,             _lock,                                         BasicLock)                             \
  nonstatic_field(BasicObjectLock,             _obj,                                          oop)                                   \
  static_field(ObjectSynchronizer,             gBlockList,                                    ObjectMonitor*)                        \
                                                                                                                                     \
  /*********************/                                                                                                            \
  /* Matcher (C2 only) */                                                                                                            \
  /*********************/                                                                                                            \
                                                                                                                                     \
  unchecked_c2_static_field(Matcher,           _regEncode,                          sizeof(Matcher::_regEncode)) /* NOTE: no type */ \
                                                                                                                                     \
  c2_nonstatic_field(Node,               _in,                      Node**)                                                           \
  c2_nonstatic_field(Node,               _out,                     Node**)                                                           \
  c2_nonstatic_field(Node,               _cnt,                     node_idx_t)                                                       \
  c2_nonstatic_field(Node,               _max,                     node_idx_t)                                                       \
  c2_nonstatic_field(Node,               _outcnt,                  node_idx_t)                                                       \
  c2_nonstatic_field(Node,               _outmax,                  node_idx_t)                                                       \
  c2_nonstatic_field(Node,               _idx,                     const node_idx_t)                                                 \
  c2_nonstatic_field(Node,               _class_id,                jushort)                                                          \
  c2_nonstatic_field(Node,               _flags,                   jushort)                                                          \
                                                                                                                                     \
  c2_nonstatic_field(Compile,            _root,                    RootNode*)                                                        \
  c2_nonstatic_field(Compile,            _unique,                  uint)                                                             \
  c2_nonstatic_field(Compile,            _entry_bci,               int)                                                              \
  c2_nonstatic_field(Compile,            _top,                     Node*)                                                            \
  c2_nonstatic_field(Compile,            _cfg,                     PhaseCFG*)                                                        \
  c2_nonstatic_field(Compile,            _regalloc,                PhaseRegAlloc*)                                                   \
  c2_nonstatic_field(Compile,            _method,                  ciMethod*)                                                        \
  c2_nonstatic_field(Compile,            _compile_id,              const int)                                                        \
  c2_nonstatic_field(Compile,            _save_argument_registers, const bool)                                                       \
  c2_nonstatic_field(Compile,            _subsume_loads,           const bool)                                                       \
  c2_nonstatic_field(Compile,            _do_escape_analysis,      const bool)                                                       \
  c2_nonstatic_field(Compile,            _ilt,                     InlineTree*)                                                      \
                                                                                                                                     \
  c2_nonstatic_field(InlineTree,         _caller_jvms,             JVMState*)                                                        \
  c2_nonstatic_field(InlineTree,         _method,                  ciMethod*)                                                        \
  c2_nonstatic_field(InlineTree,         _caller_tree,             InlineTree*)                                                      \
  c2_nonstatic_field(InlineTree,         _subtrees,                GrowableArray<InlineTree*>)                                       \
                                                                                                                                     \
  c2_nonstatic_field(OptoRegPair,        _first,                   short)                                                            \
  c2_nonstatic_field(OptoRegPair,        _second,                  short)                                                            \
                                                                                                                                     \
  c2_nonstatic_field(JVMState,           _caller,                  JVMState*)                                                        \
  c2_nonstatic_field(JVMState,           _depth,                   uint)                                                             \
  c2_nonstatic_field(JVMState,           _locoff,                  uint)                                                             \
  c2_nonstatic_field(JVMState,           _stkoff,                  uint)                                                             \
  c2_nonstatic_field(JVMState,           _monoff,                  uint)                                                             \
  c2_nonstatic_field(JVMState,           _scloff,                  uint)                                                             \
  c2_nonstatic_field(JVMState,           _endoff,                  uint)                                                             \
  c2_nonstatic_field(JVMState,           _sp,                      uint)                                                             \
  c2_nonstatic_field(JVMState,           _bci,                     int)                                                              \
  c2_nonstatic_field(JVMState,           _method,                  ciMethod*)                                                        \
  c2_nonstatic_field(JVMState,           _map,                     SafePointNode*)                                                   \
                                                                                                                                     \
  c2_nonstatic_field(SafePointNode,      _jvms,                    JVMState* const)                                                  \
                                                                                                                                     \
  c2_nonstatic_field(MachSafePointNode,  _jvms,                    JVMState*)                                                        \
  c2_nonstatic_field(MachSafePointNode,  _jvmadj,                  uint)                                                             \
                                                                                                                                     \
  c2_nonstatic_field(MachIfNode,         _prob,                    jfloat)                                                           \
  c2_nonstatic_field(MachIfNode,         _fcnt,                    jfloat)                                                           \
                                                                                                                                     \
  c2_nonstatic_field(CallNode,           _entry_point,             address)                                                          \
                                                                                                                                     \
  c2_nonstatic_field(CallJavaNode,       _method,                  ciMethod*)                                                        \
                                                                                                                                     \
  c2_nonstatic_field(CallRuntimeNode,    _name,                    const char*)                                                      \
                                                                                                                                     \
  c2_nonstatic_field(CallStaticJavaNode, _name,                    const char*)                                                      \
                                                                                                                                     \
  c2_nonstatic_field(MachCallJavaNode,   _method,                  ciMethod*)                                                        \
  c2_nonstatic_field(MachCallJavaNode,   _bci,                     int)                                                              \
                                                                                                                                     \
  c2_nonstatic_field(MachCallStaticJavaNode, _name,                const char*)                                                      \
                                                                                                                                     \
  c2_nonstatic_field(MachCallRuntimeNode,  _name,                  const char*)                                                      \
                                                                                                                                     \
  c2_nonstatic_field(PhaseCFG,           _num_blocks,              uint)                                                             \
  c2_nonstatic_field(PhaseCFG,           _blocks,                  Block_List)                                                       \
  c2_nonstatic_field(PhaseCFG,           _bbs,                     Block_Array)                                                      \
  c2_nonstatic_field(PhaseCFG,           _broot,                   Block*)                                                           \
                                                                                                                                     \
  c2_nonstatic_field(PhaseRegAlloc,      _node_regs,               OptoRegPair*)                                                     \
  c2_nonstatic_field(PhaseRegAlloc,      _node_regs_max_index,     uint)                                                             \
  c2_nonstatic_field(PhaseRegAlloc,      _framesize,               uint)                                                             \
  c2_nonstatic_field(PhaseRegAlloc,      _max_reg,                 OptoReg::Name)                                                    \
                                                                                                                                     \
  c2_nonstatic_field(PhaseChaitin,       _trip_cnt,                int)                                                              \
  c2_nonstatic_field(PhaseChaitin,       _alternate,               int)                                                              \
  c2_nonstatic_field(PhaseChaitin,       _lo_degree,               uint)                                                             \
  c2_nonstatic_field(PhaseChaitin,       _lo_stk_degree,           uint)                                                             \
  c2_nonstatic_field(PhaseChaitin,       _hi_degree,               uint)                                                             \
  c2_nonstatic_field(PhaseChaitin,       _simplified,              uint)                                                             \
  c2_nonstatic_field(PhaseChaitin,       _maxlrg,                  uint)                                                             \
                                                                                                                                     \
  c2_nonstatic_field(Block,              _nodes,                   Node_List)                                                        \
  c2_nonstatic_field(Block,              _succs,                   Block_Array)                                                      \
  c2_nonstatic_field(Block,              _num_succs,               uint)                                                             \
  c2_nonstatic_field(Block,              _pre_order,               uint)                                                             \
  c2_nonstatic_field(Block,              _dom_depth,               uint)                                                             \
  c2_nonstatic_field(Block,              _idom,                    Block*)                                                           \
  c2_nonstatic_field(Block,              _freq,                    jfloat)                                                           \
                                                                                                                                     \
  c2_nonstatic_field(CFGElement,         _freq,                    jfloat)                                                           \
                                                                                                                                     \
  c2_nonstatic_field(Block_List,         _cnt,                     uint)                                                             \
                                                                                                                                     \
  c2_nonstatic_field(Block_Array,        _size,                    uint)                                                             \
  c2_nonstatic_field(Block_Array,        _blocks,                  Block**)                                                          \
  c2_nonstatic_field(Block_Array,        _arena,                   Arena*)                                                           \
                                                                                                                                     \
  c2_nonstatic_field(Node_List,          _cnt,                     uint)                                                             \
                                                                                                                                     \
  c2_nonstatic_field(Node_Array,         _max,                     uint)                                                             \
  c2_nonstatic_field(Node_Array,         _nodes,                   Node**)                                                           \
  c2_nonstatic_field(Node_Array,         _a,                       Arena*)                                                           \
                                                                                                                                     \
                                                                                                                                     \
  /*********************/                                                                                                            \
  /* -XX flags         */                                                                                                            \
  /*********************/                                                                                                            \
                                                                                                                                     \
  nonstatic_field(Flag,                        type,                                          const char*)                           \
  nonstatic_field(Flag,                        name,                                          const char*)                           \
  unchecked_nonstatic_field(Flag,              addr,                                          sizeof(void*)) /* NOTE: no type */     \
  nonstatic_field(Flag,                        kind,                                          const char*)                           \
  nonstatic_field(Flag,                        origin,                                        FlagValueOrigin)                       \
  static_field(Flag,                           flags,                                         Flag*)                                 \
  static_field(Flag,                           numFlags,                                      size_t)                                \
                                                                                                                                     \
  /*************************/                                                                                                        \
  /* JDK / VM version info */                                                                                                        \
  /*************************/                                                                                                        \
                                                                                                                                     \
  static_field(Abstract_VM_Version,            _s_vm_release,                                 const char*)                           \
  static_field(Abstract_VM_Version,            _s_internal_vm_info_string,                    const char*)                           \
  static_field(Abstract_VM_Version,            _vm_major_version,                             int)                                   \
  static_field(Abstract_VM_Version,            _vm_minor_version,                             int)                                   \
  static_field(Abstract_VM_Version,            _vm_build_number,                              int)                                   \
                                                                                                                                     \
  static_field(JDK_Version,                    _current,                                      JDK_Version)                           \
  nonstatic_field(JDK_Version,                 _partially_initialized,                        bool)                                  \
  nonstatic_field(JDK_Version,                 _major,                                        unsigned char)                         \
                                                                                                                                     \
  /*************************/                                                                                                        \
  /* JVMTI */                                                                                                                        \
  /*************************/                                                                                                        \
                                                                                                                                     \
  static_field(JvmtiExport,                     _can_access_local_variables,                  bool)                                  \
  static_field(JvmtiExport,                     _can_hotswap_or_post_breakpoint,              bool)                                  \
  static_field(JvmtiExport,                     _can_post_on_exceptions,                      bool)                                  \
  static_field(JvmtiExport,                     _can_walk_any_space,                          bool)                                  \
                                                                                                                                     \
  /*************/                                                                                                                    \
  /* Arguments */                                                                                                                    \
  /*************/                                                                                                                    \
                                                                                                                                     \
  static_field(Arguments,                      _jvm_flags_array,                              char**)                                \
  static_field(Arguments,                      _num_jvm_flags,                                int)                                   \
  static_field(Arguments,                      _jvm_args_array,                               char**)                                \
  static_field(Arguments,                      _num_jvm_args,                                 int)                                   \
  static_field(Arguments,                      _java_command,                                 char*)                                 \
                                                                                                                                     \
  /*********************************/                                                                                                \
  /* java_lang_Class fields        */                                                                                                \
  /*********************************/                                                                                                \
                                                                                                                                     \
  static_field(java_lang_Class,                _klass_offset,                                 int)                                   \
  static_field(java_lang_Class,                _resolved_constructor_offset,                  int)                                   \
  static_field(java_lang_Class,                _array_klass_offset,                           int)                                   \
  static_field(java_lang_Class,                _oop_size_offset,                              int)                                   \
  static_field(java_lang_Class,                _static_oop_field_count_offset,                int)                                   \
                                                                                                                                     \
  /************************/                                                                                                         \
  /* Miscellaneous fields */                                                                                                         \
  /************************/                                                                                                         \
                                                                                                                                     \
  nonstatic_field(CompileTask,                 _method,                                      jobject)                                \
  nonstatic_field(CompileTask,                 _osr_bci,                                     int)                                    \
  nonstatic_field(CompileTask,                 _comp_level,                                  int)                                    \
  nonstatic_field(CompileTask,                 _compile_id,                                  uint)                                   \
  nonstatic_field(CompileTask,                 _next,                                        CompileTask*)                           \
  nonstatic_field(CompileTask,                 _prev,                                        CompileTask*)                           \
                                                                                                                                     \
  nonstatic_field(vframeArray,                 _next,                                        vframeArray*)                           \
  nonstatic_field(vframeArray,                 _original,                                    frame)                                  \
  nonstatic_field(vframeArray,                 _caller,                                      frame)                                  \
  nonstatic_field(vframeArray,                 _frames,                                      int)                                    \
                                                                                                                                     \
  nonstatic_field(vframeArrayElement,          _frame,                                       frame)                                  \
  nonstatic_field(vframeArrayElement,          _bci,                                         int)                                    \
  nonstatic_field(vframeArrayElement,          _method,                                      methodOop)                              \
                                                                                                                                     \
  nonstatic_field(AccessFlags,                 _flags,                                       jint)                                   \
  nonstatic_field(elapsedTimer,                _counter,                                     jlong)                                  \
  nonstatic_field(elapsedTimer,                _active,                                      bool)                                   \
  nonstatic_field(InvocationCounter,           _counter,                                     unsigned int)

  /* NOTE that we do not use the last_entry() macro here; it is used  */
  /* in vmStructs_<os>_<cpu>.hpp's VM_STRUCTS_OS_CPU macro (and must  */
  /* be present there)                                                */

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
                 declare_unsigned_integer_type,                           \
                 declare_c1_toplevel_type,                                \
                 declare_c2_type,                                         \
                 declare_c2_toplevel_type,                                \
                 last_entry)                                              \
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
  declare_unsigned_integer_type(unsigned char)                            \
  declare_unsigned_integer_type(u_char)                                   \
  declare_unsigned_integer_type(unsigned int)                             \
  declare_unsigned_integer_type(uint)                                     \
  declare_unsigned_integer_type(unsigned short)                           \
  declare_unsigned_integer_type(jushort)                                  \
  declare_unsigned_integer_type(unsigned long)                            \
  /* The compiler thinks this is a different type than */                 \
  /* unsigned short on Win32 */                                           \
  declare_unsigned_integer_type(u2)                                       \
  declare_unsigned_integer_type(u1)                                       \
  declare_unsigned_integer_type(unsigned)                                 \
                                                                          \
  /*****************************/                                         \
  /* C primitive pointer types */                                         \
  /*****************************/                                         \
                                                                          \
  declare_toplevel_type(int*)                                             \
  declare_toplevel_type(char*)                                            \
  declare_toplevel_type(char**)                                           \
  declare_toplevel_type(u_char*)                                          \
  declare_toplevel_type(unsigned char*)                                   \
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
  declare_unsigned_integer_type(uintx)                                    \
  declare_unsigned_integer_type(uintptr_t)                                \
  declare_unsigned_integer_type(uint32_t)                                 \
  declare_unsigned_integer_type(uint64_t)                                 \
                                                                          \
  /*******************************************************************************/ \
  /* OopDesc and Klass hierarchies (NOTE: missing methodDataOop-related classes) */ \
  /*******************************************************************************/ \
                                                                          \
  declare_toplevel_type(oopDesc)                                          \
  declare_toplevel_type(Klass_vtbl)                                       \
           declare_type(Klass, Klass_vtbl)                                \
           declare_type(arrayKlass, Klass)                                \
           declare_type(arrayKlassKlass, klassKlass)                      \
           declare_type(arrayOopDesc, oopDesc)                            \
   declare_type(compiledICHolderKlass, Klass)                             \
   declare_type(compiledICHolderOopDesc, oopDesc)                         \
           declare_type(constantPoolKlass, Klass)                         \
           declare_type(constantPoolOopDesc, oopDesc)                     \
           declare_type(constantPoolCacheKlass, Klass)                    \
           declare_type(constantPoolCacheOopDesc, oopDesc)                \
           declare_type(instanceKlass, Klass)                             \
           declare_type(instanceKlassKlass, klassKlass)                   \
           declare_type(instanceOopDesc, oopDesc)                         \
           declare_type(instanceMirrorKlass, instanceKlass)               \
           declare_type(instanceRefKlass, instanceKlass)                  \
           declare_type(klassKlass, Klass)                                \
           declare_type(klassOopDesc, oopDesc)                            \
           declare_type(markOopDesc, oopDesc)                             \
   declare_type(methodDataKlass, Klass)                                   \
   declare_type(methodDataOopDesc, oopDesc)                               \
           declare_type(methodKlass, Klass)                               \
           declare_type(constMethodKlass, Klass)                          \
           declare_type(methodOopDesc, oopDesc)                           \
           declare_type(objArrayKlass, arrayKlass)                        \
           declare_type(objArrayKlassKlass, arrayKlassKlass)              \
           declare_type(objArrayOopDesc, arrayOopDesc)                    \
           declare_type(constMethodOopDesc, oopDesc)                      \
           declare_type(typeArrayKlass, arrayKlass)                       \
           declare_type(typeArrayKlassKlass, arrayKlassKlass)             \
           declare_type(typeArrayOopDesc, arrayOopDesc)                   \
           declare_toplevel_type(Symbol)                                  \
           declare_toplevel_type(Symbol*)                                 \
                                                                          \
  declare_toplevel_type(nmethodBucket)                                    \
                                                                          \
  /********/                                                              \
  /* Oops */                                                              \
  /********/                                                              \
                                                                          \
  declare_oop_type(constantPoolOop)                                       \
  declare_oop_type(constantPoolCacheOop)                                  \
  declare_oop_type(klassOop)                                              \
  declare_oop_type(markOop)                                               \
  declare_oop_type(methodOop)                                             \
  declare_oop_type(methodDataOop)                                         \
  declare_oop_type(objArrayOop)                                           \
  declare_oop_type(oop)                                                   \
  declare_oop_type(narrowOop)                                             \
  declare_oop_type(wideKlassOop)                                          \
  declare_oop_type(constMethodOop)                                        \
  declare_oop_type(typeArrayOop)                                          \
                                                                          \
  /*************************************/                                 \
  /* MethodOop-related data structures */                                 \
  /*************************************/                                 \
                                                                          \
  declare_toplevel_type(CheckedExceptionElement)                          \
  declare_toplevel_type(LocalVariableTableElement)                        \
                                                                          \
  /******************************************/                            \
  /* Generation and space hierarchies       */                            \
  /* (needed for run-time type information) */                            \
  /******************************************/                            \
                                                                          \
  declare_toplevel_type(CollectedHeap)                                    \
           declare_type(SharedHeap,                   CollectedHeap)      \
           declare_type(GenCollectedHeap,             SharedHeap)         \
  declare_toplevel_type(Generation)                                       \
           declare_type(DefNewGeneration,             Generation)         \
           declare_type(CardGeneration,               Generation)         \
           declare_type(OneContigSpaceCardGeneration, CardGeneration)     \
           declare_type(TenuredGeneration,            OneContigSpaceCardGeneration) \
           declare_type(CompactingPermGenGen,         OneContigSpaceCardGeneration) \
  declare_toplevel_type(Space)                                            \
  declare_toplevel_type(BitMap)                                           \
           declare_type(CompactibleSpace,             Space)              \
           declare_type(ContiguousSpace,              CompactibleSpace)   \
           declare_type(EdenSpace,                    ContiguousSpace)    \
           declare_type(OffsetTableContigSpace,       ContiguousSpace)    \
           declare_type(TenuredSpace,                 OffsetTableContigSpace) \
           declare_type(ContigPermSpace,              OffsetTableContigSpace) \
  declare_toplevel_type(PermGen)                                          \
           declare_type(CompactingPermGen,            PermGen)            \
  declare_toplevel_type(BarrierSet)                                       \
           declare_type(ModRefBarrierSet,             BarrierSet)         \
           declare_type(CardTableModRefBS,            ModRefBarrierSet)   \
           declare_type(CardTableModRefBSForCTRS,     CardTableModRefBS)  \
  declare_toplevel_type(GenRemSet)                                        \
           declare_type(CardTableRS,                  GenRemSet)          \
  declare_toplevel_type(BlockOffsetSharedArray)                           \
  declare_toplevel_type(BlockOffsetTable)                                 \
           declare_type(BlockOffsetArray,             BlockOffsetTable)   \
           declare_type(BlockOffsetArrayContigSpace,  BlockOffsetArray)   \
           declare_type(BlockOffsetArrayNonContigSpace, BlockOffsetArray) \
                                                                          \
  /* Miscellaneous other GC types */                                      \
                                                                          \
  declare_toplevel_type(ageTable)                                         \
  declare_toplevel_type(Generation::StatRecord)                           \
  declare_toplevel_type(GenerationSpec)                                   \
  declare_toplevel_type(HeapWord)                                         \
  declare_toplevel_type(MemRegion)                                        \
  declare_toplevel_type(PermanentGenerationSpec)                          \
  declare_toplevel_type(ThreadLocalAllocBuffer)                           \
  declare_toplevel_type(VirtualSpace)                                     \
  declare_toplevel_type(WaterMark)                                        \
                                                                          \
  /* Pointers to Garbage Collection types */                              \
                                                                          \
  declare_toplevel_type(BarrierSet*)                                      \
  declare_toplevel_type(BlockOffsetSharedArray*)                          \
  declare_toplevel_type(GenRemSet*)                                       \
  declare_toplevel_type(CardTableRS*)                                     \
  declare_toplevel_type(CardTableModRefBS*)                               \
  declare_toplevel_type(CardTableModRefBS**)                              \
  declare_toplevel_type(CardTableModRefBSForCTRS*)                        \
  declare_toplevel_type(CardTableModRefBSForCTRS**)                       \
  declare_toplevel_type(CollectedHeap*)                                   \
  declare_toplevel_type(ContiguousSpace*)                                 \
  declare_toplevel_type(DefNewGeneration*)                                \
  declare_toplevel_type(EdenSpace*)                                       \
  declare_toplevel_type(GenCollectedHeap*)                                \
  declare_toplevel_type(Generation*)                                      \
  declare_toplevel_type(GenerationSpec**)                                 \
  declare_toplevel_type(HeapWord*)                                        \
  declare_toplevel_type(MemRegion*)                                       \
  declare_toplevel_type(OffsetTableContigSpace*)                          \
  declare_toplevel_type(OneContigSpaceCardGeneration*)                    \
  declare_toplevel_type(PermGen*)                                         \
  declare_toplevel_type(Space*)                                           \
  declare_toplevel_type(ThreadLocalAllocBuffer*)                          \
                                                                          \
  /************************/                                              \
  /* PerfMemory - jvmstat */                                              \
  /************************/                                              \
                                                                          \
  declare_toplevel_type(PerfDataPrologue)                                 \
  declare_toplevel_type(PerfDataPrologue*)                                \
  declare_toplevel_type(PerfDataEntry)                                    \
  declare_toplevel_type(PerfMemory)                                       \
                                                                          \
  /*********************************/                                     \
  /* SymbolTable, SystemDictionary */                                     \
  /*********************************/                                     \
                                                                          \
  declare_toplevel_type(BasicHashtable)                                   \
    declare_type(Hashtable<intptr_t>, BasicHashtable)                     \
  declare_type(SymbolTable, Hashtable<Symbol*>)                           \
  declare_type(StringTable, Hashtable<oop>)                               \
    declare_type(LoaderConstraintTable, Hashtable<klassOop>)              \
    declare_type(TwoOopHashtable<klassOop>, Hashtable<klassOop>)          \
    declare_type(Dictionary, TwoOopHashtable<klassOop>)                   \
    declare_type(PlaceholderTable, TwoOopHashtable<Symbol*>)              \
  declare_toplevel_type(BasicHashtableEntry)                              \
  declare_type(HashtableEntry<intptr_t>, BasicHashtableEntry)             \
    declare_type(DictionaryEntry, HashtableEntry<klassOop>)               \
    declare_type(PlaceholderEntry, HashtableEntry<Symbol*>)               \
    declare_type(LoaderConstraintEntry, HashtableEntry<klassOop>)         \
  declare_toplevel_type(HashtableBucket)                                  \
  declare_toplevel_type(SystemDictionary)                                 \
  declare_toplevel_type(vmSymbols)                                        \
  declare_toplevel_type(ProtectionDomainEntry)                            \
                                                                          \
  declare_toplevel_type(GenericGrowableArray)                             \
  declare_toplevel_type(GrowableArray<int>)                               \
  declare_toplevel_type(Arena)                                            \
    declare_type(ResourceArea, Arena)                                     \
  declare_toplevel_type(Chunk)                                            \
                                                                          \
  /***********************************************************/           \
  /* Thread hierarchy (needed for run-time type information) */           \
  /***********************************************************/           \
                                                                          \
  declare_toplevel_type(Threads)                                          \
  declare_toplevel_type(ThreadShadow)                                     \
           declare_type(Thread, ThreadShadow)                             \
           declare_type(NamedThread, Thread)                              \
           declare_type(WatcherThread, Thread)                            \
           declare_type(JavaThread, Thread)                               \
           declare_type(JvmtiAgentThread, JavaThread)                     \
           declare_type(ServiceThread, JavaThread)                        \
  declare_type(CompilerThread, JavaThread)                                \
  declare_toplevel_type(OSThread)                                         \
  declare_toplevel_type(JavaFrameAnchor)                                  \
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
  declare_toplevel_type(SharedRuntime)                                    \
  X86_ONLY(declare_toplevel_type(MethodHandles::RicochetFrame))           \
                                                                          \
  declare_toplevel_type(CodeBlob)                                         \
  declare_type(BufferBlob,               CodeBlob)                        \
  declare_type(AdapterBlob,              BufferBlob)                      \
  declare_type(MethodHandlesAdapterBlob, BufferBlob)                      \
  declare_type(nmethod,                  CodeBlob)                        \
  declare_type(RuntimeStub,              CodeBlob)                        \
  declare_type(SingletonBlob,            CodeBlob)                        \
  declare_type(SafepointBlob,            SingletonBlob)                   \
  declare_type(DeoptimizationBlob,       SingletonBlob)                   \
  declare_type(RicochetBlob,             SingletonBlob)                   \
  declare_c2_type(ExceptionBlob,         SingletonBlob)                   \
  declare_c2_type(UncommonTrapBlob,      CodeBlob)                        \
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
                                                                          \
  /************************/                                              \
  /* OopMap and OopMapSet */                                              \
  /************************/                                              \
                                                                          \
  declare_toplevel_type(OopMap)                                           \
  declare_toplevel_type(OopMapSet)                                        \
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
  /**********************/                                                \
  /* Runtime1 (C1 only) */                                                \
  /**********************/                                                \
                                                                          \
  declare_c1_toplevel_type(Runtime1)                                      \
                                                                          \
  /************/                                                          \
  /* Monitors */                                                          \
  /************/                                                          \
                                                                          \
  declare_toplevel_type(ObjectMonitor)                                    \
  declare_toplevel_type(ObjectSynchronizer)                               \
  declare_toplevel_type(BasicLock)                                        \
  declare_toplevel_type(BasicObjectLock)                                  \
                                                                          \
  /*********************/                                                 \
  /* Matcher (C2 only) */                                                 \
  /*********************/                                                 \
                                                                          \
  declare_c2_toplevel_type(Matcher)                                       \
  declare_c2_toplevel_type(Compile)                                       \
  declare_c2_toplevel_type(InlineTree)                                    \
  declare_c2_toplevel_type(OptoRegPair)                                   \
  declare_c2_toplevel_type(JVMState)                                      \
  declare_c2_toplevel_type(Phase)                                         \
    declare_c2_type(PhaseCFG, Phase)                                      \
    declare_c2_type(PhaseRegAlloc, Phase)                                 \
    declare_c2_type(PhaseChaitin, PhaseRegAlloc)                          \
  declare_c2_toplevel_type(CFGElement)                                    \
    declare_c2_type(Block, CFGElement)                                    \
  declare_c2_toplevel_type(Block_Array)                                   \
    declare_c2_type(Block_List, Block_Array)                              \
  declare_c2_toplevel_type(Node_Array)                                    \
  declare_c2_type(Node_List, Node_Array)                                  \
  declare_c2_type(Unique_Node_List, Node_List)                            \
  declare_c2_toplevel_type(Node)                                          \
  declare_c2_type(AddNode, Node)                                          \
  declare_c2_type(AddINode, AddNode)                                      \
  declare_c2_type(AddLNode, AddNode)                                      \
  declare_c2_type(AddFNode, AddNode)                                      \
  declare_c2_type(AddDNode, AddNode)                                      \
  declare_c2_type(AddPNode, Node)                                         \
  declare_c2_type(OrINode, AddNode)                                       \
  declare_c2_type(OrLNode, AddNode)                                       \
  declare_c2_type(XorINode, AddNode)                                      \
  declare_c2_type(XorLNode, AddNode)                                      \
  declare_c2_type(MaxNode, AddNode)                                       \
  declare_c2_type(MaxINode, MaxNode)                                      \
  declare_c2_type(MinINode, MaxNode)                                      \
  declare_c2_type(StartNode, MultiNode)                                   \
  declare_c2_type(StartOSRNode, StartNode)                                \
  declare_c2_type(ParmNode, ProjNode)                                     \
  declare_c2_type(ReturnNode, Node)                                       \
  declare_c2_type(RethrowNode, Node)                                      \
  declare_c2_type(TailCallNode, ReturnNode)                               \
  declare_c2_type(TailJumpNode, ReturnNode)                               \
  declare_c2_type(SafePointNode, MultiNode)                               \
  declare_c2_type(CallNode, SafePointNode)                                \
  declare_c2_type(CallJavaNode, CallNode)                                 \
  declare_c2_type(CallStaticJavaNode, CallJavaNode)                       \
  declare_c2_type(CallDynamicJavaNode, CallJavaNode)                      \
  declare_c2_type(CallRuntimeNode, CallNode)                              \
  declare_c2_type(CallLeafNode, CallRuntimeNode)                          \
  declare_c2_type(CallLeafNoFPNode, CallLeafNode)                         \
  declare_c2_type(AllocateNode, CallNode)                                 \
  declare_c2_type(AllocateArrayNode, AllocateNode)                        \
  declare_c2_type(LockNode, AbstractLockNode)                             \
  declare_c2_type(UnlockNode, AbstractLockNode)                           \
  declare_c2_type(FastLockNode, CmpNode)                                  \
  declare_c2_type(FastUnlockNode, CmpNode)                                \
  declare_c2_type(RegionNode, Node)                                       \
  declare_c2_type(JProjNode, ProjNode)                                    \
  declare_c2_type(PhiNode, TypeNode)                                      \
  declare_c2_type(GotoNode, Node)                                         \
  declare_c2_type(CProjNode, ProjNode)                                    \
  declare_c2_type(MultiBranchNode, MultiNode)                             \
  declare_c2_type(IfNode, MultiBranchNode)                                \
  declare_c2_type(IfTrueNode, CProjNode)                                  \
  declare_c2_type(IfFalseNode, CProjNode)                                 \
  declare_c2_type(PCTableNode, MultiBranchNode)                           \
  declare_c2_type(JumpNode, PCTableNode)                                  \
  declare_c2_type(JumpProjNode, JProjNode)                                \
  declare_c2_type(CatchNode, PCTableNode)                                 \
  declare_c2_type(CatchProjNode, CProjNode)                               \
  declare_c2_type(CreateExNode, TypeNode)                                 \
  declare_c2_type(ClearArrayNode, Node)                                   \
  declare_c2_type(NeverBranchNode, MultiBranchNode)                       \
  declare_c2_type(ConNode, TypeNode)                                      \
  declare_c2_type(ConINode, ConNode)                                      \
  declare_c2_type(ConPNode, ConNode)                                      \
  declare_c2_type(ConNNode, ConNode)                                      \
  declare_c2_type(ConLNode, ConNode)                                      \
  declare_c2_type(ConFNode, ConNode)                                      \
  declare_c2_type(ConDNode, ConNode)                                      \
  declare_c2_type(BinaryNode, Node)                                       \
  declare_c2_type(CMoveNode, TypeNode)                                    \
  declare_c2_type(CMoveDNode, CMoveNode)                                  \
  declare_c2_type(CMoveFNode, CMoveNode)                                  \
  declare_c2_type(CMoveINode, CMoveNode)                                  \
  declare_c2_type(CMoveLNode, CMoveNode)                                  \
  declare_c2_type(CMovePNode, CMoveNode)                                  \
  declare_c2_type(CMoveNNode, CMoveNode)                                  \
  declare_c2_type(EncodePNode, TypeNode)                                  \
  declare_c2_type(DecodeNNode, TypeNode)                                  \
  declare_c2_type(ConstraintCastNode, TypeNode)                           \
  declare_c2_type(CastIINode, ConstraintCastNode)                         \
  declare_c2_type(CastPPNode, ConstraintCastNode)                         \
  declare_c2_type(CheckCastPPNode, TypeNode)                              \
  declare_c2_type(Conv2BNode, Node)                                       \
  declare_c2_type(ConvD2FNode, Node)                                      \
  declare_c2_type(ConvD2INode, Node)                                      \
  declare_c2_type(ConvD2LNode, Node)                                      \
  declare_c2_type(ConvF2DNode, Node)                                      \
  declare_c2_type(ConvF2INode, Node)                                      \
  declare_c2_type(ConvF2LNode, Node)                                      \
  declare_c2_type(ConvI2DNode, Node)                                      \
  declare_c2_type(ConvI2FNode, Node)                                      \
  declare_c2_type(ConvI2LNode, TypeNode)                                  \
  declare_c2_type(ConvL2DNode, Node)                                      \
  declare_c2_type(ConvL2FNode, Node)                                      \
  declare_c2_type(ConvL2INode, Node)                                      \
  declare_c2_type(CastX2PNode, Node)                                      \
  declare_c2_type(CastP2XNode, Node)                                      \
  declare_c2_type(MemBarNode, MultiNode)                                  \
  declare_c2_type(MemBarAcquireNode, MemBarNode)                          \
  declare_c2_type(MemBarReleaseNode, MemBarNode)                          \
  declare_c2_type(MemBarVolatileNode, MemBarNode)                         \
  declare_c2_type(MemBarCPUOrderNode, MemBarNode)                         \
  declare_c2_type(InitializeNode, MemBarNode)                             \
  declare_c2_type(ThreadLocalNode, Node)                                  \
  declare_c2_type(Opaque1Node, Node)                                      \
  declare_c2_type(Opaque2Node, Node)                                      \
  declare_c2_type(PartialSubtypeCheckNode, Node)                          \
  declare_c2_type(MoveI2FNode, Node)                                      \
  declare_c2_type(MoveL2DNode, Node)                                      \
  declare_c2_type(MoveF2INode, Node)                                      \
  declare_c2_type(MoveD2LNode, Node)                                      \
  declare_c2_type(DivINode, Node)                                         \
  declare_c2_type(DivLNode, Node)                                         \
  declare_c2_type(DivFNode, Node)                                         \
  declare_c2_type(DivDNode, Node)                                         \
  declare_c2_type(ModINode, Node)                                         \
  declare_c2_type(ModLNode, Node)                                         \
  declare_c2_type(ModFNode, Node)                                         \
  declare_c2_type(ModDNode, Node)                                         \
  declare_c2_type(DivModNode, MultiNode)                                  \
  declare_c2_type(DivModINode, DivModNode)                                \
  declare_c2_type(DivModLNode, DivModNode)                                \
  declare_c2_type(BoxLockNode, Node)                                      \
  declare_c2_type(LoopNode, RegionNode)                                   \
  declare_c2_type(CountedLoopNode, LoopNode)                              \
  declare_c2_type(CountedLoopEndNode, IfNode)                             \
  declare_c2_type(MachNode, Node)                                         \
  declare_c2_type(MachIdealNode, MachNode)                                \
  declare_c2_type(MachTypeNode, MachNode)                                 \
  declare_c2_type(MachBreakpointNode, MachIdealNode)                      \
  declare_c2_type(MachUEPNode, MachIdealNode)                             \
  declare_c2_type(MachPrologNode, MachIdealNode)                          \
  declare_c2_type(MachEpilogNode, MachIdealNode)                          \
  declare_c2_type(MachNopNode, MachIdealNode)                             \
  declare_c2_type(MachSpillCopyNode, MachIdealNode)                       \
  declare_c2_type(MachNullCheckNode, MachIdealNode)                       \
  declare_c2_type(MachProjNode, ProjNode)                                 \
  declare_c2_type(MachIfNode, MachNode)                                   \
  declare_c2_type(MachFastLockNode, MachNode)                             \
  declare_c2_type(MachReturnNode, MachNode)                               \
  declare_c2_type(MachSafePointNode, MachReturnNode)                      \
  declare_c2_type(MachCallNode, MachSafePointNode)                        \
  declare_c2_type(MachCallJavaNode, MachCallNode)                         \
  declare_c2_type(MachCallStaticJavaNode, MachCallJavaNode)               \
  declare_c2_type(MachCallDynamicJavaNode, MachCallJavaNode)              \
  declare_c2_type(MachCallRuntimeNode, MachCallNode)                      \
  declare_c2_type(MachHaltNode, MachReturnNode)                           \
  declare_c2_type(MachTempNode, MachNode)                                 \
  declare_c2_type(MemNode, Node)                                          \
  declare_c2_type(MergeMemNode, Node)                                     \
  declare_c2_type(LoadNode, MemNode)                                      \
  declare_c2_type(LoadBNode, LoadNode)                                    \
  declare_c2_type(LoadUSNode, LoadNode)                                   \
  declare_c2_type(LoadINode, LoadNode)                                    \
  declare_c2_type(LoadRangeNode, LoadINode)                               \
  declare_c2_type(LoadLNode, LoadNode)                                    \
  declare_c2_type(LoadL_unalignedNode, LoadLNode)                         \
  declare_c2_type(LoadFNode, LoadNode)                                    \
  declare_c2_type(LoadDNode, LoadNode)                                    \
  declare_c2_type(LoadD_unalignedNode, LoadDNode)                         \
  declare_c2_type(LoadPNode, LoadNode)                                    \
  declare_c2_type(LoadNNode, LoadNode)                                    \
  declare_c2_type(LoadKlassNode, LoadPNode)                               \
  declare_c2_type(LoadNKlassNode, LoadNNode)                              \
  declare_c2_type(LoadSNode, LoadNode)                                    \
  declare_c2_type(StoreNode, MemNode)                                     \
  declare_c2_type(StoreBNode, StoreNode)                                  \
  declare_c2_type(StoreCNode, StoreNode)                                  \
  declare_c2_type(StoreINode, StoreNode)                                  \
  declare_c2_type(StoreLNode, StoreNode)                                  \
  declare_c2_type(StoreFNode, StoreNode)                                  \
  declare_c2_type(StoreDNode, StoreNode)                                  \
  declare_c2_type(StorePNode, StoreNode)                                  \
  declare_c2_type(StoreNNode, StoreNode)                                  \
  declare_c2_type(StoreCMNode, StoreNode)                                 \
  declare_c2_type(LoadPLockedNode, LoadPNode)                             \
  declare_c2_type(SCMemProjNode, ProjNode)                                \
  declare_c2_type(LoadStoreNode, Node)                                    \
  declare_c2_type(StorePConditionalNode, LoadStoreNode)                   \
  declare_c2_type(StoreLConditionalNode, LoadStoreNode)                   \
  declare_c2_type(CompareAndSwapLNode, LoadStoreNode)                     \
  declare_c2_type(CompareAndSwapINode, LoadStoreNode)                     \
  declare_c2_type(CompareAndSwapPNode, LoadStoreNode)                     \
  declare_c2_type(CompareAndSwapNNode, LoadStoreNode)                     \
  declare_c2_type(PrefetchReadNode, Node)                                 \
  declare_c2_type(PrefetchWriteNode, Node)                                \
  declare_c2_type(MulNode, Node)                                          \
  declare_c2_type(MulINode, MulNode)                                      \
  declare_c2_type(MulLNode, MulNode)                                      \
  declare_c2_type(MulFNode, MulNode)                                      \
  declare_c2_type(MulDNode, MulNode)                                      \
  declare_c2_type(MulHiLNode, Node)                                       \
  declare_c2_type(AndINode, MulINode)                                     \
  declare_c2_type(AndLNode, MulLNode)                                     \
  declare_c2_type(LShiftINode, Node)                                      \
  declare_c2_type(LShiftLNode, Node)                                      \
  declare_c2_type(RShiftINode, Node)                                      \
  declare_c2_type(RShiftLNode, Node)                                      \
  declare_c2_type(URShiftINode, Node)                                     \
  declare_c2_type(URShiftLNode, Node)                                     \
  declare_c2_type(MultiNode, Node)                                        \
  declare_c2_type(ProjNode, Node)                                         \
  declare_c2_type(TypeNode, Node)                                         \
  declare_c2_type(NodeHash, StackObj)                                     \
  declare_c2_type(RootNode, LoopNode)                                     \
  declare_c2_type(HaltNode, Node)                                         \
  declare_c2_type(SubNode, Node)                                          \
  declare_c2_type(SubINode, SubNode)                                      \
  declare_c2_type(SubLNode, SubNode)                                      \
  declare_c2_type(SubFPNode, SubNode)                                     \
  declare_c2_type(SubFNode, SubFPNode)                                    \
  declare_c2_type(SubDNode, SubFPNode)                                    \
  declare_c2_type(CmpNode, SubNode)                                       \
  declare_c2_type(CmpINode, CmpNode)                                      \
  declare_c2_type(CmpUNode, CmpNode)                                      \
  declare_c2_type(CmpPNode, CmpNode)                                      \
  declare_c2_type(CmpNNode, CmpNode)                                      \
  declare_c2_type(CmpLNode, CmpNode)                                      \
  declare_c2_type(CmpL3Node, CmpLNode)                                    \
  declare_c2_type(CmpFNode, CmpNode)                                      \
  declare_c2_type(CmpF3Node, CmpFNode)                                    \
  declare_c2_type(CmpDNode, CmpNode)                                      \
  declare_c2_type(CmpD3Node, CmpDNode)                                    \
  declare_c2_type(BoolNode, Node)                                         \
  declare_c2_type(AbsNode, Node)                                          \
  declare_c2_type(AbsINode, AbsNode)                                      \
  declare_c2_type(AbsFNode, AbsNode)                                      \
  declare_c2_type(AbsDNode, AbsNode)                                      \
  declare_c2_type(CmpLTMaskNode, Node)                                    \
  declare_c2_type(NegNode, Node)                                          \
  declare_c2_type(NegFNode, NegNode)                                      \
  declare_c2_type(NegDNode, NegNode)                                      \
  declare_c2_type(CosDNode, Node)                                         \
  declare_c2_type(SinDNode, Node)                                         \
  declare_c2_type(TanDNode, Node)                                         \
  declare_c2_type(AtanDNode, Node)                                        \
  declare_c2_type(SqrtDNode, Node)                                        \
  declare_c2_type(ExpDNode, Node)                                         \
  declare_c2_type(LogDNode, Node)                                         \
  declare_c2_type(Log10DNode, Node)                                       \
  declare_c2_type(PowDNode, Node)                                         \
  declare_c2_type(ReverseBytesINode, Node)                                \
  declare_c2_type(ReverseBytesLNode, Node)                                \
  declare_c2_type(VectorNode, Node)                                       \
  declare_c2_type(AddVBNode, VectorNode)                                  \
  declare_c2_type(AddVCNode, VectorNode)                                  \
  declare_c2_type(AddVSNode, VectorNode)                                  \
  declare_c2_type(AddVINode, VectorNode)                                  \
  declare_c2_type(AddVLNode, VectorNode)                                  \
  declare_c2_type(AddVFNode, VectorNode)                                  \
  declare_c2_type(AddVDNode, VectorNode)                                  \
  declare_c2_type(SubVBNode, VectorNode)                                  \
  declare_c2_type(SubVCNode, VectorNode)                                  \
  declare_c2_type(SubVSNode, VectorNode)                                  \
  declare_c2_type(SubVINode, VectorNode)                                  \
  declare_c2_type(SubVLNode, VectorNode)                                  \
  declare_c2_type(SubVFNode, VectorNode)                                  \
  declare_c2_type(SubVDNode, VectorNode)                                  \
  declare_c2_type(MulVFNode, VectorNode)                                  \
  declare_c2_type(MulVDNode, VectorNode)                                  \
  declare_c2_type(DivVFNode, VectorNode)                                  \
  declare_c2_type(DivVDNode, VectorNode)                                  \
  declare_c2_type(LShiftVBNode, VectorNode)                               \
  declare_c2_type(LShiftVCNode, VectorNode)                               \
  declare_c2_type(LShiftVSNode, VectorNode)                               \
  declare_c2_type(LShiftVINode, VectorNode)                               \
  declare_c2_type(URShiftVBNode, VectorNode)                              \
  declare_c2_type(URShiftVCNode, VectorNode)                              \
  declare_c2_type(URShiftVSNode, VectorNode)                              \
  declare_c2_type(URShiftVINode, VectorNode)                              \
  declare_c2_type(AndVNode, VectorNode)                                   \
  declare_c2_type(OrVNode, VectorNode)                                    \
  declare_c2_type(XorVNode, VectorNode)                                   \
  declare_c2_type(VectorLoadNode, LoadNode)                               \
  declare_c2_type(Load16BNode, VectorLoadNode)                            \
  declare_c2_type(Load8BNode, VectorLoadNode)                             \
  declare_c2_type(Load4BNode, VectorLoadNode)                             \
  declare_c2_type(Load8CNode, VectorLoadNode)                             \
  declare_c2_type(Load4CNode, VectorLoadNode)                             \
  declare_c2_type(Load2CNode, VectorLoadNode)                             \
  declare_c2_type(Load8SNode, VectorLoadNode)                             \
  declare_c2_type(Load4SNode, VectorLoadNode)                             \
  declare_c2_type(Load2SNode, VectorLoadNode)                             \
  declare_c2_type(Load4INode, VectorLoadNode)                             \
  declare_c2_type(Load2INode, VectorLoadNode)                             \
  declare_c2_type(Load2LNode, VectorLoadNode)                             \
  declare_c2_type(Load4FNode, VectorLoadNode)                             \
  declare_c2_type(Load2FNode, VectorLoadNode)                             \
  declare_c2_type(Load2DNode, VectorLoadNode)                             \
  declare_c2_type(VectorStoreNode, StoreNode)                             \
  declare_c2_type(Store16BNode, VectorStoreNode)                          \
  declare_c2_type(Store8BNode, VectorStoreNode)                           \
  declare_c2_type(Store4BNode, VectorStoreNode)                           \
  declare_c2_type(Store8CNode, VectorStoreNode)                           \
  declare_c2_type(Store4CNode, VectorStoreNode)                           \
  declare_c2_type(Store2CNode, VectorStoreNode)                           \
  declare_c2_type(Store4INode, VectorStoreNode)                           \
  declare_c2_type(Store2INode, VectorStoreNode)                           \
  declare_c2_type(Store2LNode, VectorStoreNode)                           \
  declare_c2_type(Store4FNode, VectorStoreNode)                           \
  declare_c2_type(Store2FNode, VectorStoreNode)                           \
  declare_c2_type(Store2DNode, VectorStoreNode)                           \
  declare_c2_type(Replicate16BNode, VectorNode)                           \
  declare_c2_type(Replicate8BNode, VectorNode)                            \
  declare_c2_type(Replicate4BNode, VectorNode)                            \
  declare_c2_type(Replicate8CNode, VectorNode)                            \
  declare_c2_type(Replicate4CNode, VectorNode)                            \
  declare_c2_type(Replicate2CNode, VectorNode)                            \
  declare_c2_type(Replicate8SNode, VectorNode)                            \
  declare_c2_type(Replicate4SNode, VectorNode)                            \
  declare_c2_type(Replicate2SNode, VectorNode)                            \
  declare_c2_type(Replicate4INode, VectorNode)                            \
  declare_c2_type(Replicate2INode, VectorNode)                            \
  declare_c2_type(Replicate2LNode, VectorNode)                            \
  declare_c2_type(Replicate4FNode, VectorNode)                            \
  declare_c2_type(Replicate2FNode, VectorNode)                            \
  declare_c2_type(Replicate2DNode, VectorNode)                            \
  declare_c2_type(PackNode, VectorNode)                                   \
  declare_c2_type(PackBNode, PackNode)                                    \
  declare_c2_type(PackCNode, PackNode)                                    \
  declare_c2_type(PackSNode, PackNode)                                    \
  declare_c2_type(PackINode, PackNode)                                    \
  declare_c2_type(PackLNode, PackNode)                                    \
  declare_c2_type(PackFNode, PackNode)                                    \
  declare_c2_type(PackDNode, PackNode)                                    \
  declare_c2_type(Pack2x1BNode, PackNode)                                 \
  declare_c2_type(Pack2x2BNode, PackNode)                                 \
  declare_c2_type(ExtractNode, Node)                                      \
  declare_c2_type(ExtractBNode, ExtractNode)                              \
  declare_c2_type(ExtractCNode, ExtractNode)                              \
  declare_c2_type(ExtractSNode, ExtractNode)                              \
  declare_c2_type(ExtractINode, ExtractNode)                              \
  declare_c2_type(ExtractLNode, ExtractNode)                              \
  declare_c2_type(ExtractFNode, ExtractNode)                              \
  declare_c2_type(ExtractDNode, ExtractNode)                              \
                                                                          \
  /*********************/                                                 \
  /* Adapter Blob Entries */                                              \
  /*********************/                                                 \
  declare_toplevel_type(AdapterHandlerEntry)                              \
  declare_toplevel_type(AdapterHandlerEntry*)                             \
                                                                          \
  /*********************/                                                 \
  /* CI */                                                                \
  /*********************/                                                 \
  declare_toplevel_type(ciEnv)                                            \
  declare_toplevel_type(ciObjectFactory)                                  \
  declare_toplevel_type(ciConstant)                                       \
  declare_toplevel_type(ciField)                                          \
  declare_toplevel_type(void*)                                            \
  declare_toplevel_type(ciObject)                                         \
  declare_type(ciMethod, ciObject)                                        \
  declare_type(ciMethodData, ciObject)                                    \
  declare_type(ciType, ciObject)                                          \
  declare_type(ciInstance, ciObject)                                      \
  declare_toplevel_type(ciSymbol)                                         \
  declare_type(ciKlass, ciType)                                           \
  declare_type(ciInstanceKlass, ciKlass)                                  \
  declare_type(ciArrayKlass, ciKlass)                                     \
  declare_type(ciTypeArrayKlass, ciArrayKlass)                            \
  declare_type(ciObjArrayKlass, ciArrayKlass)                             \
  declare_type(ciMethodKlass, ciKlass)                                    \
  declare_type(ciKlassKlass, ciKlass)                                     \
  declare_type(ciInstanceKlassKlass, ciKlassKlass)                        \
  declare_type(ciArrayKlassKlass, ciKlassKlass)                           \
  declare_type(ciTypeArrayKlassKlass, ciArrayKlassKlass)                  \
  declare_type(ciObjArrayKlassKlass, ciArrayKlassKlass)                   \
                                                                          \
  /********************/                                                  \
  /* -XX flags        */                                                  \
  /********************/                                                  \
                                                                          \
  declare_toplevel_type(Flag)                                             \
  declare_toplevel_type(Flag*)                                            \
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
  declare_toplevel_type(JDK_Version)                                      \
                                                                          \
  /*************/                                                         \
  /* Arguments */                                                         \
  /*************/                                                         \
                                                                          \
  declare_toplevel_type(Arguments)                                        \
                                                                          \
  /***************/                                                       \
  /* Other types */                                                       \
  /***************/                                                       \
                                                                          \
  /* all enum types */                                                    \
                                                                          \
   declare_integer_type(Bytecodes::Code)                                  \
   declare_integer_type(Generation::Name)                                 \
   declare_integer_type(instanceKlass::ClassState)                        \
   declare_integer_type(JavaThreadState)                                  \
   declare_integer_type(Location::Type)                                   \
   declare_integer_type(Location::Where)                                  \
   declare_integer_type(PermGen::Name)                                    \
   declare_integer_type(FlagValueOrigin)                                  \
   COMPILER2_PRESENT(declare_integer_type(OptoReg::Name))                 \
                                                                          \
   declare_integer_type(AccessFlags)  /* FIXME: wrong type (not integer) */\
  declare_toplevel_type(address)      /* FIXME: should this be an integer type? */\
   declare_integer_type(BasicType)   /* FIXME: wrong type (not integer) */\
  declare_toplevel_type(BreakpointInfo)                                   \
  declare_toplevel_type(BreakpointInfo*)                                  \
  declare_toplevel_type(CodeBlob*)                                        \
  declare_toplevel_type(CompressedWriteStream*)                           \
  declare_toplevel_type(ConstantPoolCacheEntry)                           \
  declare_toplevel_type(elapsedTimer)                                     \
  declare_toplevel_type(frame)                                            \
  declare_toplevel_type(intptr_t*)                                        \
   declare_unsigned_integer_type(InvocationCounter) /* FIXME: wrong type (not integer) */ \
  declare_toplevel_type(JavaThread*)                                      \
  declare_toplevel_type(java_lang_Class)                                  \
  declare_integer_type(JavaThread::AsyncRequests)                         \
  declare_toplevel_type(jbyte*)                                           \
  declare_toplevel_type(jbyte**)                                          \
  declare_toplevel_type(jint*)                                            \
  declare_toplevel_type(jniIdMapBase*)                                    \
  declare_unsigned_integer_type(juint)                                    \
  declare_unsigned_integer_type(julong)                                   \
  declare_toplevel_type(JNIHandleBlock*)                                  \
  declare_toplevel_type(JNIid)                                            \
  declare_toplevel_type(JNIid*)                                           \
  declare_toplevel_type(jmethodID*)                                       \
  declare_toplevel_type(Mutex*)                                           \
  declare_toplevel_type(nmethod*)                                         \
  COMPILER2_PRESENT(declare_unsigned_integer_type(node_idx_t))            \
  declare_toplevel_type(ObjectMonitor*)                                   \
  declare_toplevel_type(oop*)                                             \
  declare_toplevel_type(OopMap**)                                         \
  declare_toplevel_type(OopMapCache*)                                     \
  declare_toplevel_type(OopMapSet*)                                       \
  declare_toplevel_type(VMReg)                                            \
  declare_toplevel_type(OSThread*)                                        \
   declare_integer_type(ReferenceType)                                    \
  declare_toplevel_type(StubQueue*)                                       \
  declare_toplevel_type(Thread*)                                          \
  declare_toplevel_type(Universe)                                         \
  declare_toplevel_type(vframeArray)                                      \
  declare_toplevel_type(vframeArrayElement)


  /* NOTE that we do not use the last_entry() macro here; it is used  */
  /* in vmStructs_<os>_<cpu>.hpp's VM_TYPES_OS_CPU macro (and must be */
  /* present there)                                                   */

//--------------------------------------------------------------------------------
// VM_INT_CONSTANTS
//
// This table contains integer constants required over in the
// serviceability agent. The "declare_constant" macro is used for all
// enums, etc., while "declare_preprocessor_constant" must be used for
// all #defined constants.

#define VM_INT_CONSTANTS(declare_constant,                                \
                         declare_preprocessor_constant,                   \
                         declare_c1_constant,                             \
                         declare_c2_constant,                             \
                         declare_c2_preprocessor_constant,                \
                         last_entry)                                      \
                                                                          \
  /******************/                                                    \
  /* Useful globals */                                                    \
  /******************/                                                    \
                                                                          \
  declare_constant(UseTLAB)                                               \
                                                                          \
  /**************/                                                        \
  /* Stack bias */                                                        \
  /**************/                                                        \
                                                                          \
  declare_preprocessor_constant("STACK_BIAS", STACK_BIAS)                 \
                                                                          \
  /****************/                                                      \
  /* Object sizes */                                                      \
  /****************/                                                      \
                                                                          \
  declare_constant(oopSize)                                               \
  declare_constant(LogBytesPerWord)                                       \
  declare_constant(BytesPerLong)                                          \
                                                                          \
  /********************************************/                          \
  /* Generation and Space Hierarchy Constants */                          \
  /********************************************/                          \
                                                                          \
  declare_constant(ageTable::table_size)                                  \
                                                                          \
  declare_constant(BarrierSet::ModRef)                                    \
  declare_constant(BarrierSet::CardTableModRef)                           \
  declare_constant(BarrierSet::Other)                                     \
                                                                          \
  declare_constant(BlockOffsetSharedArray::LogN)                          \
  declare_constant(BlockOffsetSharedArray::LogN_words)                    \
  declare_constant(BlockOffsetSharedArray::N_bytes)                       \
  declare_constant(BlockOffsetSharedArray::N_words)                       \
                                                                          \
  declare_constant(BlockOffsetArray::N_words)                             \
                                                                          \
  declare_constant(CardTableModRefBS::clean_card)                         \
  declare_constant(CardTableModRefBS::last_card)                          \
  declare_constant(CardTableModRefBS::dirty_card)                         \
  declare_constant(CardTableModRefBS::Precise)                            \
  declare_constant(CardTableModRefBS::ObjHeadPreciseArray)                \
  declare_constant(CardTableModRefBS::card_shift)                         \
  declare_constant(CardTableModRefBS::card_size)                          \
  declare_constant(CardTableModRefBS::card_size_in_words)                 \
                                                                          \
  declare_constant(CardTableRS::youngergen_card)                          \
                                                                          \
  declare_constant(CollectedHeap::Abstract)                               \
  declare_constant(CollectedHeap::SharedHeap)                             \
  declare_constant(CollectedHeap::GenCollectedHeap)                       \
                                                                          \
  declare_constant(GenCollectedHeap::max_gens)                            \
                                                                          \
  /* constants from Generation::Name enum */                              \
                                                                          \
  declare_constant(Generation::DefNew)                                    \
  declare_constant(Generation::MarkSweepCompact)                          \
  declare_constant(Generation::Other)                                     \
                                                                          \
  declare_constant(Generation::LogOfGenGrain)                             \
  declare_constant(Generation::GenGrain)                                  \
                                                                          \
  declare_constant(HeapWordSize)                                          \
  declare_constant(LogHeapWordSize)                                       \
                                                                          \
  /* constants from PermGen::Name enum */                                 \
                                                                          \
  declare_constant(PermGen::MarkSweepCompact)                             \
  declare_constant(PermGen::MarkSweep)                                    \
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
  /***************/                                                       \
  /* SymbolTable */                                                       \
  /***************/                                                       \
                                                                          \
  declare_constant(SymbolTable::symbol_table_size)                        \
                                                                          \
  /***********************************/                                   \
  /* LoaderConstraintTable constants */                                   \
  /***********************************/                                   \
                                                                          \
  declare_constant(LoaderConstraintTable::_loader_constraint_size)        \
  declare_constant(LoaderConstraintTable::_nof_buckets)                   \
                                                                          \
  /************************************************************/          \
  /* HotSpot specific JVM_ACC constants from global anon enum */          \
  /************************************************************/          \
                                                                          \
  declare_constant(JVM_ACC_WRITTEN_FLAGS)                                 \
  declare_constant(JVM_ACC_MONITOR_MATCH)                                 \
  declare_constant(JVM_ACC_HAS_MONITOR_BYTECODES)                         \
  declare_constant(JVM_ACC_HAS_LOOPS)                                     \
  declare_constant(JVM_ACC_LOOPS_FLAG_INIT)                               \
  declare_constant(JVM_ACC_QUEUED)                                        \
  declare_constant(JVM_ACC_NOT_OSR_COMPILABLE)                            \
  declare_constant(JVM_ACC_HAS_LINE_NUMBER_TABLE)                         \
  declare_constant(JVM_ACC_HAS_CHECKED_EXCEPTIONS)                        \
  declare_constant(JVM_ACC_HAS_JSRS)                                      \
  declare_constant(JVM_ACC_IS_OLD)                                        \
  declare_constant(JVM_ACC_IS_OBSOLETE)                                   \
  declare_constant(JVM_ACC_IS_PREFIXED_NATIVE)                            \
  declare_constant(JVM_ACC_HAS_MIRANDA_METHODS)                           \
  declare_constant(JVM_ACC_HAS_VANILLA_CONSTRUCTOR)                       \
  declare_constant(JVM_ACC_HAS_FINALIZER)                                 \
  declare_constant(JVM_ACC_IS_CLONEABLE)                                  \
  declare_constant(JVM_ACC_HAS_LOCAL_VARIABLE_TABLE)                      \
  declare_constant(JVM_ACC_PROMOTED_FLAGS)                                \
  declare_constant(JVM_ACC_FIELD_ACCESS_WATCHED)                          \
  declare_constant(JVM_ACC_FIELD_MODIFICATION_WATCHED)                    \
                                                                          \
  /*****************************/                                         \
  /* Thread::SuspendFlags enum */                                         \
  /*****************************/                                         \
                                                                          \
  declare_constant(Thread::_external_suspend)                             \
  declare_constant(Thread::_ext_suspended)                                \
  declare_constant(Thread::_has_async_exception)                          \
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
                                                                          \
  /******************************/                                        \
  /* Klass misc. enum constants */                                        \
  /******************************/                                        \
                                                                          \
  declare_constant(Klass::_primary_super_limit)                           \
  declare_constant(Klass::_lh_instance_slow_path_bit)                     \
  declare_constant(Klass::_lh_log2_element_size_shift)                    \
  declare_constant(Klass::_lh_element_type_shift)                         \
  declare_constant(Klass::_lh_header_size_shift)                          \
  declare_constant(Klass::_lh_array_tag_shift)                            \
  declare_constant(Klass::_lh_array_tag_type_value)                       \
  declare_constant(Klass::_lh_array_tag_obj_value)                        \
                                                                          \
  /********************************/                                      \
  /* constMethodOopDesc anon-enum */                                      \
  /********************************/                                      \
                                                                          \
  declare_constant(constMethodOopDesc::_has_linenumber_table)             \
  declare_constant(constMethodOopDesc::_has_checked_exceptions)           \
  declare_constant(constMethodOopDesc::_has_localvariable_table)          \
                                                                          \
  /*************************************/                                 \
  /* instanceKlass enum                */                                 \
  /*************************************/                                 \
                                                                          \
                                                                          \
  /*************************************/                                 \
  /* FieldInfo FieldOffset enum        */                                 \
  /*************************************/                                 \
                                                                          \
  declare_constant(FieldInfo::access_flags_offset)                        \
  declare_constant(FieldInfo::name_index_offset)                          \
  declare_constant(FieldInfo::signature_index_offset)                     \
  declare_constant(FieldInfo::initval_index_offset)                       \
  declare_constant(FieldInfo::low_offset)                                 \
  declare_constant(FieldInfo::high_offset)                                \
  declare_constant(FieldInfo::field_slots)                                \
                                                                          \
  /************************************************/                      \
  /* instanceKlass InnerClassAttributeOffset enum */                      \
  /************************************************/                      \
                                                                          \
  declare_constant(instanceKlass::inner_class_inner_class_info_offset)    \
  declare_constant(instanceKlass::inner_class_outer_class_info_offset)    \
  declare_constant(instanceKlass::inner_class_inner_name_offset)          \
  declare_constant(instanceKlass::inner_class_access_flags_offset)        \
  declare_constant(instanceKlass::inner_class_next_offset)                \
                                                                          \
  /*********************************/                                     \
  /* instanceKlass ClassState enum */                                     \
  /*********************************/                                     \
                                                                          \
  declare_constant(instanceKlass::unparsable_by_gc)                       \
  declare_constant(instanceKlass::allocated)                              \
  declare_constant(instanceKlass::loaded)                                 \
  declare_constant(instanceKlass::linked)                                 \
  declare_constant(instanceKlass::being_initialized)                      \
  declare_constant(instanceKlass::fully_initialized)                      \
  declare_constant(instanceKlass::initialization_error)                   \
                                                                          \
  /*********************************/                                     \
  /* Symbol* - symbol max length */                                     \
  /*********************************/                                     \
                                                                          \
  declare_constant(Symbol::max_symbol_length)                             \
                                                                          \
  /*************************************************/                     \
  /* constantPoolOop layout enum for InvokeDynamic */                     \
  /*************************************************/                     \
                                                                          \
  declare_constant(constantPoolOopDesc::_indy_bsm_offset)                 \
  declare_constant(constantPoolOopDesc::_indy_argc_offset)                \
  declare_constant(constantPoolOopDesc::_indy_argv_offset)                \
                                                                          \
  /*********************************************/                         \
  /* ConstantPoolCacheEntry FlagBitValues enum */                         \
  /*********************************************/                         \
                                                                          \
  declare_constant(ConstantPoolCacheEntry::hotSwapBit)                    \
  declare_constant(ConstantPoolCacheEntry::methodInterface)               \
  declare_constant(ConstantPoolCacheEntry::volatileField)                 \
  declare_constant(ConstantPoolCacheEntry::vfinalMethod)                  \
  declare_constant(ConstantPoolCacheEntry::finalField)                    \
                                                                          \
  /******************************************/                            \
  /* ConstantPoolCacheEntry FlagValues enum */                            \
  /******************************************/                            \
                                                                          \
  declare_constant(ConstantPoolCacheEntry::tosBits)                       \
                                                                          \
  /***************************************/                               \
  /* java_lang_Thread::ThreadStatus enum */                               \
  /***************************************/                               \
                                                                          \
  declare_constant(java_lang_Thread::NEW)                                 \
  declare_constant(java_lang_Thread::RUNNABLE)                            \
  declare_constant(java_lang_Thread::SLEEPING)                            \
  declare_constant(java_lang_Thread::IN_OBJECT_WAIT)                      \
  declare_constant(java_lang_Thread::IN_OBJECT_WAIT_TIMED)                \
  declare_constant(java_lang_Thread::PARKED)                              \
  declare_constant(java_lang_Thread::PARKED_TIMED)                        \
  declare_constant(java_lang_Thread::BLOCKED_ON_MONITOR_ENTER)            \
  declare_constant(java_lang_Thread::TERMINATED)                          \
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
  declare_constant(Deoptimization::Reason_unloaded)                       \
  declare_constant(Deoptimization::Reason_uninitialized)                  \
  declare_constant(Deoptimization::Reason_unreached)                      \
  declare_constant(Deoptimization::Reason_unhandled)                      \
  declare_constant(Deoptimization::Reason_constraint)                     \
  declare_constant(Deoptimization::Reason_div0_check)                     \
  declare_constant(Deoptimization::Reason_age)                            \
  declare_constant(Deoptimization::Reason_predicate)                      \
  declare_constant(Deoptimization::Reason_loop_limit_check)               \
  declare_constant(Deoptimization::Reason_LIMIT)                          \
  declare_constant(Deoptimization::Reason_RECORDED_LIMIT)                 \
                                                                          \
  /*********************/                                                 \
  /* Matcher (C2 only) */                                                 \
  /*********************/                                                 \
                                                                          \
  declare_c2_preprocessor_constant("Matcher::interpreter_frame_pointer_reg", Matcher::interpreter_frame_pointer_reg()) \
                                                                          \
  /*********************************************/                         \
  /* MethodCompilation (globalDefinitions.hpp) */                         \
  /*********************************************/                         \
                                                                          \
  declare_constant(InvocationEntryBci)                                    \
  declare_constant(InvalidOSREntryBci)                                    \
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
  declare_constant(OopMapValue::value_value)                              \
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
  /* ObjectSynchronizer */                                                \
  /**********************/                                                \
                                                                          \
  declare_constant(ObjectSynchronizer::_BLOCKSIZE)                        \
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
                                                                          \
  X86_ONLY(declare_constant(frame::entry_frame_call_wrapper_offset))      \
  declare_constant(frame::pc_return_offset)                               \
                                                                          \
  /*************/                                                         \
  /* vmSymbols */                                                         \
  /*************/                                                         \
                                                                          \
  declare_constant(vmSymbols::FIRST_SID)                                  \
  declare_constant(vmSymbols::SID_LIMIT)                                  \
                                                                          \
  /********************************/                                      \
  /* Calling convention constants */                                      \
  /********************************/                                      \
                                                                          \
  declare_constant(RegisterImpl::number_of_registers)                     \
  declare_constant(ConcreteRegisterImpl::number_of_registers)             \
  declare_preprocessor_constant("REG_COUNT", REG_COUNT)                \
  declare_c2_preprocessor_constant("SAVED_ON_ENTRY_REG_COUNT", SAVED_ON_ENTRY_REG_COUNT) \
  declare_c2_preprocessor_constant("C_SAVED_ON_ENTRY_REG_COUNT", C_SAVED_ON_ENTRY_REG_COUNT)

  /* NOTE that we do not use the last_entry() macro here; it is used  */
  /* in vmStructs_<os>_<cpu>.hpp's VM_INT_CONSTANTS_OS_CPU macro (and */
  /* must be present there)                                           */

//--------------------------------------------------------------------------------
// VM_LONG_CONSTANTS
//
// This table contains long constants required over in the
// serviceability agent. The "declare_constant" macro is used for all
// enums, etc., while "declare_preprocessor_constant" must be used for
// all #defined constants.

#define VM_LONG_CONSTANTS(declare_constant, declare_preprocessor_constant, declare_c1_constant, declare_c2_constant, declare_c2_preprocessor_constant, last_entry) \
                                                                          \
  /*********************/                                                 \
  /* MarkOop constants */                                                 \
  /*********************/                                                 \
                                                                          \
  /* Note: some of these are declared as long constants just for */       \
  /* consistency. The mask constants are the only ones requiring */       \
  /* 64 bits (on 64-bit platforms). */                                    \
                                                                          \
  declare_constant(markOopDesc::age_bits)                                 \
  declare_constant(markOopDesc::lock_bits)                                \
  declare_constant(markOopDesc::biased_lock_bits)                         \
  declare_constant(markOopDesc::max_hash_bits)                            \
  declare_constant(markOopDesc::hash_bits)                                \
                                                                          \
  declare_constant(markOopDesc::lock_shift)                               \
  declare_constant(markOopDesc::biased_lock_shift)                        \
  declare_constant(markOopDesc::age_shift)                                \
  declare_constant(markOopDesc::hash_shift)                               \
                                                                          \
  declare_constant(markOopDesc::lock_mask)                                \
  declare_constant(markOopDesc::lock_mask_in_place)                       \
  declare_constant(markOopDesc::biased_lock_mask)                         \
  declare_constant(markOopDesc::biased_lock_mask_in_place)                \
  declare_constant(markOopDesc::biased_lock_bit_in_place)                 \
  declare_constant(markOopDesc::age_mask)                                 \
  declare_constant(markOopDesc::age_mask_in_place)                        \
  declare_constant(markOopDesc::hash_mask)                                \
  declare_constant(markOopDesc::hash_mask_in_place)                       \
  declare_constant(markOopDesc::biased_lock_alignment)                    \
                                                                          \
  declare_constant(markOopDesc::locked_value)                             \
  declare_constant(markOopDesc::unlocked_value)                           \
  declare_constant(markOopDesc::monitor_value)                            \
  declare_constant(markOopDesc::marked_value)                             \
  declare_constant(markOopDesc::biased_lock_pattern)                      \
                                                                          \
  declare_constant(markOopDesc::no_hash)                                  \
  declare_constant(markOopDesc::no_hash_in_place)                         \
  declare_constant(markOopDesc::no_lock_in_place)                         \
  declare_constant(markOopDesc::max_age)                                  \
                                                                          \
  /* Constants in markOop used by CMS. */                                 \
  declare_constant(markOopDesc::cms_shift)                                \
  declare_constant(markOopDesc::cms_mask)                                 \
  declare_constant(markOopDesc::size_shift)                               \

  /* NOTE that we do not use the last_entry() macro here; it is used   */
  /* in vmStructs_<os>_<cpu>.hpp's VM_LONG_CONSTANTS_OS_CPU macro (and */
  /* must be present there)                                            */


//--------------------------------------------------------------------------------
// Macros operating on the above lists
//--------------------------------------------------------------------------------

// This utility macro quotes the passed string
#define QUOTE(x) #x

//--------------------------------------------------------------------------------
// VMStructEntry macros
//

// This macro generates a VMStructEntry line for a nonstatic field
#define GENERATE_NONSTATIC_VM_STRUCT_ENTRY(typeName, fieldName, type)              \
 { QUOTE(typeName), QUOTE(fieldName), QUOTE(type), 0, cast_uint64_t(offset_of(typeName, fieldName)), NULL },

// This macro generates a VMStructEntry line for a static field
#define GENERATE_STATIC_VM_STRUCT_ENTRY(typeName, fieldName, type)                 \
 { QUOTE(typeName), QUOTE(fieldName), QUOTE(type), 1, 0, &typeName::fieldName },

// This macro generates a VMStructEntry line for an unchecked
// nonstatic field, in which the size of the type is also specified.
// The type string is given as NULL, indicating an "opaque" type.
#define GENERATE_UNCHECKED_NONSTATIC_VM_STRUCT_ENTRY(typeName, fieldName, size)    \
  { QUOTE(typeName), QUOTE(fieldName), NULL, 0, cast_uint64_t(offset_of(typeName, fieldName)), NULL },

// This macro generates a VMStructEntry line for an unchecked
// static field, in which the size of the type is also specified.
// The type string is given as NULL, indicating an "opaque" type.
#define GENERATE_UNCHECKED_STATIC_VM_STRUCT_ENTRY(typeName, fieldName, size)       \
 { QUOTE(typeName), QUOTE(fieldName), NULL, 1, 0, (void*) &typeName::fieldName },

// This macro generates the sentinel value indicating the end of the list
#define GENERATE_VM_STRUCT_LAST_ENTRY() \
 { NULL, NULL, NULL, 0, 0, NULL }

// This macro checks the type of a VMStructEntry by comparing pointer types
#define CHECK_NONSTATIC_VM_STRUCT_ENTRY(typeName, fieldName, type)                 \
 {typeName *dummyObj = NULL; type* dummy = &dummyObj->fieldName; }

// This macro checks the type of a volatile VMStructEntry by comparing pointer types
#define CHECK_VOLATILE_NONSTATIC_VM_STRUCT_ENTRY(typeName, fieldName, type)        \
 {typedef type dummyvtype; typeName *dummyObj = NULL; volatile dummyvtype* dummy = &dummyObj->fieldName; }

// This macro checks the type of a VMStructEntry by comparing pointer types
#define CHECK_STATIC_VM_STRUCT_ENTRY(typeName, fieldName, type)                    \
 {type* dummy = &typeName::fieldName; }

// This macro ensures the type of a field and its containing type are
// present in the type table. The assertion string is shorter than
// preferable because (incredibly) of a bug in Solstice NFS client
// which seems to prevent very long lines from compiling. This assertion
// means that an entry in VMStructs::localHotSpotVMStructs[] was not
// found in VMStructs::localHotSpotVMTypes[].
#define ENSURE_FIELD_TYPE_PRESENT(typeName, fieldName, type)                       \
 { assert(findType(QUOTE(typeName)) != 0, "type \"" QUOTE(typeName) "\" not found in type table"); \
   assert(findType(QUOTE(type)) != 0, "type \"" QUOTE(type) "\" not found in type table"); }

// This is a no-op macro for unchecked fields
#define CHECK_NO_OP(a, b, c)

// This is a no-op macro for the sentinel value
#define CHECK_SENTINEL()

//
// Build-specific macros:
//

// Generate and check a nonstatic field in non-product builds
#ifndef PRODUCT
# define GENERATE_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY(a, b, c) GENERATE_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define CHECK_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)    CHECK_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define ENSURE_NONPRODUCT_FIELD_TYPE_PRESENT(a, b, c)          ENSURE_FIELD_TYPE_PRESENT(a, b, c)
# define GENERATE_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY(a, b, c) GENERATE_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define CHECK_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)    CHECK_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define ENSURE_NONPRODUCT_FIELD_TYPE_PRESENT(a, b, c)          ENSURE_FIELD_TYPE_PRESENT(a, b, c)
#else
# define GENERATE_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define CHECK_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define ENSURE_NONPRODUCT_FIELD_TYPE_PRESENT(a, b, c)
# define GENERATE_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define CHECK_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define ENSURE_NONPRODUCT_FIELD_TYPE_PRESENT(a, b, c)
#endif /* PRODUCT */

// Generate and check a nonstatic field in C1 builds
#ifdef COMPILER1
# define GENERATE_C1_NONSTATIC_VM_STRUCT_ENTRY(a, b, c) GENERATE_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define CHECK_C1_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)    CHECK_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define ENSURE_C1_FIELD_TYPE_PRESENT(a, b, c)          ENSURE_FIELD_TYPE_PRESENT(a, b, c)
#else
# define GENERATE_C1_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define CHECK_C1_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define ENSURE_C1_FIELD_TYPE_PRESENT(a, b, c)
#endif /* COMPILER1 */
// Generate and check a nonstatic field in C2 builds
#ifdef COMPILER2
# define GENERATE_C2_NONSTATIC_VM_STRUCT_ENTRY(a, b, c) GENERATE_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define CHECK_C2_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)    CHECK_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define ENSURE_C2_FIELD_TYPE_PRESENT(a, b, c)          ENSURE_FIELD_TYPE_PRESENT(a, b, c)
#else
# define GENERATE_C2_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define CHECK_C2_NONSTATIC_VM_STRUCT_ENTRY(a, b, c)
# define ENSURE_C2_FIELD_TYPE_PRESENT(a, b, c)
#endif /* COMPILER2 */

// Generate but do not check a static field in C1 builds
#ifdef COMPILER1
# define GENERATE_C1_UNCHECKED_STATIC_VM_STRUCT_ENTRY(a, b, c) GENERATE_UNCHECKED_STATIC_VM_STRUCT_ENTRY(a, b, c)
#else
# define GENERATE_C1_UNCHECKED_STATIC_VM_STRUCT_ENTRY(a, b, c)
#endif /* COMPILER1 */

// Generate but do not check a static field in C2 builds
#ifdef COMPILER2
# define GENERATE_C2_UNCHECKED_STATIC_VM_STRUCT_ENTRY(a, b, c) GENERATE_UNCHECKED_STATIC_VM_STRUCT_ENTRY(a, b, c)
#else
# define GENERATE_C2_UNCHECKED_STATIC_VM_STRUCT_ENTRY(a, b, c)
#endif /* COMPILER2 */

//--------------------------------------------------------------------------------
// VMTypeEntry macros
//

#define GENERATE_VM_TYPE_ENTRY(type, superclass) \
 { QUOTE(type), QUOTE(superclass), 0, 0, 0, sizeof(type) },

#define GENERATE_TOPLEVEL_VM_TYPE_ENTRY(type) \
 { QUOTE(type), NULL,              0, 0, 0, sizeof(type) },

#define GENERATE_OOP_VM_TYPE_ENTRY(type) \
 { QUOTE(type), NULL,              1, 0, 0, sizeof(type) },

#define GENERATE_INTEGER_VM_TYPE_ENTRY(type) \
 { QUOTE(type), NULL,              0, 1, 0, sizeof(type) },

#define GENERATE_UNSIGNED_INTEGER_VM_TYPE_ENTRY(type) \
 { QUOTE(type), NULL,              0, 1, 1, sizeof(type) },

#define GENERATE_VM_TYPE_LAST_ENTRY() \
 { NULL, NULL, 0, 0, 0, 0 }

#define CHECK_VM_TYPE_ENTRY(type, superclass) \
 { type* dummyObj = NULL; superclass* dummySuperObj = dummyObj; }

#define CHECK_VM_TYPE_NO_OP(a)
#define CHECK_SINGLE_ARG_VM_TYPE_NO_OP(a)

//
// Build-specific macros:
//

#ifdef COMPILER1
# define GENERATE_C1_TOPLEVEL_VM_TYPE_ENTRY(a)               GENERATE_TOPLEVEL_VM_TYPE_ENTRY(a)
# define CHECK_C1_TOPLEVEL_VM_TYPE_ENTRY(a)
#else
# define GENERATE_C1_TOPLEVEL_VM_TYPE_ENTRY(a)
# define CHECK_C1_TOPLEVEL_VM_TYPE_ENTRY(a)
#endif /* COMPILER1 */

#ifdef COMPILER2
# define GENERATE_C2_VM_TYPE_ENTRY(a, b)                     GENERATE_VM_TYPE_ENTRY(a, b)
# define CHECK_C2_VM_TYPE_ENTRY(a, b)                        CHECK_VM_TYPE_ENTRY(a, b)
# define GENERATE_C2_TOPLEVEL_VM_TYPE_ENTRY(a)               GENERATE_TOPLEVEL_VM_TYPE_ENTRY(a)
# define CHECK_C2_TOPLEVEL_VM_TYPE_ENTRY(a)
#else
# define GENERATE_C2_VM_TYPE_ENTRY(a, b)
# define CHECK_C2_VM_TYPE_ENTRY(a, b)
# define GENERATE_C2_TOPLEVEL_VM_TYPE_ENTRY(a)
# define CHECK_C2_TOPLEVEL_VM_TYPE_ENTRY(a)
#endif /* COMPILER2 */


//--------------------------------------------------------------------------------
// VMIntConstantEntry macros
//

#define GENERATE_VM_INT_CONSTANT_ENTRY(name) \
 { QUOTE(name), (int32_t) name },

#define GENERATE_PREPROCESSOR_VM_INT_CONSTANT_ENTRY(name, value) \
 { name, (int32_t) value },

// This macro generates the sentinel value indicating the end of the list
#define GENERATE_VM_INT_CONSTANT_LAST_ENTRY() \
 { NULL, 0 }


// Generate an int constant for a C1 build
#ifdef COMPILER1
# define GENERATE_C1_VM_INT_CONSTANT_ENTRY(name)  GENERATE_VM_INT_CONSTANT_ENTRY(name)
#else
# define GENERATE_C1_VM_INT_CONSTANT_ENTRY(name)
#endif /* COMPILER1 */

// Generate an int constant for a C2 build
#ifdef COMPILER2
# define GENERATE_C2_VM_INT_CONSTANT_ENTRY(name)                      GENERATE_VM_INT_CONSTANT_ENTRY(name)
# define GENERATE_C2_PREPROCESSOR_VM_INT_CONSTANT_ENTRY(name, value)  GENERATE_PREPROCESSOR_VM_INT_CONSTANT_ENTRY(name, value)
#else
# define GENERATE_C2_VM_INT_CONSTANT_ENTRY(name)
# define GENERATE_C2_PREPROCESSOR_VM_INT_CONSTANT_ENTRY(name, value)
#endif /* COMPILER1 */

//--------------------------------------------------------------------------------
// VMLongConstantEntry macros
//

#define GENERATE_VM_LONG_CONSTANT_ENTRY(name) \
  { QUOTE(name), cast_uint64_t(name) },

#define GENERATE_PREPROCESSOR_VM_LONG_CONSTANT_ENTRY(name, value) \
  { name, cast_uint64_t(value) },

// This macro generates the sentinel value indicating the end of the list
#define GENERATE_VM_LONG_CONSTANT_LAST_ENTRY() \
 { NULL, 0 }

// Generate a long constant for a C1 build
#ifdef COMPILER1
# define GENERATE_C1_VM_LONG_CONSTANT_ENTRY(name)  GENERATE_VM_LONG_CONSTANT_ENTRY(name)
#else
# define GENERATE_C1_VM_LONG_CONSTANT_ENTRY(name)
#endif /* COMPILER1 */

// Generate a long constant for a C2 build
#ifdef COMPILER2
# define GENERATE_C2_VM_LONG_CONSTANT_ENTRY(name)                     GENERATE_VM_LONG_CONSTANT_ENTRY(name)
# define GENERATE_C2_PREPROCESSOR_VM_LONG_CONSTANT_ENTRY(name, value) GENERATE_PREPROCESSOR_VM_LONG_CONSTANT_ENTRY(name, value)
#else
# define GENERATE_C2_VM_LONG_CONSTANT_ENTRY(name)
# define GENERATE_C2_PREPROCESSOR_VM_LONG_CONSTANT_ENTRY(name, value)
#endif /* COMPILER1 */

//
// Instantiation of VMStructEntries, VMTypeEntries and VMIntConstantEntries
//

// These initializers are allowed to access private fields in classes
// as long as class VMStructs is a friend
VMStructEntry VMStructs::localHotSpotVMStructs[] = {

  VM_STRUCTS(GENERATE_NONSTATIC_VM_STRUCT_ENTRY, \
             GENERATE_STATIC_VM_STRUCT_ENTRY, \
             GENERATE_UNCHECKED_NONSTATIC_VM_STRUCT_ENTRY, \
             GENERATE_NONSTATIC_VM_STRUCT_ENTRY, \
             GENERATE_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY, \
             GENERATE_C1_NONSTATIC_VM_STRUCT_ENTRY, \
             GENERATE_C2_NONSTATIC_VM_STRUCT_ENTRY, \
             GENERATE_C1_UNCHECKED_STATIC_VM_STRUCT_ENTRY, \
             GENERATE_C2_UNCHECKED_STATIC_VM_STRUCT_ENTRY, \
             GENERATE_VM_STRUCT_LAST_ENTRY)

#ifndef SERIALGC
  VM_STRUCTS_PARALLELGC(GENERATE_NONSTATIC_VM_STRUCT_ENTRY, \
                        GENERATE_STATIC_VM_STRUCT_ENTRY)

  VM_STRUCTS_CMS(GENERATE_NONSTATIC_VM_STRUCT_ENTRY, \
                 GENERATE_NONSTATIC_VM_STRUCT_ENTRY, \
                 GENERATE_STATIC_VM_STRUCT_ENTRY)

  VM_STRUCTS_G1(GENERATE_NONSTATIC_VM_STRUCT_ENTRY, \
                GENERATE_STATIC_VM_STRUCT_ENTRY)
#endif // SERIALGC

  VM_STRUCTS_CPU(GENERATE_NONSTATIC_VM_STRUCT_ENTRY, \
                 GENERATE_STATIC_VM_STRUCT_ENTRY, \
                 GENERATE_UNCHECKED_NONSTATIC_VM_STRUCT_ENTRY, \
                 GENERATE_NONSTATIC_VM_STRUCT_ENTRY, \
                 GENERATE_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY, \
                 GENERATE_C2_NONSTATIC_VM_STRUCT_ENTRY, \
                 GENERATE_C1_UNCHECKED_STATIC_VM_STRUCT_ENTRY, \
                 GENERATE_C2_UNCHECKED_STATIC_VM_STRUCT_ENTRY, \
                 GENERATE_VM_STRUCT_LAST_ENTRY)

  VM_STRUCTS_OS_CPU(GENERATE_NONSTATIC_VM_STRUCT_ENTRY, \
                    GENERATE_STATIC_VM_STRUCT_ENTRY, \
                    GENERATE_UNCHECKED_NONSTATIC_VM_STRUCT_ENTRY, \
                    GENERATE_NONSTATIC_VM_STRUCT_ENTRY, \
                    GENERATE_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY, \
                    GENERATE_C2_NONSTATIC_VM_STRUCT_ENTRY, \
                    GENERATE_C1_UNCHECKED_STATIC_VM_STRUCT_ENTRY, \
                    GENERATE_C2_UNCHECKED_STATIC_VM_STRUCT_ENTRY, \
                    GENERATE_VM_STRUCT_LAST_ENTRY)
};

VMTypeEntry VMStructs::localHotSpotVMTypes[] = {

  VM_TYPES(GENERATE_VM_TYPE_ENTRY,
           GENERATE_TOPLEVEL_VM_TYPE_ENTRY,
           GENERATE_OOP_VM_TYPE_ENTRY,
           GENERATE_INTEGER_VM_TYPE_ENTRY,
           GENERATE_UNSIGNED_INTEGER_VM_TYPE_ENTRY,
           GENERATE_C1_TOPLEVEL_VM_TYPE_ENTRY,
           GENERATE_C2_VM_TYPE_ENTRY,
           GENERATE_C2_TOPLEVEL_VM_TYPE_ENTRY,
           GENERATE_VM_TYPE_LAST_ENTRY)

#ifndef SERIALGC
  VM_TYPES_PARALLELGC(GENERATE_VM_TYPE_ENTRY,
                      GENERATE_TOPLEVEL_VM_TYPE_ENTRY)

  VM_TYPES_CMS(GENERATE_VM_TYPE_ENTRY,
               GENERATE_TOPLEVEL_VM_TYPE_ENTRY)

  VM_TYPES_PARNEW(GENERATE_VM_TYPE_ENTRY)

  VM_TYPES_G1(GENERATE_VM_TYPE_ENTRY,
              GENERATE_TOPLEVEL_VM_TYPE_ENTRY)
#endif // SERIALGC

  VM_TYPES_CPU(GENERATE_VM_TYPE_ENTRY,
               GENERATE_TOPLEVEL_VM_TYPE_ENTRY,
               GENERATE_OOP_VM_TYPE_ENTRY,
               GENERATE_INTEGER_VM_TYPE_ENTRY,
               GENERATE_UNSIGNED_INTEGER_VM_TYPE_ENTRY,
               GENERATE_C1_TOPLEVEL_VM_TYPE_ENTRY,
               GENERATE_C2_VM_TYPE_ENTRY,
               GENERATE_C2_TOPLEVEL_VM_TYPE_ENTRY,
               GENERATE_VM_TYPE_LAST_ENTRY)

  VM_TYPES_OS_CPU(GENERATE_VM_TYPE_ENTRY,
                  GENERATE_TOPLEVEL_VM_TYPE_ENTRY,
                  GENERATE_OOP_VM_TYPE_ENTRY,
                  GENERATE_INTEGER_VM_TYPE_ENTRY,
                  GENERATE_UNSIGNED_INTEGER_VM_TYPE_ENTRY,
                  GENERATE_C1_TOPLEVEL_VM_TYPE_ENTRY,
                  GENERATE_C2_VM_TYPE_ENTRY,
                  GENERATE_C2_TOPLEVEL_VM_TYPE_ENTRY,
                  GENERATE_VM_TYPE_LAST_ENTRY)
};

VMIntConstantEntry VMStructs::localHotSpotVMIntConstants[] = {

  VM_INT_CONSTANTS(GENERATE_VM_INT_CONSTANT_ENTRY,
                   GENERATE_PREPROCESSOR_VM_INT_CONSTANT_ENTRY,
                   GENERATE_C1_VM_INT_CONSTANT_ENTRY,
                   GENERATE_C2_VM_INT_CONSTANT_ENTRY,
                   GENERATE_C2_PREPROCESSOR_VM_INT_CONSTANT_ENTRY,
                   GENERATE_VM_INT_CONSTANT_LAST_ENTRY)

#ifndef SERIALGC
  VM_INT_CONSTANTS_CMS(GENERATE_VM_INT_CONSTANT_ENTRY)

  VM_INT_CONSTANTS_PARNEW(GENERATE_VM_INT_CONSTANT_ENTRY)
#endif // SERIALGC

  VM_INT_CONSTANTS_CPU(GENERATE_VM_INT_CONSTANT_ENTRY,
                       GENERATE_PREPROCESSOR_VM_INT_CONSTANT_ENTRY,
                       GENERATE_C1_VM_INT_CONSTANT_ENTRY,
                       GENERATE_C2_VM_INT_CONSTANT_ENTRY,
                       GENERATE_C2_PREPROCESSOR_VM_INT_CONSTANT_ENTRY,
                       GENERATE_VM_INT_CONSTANT_LAST_ENTRY)

  VM_INT_CONSTANTS_OS_CPU(GENERATE_VM_INT_CONSTANT_ENTRY,
                          GENERATE_PREPROCESSOR_VM_INT_CONSTANT_ENTRY,
                          GENERATE_C1_VM_INT_CONSTANT_ENTRY,
                          GENERATE_C2_VM_INT_CONSTANT_ENTRY,
                          GENERATE_C2_PREPROCESSOR_VM_INT_CONSTANT_ENTRY,
                          GENERATE_VM_INT_CONSTANT_LAST_ENTRY)
};

VMLongConstantEntry VMStructs::localHotSpotVMLongConstants[] = {

  VM_LONG_CONSTANTS(GENERATE_VM_LONG_CONSTANT_ENTRY,
                    GENERATE_PREPROCESSOR_VM_LONG_CONSTANT_ENTRY,
                    GENERATE_C1_VM_LONG_CONSTANT_ENTRY,
                    GENERATE_C2_VM_LONG_CONSTANT_ENTRY,
                    GENERATE_C2_PREPROCESSOR_VM_LONG_CONSTANT_ENTRY,
                    GENERATE_VM_LONG_CONSTANT_LAST_ENTRY)

  VM_LONG_CONSTANTS_CPU(GENERATE_VM_LONG_CONSTANT_ENTRY,
                        GENERATE_PREPROCESSOR_VM_LONG_CONSTANT_ENTRY,
                        GENERATE_C1_VM_LONG_CONSTANT_ENTRY,
                        GENERATE_C2_VM_LONG_CONSTANT_ENTRY,
                        GENERATE_C2_PREPROCESSOR_VM_LONG_CONSTANT_ENTRY,
                        GENERATE_VM_LONG_CONSTANT_LAST_ENTRY)

  VM_LONG_CONSTANTS_OS_CPU(GENERATE_VM_LONG_CONSTANT_ENTRY,
                           GENERATE_PREPROCESSOR_VM_LONG_CONSTANT_ENTRY,
                           GENERATE_C1_VM_LONG_CONSTANT_ENTRY,
                           GENERATE_C2_VM_LONG_CONSTANT_ENTRY,
                           GENERATE_C2_PREPROCESSOR_VM_LONG_CONSTANT_ENTRY,
                           GENERATE_VM_LONG_CONSTANT_LAST_ENTRY)
};

// This is used both to check the types of referenced fields and, in
// debug builds, to ensure that all of the field types are present.
void
VMStructs::init() {
  VM_STRUCTS(CHECK_NONSTATIC_VM_STRUCT_ENTRY,
             CHECK_STATIC_VM_STRUCT_ENTRY,
             CHECK_NO_OP,
             CHECK_VOLATILE_NONSTATIC_VM_STRUCT_ENTRY,
             CHECK_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY,
             CHECK_C1_NONSTATIC_VM_STRUCT_ENTRY,
             CHECK_C2_NONSTATIC_VM_STRUCT_ENTRY,
             CHECK_NO_OP,
             CHECK_NO_OP,
             CHECK_SENTINEL);

#ifndef SERIALGC
  VM_STRUCTS_PARALLELGC(CHECK_NONSTATIC_VM_STRUCT_ENTRY,
             CHECK_STATIC_VM_STRUCT_ENTRY);

  VM_STRUCTS_CMS(CHECK_NONSTATIC_VM_STRUCT_ENTRY,
             CHECK_VOLATILE_NONSTATIC_VM_STRUCT_ENTRY,
             CHECK_STATIC_VM_STRUCT_ENTRY);

  VM_STRUCTS_G1(CHECK_NONSTATIC_VM_STRUCT_ENTRY,
                CHECK_STATIC_VM_STRUCT_ENTRY);
#endif // SERIALGC

  VM_STRUCTS_CPU(CHECK_NONSTATIC_VM_STRUCT_ENTRY,
                 CHECK_STATIC_VM_STRUCT_ENTRY,
                 CHECK_NO_OP,
                 CHECK_VOLATILE_NONSTATIC_VM_STRUCT_ENTRY,
                 CHECK_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY,
                 CHECK_C2_NONSTATIC_VM_STRUCT_ENTRY,
                 CHECK_NO_OP,
                 CHECK_NO_OP,
                 CHECK_SENTINEL);

  VM_STRUCTS_OS_CPU(CHECK_NONSTATIC_VM_STRUCT_ENTRY,
                    CHECK_STATIC_VM_STRUCT_ENTRY,
                    CHECK_NO_OP,
                    CHECK_VOLATILE_NONSTATIC_VM_STRUCT_ENTRY,
                    CHECK_NONPRODUCT_NONSTATIC_VM_STRUCT_ENTRY,
                    CHECK_C2_NONSTATIC_VM_STRUCT_ENTRY,
                    CHECK_NO_OP,
                    CHECK_NO_OP,
                    CHECK_SENTINEL);

  VM_TYPES(CHECK_VM_TYPE_ENTRY,
           CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
           CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
           CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
           CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
           CHECK_C1_TOPLEVEL_VM_TYPE_ENTRY,
           CHECK_C2_VM_TYPE_ENTRY,
           CHECK_C2_TOPLEVEL_VM_TYPE_ENTRY,
           CHECK_SENTINEL);

#ifndef SERIALGC
  VM_TYPES_PARALLELGC(CHECK_VM_TYPE_ENTRY,
                      CHECK_SINGLE_ARG_VM_TYPE_NO_OP);

  VM_TYPES_CMS(CHECK_VM_TYPE_ENTRY,
               CHECK_SINGLE_ARG_VM_TYPE_NO_OP);

  VM_TYPES_PARNEW(CHECK_VM_TYPE_ENTRY)

  VM_TYPES_G1(CHECK_VM_TYPE_ENTRY,
              CHECK_SINGLE_ARG_VM_TYPE_NO_OP);
#endif // SERIALGC

  VM_TYPES_CPU(CHECK_VM_TYPE_ENTRY,
               CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
               CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
               CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
               CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
               CHECK_C1_TOPLEVEL_VM_TYPE_ENTRY,
               CHECK_C2_VM_TYPE_ENTRY,
               CHECK_C2_TOPLEVEL_VM_TYPE_ENTRY,
               CHECK_SENTINEL);

  VM_TYPES_OS_CPU(CHECK_VM_TYPE_ENTRY,
                  CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
                  CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
                  CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
                  CHECK_SINGLE_ARG_VM_TYPE_NO_OP,
                  CHECK_C1_TOPLEVEL_VM_TYPE_ENTRY,
                  CHECK_C2_VM_TYPE_ENTRY,
                  CHECK_C2_TOPLEVEL_VM_TYPE_ENTRY,
                  CHECK_SENTINEL);

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
  debug_only(VM_STRUCTS(ENSURE_FIELD_TYPE_PRESENT, \
                        CHECK_NO_OP, \
                        CHECK_NO_OP, \
                        CHECK_NO_OP, \
                        CHECK_NO_OP, \
                        CHECK_NO_OP, \
                        CHECK_NO_OP, \
                        CHECK_NO_OP, \
                        CHECK_NO_OP, \
                        CHECK_SENTINEL));
  debug_only(VM_STRUCTS(CHECK_NO_OP, \
                        ENSURE_FIELD_TYPE_PRESENT, \
                        CHECK_NO_OP, \
                        ENSURE_FIELD_TYPE_PRESENT, \
                        ENSURE_NONPRODUCT_FIELD_TYPE_PRESENT, \
                        ENSURE_C1_FIELD_TYPE_PRESENT, \
                        ENSURE_C2_FIELD_TYPE_PRESENT, \
                        CHECK_NO_OP, \
                        CHECK_NO_OP, \
                        CHECK_SENTINEL));
#ifndef SERIALGC
  debug_only(VM_STRUCTS_PARALLELGC(ENSURE_FIELD_TYPE_PRESENT, \
                                   ENSURE_FIELD_TYPE_PRESENT));
  debug_only(VM_STRUCTS_CMS(ENSURE_FIELD_TYPE_PRESENT, \
                            ENSURE_FIELD_TYPE_PRESENT, \
                            ENSURE_FIELD_TYPE_PRESENT));
  debug_only(VM_STRUCTS_G1(ENSURE_FIELD_TYPE_PRESENT, \
                           ENSURE_FIELD_TYPE_PRESENT));
#endif // SERIALGC
  debug_only(VM_STRUCTS_CPU(ENSURE_FIELD_TYPE_PRESENT, \
                            ENSURE_FIELD_TYPE_PRESENT, \
                            CHECK_NO_OP, \
                            ENSURE_FIELD_TYPE_PRESENT, \
                            ENSURE_NONPRODUCT_FIELD_TYPE_PRESENT, \
                            ENSURE_C2_FIELD_TYPE_PRESENT, \
                            CHECK_NO_OP, \
                            CHECK_NO_OP, \
                            CHECK_SENTINEL));
  debug_only(VM_STRUCTS_OS_CPU(ENSURE_FIELD_TYPE_PRESENT, \
                               ENSURE_FIELD_TYPE_PRESENT, \
                               CHECK_NO_OP, \
                               ENSURE_FIELD_TYPE_PRESENT, \
                               ENSURE_NONPRODUCT_FIELD_TYPE_PRESENT, \
                               ENSURE_C2_FIELD_TYPE_PRESENT, \
                               CHECK_NO_OP, \
                               CHECK_NO_OP, \
                               CHECK_SENTINEL));
#endif
}

extern "C" {

// see comments on cast_uint64_t at the top of this file
#define ASSIGN_CONST_TO_64BIT_VAR(var, expr) \
    JNIEXPORT uint64_t var = cast_uint64_t(expr);
#define ASSIGN_OFFSET_TO_64BIT_VAR(var, type, field)   \
  ASSIGN_CONST_TO_64BIT_VAR(var, offset_of(type, field))
#define ASSIGN_STRIDE_TO_64BIT_VAR(var, array) \
  ASSIGN_CONST_TO_64BIT_VAR(var, (char*)&array[1] - (char*)&array[0])

JNIEXPORT VMStructEntry* gHotSpotVMStructs                 = VMStructs::localHotSpotVMStructs;
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMStructEntryTypeNameOffset,  VMStructEntry, typeName);
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMStructEntryFieldNameOffset, VMStructEntry, fieldName);
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMStructEntryTypeStringOffset, VMStructEntry, typeString);
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMStructEntryIsStaticOffset, VMStructEntry, isStatic);
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMStructEntryOffsetOffset, VMStructEntry, offset);
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMStructEntryAddressOffset, VMStructEntry, address);
ASSIGN_STRIDE_TO_64BIT_VAR(gHotSpotVMStructEntryArrayStride, gHotSpotVMStructs);
JNIEXPORT VMTypeEntry*   gHotSpotVMTypes                   = VMStructs::localHotSpotVMTypes;
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMTypeEntryTypeNameOffset, VMTypeEntry, typeName);
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMTypeEntrySuperclassNameOffset, VMTypeEntry, superclassName);
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMTypeEntryIsOopTypeOffset, VMTypeEntry, isOopType);
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMTypeEntryIsIntegerTypeOffset, VMTypeEntry, isIntegerType);
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMTypeEntryIsUnsignedOffset, VMTypeEntry, isUnsigned);
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMTypeEntrySizeOffset, VMTypeEntry, size);
ASSIGN_STRIDE_TO_64BIT_VAR(gHotSpotVMTypeEntryArrayStride,gHotSpotVMTypes);
JNIEXPORT VMIntConstantEntry* gHotSpotVMIntConstants       = VMStructs::localHotSpotVMIntConstants;
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMIntConstantEntryNameOffset, VMIntConstantEntry, name);
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMIntConstantEntryValueOffset, VMIntConstantEntry, value);
ASSIGN_STRIDE_TO_64BIT_VAR(gHotSpotVMIntConstantEntryArrayStride, gHotSpotVMIntConstants);
JNIEXPORT VMLongConstantEntry* gHotSpotVMLongConstants     = VMStructs::localHotSpotVMLongConstants;
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMLongConstantEntryNameOffset, VMLongConstantEntry, name);
ASSIGN_OFFSET_TO_64BIT_VAR(gHotSpotVMLongConstantEntryValueOffset, VMLongConstantEntry, value);
ASSIGN_STRIDE_TO_64BIT_VAR(gHotSpotVMLongConstantEntryArrayStride, gHotSpotVMLongConstants);
}

#ifdef ASSERT
static int recursiveFindType(VMTypeEntry* origtypes, const char* typeName, bool isRecurse) {
  {
    VMTypeEntry* types = origtypes;
    while (types->typeName != NULL) {
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
    char * s = new char[len];
    strncpy(s, typeName, len - 1);
    s[len-1] = '\0';
    // tty->print_cr("checking \"%s\" for \"%s\"", s, typeName);
    if (recursiveFindType(origtypes, s, true) == 1) {
      delete s;
      return 1;
    }
    delete s;
  }
  if (strstr(typeName, "GrowableArray<") == typeName) {
    const char * start = typeName + strlen("GrowableArray<");
    const char * end = strrchr(typeName, '>');
    int len = end - start + 1;
    char * s = new char[len];
    strncpy(s, start, len - 1);
    s[len-1] = '\0';
    // tty->print_cr("checking \"%s\" for \"%s\"", s, typeName);
    if (recursiveFindType(origtypes, s, true) == 1) {
      delete s;
      return 1;
    }
    delete s;
  }
  if (strstr(typeName, "const ") == typeName) {
    const char * s = typeName + strlen("const ");
    // tty->print_cr("checking \"%s\" for \"%s\"", s, typeName);
    if (recursiveFindType(origtypes, s, true) == 1) {
      return 1;
    }
  }
  if (strstr(typeName, " const") == typeName + len - 6) {
    char * s = strdup(typeName);
    s[len - 6] = '\0';
    // tty->print_cr("checking \"%s\" for \"%s\"", s, typeName);
    if (recursiveFindType(origtypes, s, true) == 1) {
      return 1;
    }
  }
  if (!isRecurse) {
    tty->print_cr("type \"%s\" not found", typeName);
  }
  return 0;
}


int
VMStructs::findType(const char* typeName) {
  VMTypeEntry* types = gHotSpotVMTypes;

  return recursiveFindType(types, typeName, false);
}
#endif

void vmStructs_init() {
  debug_only(VMStructs::init());
}
