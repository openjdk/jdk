/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"

#include "aot/aotCodeHeap.hpp"
#include "aot/aotLoader.hpp"
#include "classfile/javaAssertions.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/shared/gcLocker.hpp"
#include "interpreter/abstractInterpreter.hpp"
#include "jvmci/compilerRuntime.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "oops/method.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/vm_operations.hpp"

bool AOTLib::_narrow_oop_shift_initialized = false;
int  AOTLib::_narrow_oop_shift = 0;
int  AOTLib::_narrow_klass_shift = 0;

address AOTLib::load_symbol(const char *name) {
  address symbol = (address) dlsym(_dl_handle, name);
  if (symbol == NULL) {
    tty->print_cr("Shared file %s error: missing %s", _name, name);
    vm_exit(1);
  }
  return symbol;
}

Klass* AOTCodeHeap::get_klass_from_got(const char* klass_name, int klass_len, const Method* method) {
  AOTKlassData* klass_data = (AOTKlassData*)_lib->load_symbol(klass_name);
  Klass* k = (Klass*)_metaspace_got[klass_data->_got_index];
  if (k == NULL) {
    Thread* thread = Thread::current();
    k = lookup_klass(klass_name, klass_len, method, thread);
    // Note, exceptions are cleared.
    if (k == NULL) {
      fatal("Shared file %s error: klass %s should be resolved already", _lib->name(), klass_name);
      vm_exit(1);
    }
    _metaspace_got[klass_data->_got_index] = k;
  }
  return k;
}

Klass* AOTCodeHeap::lookup_klass(const char* name, int len, const Method* method, Thread* thread) {
  ResourceMark rm(thread);
  assert(method != NULL, "incorrect call parameter");
  methodHandle caller(thread, (Method*)method);

  // Use class loader of aot method.
  Handle loader(thread, caller->method_holder()->class_loader());
  Handle protection_domain(thread, caller->method_holder()->protection_domain());

  // Ignore wrapping L and ;
  if (name[0] == 'L') {
    assert(len > 2, "small name %s", name);
    name++;
    len -= 2;
  }
  TempNewSymbol sym = SymbolTable::probe(name, len);
  if (sym == NULL) {
    log_debug(aot, class, resolve)("Probe failed for AOT class %s", name);
    return NULL;
  }
  Klass* k = SystemDictionary::find_instance_or_array_klass(sym, loader, protection_domain, thread);
  assert(!thread->has_pending_exception(), "should not throw");

  if (k != NULL) {
    log_info(aot, class, resolve)("%s %s (lookup)", caller->method_holder()->external_name(), k->external_name());
  }
  return k;
}

void AOTLib::handle_config_error(const char* format, ...) {
  if (PrintAOT) {
    va_list ap;
    va_start(ap, format);
    tty->vprint_cr(format, ap);
    va_end(ap);
  }
  if (UseAOTStrictLoading) {
    vm_exit(1);
  }
  _valid = false;
}

void AOTLib::verify_flag(bool aot_flag, bool flag, const char* name) {
  if (_valid && aot_flag != flag) {
    handle_config_error("Shared file %s error: %s has different value '%s' from current '%s'", _name, name , (aot_flag ? "true" : "false"), (flag ? "true" : "false"));
  }
}

void AOTLib::verify_flag(int aot_flag, int flag, const char* name) {
  if (_valid && aot_flag != flag) {
    handle_config_error("Shared file %s error: %s has different value '%d' from current '%d'", _name, name , aot_flag, flag);
  }
}

void AOTLib::verify_config() {
  GrowableArray<AOTLib*>* libraries = AOTLoader::libraries();
  for (GrowableArrayIterator<AOTLib*> lib = libraries->begin(); lib != libraries->end(); ++lib) {
    if ((*lib)->_config == _config) {
      handle_config_error("AOT library %s already loaded.", (*lib)->_name);
      return;
    }
  }

  if (_header->_version != AOTHeader::AOT_SHARED_VERSION) {
    handle_config_error("Invalid version of the shared file %s. Expected %d but was %d", _name, _header->_version, AOTHeader::AOT_SHARED_VERSION);
    return;
  }

  const char* aot_jvm_version = (const char*)_header + _header->_jvm_version_offset + 2;
  if (strcmp(aot_jvm_version, VM_Version::jre_release_version()) != 0) {
    handle_config_error("JVM version '%s' recorded in the shared file %s does not match current version '%s'", aot_jvm_version, _name, VM_Version::jre_release_version());
    return;
  }

  // Debug VM has different layout of runtime and metadata structures
#ifdef ASSERT
  verify_flag(_config->_debug_VM, true, "Debug VM version");
#else
  verify_flag(!(_config->_debug_VM), true, "Product VM version");
#endif
  // Check configuration size
  verify_flag(_config->_config_size, AOTConfiguration::CONFIG_SIZE, "AOT configuration size");

  // Check flags
  verify_flag(_config->_useCompressedOops, UseCompressedOops, "UseCompressedOops");
  verify_flag(_config->_useCompressedClassPointers, UseCompressedClassPointers, "UseCompressedClassPointers");
  verify_flag(_config->_useG1GC, UseG1GC, "UseG1GC");
  verify_flag(_config->_useCMSGC, UseConcMarkSweepGC, "UseConcMarkSweepGC");
  verify_flag(_config->_useTLAB, UseTLAB, "UseTLAB");
  verify_flag(_config->_useBiasedLocking, UseBiasedLocking, "UseBiasedLocking");
  verify_flag(_config->_objectAlignment, ObjectAlignmentInBytes, "ObjectAlignmentInBytes");
  verify_flag(_config->_contendedPaddingWidth, ContendedPaddingWidth, "ContendedPaddingWidth");
  verify_flag(_config->_fieldsAllocationStyle, FieldsAllocationStyle, "FieldsAllocationStyle");
  verify_flag(_config->_compactFields, CompactFields, "CompactFields");
  verify_flag(_config->_enableContended, EnableContended, "EnableContended");
  verify_flag(_config->_restrictContended, RestrictContended, "RestrictContended");

  if (!TieredCompilation && _config->_tieredAOT) {
    handle_config_error("Shared file %s error: Expected to run with tiered compilation on", _name);
  }

  // Shifts are static values which initialized by 0 until java heap initialization.
  // AOT libs are loaded before heap initialized so shift values are not set.
  // It is okay since ObjectAlignmentInBytes flag which defines shifts value is set before AOT libs are loaded.
  // Set shifts value based on first AOT library config.
  if (UseCompressedOops && _valid) {
    if (!_narrow_oop_shift_initialized) {
      _narrow_oop_shift = _config->_narrowOopShift;
      if (UseCompressedClassPointers) { // It is set only if UseCompressedOops is set
        _narrow_klass_shift = _config->_narrowKlassShift;
      }
      _narrow_oop_shift_initialized = true;
    } else {
      verify_flag(_config->_narrowOopShift, _narrow_oop_shift, "aot_config->_narrowOopShift");
      if (UseCompressedClassPointers) { // It is set only if UseCompressedOops is set
        verify_flag(_config->_narrowKlassShift, _narrow_klass_shift, "aot_config->_narrowKlassShift");
      }
    }
  }
}

AOTLib::~AOTLib() {
  free((void*) _name);
}

AOTCodeHeap::~AOTCodeHeap() {
  if (_classes != NULL) {
    FREE_C_HEAP_ARRAY(AOTClass, _classes);
  }
  if (_code_to_aot != NULL) {
    FREE_C_HEAP_ARRAY(CodeToAMethod, _code_to_aot);
  }
}

AOTLib::AOTLib(void* handle, const char* name, int dso_id) : _valid(true), _dl_handle(handle), _dso_id(dso_id) {
  _name = (const char*) strdup(name);

  // Verify that VM runs with the same parameters as AOT tool.
  _config = (AOTConfiguration*) load_symbol("JVM.config");
  _header = (AOTHeader*) load_symbol("JVM.header");

  verify_config();

  if (!_valid && PrintAOT) {
      tty->print("%7d ", (int) tty->time_stamp().milliseconds());
      tty->print_cr("%4d     skipped %s  aot library", _dso_id, _name);
  }
}

AOTCodeHeap::AOTCodeHeap(AOTLib* lib) :
    CodeHeap("CodeHeap 'AOT'", CodeBlobType::AOT), _lib(lib), _classes(NULL), _code_to_aot(NULL) {
  assert(_lib->is_valid(), "invalid library");

  _lib_symbols_initialized = false;
  _aot_id = 0;

  _class_count = _lib->header()->_class_count;
  _method_count = _lib->header()->_method_count;

  // Collect metaspace info: names -> address in .got section
  _metaspace_names = (const char*) _lib->load_symbol("JVM.metaspace.names");
  _method_metadata =     (address) _lib->load_symbol("JVM.method.metadata");
  _methods_offsets =     (address) _lib->load_symbol("JVM.methods.offsets");
  _klasses_offsets =     (address) _lib->load_symbol("JVM.klasses.offsets");
  _dependencies    =     (address) _lib->load_symbol("JVM.klasses.dependencies");
  _code_space      =     (address) _lib->load_symbol("JVM.text");

  // First cell is number of elements.
  jlong* got_sect;
  _metaspace_got      = (Metadata**) _lib->load_symbol("JVM.metaspace.got");
  _metaspace_got_size = _lib->header()->_metaspace_got_size;

  _metadata_got      = (Metadata**) _lib->load_symbol("JVM.metadata.got");
  _metadata_got_size = _lib->header()->_metadata_got_size;

  _oop_got      = (oop*) _lib->load_symbol("JVM.oop.got");
  _oop_got_size = _lib->header()->_oop_got_size;

  // Collect stubs info
  _stubs_offsets = (int*) _lib->load_symbol("JVM.stubs.offsets");

  // code segments table
  _code_segments = (address) _lib->load_symbol("JVM.code.segments");

  // method state
  _method_state = (jlong*) _lib->load_symbol("JVM.method.state");

  // Create a table for mapping classes
  _classes = NEW_C_HEAP_ARRAY(AOTClass, _class_count, mtCode);
  memset(_classes, 0, _class_count * sizeof(AOTClass));

  // Create table for searching AOTCompiledMethod based on pc.
  _code_to_aot = NEW_C_HEAP_ARRAY(CodeToAMethod, _method_count, mtCode);
  memset(_code_to_aot, 0, _method_count * sizeof(CodeToAMethod));

  _low_boundary = _code_space;
  _memory.set_low_boundary((char *)_code_space);
  _memory.set_high_boundary((char *)_code_space);
  _memory.set_low((char *)_code_space);
  _memory.set_high((char *)_code_space);

  _segmap.set_low_boundary((char *)_code_segments);
  _segmap.set_low((char *)_code_segments);

  _log2_segment_size = exact_log2(_lib->config()->_codeSegmentSize);

  // Register aot stubs
  register_stubs();

  if (PrintAOT || (PrintCompilation && PrintAOT)) {
    tty->print("%7d ", (int) tty->time_stamp().milliseconds());
    tty->print_cr("%4d     loaded    %s  aot library", _lib->id(), _lib->name());
  }
}

void AOTCodeHeap::publish_aot(const methodHandle& mh, AOTMethodData* method_data, int code_id) {
  // The method may be explicitly excluded by the user.
  // Or Interpreter uses an intrinsic for this method.
  if (CompilerOracle::should_exclude(mh) || !AbstractInterpreter::can_be_compiled(mh)) {
    return;
  }

  address code = method_data->_code;
  const char* name = method_data->_name;
  aot_metadata* meta = method_data->_meta;

  if (meta->scopes_pcs_begin() == meta->scopes_pcs_end()) {
    // When the AOT compiler compiles something big we fail to generate metadata
    // in CodeInstaller::gather_metadata. In that case the scopes_pcs_begin == scopes_pcs_end.
    // In all successful cases we always have 2 entries of scope pcs.
    log_info(aot, class, resolve)("Failed to load %s (no metadata available)", mh->name_and_sig_as_C_string());
    _code_to_aot[code_id]._state = invalid;
    return;
  }

  jlong* state_adr = &_method_state[code_id];
  address metadata_table = method_data->_metadata_table;
  int metadata_size = method_data->_metadata_size;
  assert(code_id < _method_count, "sanity");
  _aot_id++;

#ifdef ASSERT
  if (_aot_id > CIStop || _aot_id < CIStart) {
    // Skip compilation
    return;
  }
#endif
  // Check one more time.
  if (_code_to_aot[code_id]._state == invalid) {
    return;
  }
  AOTCompiledMethod *aot = new AOTCompiledMethod(code, mh(), meta, metadata_table, metadata_size, state_adr, this, name, code_id, _aot_id);
  assert(_code_to_aot[code_id]._aot == NULL, "should be not initialized");
  _code_to_aot[code_id]._aot = aot; // Should set this first
  if (Atomic::cmpxchg(in_use, (jint*)&_code_to_aot[code_id]._state, not_set) != not_set) {
    _code_to_aot[code_id]._aot = NULL; // Clean
  } else { // success
    // Publish method
#ifdef TIERED
    mh->set_aot_code(aot);
#endif
    Method::set_code(mh, aot);
    if (PrintAOT || (PrintCompilation && PrintAOT)) {
      aot->print_on(tty, NULL);
    }
    // Publish oop only after we are visible to CompiledMethodIterator
    aot->set_oop(mh()->method_holder()->klass_holder());
  }
}

void AOTCodeHeap::link_primitive_array_klasses() {
  ResourceMark rm;
  for (int i = T_BOOLEAN; i <= T_CONFLICT; i++) {
    BasicType t = (BasicType)i;
    if (is_java_primitive(t)) {
      const Klass* arr_klass = Universe::typeArrayKlassObj(t);
      AOTKlassData* klass_data = (AOTKlassData*) dlsym(_lib->dl_handle(), arr_klass->signature_name());
      if (klass_data != NULL) {
        // Set both GOT cells, resolved and initialized klass pointers.
        // _got_index points to second cell - resolved klass pointer.
        _metaspace_got[klass_data->_got_index-1] = (Metadata*)arr_klass; // Initialized
        _metaspace_got[klass_data->_got_index  ] = (Metadata*)arr_klass; // Resolved
        if (PrintAOT) {
          tty->print_cr("[Found  %s  in  %s]", arr_klass->internal_name(), _lib->name());
        }
      }
    }
  }
}

void AOTCodeHeap::register_stubs() {
  int stubs_count = _stubs_offsets[0]; // contains number
  _stubs_offsets++;
  AOTMethodOffsets* stub_offsets = (AOTMethodOffsets*)_stubs_offsets;
  for (int i = 0; i < stubs_count; ++i) {
    const char* stub_name = _metaspace_names + stub_offsets[i]._name_offset;
    address entry = _code_space  + stub_offsets[i]._code_offset;
    aot_metadata* meta = (aot_metadata *) (_method_metadata + stub_offsets[i]._meta_offset);
    address metadata_table = (address)_metadata_got + stub_offsets[i]._metadata_got_offset;
    int metadata_size = stub_offsets[i]._metadata_got_size;
    int code_id = stub_offsets[i]._code_id;
    assert(code_id < _method_count, "sanity");
    jlong* state_adr = &_method_state[code_id];
    int len = build_u2_from((address)stub_name);
    stub_name += 2;
    char* full_name = NEW_C_HEAP_ARRAY(char, len+5, mtCode);
    if (full_name == NULL) { // No memory?
      break;
    }
    memcpy(full_name, "AOT ", 4);
    memcpy(full_name+4, stub_name, len);
    full_name[len+4] = 0;
    guarantee(_code_to_aot[code_id]._state != invalid, "stub %s can't be invalidated", full_name);
    AOTCompiledMethod* aot = new AOTCompiledMethod(entry, NULL, meta, metadata_table, metadata_size, state_adr, this, full_name, code_id, i);
    assert(_code_to_aot[code_id]._aot  == NULL, "should be not initialized");
    _code_to_aot[code_id]._aot  = aot;
    if (Atomic::cmpxchg(in_use, (jint*)&_code_to_aot[code_id]._state, not_set) != not_set) {
      fatal("stab '%s' code state is %d", full_name, _code_to_aot[code_id]._state);
    }
    // Adjust code buffer boundaries only for stubs because they are last in the buffer.
    adjust_boundaries(aot);
    if (PrintAOT && Verbose) {
      aot->print_on(tty, NULL);
    }
  }
}

#define SET_AOT_GLOBAL_SYMBOL_VALUE(AOTSYMNAME, AOTSYMTYPE, VMSYMVAL) \
  {                                                                   \
    char* error;                                                      \
    /* Clear any existing error */                                    \
    dlerror();                                                        \
    AOTSYMTYPE * adr = (AOTSYMTYPE *) dlsym(_lib->dl_handle(), AOTSYMNAME);  \
    /* Check for any dlsym lookup error */                            \
    error = dlerror();                                                \
    guarantee(error == NULL, "%s", error);                            \
    *adr = (AOTSYMTYPE) VMSYMVAL;                                     \
  }

void AOTCodeHeap::link_graal_runtime_symbols()  {
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_monitorenter", address, JVMCIRuntime::monitorenter);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_monitorexit", address, JVMCIRuntime::monitorexit);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_log_object", address, JVMCIRuntime::log_object);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_log_printf", address, JVMCIRuntime::log_printf);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_log_primitive", address, JVMCIRuntime::log_primitive);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_new_instance", address, JVMCIRuntime::new_instance);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_new_array", address, JVMCIRuntime::new_array);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_new_multi_array", address, JVMCIRuntime::new_multi_array);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_dynamic_new_array", address, JVMCIRuntime::dynamic_new_array);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_validate_object", address, JVMCIRuntime::validate_object);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_write_barrier_pre", address, JVMCIRuntime::write_barrier_pre);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_identity_hash_code", address, JVMCIRuntime::identity_hash_code);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_write_barrier_post", address, JVMCIRuntime::write_barrier_post);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_dynamic_new_instance", address, JVMCIRuntime::dynamic_new_instance);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_thread_is_interrupted", address, JVMCIRuntime::thread_is_interrupted);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_exception_handler_for_pc", address, JVMCIRuntime::exception_handler_for_pc);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_test_deoptimize_call_int", address, JVMCIRuntime::test_deoptimize_call_int);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_throw_and_post_jvmti_exception", address, JVMCIRuntime::throw_and_post_jvmti_exception);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_throw_klass_external_name_exception", address, JVMCIRuntime::throw_klass_external_name_exception);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_throw_class_cast_exception", address, JVMCIRuntime::throw_class_cast_exception);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_vm_message", address, JVMCIRuntime::vm_message);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_jvmci_runtime_vm_error", address, JVMCIRuntime::vm_error);
}

void AOTCodeHeap::link_shared_runtime_symbols() {
    SET_AOT_GLOBAL_SYMBOL_VALUE("_resolve_static_entry", address, SharedRuntime::get_resolve_static_call_stub());
    SET_AOT_GLOBAL_SYMBOL_VALUE("_resolve_virtual_entry", address, SharedRuntime::get_resolve_virtual_call_stub());
    SET_AOT_GLOBAL_SYMBOL_VALUE("_resolve_opt_virtual_entry", address, SharedRuntime::get_resolve_opt_virtual_call_stub());
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_deopt_blob_unpack", address, SharedRuntime::deopt_blob()->unpack());
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_deopt_blob_uncommon_trap", address, SharedRuntime::deopt_blob()->uncommon_trap());
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_ic_miss_stub", address, SharedRuntime::get_ic_miss_stub());
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_handle_wrong_method_stub", address, SharedRuntime::get_handle_wrong_method_stub());
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_exception_handler_for_return_address", address, SharedRuntime::exception_handler_for_return_address);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_register_finalizer", address, SharedRuntime::register_finalizer);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_OSR_migration_end", address, SharedRuntime::OSR_migration_end);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_resolve_string_by_symbol", address, CompilerRuntime::resolve_string_by_symbol);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_resolve_klass_by_symbol", address, CompilerRuntime::resolve_klass_by_symbol);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_resolve_method_by_symbol_and_load_counters", address, CompilerRuntime::resolve_method_by_symbol_and_load_counters);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_initialize_klass_by_symbol", address, CompilerRuntime::initialize_klass_by_symbol);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_invocation_event", address, CompilerRuntime::invocation_event);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_backedge_event", address, CompilerRuntime::backedge_event);

    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_shared_runtime_dpow", address, SharedRuntime::dpow);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_shared_runtime_dexp", address, SharedRuntime::dexp);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_shared_runtime_dcos", address, SharedRuntime::dcos);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_shared_runtime_dsin", address, SharedRuntime::dsin);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_shared_runtime_dtan", address, SharedRuntime::dtan);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_shared_runtime_dlog", address, SharedRuntime::dlog);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_shared_runtime_dlog10", address, SharedRuntime::dlog10);
}

void AOTCodeHeap::link_stub_routines_symbols() {
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_jbyte_arraycopy", address, StubRoutines::_jbyte_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_jshort_arraycopy", address, StubRoutines::_jshort_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_jint_arraycopy", address, StubRoutines::_jint_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_jlong_arraycopy", address, StubRoutines::_jlong_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_oop_arraycopy", address, StubRoutines::_oop_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_oop_arraycopy_uninit", address, StubRoutines::_oop_arraycopy_uninit);

    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_jbyte_disjoint_arraycopy", address, StubRoutines::_jbyte_disjoint_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_jshort_disjoint_arraycopy", address, StubRoutines::_jshort_disjoint_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_jint_disjoint_arraycopy", address, StubRoutines::_jint_disjoint_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_jlong_disjoint_arraycopy", address, StubRoutines::_jlong_disjoint_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_oop_disjoint_arraycopy", address, StubRoutines::_oop_disjoint_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_oop_disjoint_arraycopy_uninit", address, StubRoutines::_oop_disjoint_arraycopy_uninit);

    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_arrayof_jbyte_arraycopy", address, StubRoutines::_arrayof_jbyte_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_arrayof_jshort_arraycopy", address, StubRoutines::_arrayof_jshort_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_arrayof_jint_arraycopy", address, StubRoutines::_arrayof_jint_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_arrayof_jlong_arraycopy", address, StubRoutines::_arrayof_jlong_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_arrayof_oop_arraycopy", address, StubRoutines::_arrayof_oop_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_arrayof_oop_arraycopy_uninit", address, StubRoutines::_arrayof_oop_arraycopy_uninit);

    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_arrayof_jbyte_disjoint_arraycopy", address, StubRoutines::_arrayof_jbyte_disjoint_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_arrayof_jshort_disjoint_arraycopy", address, StubRoutines::_arrayof_jshort_disjoint_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_arrayof_jint_disjoint_arraycopy", address, StubRoutines::_arrayof_jint_disjoint_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_arrayof_jlong_disjoint_arraycopy", address, StubRoutines::_arrayof_jlong_disjoint_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_arrayof_oop_disjoint_arraycopy", address, StubRoutines::_arrayof_oop_disjoint_arraycopy);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_arrayof_oop_disjoint_arraycopy_uninit", address, StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit);

    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_checkcast_arraycopy", address, StubRoutines::_checkcast_arraycopy);

    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_aescrypt_encryptBlock", address, StubRoutines::_aescrypt_encryptBlock);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_aescrypt_decryptBlock", address, StubRoutines::_aescrypt_decryptBlock);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_cipherBlockChaining_encryptAESCrypt", address, StubRoutines::_cipherBlockChaining_encryptAESCrypt);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_cipherBlockChaining_decryptAESCrypt", address, StubRoutines::_cipherBlockChaining_decryptAESCrypt);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_update_bytes_crc32", address, StubRoutines::_updateBytesCRC32);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_crc_table_adr", address, StubRoutines::_crc_table_adr);


    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_sha1_implCompress", address, StubRoutines::_sha1_implCompress);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_sha1_implCompressMB", address, StubRoutines::_sha1_implCompressMB);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_sha256_implCompress", address, StubRoutines::_sha256_implCompress);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_sha256_implCompressMB", address, StubRoutines::_sha256_implCompressMB);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_sha512_implCompress", address, StubRoutines::_sha512_implCompress);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_sha512_implCompressMB", address, StubRoutines::_sha512_implCompressMB);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_multiplyToLen", address, StubRoutines::_multiplyToLen);

    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_counterMode_AESCrypt", address, StubRoutines::_counterMode_AESCrypt);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_ghash_processBlocks", address, StubRoutines::_ghash_processBlocks);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_crc32c_table_addr", address, StubRoutines::_crc32c_table_addr);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_updateBytesCRC32C", address, StubRoutines::_updateBytesCRC32C);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_updateBytesAdler32", address, StubRoutines::_updateBytesAdler32);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_squareToLen", address, StubRoutines::_squareToLen);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_mulAdd", address, StubRoutines::_mulAdd);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_montgomeryMultiply",  address, StubRoutines::_montgomeryMultiply);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_montgomerySquare", address, StubRoutines::_montgomerySquare);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_vectorizedMismatch", address, StubRoutines::_vectorizedMismatch);

    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_stub_routines_throw_delayed_StackOverflowError_entry", address, StubRoutines::_throw_delayed_StackOverflowError_entry);

}

void AOTCodeHeap::link_os_symbols() {
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_os_javaTimeMillis", address, os::javaTimeMillis);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_os_javaTimeNanos", address, os::javaTimeNanos);
}

/*
 * Link any global symbols in precompiled DSO with dlopen() _dl_handle
 * dso_handle.
 */

void AOTCodeHeap::link_global_lib_symbols() {
  if (!_lib_symbols_initialized) {
    _lib_symbols_initialized = true;

    CollectedHeap* heap = Universe::heap();
    CardTableModRefBS* ct = (CardTableModRefBS*)(heap->barrier_set());
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_card_table_address", address, ct->byte_map_base);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_heap_top_address", address, (heap->supports_inline_contig_alloc() ? heap->top_addr() : NULL));
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_heap_end_address", address, (heap->supports_inline_contig_alloc() ? heap->end_addr() : NULL));
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_polling_page", address, os::get_polling_page());
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_narrow_klass_base_address", address, Universe::narrow_klass_base());
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_narrow_oop_base_address", address, Universe::narrow_oop_base());
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_log_of_heap_region_grain_bytes", int, HeapRegion::LogOfHRGrainBytes);
    SET_AOT_GLOBAL_SYMBOL_VALUE("_aot_inline_contiguous_allocation_supported", bool, heap->supports_inline_contig_alloc());
    link_shared_runtime_symbols();
    link_stub_routines_symbols();
    link_os_symbols();
    link_graal_runtime_symbols();

    // Link primitive array klasses.
    link_primitive_array_klasses();
  }
}

#ifndef PRODUCT
int AOTCodeHeap::klasses_seen = 0;
int AOTCodeHeap::aot_klasses_found = 0;
int AOTCodeHeap::aot_klasses_fp_miss = 0;
int AOTCodeHeap::aot_klasses_cl_miss = 0;
int AOTCodeHeap::aot_methods_found = 0;

void AOTCodeHeap::print_statistics() {
  tty->print_cr("Classes seen: %d  AOT classes found: %d  AOT methods found: %d", klasses_seen, aot_klasses_found, aot_methods_found);
  tty->print_cr("AOT fingerprint mismatches: %d  AOT class loader mismatches: %d", aot_klasses_fp_miss, aot_klasses_cl_miss);
}
#endif

Method* AOTCodeHeap::find_method(KlassHandle klass, Thread* thread, const char* method_name) {
  int method_name_len = build_u2_from((address)method_name);
  method_name += 2;
  const char* signature_name = method_name + method_name_len;
  int signature_name_len = build_u2_from((address)signature_name);
  signature_name += 2;
  // The class should have been loaded so the method and signature should already be
  // in the symbol table.  If they're not there, the method doesn't exist.
  TempNewSymbol name = SymbolTable::probe(method_name, method_name_len);
  TempNewSymbol signature = SymbolTable::probe(signature_name, signature_name_len);

  Method* m;
  if (name == NULL || signature == NULL) {
    m = NULL;
  } else if (name == vmSymbols::object_initializer_name() ||
             name == vmSymbols::class_initializer_name()) {
    // Never search superclasses for constructors
    if (klass->is_instance_klass()) {
      m = InstanceKlass::cast(klass())->find_method(name, signature);
    } else {
      m = NULL;
    }
  } else {
    m = klass->lookup_method(name, signature);
    if (m == NULL && klass->is_instance_klass()) {
      m = InstanceKlass::cast(klass())->lookup_method_in_ordered_interfaces(name, signature);
    }
  }
  if (m == NULL) {
    // Fatal error because we assume classes and methods should not be changed since aot compilation.
    const char* klass_name = klass->external_name();
    int klass_len = (int)strlen(klass_name);
    char* meta_name = NEW_RESOURCE_ARRAY(char, klass_len + 1 + method_name_len + signature_name_len + 1);
    memcpy(meta_name, klass_name, klass_len);
    meta_name[klass_len] = '.';
    memcpy(&meta_name[klass_len + 1], method_name, method_name_len);
    memcpy(&meta_name[klass_len + 1 + method_name_len], signature_name, signature_name_len);
    meta_name[klass_len + 1 + method_name_len + signature_name_len] = '\0';
    Handle exception = Exceptions::new_exception(thread, vmSymbols::java_lang_NoSuchMethodError(), meta_name);
    java_lang_Throwable::print(exception, tty);
    tty->cr();
    java_lang_Throwable::print_stack_trace(exception(), tty);
    tty->cr();
    fatal("Failed to find method '%s'", meta_name);
  }
  NOT_PRODUCT( aot_methods_found++; )
  return m;
}

AOTKlassData* AOTCodeHeap::find_klass(InstanceKlass* ik) {
  ResourceMark rm;
  AOTKlassData* klass_data = (AOTKlassData*) dlsym(_lib->dl_handle(), ik->signature_name());
  return klass_data;
}

bool AOTCodeHeap::is_dependent_method(Klass* dependee, AOTCompiledMethod* aot) {
  InstanceKlass *dependee_ik = InstanceKlass::cast(dependee);
  AOTKlassData* klass_data = find_klass(dependee_ik);
  if (klass_data == NULL) {
    return false; // no AOT records for this class - no dependencies
  }
  if (!dependee_ik->has_passed_fingerprint_check()) {
    return false; // different class
  }

  int methods_offset = klass_data->_dependent_methods_offset;
  if (methods_offset >= 0) {
    address methods_cnt_adr = _dependencies + methods_offset;
    int methods_cnt = *(int*)methods_cnt_adr;
    int* indexes = (int*)(methods_cnt_adr + 4);
    for (int i = 0; i < methods_cnt; ++i) {
      int code_id = indexes[i];
      if (_code_to_aot[code_id]._aot == aot) {
        return true; // found dependent method
      }
    }
  }
  return false;
}

void AOTCodeHeap::sweep_dependent_methods(AOTKlassData* klass_data) {
  // Make dependent methods non_entrant forever.
  int methods_offset = klass_data->_dependent_methods_offset;
  if (methods_offset >= 0) {
    int marked = 0;
    address methods_cnt_adr = _dependencies + methods_offset;
    int methods_cnt = *(int*)methods_cnt_adr;
    int* indexes = (int*)(methods_cnt_adr + 4);
    for (int i = 0; i < methods_cnt; ++i) {
      int code_id = indexes[i];
      // Invalidate aot code.
      if (Atomic::cmpxchg(invalid, (jint*)&_code_to_aot[code_id]._state, not_set) != not_set) {
        if (_code_to_aot[code_id]._state == in_use) {
          AOTCompiledMethod* aot = _code_to_aot[code_id]._aot;
          assert(aot != NULL, "aot should be set");
          if (!aot->is_runtime_stub()) { // Something is wrong - should not invalidate stubs.
            aot->mark_for_deoptimization(false);
            marked++;
          }
        }
      }
    }
    if (marked > 0) {
      VM_Deoptimize op;
      VMThread::execute(&op);
    }
  }
}

bool AOTCodeHeap::load_klass_data(instanceKlassHandle kh, Thread* thread) {
  ResourceMark rm;

  NOT_PRODUCT( klasses_seen++; )

  AOTKlassData* klass_data = find_klass(kh());
  if (klass_data == NULL) {
    return false;
  }

  if (!kh->has_passed_fingerprint_check()) {
    NOT_PRODUCT( aot_klasses_fp_miss++; )
    log_trace(aot, class, fingerprint)("class  %s%s  has bad fingerprint in  %s tid=" INTPTR_FORMAT,
                                   kh->internal_name(), kh->is_shared() ? " (shared)" : "",
                                   _lib->name(), p2i(thread));
    sweep_dependent_methods(klass_data);
    return false;
  }

  if (kh->has_been_redefined()) {
    log_trace(aot, class, load)("class  %s%s in %s  has been redefined tid=" INTPTR_FORMAT,
                                   kh->internal_name(), kh->is_shared() ? " (shared)" : "",
                                   _lib->name(), p2i(thread));
    sweep_dependent_methods(klass_data);
    return false;
  }

  assert(klass_data->_class_id < _class_count, "invalid class id");
  AOTClass* aot_class = &_classes[klass_data->_class_id];
  if (aot_class->_classloader != NULL && aot_class->_classloader != kh->class_loader_data()) {
    log_trace(aot, class, load)("class  %s  in  %s already loaded for classloader %p vs %p tid=" INTPTR_FORMAT,
                             kh->internal_name(), _lib->name(), aot_class->_classloader, kh->class_loader_data(), p2i(thread));
    NOT_PRODUCT( aot_klasses_cl_miss++; )
    return false;
  }

  if (_lib->config()->_omitAssertions && JavaAssertions::enabled(kh->name()->as_C_string(), kh->class_loader() == NULL)) {
    log_trace(aot, class, load)("class  %s  in  %s does not have java assertions in compiled code, but assertions are enabled for this execution.", kh->internal_name(), _lib->name());
    sweep_dependent_methods(klass_data);
    return false;
  }

  NOT_PRODUCT( aot_klasses_found++; )

  log_trace(aot, class, load)("found  %s  in  %s for classloader %p tid=" INTPTR_FORMAT, kh->internal_name(), _lib->name(), kh->class_loader_data(), p2i(thread));

  aot_class->_classloader = kh->class_loader_data();
  // Set klass's Resolve (second) got cell.
  _metaspace_got[klass_data->_got_index] = kh();

  // Initialize global symbols of the DSO to the corresponding VM symbol values.
  link_global_lib_symbols();

  int methods_offset = klass_data->_compiled_methods_offset;
  if (methods_offset >= 0) {
    address methods_cnt_adr = _methods_offsets + methods_offset;
    int methods_cnt = *(int*)methods_cnt_adr;
    // Collect data about compiled methods
    AOTMethodData* methods_data = NEW_RESOURCE_ARRAY(AOTMethodData, methods_cnt);
    AOTMethodOffsets* methods_offsets = (AOTMethodOffsets*)(methods_cnt_adr + 4);
    for (int i = 0; i < methods_cnt; ++i) {
      AOTMethodOffsets* method_offsets = &methods_offsets[i];
      int code_id = method_offsets->_code_id;
      if (_code_to_aot[code_id]._state == invalid) {
        continue; // skip AOT methods slots which have been invalidated
      }
      AOTMethodData* method_data = &methods_data[i];
      const char* aot_name = _metaspace_names + method_offsets->_name_offset;
      method_data->_name = aot_name;
      method_data->_code = _code_space  + method_offsets->_code_offset;
      method_data->_meta = (aot_metadata*)(_method_metadata + method_offsets->_meta_offset);
      method_data->_metadata_table = (address)_metadata_got + method_offsets->_metadata_got_offset;
      method_data->_metadata_size  = method_offsets->_metadata_got_size;
      // aot_name format: "<u2_size>Ljava/lang/ThreadGroup;<u2_size>addUnstarted<u2_size>()V"
      int klass_len = build_u2_from((address)aot_name);
      const char* method_name = aot_name + 2 + klass_len;
      Method* m = AOTCodeHeap::find_method(kh, thread, method_name);
      methodHandle mh(thread, m);
      if (mh->code() != NULL) { // Does it have already compiled code?
        continue; // Don't overwrite
      }
      publish_aot(mh, method_data, code_id);
    }
  }
  return true;
}

AOTCompiledMethod* AOTCodeHeap::next_in_use_at(int start) const {
  for (int index = start; index < _method_count; index++) {
    if (_code_to_aot[index]._state != in_use) {
      continue; // Skip uninitialized entries.
    }
    AOTCompiledMethod* aot = _code_to_aot[index]._aot;
    return aot;
  }
  return NULL;
}

void* AOTCodeHeap::first() const {
  return next_in_use_at(0);
}

void* AOTCodeHeap::next(void* p) const {
  AOTCompiledMethod *aot = (AOTCompiledMethod *)p;
  int next_index = aot->method_index() + 1;
  assert(next_index <= _method_count, "");
  if (next_index == _method_count) {
    return NULL;
  }
  return next_in_use_at(next_index);
}

void* AOTCodeHeap::find_start(void* p) const {
  if (!contains(p)) {
    return NULL;
  }
  size_t offset = pointer_delta(p, low_boundary(), 1);
  // Use segments table
  size_t seg_idx = offset / _lib->config()->_codeSegmentSize;
  if ((int)(_code_segments[seg_idx]) == 0xff) {
    return NULL;
  }
  while (_code_segments[seg_idx] > 0) {
    seg_idx -= (int)_code_segments[seg_idx];
  }
  int code_offset = (int)seg_idx * _lib->config()->_codeSegmentSize;
  int aot_index = *(int*)(_code_space + code_offset);
  AOTCompiledMethod* aot = _code_to_aot[aot_index]._aot;
  assert(aot != NULL, "should find registered aot method");
  return aot;
}

AOTCompiledMethod* AOTCodeHeap::find_aot(address p) const {
  assert(contains(p), "should be here");
  return (AOTCompiledMethod *)find_start(p);
}

CodeBlob* AOTCodeHeap::find_blob_unsafe(void* start) const {
  return (CodeBlob*)AOTCodeHeap::find_start(start);
}

void AOTCodeHeap::oops_do(OopClosure* f) {
  for (int i = 0; i < _oop_got_size; i++) {
    oop* p = &_oop_got[i];
    if (*p == NULL)  continue;  // skip non-oops
    f->do_oop(p);
  }
  for (int index = 0; index < _method_count; index++) {
    if (_code_to_aot[index]._state != in_use) {
      continue; // Skip uninitialized entries.
    }
    AOTCompiledMethod* aot = _code_to_aot[index]._aot;
    aot->do_oops(f);
  }
}

// Scan only metaspace_got cells which should have only Klass*,
// metadata_got cells are scanned only for alive AOT methods
// by AOTCompiledMethod::metadata_do().
void AOTCodeHeap::got_metadata_do(void f(Metadata*)) {
  for (int i = 1; i < _metaspace_got_size; i++) {
    Metadata** p = &_metaspace_got[i];
    Metadata* md = *p;
    if (md == NULL)  continue;  // skip non-oops
    if (Metaspace::contains(md)) {
      f(md);
    } else {
      intptr_t meta = (intptr_t)md;
      fatal("Invalid value in _metaspace_got[%d] = " INTPTR_FORMAT, i, meta);
    }
  }
}

void AOTCodeHeap::cleanup_inline_caches() {
  for (int index = 0; index < _method_count; index++) {
    if (_code_to_aot[index]._state != in_use) {
      continue; // Skip uninitialized entries.
    }
    AOTCompiledMethod* aot = _code_to_aot[index]._aot;
    aot->cleanup_inline_caches();
  }
}

#ifdef ASSERT
int AOTCodeHeap::verify_icholder_relocations() {
  int count = 0;
  for (int index = 0; index < _method_count; index++) {
    if (_code_to_aot[index]._state != in_use) {
      continue; // Skip uninitialized entries.
    }
    AOTCompiledMethod* aot = _code_to_aot[index]._aot;
    count += aot->verify_icholder_relocations();
  }
  return count;
}
#endif

void AOTCodeHeap::flush_evol_dependents_on(instanceKlassHandle dependee) {
  for (int index = 0; index < _method_count; index++) {
    if (_code_to_aot[index]._state != in_use) {
      continue; // Skip uninitialized entries.
    }
    AOTCompiledMethod* aot = _code_to_aot[index]._aot;
    aot->flush_evol_dependents_on(dependee);
  }
}

void AOTCodeHeap::metadata_do(void f(Metadata*)) {
  for (int index = 0; index < _method_count; index++) {
    if (_code_to_aot[index]._state != in_use) {
      continue; // Skip uninitialized entries.
    }
    AOTCompiledMethod* aot = _code_to_aot[index]._aot;
    if (aot->_is_alive()) {
      aot->metadata_do(f);
    }
  }
  // Scan metaspace_got cells.
  got_metadata_do(f);
}
