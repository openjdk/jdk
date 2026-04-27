/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotMetaspace.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/cppVtables.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/metadataOnStackMark.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmClasses.hpp"
#include "code/aotCodeCache.hpp"
#include "code/codeCache.hpp"
#include "code/debugInfoRec.hpp"
#include "compiler/compilationPolicy.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "interpreter/bytecodes.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/bytecodeTracer.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/oopMapCache.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "logging/logTag.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "nmt/memTracker.hpp"
#include "oops/constantPool.hpp"
#include "oops/constMethod.hpp"
#include "oops/jmethodIDTable.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "oops/trainingData.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/continuationEntry.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/perfData.hpp"
#include "runtime/relocator.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/threads.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/align.hpp"
#include "utilities/quickSort.hpp"
#include "utilities/vmError.hpp"
#include "utilities/xmlstream.hpp"

// Implementation of Method

Method* Method::allocate(ClassLoaderData* loader_data,
                         int byte_code_size,
                         AccessFlags access_flags,
                         InlineTableSizes* sizes,
                         ConstMethod::MethodType method_type,
                         Symbol* name,
                         TRAPS) {
  assert(!access_flags.is_native() || byte_code_size == 0,
         "native methods should not contain byte codes");
  ConstMethod* cm = ConstMethod::allocate(loader_data,
                                          byte_code_size,
                                          sizes,
                                          method_type,
                                          CHECK_NULL);
  int size = Method::size(access_flags.is_native());
  return new (loader_data, size, MetaspaceObj::MethodType, THREAD) Method(cm, access_flags, name);
}

Method::Method(ConstMethod* xconst, AccessFlags access_flags, Symbol* name) {
  NoSafepointVerifier no_safepoint;
  set_constMethod(xconst);
  set_access_flags(access_flags);
  set_intrinsic_id(vmIntrinsics::_none);
  clear_method_data();
  clear_method_counters();
  set_vtable_index(Method::garbage_vtable_index);

  // Fix and bury in Method*
  set_interpreter_entry(nullptr); // sets i2i entry and from_int
  set_adapter_entry(nullptr);
  Method::clear_code(); // from_c/from_i get set to c2i/i2i

  if (access_flags.is_native()) {
    clear_native_function();
    set_signature_handler(nullptr);
  }

  NOT_PRODUCT(set_compiled_invocation_count(0);)
  // Name is very useful for debugging.
  NOT_PRODUCT(_name = name;)
}

// Release Method*.  The nmethod will be gone when we get here because
// we've walked the code cache.
void Method::deallocate_contents(ClassLoaderData* loader_data) {
  MetadataFactory::free_metadata(loader_data, constMethod());
  set_constMethod(nullptr);
  MetadataFactory::free_metadata(loader_data, method_data());
  clear_method_data();
  MetadataFactory::free_metadata(loader_data, method_counters());
  clear_method_counters();
  set_adapter_entry(nullptr);
  // The nmethod will be gone when we get here.
  if (code() != nullptr) _code = nullptr;
}

void Method::release_C_heap_structures() {
  if (method_data()) {
    method_data()->release_C_heap_structures();

    // Destroy MethodData embedded lock
    method_data()->~MethodData();
  }
}

address Method::get_i2c_entry() {
  if (is_abstract()) {
    return SharedRuntime::throw_AbstractMethodError_entry();
  }
  assert(adapter() != nullptr, "must have");
  return adapter()->get_i2c_entry();
}

address Method::get_c2i_entry() {
  if (is_abstract()) {
    return SharedRuntime::get_handle_wrong_method_abstract_stub();
  }
  assert(adapter() != nullptr, "must have");
  return adapter()->get_c2i_entry();
}

address Method::get_c2i_unverified_entry() {
  if (is_abstract()) {
    return SharedRuntime::get_handle_wrong_method_abstract_stub();
  }
  assert(adapter() != nullptr, "must have");
  return adapter()->get_c2i_unverified_entry();
}

address Method::get_c2i_no_clinit_check_entry() {
  if (is_abstract()) {
    return nullptr;
  }
  assert(VM_Version::supports_fast_class_init_checks(), "");
  assert(adapter() != nullptr, "must have");
  return adapter()->get_c2i_no_clinit_check_entry();
}

char* Method::name_and_sig_as_C_string() const {
  return name_and_sig_as_C_string(constants()->pool_holder(), name(), signature());
}

char* Method::name_and_sig_as_C_string(char* buf, int size) const {
  return name_and_sig_as_C_string(constants()->pool_holder(), name(), signature(), buf, size);
}

char* Method::name_and_sig_as_C_string(Klass* klass, Symbol* method_name, Symbol* signature) {
  const char* klass_name = klass->external_name();
  int klass_name_len  = (int)strlen(klass_name);
  int method_name_len = method_name->utf8_length();
  int len             = klass_name_len + 1 + method_name_len + signature->utf8_length();
  char* dest          = NEW_RESOURCE_ARRAY(char, len + 1);
  strcpy(dest, klass_name);
  dest[klass_name_len] = '.';
  strcpy(&dest[klass_name_len + 1], method_name->as_C_string());
  strcpy(&dest[klass_name_len + 1 + method_name_len], signature->as_C_string());
  dest[len] = 0;
  return dest;
}

char* Method::name_and_sig_as_C_string(Klass* klass, Symbol* method_name, Symbol* signature, char* buf, int size) {
  Symbol* klass_name = klass->name();
  klass_name->as_klass_external_name(buf, size);
  int len = (int)strlen(buf);

  if (len < size - 1) {
    buf[len++] = '.';

    method_name->as_C_string(&(buf[len]), size - len);
    len = (int)strlen(buf);

    signature->as_C_string(&(buf[len]), size - len);
  }

  return buf;
}

const char* Method::external_name() const {
  return external_name(constants()->pool_holder(), name(), signature());
}

void Method::print_external_name(outputStream *os) const {
  print_external_name(os, constants()->pool_holder(), name(), signature());
}

const char* Method::external_name(Klass* klass, Symbol* method_name, Symbol* signature) {
  stringStream ss;
  print_external_name(&ss, klass, method_name, signature);
  return ss.as_string();
}

void Method::print_external_name(outputStream *os, Klass* klass, Symbol* method_name, Symbol* signature) {
  signature->print_as_signature_external_return_type(os);
  os->print(" %s.%s(", klass->external_name(), method_name->as_C_string());
  signature->print_as_signature_external_parameters(os);
  os->print(")");
}

int Method::fast_exception_handler_bci_for(const methodHandle& mh, Klass* ex_klass, int throw_bci, TRAPS) {
  if (log_is_enabled(Debug, exceptions)) {
    ResourceMark rm(THREAD);
    log_debug(exceptions)("Looking for catch handler for exception of type \"%s\" in method \"%s\"",
                          ex_klass == nullptr ? "null" : ex_klass->external_name(), mh->name()->as_C_string());
  }
  // exception table holds quadruple entries of the form (beg_bci, end_bci, handler_bci, klass_index)
  // access exception table
  ExceptionTable table(mh());
  int length = table.length();
  // iterate through all entries sequentially
  constantPoolHandle pool(THREAD, mh->constants());
  for (int i = 0; i < length; i ++) {
    //reacquire the table in case a GC happened
    ExceptionTable table(mh());
    int beg_bci = table.start_pc(i);
    int end_bci = table.end_pc(i);
    assert(beg_bci <= end_bci, "inconsistent exception table");
    log_debug(exceptions)("  - checking exception table entry for BCI %d to %d",
                         beg_bci, end_bci);

    if (beg_bci <= throw_bci && throw_bci < end_bci) {
      // exception handler bci range covers throw_bci => investigate further
      log_debug(exceptions)("    - entry covers throw point BCI %d", throw_bci);

      int handler_bci = table.handler_pc(i);
      int klass_index = table.catch_type_index(i);
      if (klass_index == 0) {
        if (log_is_enabled(Info, exceptions)) {
          ResourceMark rm(THREAD);
          log_info(exceptions)("Found catch-all handler for exception of type \"%s\" in method \"%s\" at BCI: %d",
                               ex_klass == nullptr ? "null" : ex_klass->external_name(), mh->name()->as_C_string(), handler_bci);
        }
        return handler_bci;
      } else if (ex_klass == nullptr) {
        // Is this even possible?
        if (log_is_enabled(Info, exceptions)) {
          ResourceMark rm(THREAD);
          log_info(exceptions)("null exception class is implicitly caught by handler in method \"%s\" at BCI: %d",
                               mh()->name()->as_C_string(), handler_bci);
        }
        return handler_bci;
      } else {
        if (log_is_enabled(Debug, exceptions)) {
          ResourceMark rm(THREAD);
          log_debug(exceptions)("    - resolving catch type \"%s\"",
                               pool->klass_name_at(klass_index)->as_C_string());
        }
        // we know the exception class => get the constraint class
        // this may require loading of the constraint class; if verification
        // fails or some other exception occurs, return handler_bci
        Klass* k = pool->klass_at(klass_index, THREAD);
        if (HAS_PENDING_EXCEPTION) {
          if (log_is_enabled(Debug, exceptions)) {
            ResourceMark rm(THREAD);
            log_debug(exceptions)("    - exception \"%s\" occurred resolving catch type",
                                 PENDING_EXCEPTION->klass()->external_name());
          }
          return handler_bci;
        }
        assert(k != nullptr, "klass not loaded");
        if (ex_klass->is_subtype_of(k)) {
          if (log_is_enabled(Info, exceptions)) {
            ResourceMark rm(THREAD);
            log_info(exceptions)("Found matching handler for exception of type \"%s\" in method \"%s\" at BCI: %d",
                                 ex_klass == nullptr ? "null" : ex_klass->external_name(), mh->name()->as_C_string(), handler_bci);
          }
          return handler_bci;
        }
      }
    }
  }

  if (log_is_enabled(Debug, exceptions)) {
    ResourceMark rm(THREAD);
    log_debug(exceptions)("No catch handler found for exception of type \"%s\" in method \"%s\"",
                          ex_klass->external_name(), mh->name()->as_C_string());
  }

  return -1;
}

void Method::mask_for(int bci, InterpreterOopMap* mask) {
  methodHandle h_this(Thread::current(), this);
  mask_for(h_this, bci, mask);
}

void Method::mask_for(const methodHandle& this_mh, int bci, InterpreterOopMap* mask) {
  assert(this_mh() == this, "Sanity");
  method_holder()->mask_for(this_mh, bci, mask);
}

int Method::bci_from(address bcp) const {
  if (is_native() && bcp == nullptr) {
    return 0;
  }
  // Do not have a ResourceMark here because AsyncGetCallTrace stack walking code
  // may call this after interrupting a nested ResourceMark.
  assert((is_native() && bcp == code_base()) || contains(bcp) || VMError::is_error_reported(),
         "bcp doesn't belong to this method. bcp: " PTR_FORMAT, p2i(bcp));

  return int(bcp - code_base());
}


int Method::validate_bci(int bci) const {
  // Called from the verifier, and should return -1 if not valid.
  return ((is_native() && bci == 0) || (!is_native() && 0 <= bci && bci < code_size())) ? bci : -1;
}

// Return bci if it appears to be a valid bcp
// Return -1 otherwise.
// Used by profiling code, when invalid data is a possibility.
// The caller is responsible for validating the Method* itself.
int Method::validate_bci_from_bcp(address bcp) const {
  // keep bci as -1 if not a valid bci
  int bci = -1;
  if (bcp == nullptr || bcp == code_base()) {
    // code_size() may return 0 and we allow 0 here
    // the method may be native
    bci = 0;
  } else if (contains(bcp)) {
    bci = int(bcp - code_base());
  }
  // Assert that if we have dodged any asserts, bci is negative.
  assert(bci == -1 || bci == bci_from(bcp_from(bci)), "sane bci if >=0");
  return bci;
}

address Method::bcp_from(int bci) const {
  assert((is_native() && bci == 0) || (!is_native() && 0 <= bci && bci < code_size()),
         "illegal bci: %d for %s method", bci, is_native() ? "native" : "non-native");
  address bcp = code_base() + bci;
  assert((is_native() && bcp == code_base()) || contains(bcp), "bcp doesn't belong to this method");
  return bcp;
}

address Method::bcp_from(address bcp) const {
  if (is_native() && bcp == nullptr) {
    return code_base();
  } else {
    return bcp;
  }
}

int Method::size(bool is_native) {
  // If native, then include pointers for native_function and signature_handler
  int extra_bytes = (is_native) ? 2*sizeof(address*) : 0;
  int extra_words = align_up(extra_bytes, BytesPerWord) / BytesPerWord;
  return align_metadata_size(header_size() + extra_words);
}

Symbol* Method::klass_name() const {
  return method_holder()->name();
}

void Method::metaspace_pointers_do(MetaspaceClosure* it) {
  log_trace(aot)("Iter(Method): %p", this);

  if (!method_holder()->is_rewritten()) {
    it->push(&_constMethod, MetaspaceClosure::_writable);
  } else {
    it->push(&_constMethod);
  }
  it->push(&_adapter);
  it->push(&_method_data);
  it->push(&_method_counters);
  NOT_PRODUCT(it->push(&_name);)
}

#if INCLUDE_CDS
// Attempt to return method to original state.  Clear any pointers
// (to objects outside the shared spaces).  We won't be able to predict
// where they should point in a new JVM.  Further initialize some
// entries now in order allow them to be write protected later.

void Method::remove_unshareable_info() {
  unlink_method();
  if (method_data() != nullptr) {
    method_data()->remove_unshareable_info();
  }
  if (method_counters() != nullptr) {
    method_counters()->remove_unshareable_info();
  }
  if (CDSConfig::is_dumping_adapters() && _adapter != nullptr) {
    _adapter->remove_unshareable_info();
    _adapter = nullptr;
  }
  JFR_ONLY(REMOVE_METHOD_ID(this);)
}

void Method::restore_unshareable_info(TRAPS) {
  assert(is_method() && is_valid_method(this), "ensure C++ vtable is restored");
  if (method_data() != nullptr) {
    method_data()->restore_unshareable_info(CHECK);
  }
  if (method_counters() != nullptr) {
    method_counters()->restore_unshareable_info(CHECK);
  }
  if (_adapter != nullptr) {
    assert(_adapter->is_linked(), "must be");
    _from_compiled_entry = _adapter->get_c2i_entry();
  }
  assert(!queued_for_compilation(), "method's queued_for_compilation flag should not be set");
}
#endif

void Method::set_vtable_index(int index) {
  if (in_aot_cache() && !AOTMetaspace::remapped_readwrite() && method_holder()->verified_at_dump_time()) {
    // At runtime initialize_vtable is rerun as part of link_class_impl()
    // for a shared class loaded by the non-boot loader to obtain the loader
    // constraints based on the runtime classloaders' context.
    return; // don't write into the shared class
  } else {
    _vtable_index = index;
  }
}

void Method::set_itable_index(int index) {
  if (in_aot_cache() && !AOTMetaspace::remapped_readwrite() && method_holder()->verified_at_dump_time()) {
    // At runtime initialize_itable is rerun as part of link_class_impl()
    // for a shared class loaded by the non-boot loader to obtain the loader
    // constraints based on the runtime classloaders' context. The dumptime
    // itable index should be the same as the runtime index.
    assert(_vtable_index == itable_index_max - index,
           "archived itable index is different from runtime index");
    return; // don't write into the shared class
  } else {
    _vtable_index = itable_index_max - index;
  }
  assert(valid_itable_index(), "");
}

// The RegisterNatives call being attempted tried to register with a method that
// is not native.  Ask JVM TI what prefixes have been specified.  Then check
// to see if the native method is now wrapped with the prefixes.  See the
// SetNativeMethodPrefix(es) functions in the JVM TI Spec for details.
static Method* find_prefixed_native(Klass* k, Symbol* name, Symbol* signature, TRAPS) {
#if INCLUDE_JVMTI
  ResourceMark rm(THREAD);
  Method* method;
  int name_len = name->utf8_length();
  char* name_str = name->as_utf8();
  int prefix_count;
  char** prefixes = JvmtiExport::get_all_native_method_prefixes(&prefix_count);
  for (int i = 0; i < prefix_count; i++) {
    char* prefix = prefixes[i];
    int prefix_len = (int)strlen(prefix);

    // try adding this prefix to the method name and see if it matches another method name
    int trial_len = name_len + prefix_len;
    char* trial_name_str = NEW_RESOURCE_ARRAY(char, trial_len + 1);
    strcpy(trial_name_str, prefix);
    strcat(trial_name_str, name_str);
    TempNewSymbol trial_name = SymbolTable::probe(trial_name_str, trial_len);
    if (trial_name == nullptr) {
      continue; // no such symbol, so this prefix wasn't used, try the next prefix
    }
    method = k->lookup_method(trial_name, signature);
    if (method == nullptr) {
      continue; // signature doesn't match, try the next prefix
    }
    if (method->is_native()) {
      method->set_is_prefixed_native();
      return method; // wahoo, we found a prefixed version of the method, return it
    }
    // found as non-native, so prefix is good, add it, probably just need more prefixes
    name_len = trial_len;
    name_str = trial_name_str;
  }
#endif // INCLUDE_JVMTI
  return nullptr; // not found
}

bool Method::register_native(Klass* k, Symbol* name, Symbol* signature, address entry, TRAPS) {
  Method* method = k->lookup_method(name, signature);
  if (method == nullptr) {
    ResourceMark rm(THREAD);
    stringStream st;
    st.print("Method '");
    print_external_name(&st, k, name, signature);
    st.print("' name or signature does not match");
    THROW_MSG_(vmSymbols::java_lang_NoSuchMethodError(), st.as_string(), false);
  }
  if (!method->is_native()) {
    // trying to register to a non-native method, see if a JVM TI agent has added prefix(es)
    method = find_prefixed_native(k, name, signature, THREAD);
    if (method == nullptr) {
      ResourceMark rm(THREAD);
      stringStream st;
      st.print("Method '");
      print_external_name(&st, k, name, signature);
      st.print("' is not declared as native");
      THROW_MSG_(vmSymbols::java_lang_NoSuchMethodError(), st.as_string(), false);
    }
  }

  if (entry != nullptr) {
    method->set_native_function(entry, native_bind_event_is_interesting);
  } else {
    method->clear_native_function();
  }
  if (log_is_enabled(Debug, jni, resolve)) {
    ResourceMark rm(THREAD);
    log_debug(jni, resolve)("[Registering JNI native method %s.%s]",
                            method->method_holder()->external_name(),
                            method->name()->as_C_string());
  }
  return true;
}

bool Method::was_executed_more_than(int n) {
  // Invocation counter is reset when the Method* is compiled.
  // If the method has compiled code we therefore assume it has
  // be executed more than n times.
  if (is_accessor() || is_empty_method() || (code() != nullptr)) {
    // interpreter doesn't bump invocation counter of trivial methods
    // compiler does not bump invocation counter of compiled methods
    return true;
  }
  else if ((method_counters() != nullptr &&
            method_counters()->invocation_counter()->carry()) ||
           (method_data() != nullptr &&
            method_data()->invocation_counter()->carry())) {
    // The carry bit is set when the counter overflows and causes
    // a compilation to occur.  We don't know how many times
    // the counter has been reset, so we simply assume it has
    // been executed more than n times.
    return true;
  } else {
    return invocation_count() > n;
  }
}

void Method::print_invocation_count(outputStream* st) {
  //---<  compose+print method return type, klass, name, and signature  >---
  if (is_static())       { st->print("static "); }
  if (is_final())        { st->print("final "); }
  if (is_synchronized()) { st->print("synchronized "); }
  if (is_native())       { st->print("native "); }
  st->print("%s::", method_holder()->external_name());
  name()->print_symbol_on(st);
  signature()->print_symbol_on(st);

  if (WizardMode) {
    // dump the size of the byte codes
    st->print(" {%d}", code_size());
  }
  st->cr();

  // Counting based on signed int counters tends to overflow with
  // longer-running workloads on fast machines. The counters under
  // consideration here, however, are limited in range by counting
  // logic. See InvocationCounter:count_limit for example.
  // No "overflow precautions" need to be implemented here.
  st->print_cr ("  interpreter_invocation_count: " INT32_FORMAT_W(11), interpreter_invocation_count());
  st->print_cr ("  invocation_counter:           " INT32_FORMAT_W(11), invocation_count());
  st->print_cr ("  backedge_counter:             " INT32_FORMAT_W(11), backedge_count());

  if (method_data() != nullptr) {
    st->print_cr ("  decompile_count:              " UINT32_FORMAT_W(11), method_data()->decompile_count());
  }

#ifndef PRODUCT
  if (CountCompiledCalls) {
    st->print_cr ("  compiled_invocation_count:    " INT64_FORMAT_W(11), compiled_invocation_count());
  }
#endif
}

MethodTrainingData* Method::training_data_or_null() const {
  MethodCounters* mcs = method_counters();
  if (mcs == nullptr) {
    return nullptr;
  } else {
    MethodTrainingData* mtd = mcs->method_training_data();
    if (mtd == mcs->method_training_data_sentinel()) {
      return nullptr;
    }
    return mtd;
  }
}

bool Method::init_training_data(MethodTrainingData* td) {
  MethodCounters* mcs = method_counters();
  if (mcs == nullptr) {
    return false;
  } else {
    return mcs->init_method_training_data(td);
  }
}

bool Method::install_training_method_data(const methodHandle& method) {
  MethodTrainingData* mtd = MethodTrainingData::find(method);
  if (mtd != nullptr && mtd->final_profile() != nullptr) {
    AtomicAccess::replace_if_null(&method->_method_data, mtd->final_profile());
    return true;
  }
  return false;
}

// Build a MethodData* object to hold profiling information collected on this
// method when requested.
void Method::build_profiling_method_data(const methodHandle& method, TRAPS) {
  if (install_training_method_data(method)) {
    return;
  }
  // Do not profile the method if metaspace has hit an OOM previously
  // allocating profiling data. Callers clear pending exception so don't
  // add one here.
  if (ClassLoaderDataGraph::has_metaspace_oom()) {
    return;
  }

  ClassLoaderData* loader_data = method->method_holder()->class_loader_data();
  MethodData* method_data = MethodData::allocate(loader_data, method, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    CompileBroker::log_metaspace_failure();
    ClassLoaderDataGraph::set_metaspace_oom(true);
    return;   // return the exception (which is cleared)
  }

  if (!AtomicAccess::replace_if_null(&method->_method_data, method_data)) {
    MetadataFactory::free_metadata(loader_data, method_data);
    return;
  }

  if (PrintMethodData && (Verbose || WizardMode)) {
    ResourceMark rm(THREAD);
    tty->print("build_profiling_method_data for ");
    method->print_name(tty);
    tty->cr();
    // At the end of the run, the MDO, full of data, will be dumped.
  }
}

MethodCounters* Method::build_method_counters(Thread* current, Method* m) {
  // Do not profile the method if metaspace has hit an OOM previously
  if (ClassLoaderDataGraph::has_metaspace_oom()) {
    return nullptr;
  }

  methodHandle mh(current, m);
  MethodCounters* counters;
  if (current->is_Java_thread()) {
    JavaThread* THREAD = JavaThread::cast(current); // For exception macros.
    // Use the TRAPS version for a JavaThread so it will adjust the GC threshold
    // if needed.
    counters = MethodCounters::allocate_with_exception(mh, THREAD);
    if (HAS_PENDING_EXCEPTION) {
      CLEAR_PENDING_EXCEPTION;
    }
  } else {
    // Call metaspace allocation that doesn't throw exception if the
    // current thread isn't a JavaThread, ie. the VMThread.
    counters = MethodCounters::allocate_no_exception(mh);
  }

  if (counters == nullptr) {
    CompileBroker::log_metaspace_failure();
    ClassLoaderDataGraph::set_metaspace_oom(true);
    return nullptr;
  }

  if (!mh->init_method_counters(counters)) {
    MetadataFactory::free_metadata(mh->method_holder()->class_loader_data(), counters);
  }

  return mh->method_counters();
}

bool Method::init_method_counters(MethodCounters* counters) {
  // Try to install a pointer to MethodCounters, return true on success.
  return AtomicAccess::replace_if_null(&_method_counters, counters);
}

void Method::set_exception_handler_entered(int handler_bci) {
  if (ProfileExceptionHandlers) {
    MethodData* mdo = method_data();
    if (mdo != nullptr) {
      BitData handler_data = mdo->exception_handler_bci_to_data(handler_bci);
      handler_data.set_exception_handler_entered();
    }
  }
}

int Method::extra_stack_words() {
  // not an inline function, to avoid a header dependency on Interpreter
  return extra_stack_entries() * Interpreter::stackElementSize;
}

bool Method::compute_has_loops_flag() {
  BytecodeStream bcs(methodHandle(Thread::current(), this));
  Bytecodes::Code bc;

  while ((bc = bcs.next()) >= 0) {
    switch (bc) {
      case Bytecodes::_ifeq:
      case Bytecodes::_ifnull:
      case Bytecodes::_iflt:
      case Bytecodes::_ifle:
      case Bytecodes::_ifne:
      case Bytecodes::_ifnonnull:
      case Bytecodes::_ifgt:
      case Bytecodes::_ifge:
      case Bytecodes::_if_icmpeq:
      case Bytecodes::_if_icmpne:
      case Bytecodes::_if_icmplt:
      case Bytecodes::_if_icmpgt:
      case Bytecodes::_if_icmple:
      case Bytecodes::_if_icmpge:
      case Bytecodes::_if_acmpeq:
      case Bytecodes::_if_acmpne:
      case Bytecodes::_goto:
      case Bytecodes::_jsr:
        if (bcs.dest() < bcs.next_bci()) {
          return set_has_loops();
        }
        break;

      case Bytecodes::_goto_w:
      case Bytecodes::_jsr_w:
        if (bcs.dest_w() < bcs.next_bci()) {
          return set_has_loops();
        }
        break;

      case Bytecodes::_lookupswitch: {
        Bytecode_lookupswitch lookupswitch(this, bcs.bcp());
        if (lookupswitch.default_offset() < 0) {
          return set_has_loops();
        } else {
          for (int i = 0; i < lookupswitch.number_of_pairs(); ++i) {
            LookupswitchPair pair = lookupswitch.pair_at(i);
            if (pair.offset() < 0) {
              return set_has_loops();
            }
          }
        }
        break;
      }
      case Bytecodes::_tableswitch: {
        Bytecode_tableswitch tableswitch(this, bcs.bcp());
        if (tableswitch.default_offset() < 0) {
          return set_has_loops();
        } else {
          for (int i = 0; i < tableswitch.length(); ++i) {
            if (tableswitch.dest_offset_at(i) < 0) {
              return set_has_loops();
            }
          }
        }
        break;
      }
      default:
        break;
    }
  }

  _flags.set_has_loops_flag_init(true);
  return false;
}

bool Method::is_final_method(AccessFlags class_access_flags) const {
  // or "does_not_require_vtable_entry"
  // default method or overpass can occur, is not final (reuses vtable entry)
  // private methods in classes get vtable entries for backward class compatibility.
  if (is_overpass() || is_default_method())  return false;
  return is_final() || class_access_flags.is_final();
}

bool Method::is_final_method() const {
  return is_final_method(method_holder()->access_flags());
}

bool Method::is_default_method() const {
  if (method_holder() != nullptr &&
      method_holder()->is_interface() &&
      !is_abstract() && !is_private()) {
    return true;
  } else {
    return false;
  }
}

bool Method::can_be_statically_bound(AccessFlags class_access_flags) const {
  if (is_final_method(class_access_flags))  return true;
#ifdef ASSERT
  bool is_nonv = (vtable_index() == nonvirtual_vtable_index);
  if (class_access_flags.is_interface()) {
      ResourceMark rm;
      assert(is_nonv == is_static() || is_nonv == is_private(),
             "nonvirtual unexpected for non-static, non-private: %s",
             name_and_sig_as_C_string());
  }
#endif
  assert(valid_vtable_index() || valid_itable_index(), "method must be linked before we ask this question");
  return vtable_index() == nonvirtual_vtable_index;
}

bool Method::can_be_statically_bound() const {
  return can_be_statically_bound(method_holder()->access_flags());
}

bool Method::can_be_statically_bound(InstanceKlass* context) const {
  return (method_holder() == context) && can_be_statically_bound();
}

/**
 *  Returns false if this is one of specially treated methods for
 *  which we have to provide stack trace in throw in compiled code.
 *  Returns true otherwise.
 */
bool Method::can_omit_stack_trace() {
  if (klass_name() == vmSymbols::sun_invoke_util_ValueConversions()) {
    return false; // All methods in sun.invoke.util.ValueConversions
  }
  return true;
}

bool Method::is_accessor() const {
  return is_getter() || is_setter();
}

bool Method::is_getter() const {
  if (code_size() != 5) return false;
  if (size_of_parameters() != 1) return false;
  if (java_code_at(0) != Bytecodes::_aload_0)  return false;
  if (java_code_at(1) != Bytecodes::_getfield) return false;
  switch (java_code_at(4)) {
    case Bytecodes::_ireturn:
    case Bytecodes::_lreturn:
    case Bytecodes::_freturn:
    case Bytecodes::_dreturn:
    case Bytecodes::_areturn:
      break;
    default:
      return false;
  }
  return true;
}

bool Method::is_setter() const {
  if (code_size() != 6) return false;
  if (java_code_at(0) != Bytecodes::_aload_0) return false;
  switch (java_code_at(1)) {
    case Bytecodes::_iload_1:
    case Bytecodes::_aload_1:
    case Bytecodes::_fload_1:
      if (size_of_parameters() != 2) return false;
      break;
    case Bytecodes::_dload_1:
    case Bytecodes::_lload_1:
      if (size_of_parameters() != 3) return false;
      break;
    default:
      return false;
  }
  if (java_code_at(2) != Bytecodes::_putfield) return false;
  if (java_code_at(5) != Bytecodes::_return)   return false;
  return true;
}

bool Method::is_constant_getter() const {
  int last_index = code_size() - 1;
  // Check if the first 1-3 bytecodes are a constant push
  // and the last bytecode is a return.
  return (2 <= code_size() && code_size() <= 4 &&
          Bytecodes::is_const(java_code_at(0)) &&
          Bytecodes::length_for(java_code_at(0)) == last_index &&
          Bytecodes::is_return(java_code_at(last_index)));
}

bool Method::has_valid_initializer_flags() const {
  return (is_static() ||
          method_holder()->major_version() < 51);
}

bool Method::is_static_initializer() const {
  // For classfiles version 51 or greater, ensure that the clinit method is
  // static.  Non-static methods with the name "<clinit>" are not static
  // initializers. (older classfiles exempted for backward compatibility)
  return name() == vmSymbols::class_initializer_name() &&
         has_valid_initializer_flags();
}

bool Method::is_object_initializer() const {
   return name() == vmSymbols::object_initializer_name();
}

bool Method::needs_clinit_barrier() const {
  return is_static() && !method_holder()->is_initialized();
}

bool Method::is_object_wait0() const {
  return klass_name() == vmSymbols::java_lang_Object()
         && name() == vmSymbols::wait_name();
}

objArrayHandle Method::resolved_checked_exceptions_impl(Method* method, TRAPS) {
  int length = method->checked_exceptions_length();
  if (length == 0) {  // common case
    return objArrayHandle(THREAD, Universe::the_empty_class_array());
  } else {
    methodHandle h_this(THREAD, method);
    objArrayOop m_oop = oopFactory::new_objArray(vmClasses::Class_klass(), length, CHECK_(objArrayHandle()));
    objArrayHandle mirrors (THREAD, m_oop);
    for (int i = 0; i < length; i++) {
      CheckedExceptionElement* table = h_this->checked_exceptions_start(); // recompute on each iteration, not gc safe
      Klass* k = h_this->constants()->klass_at(table[i].class_cp_index, CHECK_(objArrayHandle()));
      if (log_is_enabled(Warning, exceptions) &&
          !k->is_subclass_of(vmClasses::Throwable_klass())) {
        ResourceMark rm(THREAD);
        log_warning(exceptions)(
          "Class %s in throws clause of method %s is not a subtype of class java.lang.Throwable",
          k->external_name(), method->external_name());
      }
      mirrors->obj_at_put(i, k->java_mirror());
    }
    return mirrors;
  }
};


int Method::line_number_from_bci(int bci) const {
  int best_bci  =  0;
  int best_line = -1;
  if (bci == SynchronizationEntryBCI) bci = 0;
  if (0 <= bci && bci < code_size() && has_linenumber_table()) {
    // The line numbers are a short array of 2-tuples [start_pc, line_number].
    // Not necessarily sorted and not necessarily one-to-one.
    CompressedLineNumberReadStream stream(compressed_linenumber_table());
    while (stream.read_pair()) {
      if (stream.bci() == bci) {
        // perfect match
        return stream.line();
      } else {
        // update best_bci/line
        if (stream.bci() < bci && stream.bci() >= best_bci) {
          best_bci  = stream.bci();
          best_line = stream.line();
        }
      }
    }
  }
  return best_line;
}


bool Method::is_klass_loaded_by_klass_index(int klass_index) const {
  if( constants()->tag_at(klass_index).is_unresolved_klass() ) {
    Thread *thread = Thread::current();
    Symbol* klass_name = constants()->klass_name_at(klass_index);
    Handle loader(thread, method_holder()->class_loader());
    return SystemDictionary::find_instance_klass(thread, klass_name, loader) != nullptr;
  } else {
    return true;
  }
}


bool Method::is_klass_loaded(int refinfo_index, Bytecodes::Code bc, bool must_be_resolved) const {
  int klass_index = constants()->klass_ref_index_at(refinfo_index, bc);
  if (must_be_resolved) {
    // Make sure klass is resolved in constantpool.
    if (constants()->tag_at(klass_index).is_unresolved_klass()) return false;
  }
  return is_klass_loaded_by_klass_index(klass_index);
}


void Method::set_native_function(address function, bool post_event_flag) {
  assert(function != nullptr, "use clear_native_function to unregister natives");
  assert(!is_special_native_intrinsic() || function == SharedRuntime::native_method_throw_unsatisfied_link_error_entry(), "");
  address* native_function = native_function_addr();

  // We can see racers trying to place the same native function into place. Once
  // is plenty.
  address current = *native_function;
  if (current == function) return;
  if (post_event_flag && JvmtiExport::should_post_native_method_bind() &&
      function != nullptr) {
    // native_method_throw_unsatisfied_link_error_entry() should only
    // be passed when post_event_flag is false.
    assert(function !=
      SharedRuntime::native_method_throw_unsatisfied_link_error_entry(),
      "post_event_flag mismatch");

    // post the bind event, and possible change the bind function
    JvmtiExport::post_native_method_bind(this, &function);
  }
  *native_function = function;
  // This function can be called more than once. We must make sure that we always
  // use the latest registered method -> check if a stub already has been generated.
  // If so, we have to make it not_entrant.
  nmethod* nm = code(); // Put it into local variable to guard against concurrent updates
  if (nm != nullptr) {
    nm->make_not_entrant(nmethod::InvalidationReason::SET_NATIVE_FUNCTION);
  }
}


bool Method::has_native_function() const {
  if (is_special_native_intrinsic())
    return false;  // special-cased in SharedRuntime::generate_native_wrapper
  address func = native_function();
  return (func != nullptr && func != SharedRuntime::native_method_throw_unsatisfied_link_error_entry());
}


void Method::clear_native_function() {
  // Note: is_method_handle_intrinsic() is allowed here.
  set_native_function(
    SharedRuntime::native_method_throw_unsatisfied_link_error_entry(),
    !native_bind_event_is_interesting);
  this->unlink_code();
}


void Method::set_signature_handler(address handler) {
  address* signature_handler =  signature_handler_addr();
  *signature_handler = handler;
}


void Method::print_made_not_compilable(int comp_level, bool is_osr, bool report, const char* reason) {
  assert(reason != nullptr, "must provide a reason");
  if (PrintCompilation && report) {
    ttyLocker ttyl;
    tty->print("made not %scompilable on ", is_osr ? "OSR " : "");
    if (comp_level == CompLevel_all) {
      tty->print("all levels ");
    } else {
      tty->print("level %d ", comp_level);
    }
    this->print_short_name(tty);
    int size = this->code_size();
    if (size > 0) {
      tty->print(" (%d bytes)", size);
    }
    if (reason != nullptr) {
      tty->print("   %s", reason);
    }
    tty->cr();
  }
  if ((TraceDeoptimization || LogCompilation) && (xtty != nullptr)) {
    ttyLocker ttyl;
    xtty->begin_elem("make_not_compilable thread='%zu' osr='%d' level='%d'",
                     os::current_thread_id(), is_osr, comp_level);
    if (reason != nullptr) {
      xtty->print(" reason=\'%s\'", reason);
    }
    xtty->method(this);
    xtty->stamp();
    xtty->end_elem();
  }
}

bool Method::is_always_compilable() const {
  // Generated adapters must be compiled
  if (is_special_native_intrinsic() && is_synthetic()) {
    assert(!is_not_c1_compilable(), "sanity check");
    assert(!is_not_c2_compilable(), "sanity check");
    return true;
  }

  return false;
}

bool Method::is_not_compilable(int comp_level) const {
  if (number_of_breakpoints() > 0)
    return true;
  if (is_always_compilable())
    return false;
  if (comp_level == CompLevel_any)
    return is_not_c1_compilable() && is_not_c2_compilable();
  if (is_c1_compile(comp_level))
    return is_not_c1_compilable();
  if (is_c2_compile(comp_level))
    return is_not_c2_compilable();
  return false;
}

// call this when compiler finds that this method is not compilable
void Method::set_not_compilable(const char* reason, int comp_level, bool report) {
  if (is_always_compilable()) {
    // Don't mark a method which should be always compilable
    return;
  }
  print_made_not_compilable(comp_level, /*is_osr*/ false, report, reason);
  if (comp_level == CompLevel_all) {
    set_is_not_c1_compilable();
    set_is_not_c2_compilable();
  } else {
    if (is_c1_compile(comp_level))
      set_is_not_c1_compilable();
    if (is_c2_compile(comp_level))
      set_is_not_c2_compilable();
  }
  assert(!CompilationPolicy::can_be_compiled(methodHandle(Thread::current(), this), comp_level), "sanity check");
}

bool Method::is_not_osr_compilable(int comp_level) const {
  if (is_not_compilable(comp_level))
    return true;
  if (comp_level == CompLevel_any)
    return is_not_c1_osr_compilable() && is_not_c2_osr_compilable();
  if (is_c1_compile(comp_level))
    return is_not_c1_osr_compilable();
  if (is_c2_compile(comp_level))
    return is_not_c2_osr_compilable();
  return false;
}

void Method::set_not_osr_compilable(const char* reason, int comp_level, bool report) {
  print_made_not_compilable(comp_level, /*is_osr*/ true, report, reason);
  if (comp_level == CompLevel_all) {
    set_is_not_c1_osr_compilable();
    set_is_not_c2_osr_compilable();
  } else {
    if (is_c1_compile(comp_level))
      set_is_not_c1_osr_compilable();
    if (is_c2_compile(comp_level))
      set_is_not_c2_osr_compilable();
  }
  assert(!CompilationPolicy::can_be_osr_compiled(methodHandle(Thread::current(), this), comp_level), "sanity check");
}

// Revert to using the interpreter and clear out the nmethod
void Method::clear_code() {
  // this may be null if c2i adapters have not been made yet
  // Only should happen at allocate time.
  if (adapter() == nullptr) {
    _from_compiled_entry = nullptr;
  } else {
    _from_compiled_entry = adapter()->get_c2i_entry();
  }
  OrderAccess::storestore();
  _from_interpreted_entry = _i2i_entry;
  OrderAccess::storestore();
  _code = nullptr;
}

void Method::unlink_code(nmethod *compare) {
  ConditionalMutexLocker ml(NMethodState_lock, !NMethodState_lock->owned_by_self(), Mutex::_no_safepoint_check_flag);
  // We need to check if either the _code or _from_compiled_code_entry_point
  // refer to this nmethod because there is a race in setting these two fields
  // in Method* as seen in bugid 4947125.
  if (code() == compare ||
      from_compiled_entry() == compare->verified_entry_point()) {
    clear_code();
  }
}

void Method::unlink_code() {
  ConditionalMutexLocker ml(NMethodState_lock, !NMethodState_lock->owned_by_self(), Mutex::_no_safepoint_check_flag);
  clear_code();
}

#if INCLUDE_CDS
// Called by class data sharing to remove any entry points (which are not shared)
void Method::unlink_method() {
  assert(CDSConfig::is_dumping_archive(), "sanity");
  _code = nullptr;
  if (!CDSConfig::is_dumping_adapters()) {
    _adapter = nullptr;
  }
  _i2i_entry = nullptr;
  _from_compiled_entry = nullptr;
  _from_interpreted_entry = nullptr;

  if (is_native()) {
    *native_function_addr() = nullptr;
    set_signature_handler(nullptr);
  }
  NOT_PRODUCT(set_compiled_invocation_count(0);)

  clear_method_data();
  clear_method_counters();
  clear_is_not_c1_compilable();
  clear_is_not_c1_osr_compilable();
  clear_is_not_c2_compilable();
  clear_is_not_c2_osr_compilable();
  clear_queued_for_compilation();

  remove_unshareable_flags();
}

void Method::remove_unshareable_flags() {
  // clear all the flags that shouldn't be in the archived version
  assert(!is_old(), "must be");
  assert(!is_obsolete(), "must be");
  assert(!is_deleted(), "must be");

  set_is_prefixed_native(false);
  set_queued_for_compilation(false);
  set_is_not_c2_compilable(false);
  set_is_not_c1_compilable(false);
  set_is_not_c2_osr_compilable(false);
  set_on_stack_flag(false);
}
#endif

// Called when the method_holder is getting linked. Setup entrypoints so the method
// is ready to be called from interpreter, compiler, and vtables.
void Method::link_method(const methodHandle& h_method, TRAPS) {
  if (log_is_enabled(Info, perf, class, link)) {
    ClassLoader::perf_ik_link_methods_count()->inc();
  }

  // If the code cache is full, we may reenter this function for the
  // leftover methods that weren't linked.
  if (adapter() != nullptr) {
    if (adapter()->in_aot_cache()) {
      assert(adapter()->is_linked(), "Adapter is shared but not linked");
    } else {
      return;
    }
  }
  assert( _code == nullptr, "nothing compiled yet" );

  // Setup interpreter entrypoint
  assert(this == h_method(), "wrong h_method()" );

  assert(adapter() == nullptr || adapter()->is_linked(), "init'd to null or restored from cache");
  address entry = Interpreter::entry_for_method(h_method);
  assert(entry != nullptr, "interpreter entry must be non-null");
  // Sets both _i2i_entry and _from_interpreted_entry
  set_interpreter_entry(entry);

  // Don't overwrite already registered native entries.
  if (is_native() && !has_native_function()) {
    set_native_function(
      SharedRuntime::native_method_throw_unsatisfied_link_error_entry(),
      !native_bind_event_is_interesting);
  }

  // Setup compiler entrypoint.  This is made eagerly, so we do not need
  // special handling of vtables.  An alternative is to make adapters more
  // lazily by calling make_adapter() from from_compiled_entry() for the
  // normal calls.  For vtable calls life gets more complicated.  When a
  // call-site goes mega-morphic we need adapters in all methods which can be
  // called from the vtable.  We need adapters on such methods that get loaded
  // later.  Ditto for mega-morphic itable calls.  If this proves to be a
  // problem we'll make these lazily later.
  if (is_abstract()) {
    h_method->_from_compiled_entry = SharedRuntime::get_handle_wrong_method_abstract_stub();
  } else if (_adapter == nullptr) {
    (void) make_adapters(h_method, CHECK);
#ifndef ZERO
    assert(adapter()->is_linked(), "Adapter must have been linked");
#endif
    h_method->_from_compiled_entry = adapter()->get_c2i_entry();
  }

  // ONLY USE the h_method now as make_adapter may have blocked

  if (h_method->is_continuation_native_intrinsic()) {
    _from_interpreted_entry = nullptr;
    _from_compiled_entry = nullptr;
    _i2i_entry = nullptr;
    if (Continuations::enabled()) {
      assert(!Threads::is_vm_complete(), "should only be called during vm init");
      AdapterHandlerLibrary::create_native_wrapper(h_method);
      if (!h_method->has_compiled_code()) {
        THROW_MSG(vmSymbols::java_lang_OutOfMemoryError(), "Initial size of CodeCache is too small");
      }
      assert(_from_interpreted_entry == get_i2c_entry(), "invariant");
    }
  }
}

address Method::make_adapters(const methodHandle& mh, TRAPS) {
  assert(!mh->is_abstract(), "abstract methods do not have adapters");
  PerfTraceTime timer(ClassLoader::perf_method_adapters_time());

  // Adapters for compiled code are made eagerly here.  They are fairly
  // small (generally < 100 bytes) and quick to make (and cached and shared)
  // so making them eagerly shouldn't be too expensive.
  AdapterHandlerEntry* adapter = AdapterHandlerLibrary::get_adapter(mh);
  if (adapter == nullptr ) {
    if (!is_init_completed()) {
      // Don't throw exceptions during VM initialization because java.lang.* classes
      // might not have been initialized, causing problems when constructing the
      // Java exception object.
      vm_exit_during_initialization("Out of space in CodeCache for adapters");
    } else {
      THROW_MSG_NULL(vmSymbols::java_lang_OutOfMemoryError(), "Out of space in CodeCache for adapters");
    }
  }

  mh->set_adapter_entry(adapter);
  return adapter->get_c2i_entry();
}

// The verified_code_entry() must be called when a invoke is resolved
// on this method.

// It returns the compiled code entry point, after asserting not null.
// This function is called after potential safepoints so that nmethod
// or adapter that it points to is still live and valid.
// This function must not hit a safepoint!
address Method::verified_code_entry() {
  DEBUG_ONLY(NoSafepointVerifier nsv;)
  assert(_from_compiled_entry != nullptr, "must be set");
  return _from_compiled_entry;
}

// Check that if an nmethod ref exists, it has a backlink to this or no backlink at all
// (could be racing a deopt).
// Not inline to avoid circular ref.
bool Method::check_code() const {
  // cached in a register or local.  There's a race on the value of the field.
  nmethod *code = AtomicAccess::load_acquire(&_code);
  return code == nullptr || (code->method() == nullptr) || (code->method() == (Method*)this && !code->is_osr_method());
}

// Install compiled code.  Instantly it can execute.
void Method::set_code(const methodHandle& mh, nmethod *code) {
  assert_lock_strong(NMethodState_lock);
  assert( code, "use clear_code to remove code" );
  assert( mh->check_code(), "" );

  guarantee(mh->adapter() != nullptr, "Adapter blob must already exist!");

  // These writes must happen in this order, because the interpreter will
  // directly jump to from_interpreted_entry which jumps to an i2c adapter
  // which jumps to _from_compiled_entry.
  mh->_code = code;             // Assign before allowing compiled code to exec

  int comp_level = code->comp_level();
  // In theory there could be a race here. In practice it is unlikely
  // and not worth worrying about.
  if (comp_level > mh->highest_comp_level()) {
    mh->set_highest_comp_level(comp_level);
  }

  OrderAccess::storestore();
  mh->_from_compiled_entry = code->verified_entry_point();
  OrderAccess::storestore();

  if (mh->is_continuation_native_intrinsic()) {
    assert(mh->_from_interpreted_entry == nullptr, "initialized incorrectly"); // see link_method

    if (mh->is_continuation_enter_intrinsic()) {
      // This is the entry used when we're in interpreter-only mode; see InterpreterMacroAssembler::jump_from_interpreted
      mh->_i2i_entry = ContinuationEntry::interpreted_entry();
    } else if (mh->is_continuation_yield_intrinsic()) {
      mh->_i2i_entry = mh->get_i2c_entry();
    } else {
      guarantee(false, "Unknown Continuation native intrinsic");
    }
    // This must come last, as it is what's tested in LinkResolver::resolve_static_call
    AtomicAccess::release_store(&mh->_from_interpreted_entry , mh->get_i2c_entry());
  } else if (!mh->is_method_handle_intrinsic()) {
    // Instantly compiled code can execute.
    mh->_from_interpreted_entry = mh->get_i2c_entry();
  }
}


bool Method::is_overridden_in(Klass* k) const {
  InstanceKlass* ik = InstanceKlass::cast(k);

  if (ik->is_interface()) return false;

  // If method is an interface, we skip it - except if it
  // is a miranda method
  if (method_holder()->is_interface()) {
    // Check that method is not a miranda method
    if (ik->lookup_method(name(), signature()) == nullptr) {
      // No implementation exist - so miranda method
      return false;
    }
    return true;
  }

  assert(ik->is_subclass_of(method_holder()), "should be subklass");
  if (!has_vtable_index()) {
    return false;
  } else {
    Method* vt_m = ik->method_at_vtable(vtable_index());
    return vt_m != this;
  }
}


// give advice about whether this Method* should be cached or not
bool Method::should_not_be_cached() const {
  if (is_old()) {
    // This method has been redefined. It is either EMCP or obsolete
    // and we don't want to cache it because that would pin the method
    // down and prevent it from being collectible if and when it
    // finishes executing.
    return true;
  }

  // caching this method should be just fine
  return false;
}


/**
 *  Returns true if this is one of the specially treated methods for
 *  security related stack walks (like Reflection.getCallerClass).
 */
bool Method::is_ignored_by_security_stack_walk() const {
  if (intrinsic_id() == vmIntrinsics::_invoke) {
    // This is Method.invoke() -- ignore it
    return true;
  }
  if (method_holder()->is_subclass_of(vmClasses::reflect_MethodAccessorImpl_klass())) {
    // This is an auxiliary frame -- ignore it
    return true;
  }
  if (is_method_handle_intrinsic() || is_compiled_lambda_form()) {
    // This is an internal adapter frame for method handles -- ignore it
    return true;
  }
  return false;
}


// Constant pool structure for invoke methods:
enum {
  _imcp_invoke_name = 1,        // utf8: 'invokeExact', etc.
  _imcp_invoke_signature,       // utf8: (variable Symbol*)
  _imcp_limit
};

// Test if this method is an MH adapter frame generated by Java code.
// Cf. java/lang/invoke/InvokerBytecodeGenerator
bool Method::is_compiled_lambda_form() const {
  return intrinsic_id() == vmIntrinsics::_compiledLambdaForm;
}

// Test if this method is an internal MH primitive method.
bool Method::is_method_handle_intrinsic() const {
  vmIntrinsics::ID iid = intrinsic_id();
  return (MethodHandles::is_signature_polymorphic(iid) &&
          MethodHandles::is_signature_polymorphic_intrinsic(iid));
}

bool Method::has_member_arg() const {
  vmIntrinsics::ID iid = intrinsic_id();
  return (MethodHandles::is_signature_polymorphic(iid) &&
          MethodHandles::has_member_arg(iid));
}

// Make an instance of a signature-polymorphic internal MH primitive.
methodHandle Method::make_method_handle_intrinsic(vmIntrinsics::ID iid,
                                                         Symbol* signature,
                                                         TRAPS) {
  ResourceMark rm(THREAD);
  methodHandle empty;

  InstanceKlass* holder = vmClasses::MethodHandle_klass();
  Symbol* name = MethodHandles::signature_polymorphic_intrinsic_name(iid);
  assert(iid == MethodHandles::signature_polymorphic_name_id(name), "");

  log_info(methodhandles)("make_method_handle_intrinsic MH.%s%s", name->as_C_string(), signature->as_C_string());

  // invariant:   cp->symbol_at_put is preceded by a refcount increment (more usually a lookup)
  name->increment_refcount();
  signature->increment_refcount();

  int cp_length = _imcp_limit;
  ClassLoaderData* loader_data = holder->class_loader_data();
  constantPoolHandle cp;
  {
    ConstantPool* cp_oop = ConstantPool::allocate(loader_data, cp_length, CHECK_(empty));
    cp = constantPoolHandle(THREAD, cp_oop);
  }
  cp->copy_fields(holder->constants());
  cp->set_pool_holder(holder);
  cp->symbol_at_put(_imcp_invoke_name,       name);
  cp->symbol_at_put(_imcp_invoke_signature,  signature);
  cp->set_has_preresolution();
  cp->set_is_for_method_handle_intrinsic();

  // decide on access bits:  public or not?
  u2 flags_bits = (JVM_ACC_NATIVE | JVM_ACC_SYNTHETIC | JVM_ACC_FINAL);
  bool must_be_static = MethodHandles::is_signature_polymorphic_static(iid);
  if (must_be_static)  flags_bits |= JVM_ACC_STATIC;
  assert((flags_bits & JVM_ACC_PUBLIC) == 0, "do not expose these methods");

  methodHandle m;
  {
    InlineTableSizes sizes;
    Method* m_oop = Method::allocate(loader_data, 0,
                                     accessFlags_from(flags_bits), &sizes,
                                     ConstMethod::NORMAL,
                                     name,
                                     CHECK_(empty));
    m = methodHandle(THREAD, m_oop);
  }
  m->set_constants(cp());
  m->set_name_index(_imcp_invoke_name);
  m->set_signature_index(_imcp_invoke_signature);
  assert(MethodHandles::is_signature_polymorphic_name(m->name()), "");
  assert(m->signature() == signature, "");
  m->constMethod()->compute_from_signature(signature, must_be_static);
  m->init_intrinsic_id(klass_id_for_intrinsics(m->method_holder()));
  assert(m->is_method_handle_intrinsic(), "");
#ifdef ASSERT
  if (!MethodHandles::is_signature_polymorphic(m->intrinsic_id()))  m->print();
  assert(MethodHandles::is_signature_polymorphic(m->intrinsic_id()), "must be an invoker");
  assert(m->intrinsic_id() == iid, "correctly predicted iid");
#endif //ASSERT

  // Finally, set up its entry points.
  assert(m->can_be_statically_bound(), "");
  m->set_vtable_index(Method::nonvirtual_vtable_index);
  m->link_method(m, CHECK_(empty));

  if (iid == vmIntrinsics::_linkToNative) {
    m->set_interpreter_entry(m->adapter()->get_i2c_entry());
  }
  if (log_is_enabled(Debug, methodhandles)) {
    LogTarget(Debug, methodhandles) lt;
    LogStream ls(lt);
    m->print_on(&ls);
  }

  return m;
}

#if INCLUDE_CDS
void Method::restore_archived_method_handle_intrinsic(methodHandle m, TRAPS) {
  if (m->adapter() != nullptr) {
    m->set_from_compiled_entry(m->adapter()->get_c2i_entry());
  }
  m->link_method(m, CHECK);

  if (m->intrinsic_id() == vmIntrinsics::_linkToNative) {
    m->set_interpreter_entry(m->adapter()->get_i2c_entry());
  }
}
#endif

Klass* Method::check_non_bcp_klass(Klass* klass) {
  if (klass != nullptr && klass->class_loader() != nullptr) {
    if (klass->is_objArray_klass())
      klass = ObjArrayKlass::cast(klass)->bottom_klass();
    return klass;
  }
  return nullptr;
}


methodHandle Method::clone_with_new_data(const methodHandle& m, u_char* new_code, int new_code_length,
                                                u_char* new_compressed_linenumber_table, int new_compressed_linenumber_size, TRAPS) {
  // Code below does not work for native methods - they should never get rewritten anyway
  assert(!m->is_native(), "cannot rewrite native methods");
  // Allocate new Method*
  AccessFlags flags = m->access_flags();

  ConstMethod* cm = m->constMethod();
  int checked_exceptions_len = cm->checked_exceptions_length();
  int localvariable_len = cm->localvariable_table_length();
  int exception_table_len = cm->exception_table_length();
  int method_parameters_len = cm->method_parameters_length();
  int method_annotations_len = cm->method_annotations_length();
  int parameter_annotations_len = cm->parameter_annotations_length();
  int type_annotations_len = cm->type_annotations_length();
  int default_annotations_len = cm->default_annotations_length();

  InlineTableSizes sizes(
      localvariable_len,
      new_compressed_linenumber_size,
      exception_table_len,
      checked_exceptions_len,
      method_parameters_len,
      cm->generic_signature_index(),
      method_annotations_len,
      parameter_annotations_len,
      type_annotations_len,
      default_annotations_len,
      0);

  ClassLoaderData* loader_data = m->method_holder()->class_loader_data();
  Method* newm_oop = Method::allocate(loader_data,
                                      new_code_length,
                                      flags,
                                      &sizes,
                                      m->method_type(),
                                      m->name(),
                                      CHECK_(methodHandle()));
  methodHandle newm (THREAD, newm_oop);

  // Create a shallow copy of Method part, but be careful to preserve the new ConstMethod*
  ConstMethod* newcm = newm->constMethod();
  int new_const_method_size = newm->constMethod()->size();

  // This works because the source and target are both Methods. Some compilers
  // (e.g., clang) complain that the target vtable pointer will be stomped,
  // so cast away newm()'s and m()'s Methodness.
  memcpy((void*)newm(), (void*)m(), sizeof(Method));

  // Create shallow copy of ConstMethod.
  memcpy(newcm, m->constMethod(), sizeof(ConstMethod));

  // Reset correct method/const method, method size, and parameter info
  newm->set_constMethod(newcm);
  newm->constMethod()->set_code_size(new_code_length);
  newm->constMethod()->set_constMethod_size(new_const_method_size);
  assert(newm->code_size() == new_code_length, "check");
  assert(newm->method_parameters_length() == method_parameters_len, "check");
  assert(newm->checked_exceptions_length() == checked_exceptions_len, "check");
  assert(newm->exception_table_length() == exception_table_len, "check");
  assert(newm->localvariable_table_length() == localvariable_len, "check");
  // Copy new byte codes
  memcpy(newm->code_base(), new_code, new_code_length);
  // Copy line number table
  if (new_compressed_linenumber_size > 0) {
    memcpy(newm->compressed_linenumber_table(),
           new_compressed_linenumber_table,
           new_compressed_linenumber_size);
  }
  // Copy method_parameters
  if (method_parameters_len > 0) {
    memcpy(newm->method_parameters_start(),
           m->method_parameters_start(),
           method_parameters_len * sizeof(MethodParametersElement));
  }
  // Copy checked_exceptions
  if (checked_exceptions_len > 0) {
    memcpy(newm->checked_exceptions_start(),
           m->checked_exceptions_start(),
           checked_exceptions_len * sizeof(CheckedExceptionElement));
  }
  // Copy exception table
  if (exception_table_len > 0) {
    memcpy(newm->exception_table_start(),
           m->exception_table_start(),
           exception_table_len * sizeof(ExceptionTableElement));
  }
  // Copy local variable number table
  if (localvariable_len > 0) {
    memcpy(newm->localvariable_table_start(),
           m->localvariable_table_start(),
           localvariable_len * sizeof(LocalVariableTableElement));
  }
  // Copy stackmap table
  if (m->has_stackmap_table()) {
    int code_attribute_length = m->stackmap_data()->length();
    Array<u1>* stackmap_data =
      MetadataFactory::new_array<u1>(loader_data, code_attribute_length, 0, CHECK_(methodHandle()));
    memcpy((void*)stackmap_data->adr_at(0),
           (void*)m->stackmap_data()->adr_at(0), code_attribute_length);
    newm->set_stackmap_data(stackmap_data);
  }

  // copy annotations over to new method
  newcm->copy_annotations_from(loader_data, cm, CHECK_(methodHandle()));
  return newm;
}

vmSymbolID Method::klass_id_for_intrinsics(const Klass* holder) {
  // if loader is not the default loader (i.e., non-null), we can't know the intrinsics
  // because we are not loading from core libraries
  // exception: the AES intrinsics come from lib/ext/sunjce_provider.jar
  // which does not use the class default class loader so we check for its loader here
  const InstanceKlass* ik = InstanceKlass::cast(holder);
  if ((ik->class_loader() != nullptr) && !SystemDictionary::is_platform_class_loader(ik->class_loader())) {
    return vmSymbolID::NO_SID;   // regardless of name, no intrinsics here
  }

  // see if the klass name is well-known:
  Symbol* klass_name = ik->name();
  vmSymbolID id = vmSymbols::find_sid(klass_name);
  if (id != vmSymbolID::NO_SID && vmIntrinsics::class_has_intrinsics(id)) {
    return id;
  } else {
    return vmSymbolID::NO_SID;
  }
}

void Method::init_intrinsic_id(vmSymbolID klass_id) {
  assert(_intrinsic_id == static_cast<int>(vmIntrinsics::_none), "do this just once");
  const uintptr_t max_id_uint = right_n_bits((int)(sizeof(_intrinsic_id) * BitsPerByte));
  assert((uintptr_t)vmIntrinsics::ID_LIMIT <= max_id_uint, "else fix size");
  assert(intrinsic_id_size_in_bytes() == sizeof(_intrinsic_id), "");

  // the klass name is well-known:
  assert(klass_id == klass_id_for_intrinsics(method_holder()), "must be");
  assert(klass_id != vmSymbolID::NO_SID, "caller responsibility");

  // ditto for method and signature:
  vmSymbolID name_id = vmSymbols::find_sid(name());
  if (klass_id != VM_SYMBOL_ENUM_NAME(java_lang_invoke_MethodHandle)
      && klass_id != VM_SYMBOL_ENUM_NAME(java_lang_invoke_VarHandle)
      && name_id == vmSymbolID::NO_SID) {
    return;
  }
  vmSymbolID sig_id = vmSymbols::find_sid(signature());
  if (klass_id != VM_SYMBOL_ENUM_NAME(java_lang_invoke_MethodHandle)
      && klass_id != VM_SYMBOL_ENUM_NAME(java_lang_invoke_VarHandle)
      && sig_id == vmSymbolID::NO_SID) {
    return;
  }

  u2 flags = access_flags().as_method_flags();
  vmIntrinsics::ID id = vmIntrinsics::find_id(klass_id, name_id, sig_id, flags);
  if (id != vmIntrinsics::_none) {
    set_intrinsic_id(id);
    if (id == vmIntrinsics::_Class_cast) {
      // Even if the intrinsic is rejected, we want to inline this simple method.
      set_force_inline();
    }
    return;
  }

  // A few slightly irregular cases:
  switch (klass_id) {
  // Signature-polymorphic methods: MethodHandle.invoke*, InvokeDynamic.*., VarHandle
  case VM_SYMBOL_ENUM_NAME(java_lang_invoke_MethodHandle):
  case VM_SYMBOL_ENUM_NAME(java_lang_invoke_VarHandle):
    if (!is_native())  break;
    id = MethodHandles::signature_polymorphic_name_id(method_holder(), name());
    if (is_static() != MethodHandles::is_signature_polymorphic_static(id))
      id = vmIntrinsics::_none;
    break;

  default:
    break;
  }

  if (id != vmIntrinsics::_none) {
    // Set up its iid.  It is an alias method.
    set_intrinsic_id(id);
    return;
  }
}

bool Method::load_signature_classes(const methodHandle& m, TRAPS) {
  if (!THREAD->can_call_java()) {
    // There is nothing useful this routine can do from within the Compile thread.
    // Hopefully, the signature contains only well-known classes.
    // We could scan for this and return true/false, but the caller won't care.
    return false;
  }
  bool sig_is_loaded = true;
  ResourceMark rm(THREAD);
  for (ResolvingSignatureStream ss(m()); !ss.is_done(); ss.next()) {
    if (ss.is_reference()) {
      // load everything, including arrays "[Lfoo;"
      Klass* klass = ss.as_klass(SignatureStream::ReturnNull, THREAD);
      // We are loading classes eagerly. If a ClassNotFoundException or
      // a LinkageError was generated, be sure to ignore it.
      if (HAS_PENDING_EXCEPTION) {
        if (PENDING_EXCEPTION->is_a(vmClasses::ClassNotFoundException_klass()) ||
            PENDING_EXCEPTION->is_a(vmClasses::LinkageError_klass())) {
          CLEAR_PENDING_EXCEPTION;
        } else {
          return false;
        }
      }
      if( klass == nullptr) { sig_is_loaded = false; }
    }
  }
  return sig_is_loaded;
}

// Exposed so field engineers can debug VM
void Method::print_short_name(outputStream* st) const {
  ResourceMark rm;
#ifdef PRODUCT
  st->print(" %s::", method_holder()->external_name());
#else
  st->print(" %s::", method_holder()->internal_name());
#endif
  name()->print_symbol_on(st);
  if (WizardMode) signature()->print_symbol_on(st);
  else if (MethodHandles::is_signature_polymorphic(intrinsic_id()))
    MethodHandles::print_as_basic_type_signature_on(st, signature());
}

// Comparer for sorting an object array containing
// Method*s.
static int method_comparator(Method* a, Method* b) {
  return a->name()->fast_compare(b->name());
}

// This is only done during class loading, so it is OK to assume method_idnum matches the methods() array
// default_methods also uses this without the ordering for fast find_method
void Method::sort_methods(Array<Method*>* methods, bool set_idnums, method_comparator_func func) {
  int length = methods->length();
  if (length > 1) {
    if (func == nullptr) {
      func = method_comparator;
    }
    {
      NoSafepointVerifier nsv;
      QuickSort::sort(methods->data(), length, func);
    }
    // Reset method ordering
    if (set_idnums) {
      for (u2 i = 0; i < length; i++) {
        Method* m = methods->at(i);
        m->set_method_idnum(i);
        m->set_orig_method_idnum(i);
      }
    }
  }
}

//-----------------------------------------------------------------------------------
// Non-product code unless JVM/TI needs it

#if !defined(PRODUCT) || INCLUDE_JVMTI
class SignatureTypePrinter : public SignatureTypeNames {
 private:
  outputStream* _st;
  bool _use_separator;

  void type_name(const char* name) {
    if (_use_separator) _st->print(", ");
    _st->print("%s", name);
    _use_separator = true;
  }

 public:
  SignatureTypePrinter(Symbol* signature, outputStream* st) : SignatureTypeNames(signature) {
    _st = st;
    _use_separator = false;
  }

  void print_parameters()              { _use_separator = false; do_parameters_on(this); }
  void print_returntype()              { _use_separator = false; do_type(return_type()); }
};


void Method::print_name(outputStream* st) const {
  Thread *thread = Thread::current();
  ResourceMark rm(thread);
  st->print("%s ", is_static() ? "static" : "virtual");
  if (WizardMode) {
    st->print("%s.", method_holder()->internal_name());
    name()->print_symbol_on(st);
    signature()->print_symbol_on(st);
  } else {
    SignatureTypePrinter sig(signature(), st);
    sig.print_returntype();
    st->print(" %s.", method_holder()->internal_name());
    name()->print_symbol_on(st);
    st->print("(");
    sig.print_parameters();
    st->print(")");
  }
}
#endif // !PRODUCT || INCLUDE_JVMTI


void Method::print_codes_on(outputStream* st, int flags, bool buffered) const {
  print_codes_on(0, code_size(), st, flags, buffered);
}

void Method::print_codes_on(int from, int to, outputStream* st, int flags, bool buffered) const {
  Thread *thread = Thread::current();
  ResourceMark rm(thread);
  methodHandle mh (thread, (Method*)this);
  BytecodeTracer::print_method_codes(mh, from, to, st, flags, buffered);
}

CompressedLineNumberReadStream::CompressedLineNumberReadStream(u_char* buffer) : CompressedReadStream(buffer) {
  _bci = 0;
  _line = 0;
};

bool CompressedLineNumberReadStream::read_pair() {
  jubyte next = read_byte();
  // Check for terminator
  if (next == 0) return false;
  if (next == 0xFF) {
    // Escape character, regular compression used
    _bci  += read_signed_int();
    _line += read_signed_int();
  } else {
    // Single byte compression used
    _bci  += next >> 3;
    _line += next & 0x7;
  }
  return true;
}

#if INCLUDE_JVMTI

Bytecodes::Code Method::orig_bytecode_at(int bci) const {
  BreakpointInfo* bp = method_holder()->breakpoints();
  for (; bp != nullptr; bp = bp->next()) {
    if (bp->match(this, bci)) {
      return bp->orig_bytecode();
    }
  }
  {
    ResourceMark rm;
    fatal("no original bytecode found in %s at bci %d", name_and_sig_as_C_string(), bci);
  }
  return Bytecodes::_shouldnotreachhere;
}

void Method::set_orig_bytecode_at(int bci, Bytecodes::Code code) {
  assert(code != Bytecodes::_breakpoint, "cannot patch breakpoints this way");
  BreakpointInfo* bp = method_holder()->breakpoints();
  for (; bp != nullptr; bp = bp->next()) {
    if (bp->match(this, bci)) {
      bp->set_orig_bytecode(code);
      // and continue, in case there is more than one
    }
  }
}

void Method::set_breakpoint(int bci) {
  InstanceKlass* ik = method_holder();
  BreakpointInfo *bp = new BreakpointInfo(this, bci);
  bp->set_next(ik->breakpoints());
  ik->set_breakpoints(bp);
  // do this last:
  bp->set(this);
}

static void clear_matches(Method* m, int bci) {
  InstanceKlass* ik = m->method_holder();
  BreakpointInfo* prev_bp = nullptr;
  BreakpointInfo* next_bp;
  for (BreakpointInfo* bp = ik->breakpoints(); bp != nullptr; bp = next_bp) {
    next_bp = bp->next();
    // bci value of -1 is used to delete all breakpoints in method m (ex: clear_all_breakpoint).
    if (bci >= 0 ? bp->match(m, bci) : bp->match(m)) {
      // do this first:
      bp->clear(m);
      // unhook it
      if (prev_bp != nullptr)
        prev_bp->set_next(next_bp);
      else
        ik->set_breakpoints(next_bp);
      delete bp;
      // When class is redefined JVMTI sets breakpoint in all versions of EMCP methods
      // at same location. So we have multiple matching (method_index and bci)
      // BreakpointInfo nodes in BreakpointInfo list. We should just delete one
      // breakpoint for clear_breakpoint request and keep all other method versions
      // BreakpointInfo for future clear_breakpoint request.
      // bcivalue of -1 is used to clear all breakpoints (see clear_all_breakpoints)
      // which is being called when class is unloaded. We delete all the Breakpoint
      // information for all versions of method. We may not correctly restore the original
      // bytecode in all method versions, but that is ok. Because the class is being unloaded
      // so these methods won't be used anymore.
      if (bci >= 0) {
        break;
      }
    } else {
      // This one is a keeper.
      prev_bp = bp;
    }
  }
}

void Method::clear_breakpoint(int bci) {
  assert(bci >= 0, "");
  clear_matches(this, bci);
}

void Method::clear_all_breakpoints() {
  clear_matches(this, -1);
}

#endif // INCLUDE_JVMTI

int Method::highest_osr_comp_level() const {
  const MethodCounters* mcs = method_counters();
  if (mcs != nullptr) {
    return mcs->highest_osr_comp_level();
  } else {
    return CompLevel_none;
  }
}

void Method::set_highest_comp_level(int level) {
  MethodCounters* mcs = method_counters();
  if (mcs != nullptr) {
    mcs->set_highest_comp_level(level);
  }
}

void Method::set_highest_osr_comp_level(int level) {
  MethodCounters* mcs = method_counters();
  if (mcs != nullptr) {
    mcs->set_highest_osr_comp_level(level);
  }
}

#if INCLUDE_JVMTI

BreakpointInfo::BreakpointInfo(Method* m, int bci) {
  _bci = bci;
  _name_index = m->name_index();
  _signature_index = m->signature_index();
  _orig_bytecode = (Bytecodes::Code) *m->bcp_from(_bci);
  if (_orig_bytecode == Bytecodes::_breakpoint)
    _orig_bytecode = m->orig_bytecode_at(_bci);
  _next = nullptr;
}

void BreakpointInfo::set(Method* method) {
#ifdef ASSERT
  {
    Bytecodes::Code code = (Bytecodes::Code) *method->bcp_from(_bci);
    if (code == Bytecodes::_breakpoint)
      code = method->orig_bytecode_at(_bci);
    assert(orig_bytecode() == code, "original bytecode must be the same");
  }
#endif
  Thread *thread = Thread::current();
  *method->bcp_from(_bci) = Bytecodes::_breakpoint;
  method->incr_number_of_breakpoints(thread);
  {
    // Deoptimize all dependents on this method
    HandleMark hm(thread);
    methodHandle mh(thread, method);
    CodeCache::mark_dependents_on_method_for_breakpoint(mh);
  }
}

void BreakpointInfo::clear(Method* method) {
  *method->bcp_from(_bci) = orig_bytecode();
  assert(method->number_of_breakpoints() > 0, "must not go negative");
  method->decr_number_of_breakpoints(Thread::current());
}

#endif // INCLUDE_JVMTI

// jmethodID handling
// jmethodIDs are 64-bit integers that will never run out and are mapped in a table
// to their Method and vice versa.  If JNI code has access to stale jmethodID, this
// wastes no memory but the Method* returned is null.

// Add a method id to the jmethod_ids
jmethodID Method::make_jmethod_id(ClassLoaderData* cld, Method* m) {
  // Have to add jmethod_ids() to class loader data thread-safely.
  // Also have to add the method to the InstanceKlass list safely, which the lock
  // protects as well.
  assert(JmethodIdCreation_lock->owned_by_self(), "sanity check");
  jmethodID jmid = JmethodIDTable::make_jmethod_id(m);
  assert(jmid != nullptr, "must be created");

  // Add to growable array in CLD.
  cld->add_jmethod_id(jmid);
  return jmid;
}

// This looks in the InstanceKlass cache, then calls back to make_jmethod_id if not found.
jmethodID Method::jmethod_id() {
  return method_holder()->get_jmethod_id(this);
}

// Get the Method out of the table given the method id.
Method* Method::resolve_jmethod_id(jmethodID mid) {
  assert(mid != nullptr, "JNI method id should not be null");
  return JmethodIDTable::resolve_jmethod_id(mid);
}

void Method::change_method_associated_with_jmethod_id(jmethodID jmid, Method* new_method) {
  // Can't assert the method_holder is the same because the new method has the
  // scratch method holder.
  assert(resolve_jmethod_id(jmid)->method_holder()->class_loader()
           == new_method->method_holder()->class_loader() ||
         new_method->method_holder()->class_loader() == nullptr, // allow substitution to Unsafe method
         "changing to a different class loader");
  JmethodIDTable::change_method_associated_with_jmethod_id(jmid, new_method);
}

// If there's a jmethodID for this method, clear the Method
// but leave jmethodID for this method in the table.
// It's deallocated with class unloading.
void Method::clear_jmethod_id() {
  jmethodID mid = method_holder()->jmethod_id_or_null(this);
  if (mid != nullptr) {
    JmethodIDTable::clear_jmethod_id(mid, this);
  }
}

bool Method::validate_jmethod_id(jmethodID mid) {
  Method* m = resolve_jmethod_id(mid);
  assert(m != nullptr, "should be called with non-null method");
  InstanceKlass* ik = m->method_holder();
  ClassLoaderData* cld = ik->class_loader_data();
  if (cld->jmethod_ids() == nullptr) return false;
  return (cld->jmethod_ids()->contains(mid));
}

Method* Method::checked_resolve_jmethod_id(jmethodID mid) {
  if (mid == nullptr) return nullptr;
  Method* o = resolve_jmethod_id(mid);
  if (o == nullptr) {
    return nullptr;
  }
  // Method should otherwise be valid. Assert for testing.
  assert(is_valid_method(o), "should be valid jmethodid");
  // If the method's class holder object is unreferenced, but not yet marked as
  // unloaded, we need to return null here too because after a safepoint, its memory
  // will be reclaimed.
  return o->method_holder()->is_loader_alive() ? o : nullptr;
}

void Method::set_on_stack(const bool value) {
  // Set both the method itself and its constant pool.  The constant pool
  // on stack means some method referring to it is also on the stack.
  constants()->set_on_stack(value);

  bool already_set = on_stack_flag();
  set_on_stack_flag(value);
  if (value && !already_set) {
    MetadataOnStackMark::record(this);
  }
}

void Method::record_gc_epoch() {
  // If any method is on the stack in continuations, none of them can be reclaimed,
  // so save the marking cycle to check for the whole class in the cpCache.
  // The cpCache is writeable.
  constants()->cache()->record_gc_epoch();
}

bool Method::has_method_vptr(const void* ptr) {
  Method m;
  // This assumes that the vtbl pointer is the first word of a C++ object.
  return dereference_vptr(&m) == dereference_vptr(ptr);
}

// Check that this pointer is valid by checking that the vtbl pointer matches
bool Method::is_valid_method(const Method* m) {
  if (m == nullptr) {
    return false;
  } else if ((intptr_t(m) & (wordSize-1)) != 0) {
    // Quick sanity check on pointer.
    return false;
  } else if (!os::is_readable_range(m, m + 1)) {
    return false;
  } else if (m->in_aot_cache()) {
    return CppVtables::is_valid_shared_method(m);
  } else if (Metaspace::contains_non_shared(m)) {
    return has_method_vptr((const void*)m);
  } else {
    return false;
  }
}

// Printing

#ifndef PRODUCT

void Method::print_on(outputStream* st) const {
  ResourceMark rm;
  assert(is_method(), "must be method");
  st->print_cr("%s", internal_name());
  st->print_cr(" - this oop:          " PTR_FORMAT, p2i(this));
  st->print   (" - method holder:     "); method_holder()->print_value_on(st); st->cr();
  st->print   (" - constants:         " PTR_FORMAT " ", p2i(constants()));
  constants()->print_value_on(st); st->cr();
  st->print   (" - access:            0x%x  ", access_flags().as_method_flags()); access_flags().print_on(st); st->cr();
  st->print   (" - flags:             0x%x  ", _flags.as_int()); _flags.print_on(st); st->cr();
  st->print   (" - name:              ");    name()->print_value_on(st); st->cr();
  st->print   (" - signature:         ");    signature()->print_value_on(st); st->cr();
  st->print_cr(" - max stack:         %d",   max_stack());
  st->print_cr(" - max locals:        %d",   max_locals());
  st->print_cr(" - size of params:    %d",   size_of_parameters());
  st->print_cr(" - method size:       %d",   method_size());
  if (intrinsic_id() != vmIntrinsics::_none)
    st->print_cr(" - intrinsic id:      %d %s", vmIntrinsics::as_int(intrinsic_id()), vmIntrinsics::name_at(intrinsic_id()));
  if (highest_comp_level() != CompLevel_none)
    st->print_cr(" - highest level:     %d", highest_comp_level());
  st->print_cr(" - vtable index:      %d",   _vtable_index);
  st->print_cr(" - i2i entry:         " PTR_FORMAT, p2i(interpreter_entry()));
  st->print(   " - adapters:          ");
  AdapterHandlerEntry* a = ((Method*)this)->adapter();
  if (a == nullptr)
    st->print_cr(PTR_FORMAT, p2i(a));
  else
    a->print_adapter_on(st);
  st->print_cr(" - compiled entry     " PTR_FORMAT, p2i(from_compiled_entry()));
  st->print_cr(" - code size:         %d",   code_size());
  if (code_size() != 0) {
    st->print_cr(" - code start:        " PTR_FORMAT, p2i(code_base()));
    st->print_cr(" - code end (excl):   " PTR_FORMAT, p2i(code_base() + code_size()));
  }
  if (method_data() != nullptr) {
    st->print_cr(" - method data:       " PTR_FORMAT, p2i(method_data()));
  }
  st->print_cr(" - checked ex length: %d",   checked_exceptions_length());
  if (checked_exceptions_length() > 0) {
    CheckedExceptionElement* table = checked_exceptions_start();
    st->print_cr(" - checked ex start:  " PTR_FORMAT, p2i(table));
    if (Verbose) {
      for (int i = 0; i < checked_exceptions_length(); i++) {
        st->print_cr("   - throws %s", constants()->printable_name_at(table[i].class_cp_index));
      }
    }
  }
  if (has_linenumber_table()) {
    u_char* table = compressed_linenumber_table();
    st->print_cr(" - linenumber start:  " PTR_FORMAT, p2i(table));
    if (Verbose) {
      CompressedLineNumberReadStream stream(table);
      while (stream.read_pair()) {
        st->print_cr("   - line %d: %d", stream.line(), stream.bci());
      }
    }
  }
  st->print_cr(" - localvar length:   %d",   localvariable_table_length());
  if (localvariable_table_length() > 0) {
    LocalVariableTableElement* table = localvariable_table_start();
    st->print_cr(" - localvar start:    " PTR_FORMAT, p2i(table));
    if (Verbose) {
      for (int i = 0; i < localvariable_table_length(); i++) {
        int bci = table[i].start_bci;
        int len = table[i].length;
        const char* name = constants()->printable_name_at(table[i].name_cp_index);
        const char* desc = constants()->printable_name_at(table[i].descriptor_cp_index);
        int slot = table[i].slot;
        st->print_cr("   - %s %s bci=%d len=%d slot=%d", desc, name, bci, len, slot);
      }
    }
  }
  if (code() != nullptr) {
    st->print   (" - compiled code: ");
    code()->print_value_on(st);
  }
  if (is_native()) {
    st->print_cr(" - native function:   " PTR_FORMAT, p2i(native_function()));
    st->print_cr(" - signature handler: " PTR_FORMAT, p2i(signature_handler()));
  }
}

void Method::print_linkage_flags(outputStream* st) {
  access_flags().print_on(st);
  if (is_default_method()) {
    st->print("default ");
  }
  if (is_overpass()) {
    st->print("overpass ");
  }
}
#endif //PRODUCT

void Method::print_value_on(outputStream* st) const {
  assert(is_method(), "must be method");
  st->print("%s", internal_name());
  print_address_on(st);
  st->print(" ");
  name()->print_value_on(st);
  st->print(" ");
  signature()->print_value_on(st);
  st->print(" in ");
  method_holder()->print_value_on(st);
  if (WizardMode) st->print("#%d", _vtable_index);
  if (WizardMode) st->print("[%d,%d]", size_of_parameters(), max_locals());
  if (WizardMode && code() != nullptr) st->print(" ((nmethod*)%p)", code());
}

// Verification

void Method::verify_on(outputStream* st) {
  guarantee(is_method(), "object must be method");
  guarantee(constants()->is_constantPool(), "should be constant pool");
  MethodData* md = method_data();
  guarantee(md == nullptr ||
      md->is_methodData(), "should be method data");
}
