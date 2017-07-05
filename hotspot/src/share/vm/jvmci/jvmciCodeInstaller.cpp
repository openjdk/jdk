/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "code/compiledIC.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/disassembler.hpp"
#include "oops/oop.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "jvmci/jvmciEnv.hpp"
#include "jvmci/jvmciCompiler.hpp"
#include "jvmci/jvmciCodeInstaller.hpp"
#include "jvmci/jvmciJavaClasses.hpp"
#include "jvmci/jvmciCompilerToVM.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "asm/register.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/vmreg.hpp"

#ifdef TARGET_ARCH_x86
# include "vmreg_x86.inline.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "vmreg_sparc.inline.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "vmreg_zero.inline.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "vmreg_arm.inline.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "vmreg_ppc.inline.hpp"
#endif


// frequently used constants
// Allocate them with new so they are never destroyed (otherwise, a
// forced exit could destroy these objects while they are still in
// use).
ConstantOopWriteValue* CodeInstaller::_oop_null_scope_value = new (ResourceObj::C_HEAP, mtCompiler) ConstantOopWriteValue(NULL);
ConstantIntValue*      CodeInstaller::_int_m1_scope_value = new (ResourceObj::C_HEAP, mtCompiler) ConstantIntValue(-1);
ConstantIntValue*      CodeInstaller::_int_0_scope_value =  new (ResourceObj::C_HEAP, mtCompiler) ConstantIntValue(0);
ConstantIntValue*      CodeInstaller::_int_1_scope_value =  new (ResourceObj::C_HEAP, mtCompiler) ConstantIntValue(1);
ConstantIntValue*      CodeInstaller::_int_2_scope_value =  new (ResourceObj::C_HEAP, mtCompiler) ConstantIntValue(2);
LocationValue*         CodeInstaller::_illegal_value = new (ResourceObj::C_HEAP, mtCompiler) LocationValue(Location());

Method* getMethodFromHotSpotMethod(oop hotspot_method) {
  assert(hotspot_method != NULL && hotspot_method->is_a(HotSpotResolvedJavaMethodImpl::klass()), "sanity");
  return CompilerToVM::asMethod(hotspot_method);
}

VMReg getVMRegFromLocation(oop location, int total_frame_size) {
  oop reg = code_Location::reg(location);
  jint offset = code_Location::offset(location);

  if (reg != NULL) {
    // register
    jint number = code_Register::number(reg);
    VMReg vmReg = CodeInstaller::get_hotspot_reg(number);
    assert(offset % 4 == 0, "must be aligned");
    return vmReg->next(offset / 4);
  } else {
    // stack slot
    assert(offset % 4 == 0, "must be aligned");
    return VMRegImpl::stack2reg(offset / 4);
  }
}

// creates a HotSpot oop map out of the byte arrays provided by DebugInfo
OopMap* CodeInstaller::create_oop_map(oop debug_info) {
  oop reference_map = DebugInfo::referenceMap(debug_info);
  if (HotSpotReferenceMap::maxRegisterSize(reference_map) > 16) {
    _has_wide_vector = true;
  }
  OopMap* map = new OopMap(_total_frame_size, _parameter_count);
  objArrayOop objects = HotSpotReferenceMap::objects(reference_map);
  objArrayOop derivedBase = HotSpotReferenceMap::derivedBase(reference_map);
  typeArrayOop sizeInBytes = HotSpotReferenceMap::sizeInBytes(reference_map);
  for (int i = 0; i < objects->length(); i++) {
    oop location = objects->obj_at(i);
    oop baseLocation = derivedBase->obj_at(i);
    int bytes = sizeInBytes->int_at(i);

    VMReg vmReg = getVMRegFromLocation(location, _total_frame_size);
    if (baseLocation != NULL) {
      // derived oop
      assert(bytes == 8, "derived oop can't be compressed");
      VMReg baseReg = getVMRegFromLocation(baseLocation, _total_frame_size);
      map->set_derived_oop(vmReg, baseReg);
    } else if (bytes == 8) {
      // wide oop
      map->set_oop(vmReg);
    } else {
      // narrow oop
      assert(bytes == 4, "wrong size");
      map->set_narrowoop(vmReg);
    }
  }

  oop callee_save_info = (oop) DebugInfo::calleeSaveInfo(debug_info);
  if (callee_save_info != NULL) {
    objArrayOop registers = RegisterSaveLayout::registers(callee_save_info);
    typeArrayOop slots = RegisterSaveLayout::slots(callee_save_info);
    for (jint i = 0; i < slots->length(); i++) {
      oop jvmci_reg = registers->obj_at(i);
      jint jvmci_reg_number = code_Register::number(jvmci_reg);
      VMReg hotspot_reg = CodeInstaller::get_hotspot_reg(jvmci_reg_number);
      // HotSpot stack slots are 4 bytes
      jint jvmci_slot = slots->int_at(i);
      jint hotspot_slot = jvmci_slot * VMRegImpl::slots_per_word;
      VMReg hotspot_slot_as_reg = VMRegImpl::stack2reg(hotspot_slot);
      map->set_callee_saved(hotspot_slot_as_reg, hotspot_reg);
#ifdef _LP64
      // (copied from generate_oop_map() in c1_Runtime1_x86.cpp)
      VMReg hotspot_slot_hi_as_reg = VMRegImpl::stack2reg(hotspot_slot + 1);
      map->set_callee_saved(hotspot_slot_hi_as_reg, hotspot_reg->next());
#endif
    }
  }
  return map;
}

Metadata* CodeInstaller::record_metadata_reference(Handle& constant) {
  oop obj = HotSpotMetaspaceConstantImpl::metaspaceObject(constant);
  if (obj->is_a(HotSpotResolvedObjectTypeImpl::klass())) {
    Klass* klass = java_lang_Class::as_Klass(HotSpotResolvedObjectTypeImpl::javaClass(obj));
    assert(!HotSpotMetaspaceConstantImpl::compressed(constant), "unexpected compressed klass pointer %s @ " INTPTR_FORMAT, klass->name()->as_C_string(), p2i(klass));
    int index = _oop_recorder->find_index(klass);
    TRACE_jvmci_3("metadata[%d of %d] = %s", index, _oop_recorder->metadata_count(), klass->name()->as_C_string());
    return klass;
  } else if (obj->is_a(HotSpotResolvedJavaMethodImpl::klass())) {
    Method* method = (Method*) (address) HotSpotResolvedJavaMethodImpl::metaspaceMethod(obj);
    assert(!HotSpotMetaspaceConstantImpl::compressed(constant), "unexpected compressed method pointer %s @ " INTPTR_FORMAT, method->name()->as_C_string(), p2i(method));
    int index = _oop_recorder->find_index(method);
    TRACE_jvmci_3("metadata[%d of %d] = %s", index, _oop_recorder->metadata_count(), method->name()->as_C_string());
    return method;
  } else {
    fatal("unexpected metadata reference for constant of type %s", obj->klass()->name()->as_C_string());
    return NULL;
  }
}

#ifdef _LP64
narrowKlass CodeInstaller::record_narrow_metadata_reference(Handle& constant) {
  oop obj = HotSpotMetaspaceConstantImpl::metaspaceObject(constant);
  assert(HotSpotMetaspaceConstantImpl::compressed(constant), "unexpected uncompressed pointer");
  assert(obj->is_a(HotSpotResolvedObjectTypeImpl::klass()), "unexpected compressed pointer of type %s", obj->klass()->name()->as_C_string());

  Klass* klass = java_lang_Class::as_Klass(HotSpotResolvedObjectTypeImpl::javaClass(obj));
  int index = _oop_recorder->find_index(klass);
  TRACE_jvmci_3("narrowKlass[%d of %d] = %s", index, _oop_recorder->metadata_count(), klass->name()->as_C_string());
  return Klass::encode_klass(klass);
}
#endif

Location::Type CodeInstaller::get_oop_type(oop value) {
  oop lirKind = Value::lirKind(value);
  oop platformKind = LIRKind::platformKind(lirKind);
  assert(LIRKind::referenceMask(lirKind) == 1, "unexpected referenceMask");

  if (platformKind == word_kind()) {
    return Location::oop;
  } else {
    return Location::narrowoop;
  }
}

ScopeValue* CodeInstaller::get_scope_value(oop value, BasicType type, GrowableArray<ScopeValue*>* objects, ScopeValue* &second) {
  second = NULL;
  if (value == Value::ILLEGAL()) {
    assert(type == T_ILLEGAL, "expected legal value");
    return _illegal_value;
  } else if (value->is_a(RegisterValue::klass())) {
    oop reg = RegisterValue::reg(value);
    jint number = code_Register::number(reg);
    VMReg hotspotRegister = get_hotspot_reg(number);
    if (is_general_purpose_reg(hotspotRegister)) {
      Location::Type locationType;
      if (type == T_OBJECT) {
        locationType = get_oop_type(value);
      } else if (type == T_LONG) {
        locationType = Location::lng;
      } else {
        assert(type == T_INT || type == T_FLOAT || type == T_SHORT || type == T_CHAR || type == T_BYTE || type == T_BOOLEAN, "unexpected type in cpu register");
        locationType = Location::int_in_long;
      }
      ScopeValue* value = new LocationValue(Location::new_reg_loc(locationType, hotspotRegister));
      if (type == T_LONG) {
        second = value;
      }
      return value;
    } else {
      assert(type == T_FLOAT || type == T_DOUBLE, "only float and double expected in xmm register");
      Location::Type locationType;
      if (type == T_FLOAT) {
        // this seems weird, but the same value is used in c1_LinearScan
        locationType = Location::normal;
      } else {
        locationType = Location::dbl;
      }
      ScopeValue* value = new LocationValue(Location::new_reg_loc(locationType, hotspotRegister));
      if (type == T_DOUBLE) {
        second = value;
      }
      return value;
    }
  } else if (value->is_a(StackSlot::klass())) {
    jint offset = StackSlot::offset(value);
    if (StackSlot::addFrameSize(value)) {
      offset += _total_frame_size;
    }

    Location::Type locationType;
    if (type == T_OBJECT) {
      locationType = get_oop_type(value);
    } else if (type == T_LONG) {
      locationType = Location::lng;
    } else if (type == T_DOUBLE) {
      locationType = Location::dbl;
    } else {
      assert(type == T_INT || type == T_FLOAT || type == T_SHORT || type == T_CHAR || type == T_BYTE || type == T_BOOLEAN, "unexpected type in stack slot");
      locationType = Location::normal;
    }
    ScopeValue* value = new LocationValue(Location::new_stk_loc(locationType, offset));
    if (type == T_DOUBLE || type == T_LONG) {
      second = value;
    }
    return value;
  } else if (value->is_a(JavaConstant::klass())) {
    if (value->is_a(PrimitiveConstant::klass())) {
      if (value->is_a(RawConstant::klass())) {
        jlong prim = PrimitiveConstant::primitive(value);
        return new ConstantLongValue(prim);
      } else {
        assert(type == JVMCIRuntime::kindToBasicType(JavaKind::typeChar(PrimitiveConstant::kind(value))), "primitive constant type doesn't match");
        if (type == T_INT || type == T_FLOAT) {
          jint prim = (jint)PrimitiveConstant::primitive(value);
          switch (prim) {
            case -1: return _int_m1_scope_value;
            case  0: return _int_0_scope_value;
            case  1: return _int_1_scope_value;
            case  2: return _int_2_scope_value;
            default: return new ConstantIntValue(prim);
          }
        } else {
          assert(type == T_LONG || type == T_DOUBLE, "unexpected primitive constant type");
          jlong prim = PrimitiveConstant::primitive(value);
          second = _int_1_scope_value;
          return new ConstantLongValue(prim);
        }
      }
    } else {
      assert(type == T_OBJECT, "unexpected object constant");
      if (value->is_a(NullConstant::klass()) || value->is_a(HotSpotCompressedNullConstant::klass())) {
        return _oop_null_scope_value;
      } else {
        assert(value->is_a(HotSpotObjectConstantImpl::klass()), "unexpected constant type");
        oop obj = HotSpotObjectConstantImpl::object(value);
        assert(obj != NULL, "null value must be in NullConstant");
        return new ConstantOopWriteValue(JNIHandles::make_local(obj));
      }
    }
  } else if (value->is_a(VirtualObject::klass())) {
    assert(type == T_OBJECT, "unexpected virtual object");
    int id = VirtualObject::id(value);
    ScopeValue* object = objects->at(id);
    assert(object != NULL, "missing value");
    return object;
  } else {
    value->klass()->print();
    value->print();
  }
  ShouldNotReachHere();
  return NULL;
}

void CodeInstaller::record_object_value(ObjectValue* sv, oop value, GrowableArray<ScopeValue*>* objects) {
  oop type = VirtualObject::type(value);
  int id = VirtualObject::id(value);
  oop javaMirror = HotSpotResolvedObjectTypeImpl::javaClass(type);
  Klass* klass = java_lang_Class::as_Klass(javaMirror);
  bool isLongArray = klass == Universe::longArrayKlassObj();

  objArrayOop values = VirtualObject::values(value);
  objArrayOop slotKinds = VirtualObject::slotKinds(value);
  for (jint i = 0; i < values->length(); i++) {
    ScopeValue* cur_second = NULL;
    oop object = values->obj_at(i);
    oop kind = slotKinds->obj_at(i);
    BasicType type = JVMCIRuntime::kindToBasicType(JavaKind::typeChar(kind));
    ScopeValue* value = get_scope_value(object, type, objects, cur_second);

    if (isLongArray && cur_second == NULL) {
      // we're trying to put ints into a long array... this isn't really valid, but it's used for some optimizations.
      // add an int 0 constant
      cur_second = _int_0_scope_value;
    }

    if (cur_second != NULL) {
      sv->field_values()->append(cur_second);
    }
    assert(value != NULL, "missing value");
    sv->field_values()->append(value);
  }
}

MonitorValue* CodeInstaller::get_monitor_value(oop value, GrowableArray<ScopeValue*>* objects) {
  guarantee(value->is_a(StackLockValue::klass()), "Monitors must be of type StackLockValue");

  ScopeValue* second = NULL;
  ScopeValue* owner_value = get_scope_value(StackLockValue::owner(value), T_OBJECT, objects, second);
  assert(second == NULL, "monitor cannot occupy two stack slots");

  ScopeValue* lock_data_value = get_scope_value(StackLockValue::slot(value), T_LONG, objects, second);
  assert(second == lock_data_value, "monitor is LONG value that occupies two stack slots");
  assert(lock_data_value->is_location(), "invalid monitor location");
  Location lock_data_loc = ((LocationValue*)lock_data_value)->location();

  bool eliminated = false;
  if (StackLockValue::eliminated(value)) {
    eliminated = true;
  }

  return new MonitorValue(owner_value, lock_data_loc, eliminated);
}

void CodeInstaller::initialize_dependencies(oop compiled_code, OopRecorder* recorder) {
  JavaThread* thread = JavaThread::current();
  CompilerThread* compilerThread = thread->is_Compiler_thread() ? thread->as_CompilerThread() : NULL;
  _oop_recorder = recorder;
  _dependencies = new Dependencies(&_arena, _oop_recorder, compilerThread != NULL ? compilerThread->log() : NULL);
  objArrayHandle assumptions = HotSpotCompiledCode::assumptions(compiled_code);
  if (!assumptions.is_null()) {
    int length = assumptions->length();
    for (int i = 0; i < length; ++i) {
      Handle assumption = assumptions->obj_at(i);
      if (!assumption.is_null()) {
        if (assumption->klass() == Assumptions_NoFinalizableSubclass::klass()) {
          assumption_NoFinalizableSubclass(assumption);
        } else if (assumption->klass() == Assumptions_ConcreteSubtype::klass()) {
          assumption_ConcreteSubtype(assumption);
        } else if (assumption->klass() == Assumptions_LeafType::klass()) {
          assumption_LeafType(assumption);
        } else if (assumption->klass() == Assumptions_ConcreteMethod::klass()) {
          assumption_ConcreteMethod(assumption);
        } else if (assumption->klass() == Assumptions_CallSiteTargetValue::klass()) {
          assumption_CallSiteTargetValue(assumption);
        } else {
          assumption->print();
          fatal("unexpected Assumption subclass");
        }
      }
    }
  }
  if (JvmtiExport::can_hotswap_or_post_breakpoint()) {
    objArrayHandle methods = HotSpotCompiledCode::methods(compiled_code);
    if (!methods.is_null()) {
      int length = methods->length();
      for (int i = 0; i < length; ++i) {
        Handle method_handle = methods->obj_at(i);
        methodHandle method = getMethodFromHotSpotMethod(method_handle());
        _dependencies->assert_evol_method(method());
      }
    }
  }
}

RelocBuffer::~RelocBuffer() {
  if (_buffer != NULL) {
    FREE_C_HEAP_ARRAY(char, _buffer);
  }
}

address RelocBuffer::begin() const {
  if (_buffer != NULL) {
    return (address) _buffer;
  }
  return (address) _static_buffer;
}

void RelocBuffer::set_size(size_t bytes) {
  assert(bytes <= _size, "can't grow in size!");
  _size = bytes;
}

void RelocBuffer::ensure_size(size_t bytes) {
  assert(_buffer == NULL, "can only be used once");
  assert(_size == 0, "can only be used once");
  if (bytes >= RelocBuffer::stack_size) {
    _buffer = NEW_C_HEAP_ARRAY(char, bytes, mtInternal);
  }
  _size = bytes;
}

JVMCIEnv::CodeInstallResult CodeInstaller::gather_metadata(Handle target, Handle& compiled_code, CodeMetadata& metadata) {
  CodeBuffer buffer("JVMCI Compiler CodeBuffer for Metadata");
  jobject compiled_code_obj = JNIHandles::make_local(compiled_code());
  initialize_dependencies(JNIHandles::resolve(compiled_code_obj), NULL);

  // Get instructions and constants CodeSections early because we need it.
  _instructions = buffer.insts();
  _constants = buffer.consts();

  initialize_fields(target(), JNIHandles::resolve(compiled_code_obj));
  if (!initialize_buffer(buffer)) {
    return JVMCIEnv::code_too_large;
  }
  process_exception_handlers();

  _debug_recorder->pcs_size(); // ehm, create the sentinel record

  assert(_debug_recorder->pcs_length() >= 2, "must be at least 2");

  metadata.set_pc_desc(_debug_recorder->pcs(), _debug_recorder->pcs_length());
  metadata.set_scopes(_debug_recorder->stream()->buffer(), _debug_recorder->data_size());
  metadata.set_exception_table(&_exception_handler_table);

  RelocBuffer* reloc_buffer = metadata.get_reloc_buffer();

  reloc_buffer->ensure_size(buffer.total_relocation_size());
  size_t size = (size_t) buffer.copy_relocations_to(reloc_buffer->begin(), (CodeBuffer::csize_t) reloc_buffer->size(), true);
  reloc_buffer->set_size(size);
  return JVMCIEnv::ok;
}

// constructor used to create a method
JVMCIEnv::CodeInstallResult CodeInstaller::install(JVMCICompiler* compiler, Handle target, Handle& compiled_code, CodeBlob*& cb, Handle installed_code, Handle speculation_log) {
  CodeBuffer buffer("JVMCI Compiler CodeBuffer");
  jobject compiled_code_obj = JNIHandles::make_local(compiled_code());
  OopRecorder* recorder = new OopRecorder(&_arena, true);
  initialize_dependencies(JNIHandles::resolve(compiled_code_obj), recorder);

  // Get instructions and constants CodeSections early because we need it.
  _instructions = buffer.insts();
  _constants = buffer.consts();

  initialize_fields(target(), JNIHandles::resolve(compiled_code_obj));
  JVMCIEnv::CodeInstallResult result = initialize_buffer(buffer);
  if (result != JVMCIEnv::ok) {
    return result;
  }
  process_exception_handlers();

  int stack_slots = _total_frame_size / HeapWordSize; // conversion to words

  if (!compiled_code->is_a(HotSpotCompiledNmethod::klass())) {
    oop stubName = HotSpotCompiledCode::name(compiled_code_obj);
    char* name = strdup(java_lang_String::as_utf8_string(stubName));
    cb = RuntimeStub::new_runtime_stub(name,
                                       &buffer,
                                       CodeOffsets::frame_never_safe,
                                       stack_slots,
                                       _debug_recorder->_oopmaps,
                                       false);
    result = JVMCIEnv::ok;
  } else {
    nmethod* nm = NULL;
    methodHandle method = getMethodFromHotSpotMethod(HotSpotCompiledNmethod::method(compiled_code));
    jint entry_bci = HotSpotCompiledNmethod::entryBCI(compiled_code);
    jint id = HotSpotCompiledNmethod::id(compiled_code);
    bool has_unsafe_access = HotSpotCompiledNmethod::hasUnsafeAccess(compiled_code) == JNI_TRUE;
    JVMCIEnv* env = (JVMCIEnv*) (address) HotSpotCompiledNmethod::jvmciEnv(compiled_code);
    if (id == -1) {
      // Make sure a valid compile_id is associated with every compile
      id = CompileBroker::assign_compile_id_unlocked(Thread::current(), method, entry_bci);
    }
    result = JVMCIEnv::register_method(method, nm, entry_bci, &_offsets, _custom_stack_area_offset, &buffer,
                                       stack_slots, _debug_recorder->_oopmaps, &_exception_handler_table,
                                       compiler, _debug_recorder, _dependencies, env, id,
                                       has_unsafe_access, _has_wide_vector, installed_code, compiled_code, speculation_log);
    cb = nm;
  }

  if (cb != NULL) {
    // Make sure the pre-calculated constants section size was correct.
    guarantee((cb->code_begin() - cb->content_begin()) >= _constants_size, "%d < %d", (int)(cb->code_begin() - cb->content_begin()), _constants_size);
  }
  return result;
}

void CodeInstaller::initialize_fields(oop target, oop compiled_code) {
  if (compiled_code->is_a(HotSpotCompiledNmethod::klass())) {
    Handle hotspotJavaMethod = HotSpotCompiledNmethod::method(compiled_code);
    methodHandle method = getMethodFromHotSpotMethod(hotspotJavaMethod());
    _parameter_count = method->size_of_parameters();
    TRACE_jvmci_2("installing code for %s", method->name_and_sig_as_C_string());
  } else {
    // Must be a HotSpotCompiledRuntimeStub.
    // Only used in OopMap constructor for non-product builds
    _parameter_count = 0;
  }
  _sites_handle = JNIHandles::make_local(HotSpotCompiledCode::sites(compiled_code));
  _exception_handlers_handle = JNIHandles::make_local(HotSpotCompiledCode::exceptionHandlers(compiled_code));

  _code_handle = JNIHandles::make_local(HotSpotCompiledCode::targetCode(compiled_code));
  _code_size = HotSpotCompiledCode::targetCodeSize(compiled_code);
  _total_frame_size = HotSpotCompiledCode::totalFrameSize(compiled_code);
  _custom_stack_area_offset = HotSpotCompiledCode::customStackAreaOffset(compiled_code);

  // Pre-calculate the constants section size.  This is required for PC-relative addressing.
  _data_section_handle = JNIHandles::make_local(HotSpotCompiledCode::dataSection(compiled_code));
  guarantee(HotSpotCompiledCode::dataSectionAlignment(compiled_code) <= _constants->alignment(), "Alignment inside constants section is restricted by alignment of section begin");
  _constants_size = data_section()->length();

  _data_section_patches_handle = JNIHandles::make_local(HotSpotCompiledCode::dataSectionPatches(compiled_code));

#ifndef PRODUCT
  _comments_handle = JNIHandles::make_local(HotSpotCompiledCode::comments(compiled_code));
#endif

  _next_call_type = INVOKE_INVALID;

  _has_wide_vector = false;

  oop arch = TargetDescription::arch(target);
  _word_kind_handle = JNIHandles::make_local(Architecture::wordKind(arch));
}

int CodeInstaller::estimate_stubs_size() {
  // Estimate the number of static call stubs that might be emitted.
  int static_call_stubs = 0;
  objArrayOop sites = this->sites();
  for (int i = 0; i < sites->length(); i++) {
    oop site = sites->obj_at(i);
    if (site->is_a(CompilationResult_Mark::klass())) {
      oop id_obj = CompilationResult_Mark::id(site);
      if (id_obj != NULL) {
        assert(java_lang_boxing_object::is_instance(id_obj, T_INT), "Integer id expected");
        jint id = id_obj->int_field(java_lang_boxing_object::value_offset_in_bytes(T_INT));
        if (id == INVOKESTATIC || id == INVOKESPECIAL) {
          static_call_stubs++;
        }
      }
    }
  }
  return static_call_stubs * CompiledStaticCall::to_interp_stub_size();
}

// perform data and call relocation on the CodeBuffer
JVMCIEnv::CodeInstallResult CodeInstaller::initialize_buffer(CodeBuffer& buffer) {
  HandleMark hm;
  objArrayHandle sites = this->sites();
  int locs_buffer_size = sites->length() * (relocInfo::length_limit + sizeof(relocInfo));

  // Allocate enough space in the stub section for the static call
  // stubs.  Stubs have extra relocs but they are managed by the stub
  // section itself so they don't need to be accounted for in the
  // locs_buffer above.
  int stubs_size = estimate_stubs_size();
  int total_size = round_to(_code_size, buffer.insts()->alignment()) + round_to(_constants_size, buffer.consts()->alignment()) + round_to(stubs_size, buffer.stubs()->alignment());

  if (total_size > JVMCINMethodSizeLimit) {
    return JVMCIEnv::code_too_large;
  }

  buffer.initialize(total_size, locs_buffer_size);
  if (buffer.blob() == NULL) {
    return JVMCIEnv::cache_full;
  }
  buffer.initialize_stubs_size(stubs_size);
  buffer.initialize_consts_size(_constants_size);

  _debug_recorder = new DebugInformationRecorder(_oop_recorder);
  _debug_recorder->set_oopmaps(new OopMapSet());

  buffer.initialize_oop_recorder(_oop_recorder);

  // copy the constant data into the newly created CodeBuffer
  address end_data = _constants->start() + _constants_size;
  memcpy(_constants->start(), data_section()->base(T_BYTE), _constants_size);
  _constants->set_end(end_data);

  // copy the code into the newly created CodeBuffer
  address end_pc = _instructions->start() + _code_size;
  guarantee(_instructions->allocates2(end_pc), "initialize should have reserved enough space for all the code");
  memcpy(_instructions->start(), code()->base(T_BYTE), _code_size);
  _instructions->set_end(end_pc);

  for (int i = 0; i < data_section_patches()->length(); i++) {
    Handle patch = data_section_patches()->obj_at(i);
    Handle reference = CompilationResult_DataPatch::reference(patch);
    assert(reference->is_a(CompilationResult_ConstantReference::klass()), "patch in data section must be a ConstantReference");
    Handle constant = CompilationResult_ConstantReference::constant(reference);
    address dest = _constants->start() + CompilationResult_Site::pcOffset(patch);
    if (constant->is_a(HotSpotMetaspaceConstantImpl::klass())) {
      if (HotSpotMetaspaceConstantImpl::compressed(constant)) {
#ifdef _LP64
        *((narrowKlass*) dest) = record_narrow_metadata_reference(constant);
#else
        fatal("unexpected compressed Klass* in 32-bit mode");
#endif
      } else {
        *((Metadata**) dest) = record_metadata_reference(constant);
      }
    } else if (constant->is_a(HotSpotObjectConstantImpl::klass())) {
      Handle obj = HotSpotObjectConstantImpl::object(constant);
      jobject value = JNIHandles::make_local(obj());
      int oop_index = _oop_recorder->find_index(value);

      if (HotSpotObjectConstantImpl::compressed(constant)) {
#ifdef _LP64
        _constants->relocate(dest, oop_Relocation::spec(oop_index), relocInfo::narrow_oop_in_const);
#else
        fatal("unexpected compressed oop in 32-bit mode");
#endif
      } else {
        _constants->relocate(dest, oop_Relocation::spec(oop_index));
      }
    } else {
      ShouldNotReachHere();
    }
  }
  jint last_pc_offset = -1;
  for (int i = 0; i < sites->length(); i++) {
    {
        No_Safepoint_Verifier no_safepoint;
        oop site = sites->obj_at(i);
        jint pc_offset = CompilationResult_Site::pcOffset(site);

        if (site->is_a(CompilationResult_Call::klass())) {
          TRACE_jvmci_4("call at %i", pc_offset);
          site_Call(buffer, pc_offset, site);
        } else if (site->is_a(CompilationResult_Infopoint::klass())) {
          // three reasons for infopoints denote actual safepoints
          oop reason = CompilationResult_Infopoint::reason(site);
          if (InfopointReason::SAFEPOINT() == reason || InfopointReason::CALL() == reason || InfopointReason::IMPLICIT_EXCEPTION() == reason) {
            TRACE_jvmci_4("safepoint at %i", pc_offset);
            site_Safepoint(buffer, pc_offset, site);
          } else {
            // if the infopoint is not an actual safepoint, it must have one of the other reasons
            // (safeguard against new safepoint types that require handling above)
            assert(InfopointReason::METHOD_START() == reason || InfopointReason::METHOD_END() == reason || InfopointReason::LINE_NUMBER() == reason, "");
            site_Infopoint(buffer, pc_offset, site);
          }
        } else if (site->is_a(CompilationResult_DataPatch::klass())) {
          TRACE_jvmci_4("datapatch at %i", pc_offset);
          site_DataPatch(buffer, pc_offset, site);
        } else if (site->is_a(CompilationResult_Mark::klass())) {
          TRACE_jvmci_4("mark at %i", pc_offset);
          site_Mark(buffer, pc_offset, site);
        } else {
          fatal("unexpected Site subclass");
        }
        last_pc_offset = pc_offset;
    }
    if (CodeInstallSafepointChecks && SafepointSynchronize::do_call_back()) {
      // this is a hacky way to force a safepoint check but nothing else was jumping out at me.
      ThreadToNativeFromVM ttnfv(JavaThread::current());
    }
  }

#ifndef PRODUCT
  if (comments() != NULL) {
    No_Safepoint_Verifier no_safepoint;
    for (int i = 0; i < comments()->length(); i++) {
      oop comment = comments()->obj_at(i);
      assert(comment->is_a(HotSpotCompiledCode_Comment::klass()), "cce");
      jint offset = HotSpotCompiledCode_Comment::pcOffset(comment);
      char* text = java_lang_String::as_utf8_string(HotSpotCompiledCode_Comment::text(comment));
      buffer.block_comment(offset, text);
    }
  }
#endif
  return JVMCIEnv::ok;
}

void CodeInstaller::assumption_NoFinalizableSubclass(Handle assumption) {
  Handle receiverType_handle = Assumptions_NoFinalizableSubclass::receiverType(assumption());
  Klass* receiverType = java_lang_Class::as_Klass(HotSpotResolvedObjectTypeImpl::javaClass(receiverType_handle));
  _dependencies->assert_has_no_finalizable_subclasses(receiverType);
}

void CodeInstaller::assumption_ConcreteSubtype(Handle assumption) {
  Handle context_handle = Assumptions_ConcreteSubtype::context(assumption());
  Handle subtype_handle = Assumptions_ConcreteSubtype::subtype(assumption());
  Klass* context = java_lang_Class::as_Klass(HotSpotResolvedObjectTypeImpl::javaClass(context_handle));
  Klass* subtype = java_lang_Class::as_Klass(HotSpotResolvedObjectTypeImpl::javaClass(subtype_handle));

  assert(context->is_abstract(), "");
  _dependencies->assert_abstract_with_unique_concrete_subtype(context, subtype);
}

void CodeInstaller::assumption_LeafType(Handle assumption) {
  Handle context_handle = Assumptions_LeafType::context(assumption());
  Klass* context = java_lang_Class::as_Klass(HotSpotResolvedObjectTypeImpl::javaClass(context_handle));

  _dependencies->assert_leaf_type(context);
}

void CodeInstaller::assumption_ConcreteMethod(Handle assumption) {
  Handle impl_handle = Assumptions_ConcreteMethod::impl(assumption());
  Handle context_handle = Assumptions_ConcreteMethod::context(assumption());

  methodHandle impl = getMethodFromHotSpotMethod(impl_handle());
  Klass* context = java_lang_Class::as_Klass(HotSpotResolvedObjectTypeImpl::javaClass(context_handle));

  _dependencies->assert_unique_concrete_method(context, impl());
}

void CodeInstaller::assumption_CallSiteTargetValue(Handle assumption) {
  Handle callSite = Assumptions_CallSiteTargetValue::callSite(assumption());
  Handle methodHandle = Assumptions_CallSiteTargetValue::methodHandle(assumption());

  _dependencies->assert_call_site_target_value(callSite(), methodHandle());
}

void CodeInstaller::process_exception_handlers() {
  if (exception_handlers() != NULL) {
    objArrayOop handlers = exception_handlers();
    for (int i = 0; i < handlers->length(); i++) {
      oop exc = handlers->obj_at(i);
      jint pc_offset = CompilationResult_Site::pcOffset(exc);
      jint handler_offset = CompilationResult_ExceptionHandler::handlerPos(exc);

      // Subtable header
      _exception_handler_table.add_entry(HandlerTableEntry(1, pc_offset, 0));

      // Subtable entry
      _exception_handler_table.add_entry(HandlerTableEntry(-1, handler_offset, 0));
    }
  }
}

// If deoptimization happens, the interpreter should reexecute these bytecodes.
// This function mainly helps the compilers to set up the reexecute bit.
static bool bytecode_should_reexecute(Bytecodes::Code code) {
  switch (code) {
    case Bytecodes::_invokedynamic:
    case Bytecodes::_invokevirtual:
    case Bytecodes::_invokeinterface:
    case Bytecodes::_invokespecial:
    case Bytecodes::_invokestatic:
      return false;
    default:
      return true;
    }
  return true;
}

GrowableArray<ScopeValue*>* CodeInstaller::record_virtual_objects(oop debug_info) {
  objArrayOop virtualObjects = DebugInfo::virtualObjectMapping(debug_info);
  if (virtualObjects == NULL) {
    return NULL;
  }
  GrowableArray<ScopeValue*>* objects = new GrowableArray<ScopeValue*>(virtualObjects->length(), virtualObjects->length(), NULL);
  // Create the unique ObjectValues
  for (int i = 0; i < virtualObjects->length(); i++) {
    oop value = virtualObjects->obj_at(i);
    int id = VirtualObject::id(value);
    oop type = VirtualObject::type(value);
    oop javaMirror = HotSpotResolvedObjectTypeImpl::javaClass(type);
    ObjectValue* sv = new ObjectValue(id, new ConstantOopWriteValue(JNIHandles::make_local(Thread::current(), javaMirror)));
    assert(objects->at(id) == NULL, "once");
    objects->at_put(id, sv);
  }
  // All the values which could be referenced by the VirtualObjects
  // exist, so now describe all the VirtualObjects themselves.
  for (int i = 0; i < virtualObjects->length(); i++) {
    oop value = virtualObjects->obj_at(i);
    int id = VirtualObject::id(value);
    record_object_value(objects->at(id)->as_ObjectValue(), value, objects);
  }
  _debug_recorder->dump_object_pool(objects);
  return objects;
}

void CodeInstaller::record_scope(jint pc_offset, oop debug_info) {
  oop position = DebugInfo::bytecodePosition(debug_info);
  if (position == NULL) {
    // Stubs do not record scope info, just oop maps
    return;
  }

  GrowableArray<ScopeValue*>* objectMapping = record_virtual_objects(debug_info);
  record_scope(pc_offset, position, objectMapping);
}

void CodeInstaller::record_scope(jint pc_offset, oop position, GrowableArray<ScopeValue*>* objects) {
  oop frame = NULL;
  if (position->is_a(BytecodeFrame::klass())) {
    frame = position;
  }
  oop caller_frame = BytecodePosition::caller(position);
  if (caller_frame != NULL) {
    record_scope(pc_offset, caller_frame, objects);
  }

  oop hotspot_method = BytecodePosition::method(position);
  Method* method = getMethodFromHotSpotMethod(hotspot_method);
  jint bci = BytecodePosition::bci(position);
  if (bci == BytecodeFrame::BEFORE_BCI()) {
    bci = SynchronizationEntryBCI;
  }

  TRACE_jvmci_2("Recording scope pc_offset=%d bci=%d method=%s", pc_offset, bci, method->name_and_sig_as_C_string());

  bool reexecute = false;
  if (frame != NULL) {
    if (bci == SynchronizationEntryBCI){
       reexecute = false;
    } else {
      Bytecodes::Code code = Bytecodes::java_code_at(method, method->bcp_from(bci));
      reexecute = bytecode_should_reexecute(code);
      if (frame != NULL) {
        reexecute = (BytecodeFrame::duringCall(frame) == JNI_FALSE);
      }
    }
  }

  DebugToken* locals_token = NULL;
  DebugToken* expressions_token = NULL;
  DebugToken* monitors_token = NULL;
  bool throw_exception = false;

  if (frame != NULL) {
    jint local_count = BytecodeFrame::numLocals(frame);
    jint expression_count = BytecodeFrame::numStack(frame);
    jint monitor_count = BytecodeFrame::numLocks(frame);
    objArrayOop values = BytecodeFrame::values(frame);
    objArrayOop slotKinds = BytecodeFrame::slotKinds(frame);

    assert(local_count + expression_count + monitor_count == values->length(), "unexpected values length");
    assert(local_count + expression_count == slotKinds->length(), "unexpected slotKinds length");

    GrowableArray<ScopeValue*>* locals = local_count > 0 ? new GrowableArray<ScopeValue*> (local_count) : NULL;
    GrowableArray<ScopeValue*>* expressions = expression_count > 0 ? new GrowableArray<ScopeValue*> (expression_count) : NULL;
    GrowableArray<MonitorValue*>* monitors = monitor_count > 0 ? new GrowableArray<MonitorValue*> (monitor_count) : NULL;

    TRACE_jvmci_2("Scope at bci %d with %d values", bci, values->length());
    TRACE_jvmci_2("%d locals %d expressions, %d monitors", local_count, expression_count, monitor_count);

    for (jint i = 0; i < values->length(); i++) {
      ScopeValue* second = NULL;
      oop value = values->obj_at(i);
      if (i < local_count) {
        oop kind = slotKinds->obj_at(i);
        BasicType type = JVMCIRuntime::kindToBasicType(JavaKind::typeChar(kind));
        ScopeValue* first = get_scope_value(value, type, objects, second);
        if (second != NULL) {
          locals->append(second);
        }
        locals->append(first);
      } else if (i < local_count + expression_count) {
        oop kind = slotKinds->obj_at(i);
        BasicType type = JVMCIRuntime::kindToBasicType(JavaKind::typeChar(kind));
        ScopeValue* first = get_scope_value(value, type, objects, second);
        if (second != NULL) {
          expressions->append(second);
        }
        expressions->append(first);
      } else {
        monitors->append(get_monitor_value(value, objects));
      }
      if (second != NULL) {
        i++;
        assert(i < values->length(), "double-slot value not followed by Value.ILLEGAL");
        assert(values->obj_at(i) == Value::ILLEGAL(), "double-slot value not followed by Value.ILLEGAL");
      }
    }

    locals_token = _debug_recorder->create_scope_values(locals);
    expressions_token = _debug_recorder->create_scope_values(expressions);
    monitors_token = _debug_recorder->create_monitor_values(monitors);

    throw_exception = BytecodeFrame::rethrowException(frame) == JNI_TRUE;
  }

  _debug_recorder->describe_scope(pc_offset, method, NULL, bci, reexecute, throw_exception, false, false,
                                  locals_token, expressions_token, monitors_token);
}

void CodeInstaller::site_Safepoint(CodeBuffer& buffer, jint pc_offset, oop site) {
  oop debug_info = CompilationResult_Infopoint::debugInfo(site);
  assert(debug_info != NULL, "debug info expected");

  // address instruction = _instructions->start() + pc_offset;
  // jint next_pc_offset = Assembler::locate_next_instruction(instruction) - _instructions->start();
  _debug_recorder->add_safepoint(pc_offset, create_oop_map(debug_info));
  record_scope(pc_offset, debug_info);
  _debug_recorder->end_safepoint(pc_offset);
}

void CodeInstaller::site_Infopoint(CodeBuffer& buffer, jint pc_offset, oop site) {
  oop debug_info = CompilationResult_Infopoint::debugInfo(site);
  assert(debug_info != NULL, "debug info expected");

  _debug_recorder->add_non_safepoint(pc_offset);
  record_scope(pc_offset, debug_info);
  _debug_recorder->end_non_safepoint(pc_offset);
}

void CodeInstaller::site_Call(CodeBuffer& buffer, jint pc_offset, oop site) {
  oop target = CompilationResult_Call::target(site);
  InstanceKlass* target_klass = InstanceKlass::cast(target->klass());

  oop hotspot_method = NULL; // JavaMethod
  oop foreign_call = NULL;

  if (target_klass->is_subclass_of(SystemDictionary::HotSpotForeignCallTarget_klass())) {
    foreign_call = target;
  } else {
    hotspot_method = target;
  }

  oop debug_info = CompilationResult_Call::debugInfo(site);

  assert(!!hotspot_method ^ !!foreign_call, "Call site needs exactly one type");

  NativeInstruction* inst = nativeInstruction_at(_instructions->start() + pc_offset);
  jint next_pc_offset = CodeInstaller::pd_next_offset(inst, pc_offset, hotspot_method);

  if (debug_info != NULL) {
    _debug_recorder->add_safepoint(next_pc_offset, create_oop_map(debug_info));
    record_scope(next_pc_offset, debug_info);
  }

  if (foreign_call != NULL) {
    jlong foreign_call_destination = HotSpotForeignCallTarget::address(foreign_call);
    CodeInstaller::pd_relocate_ForeignCall(inst, foreign_call_destination);
  } else { // method != NULL
    assert(hotspot_method != NULL, "unexpected JavaMethod");
    assert(debug_info != NULL, "debug info expected");

    TRACE_jvmci_3("method call");
    CodeInstaller::pd_relocate_JavaMethod(hotspot_method, pc_offset);
    if (_next_call_type == INVOKESTATIC || _next_call_type == INVOKESPECIAL) {
      // Need a static call stub for transitions from compiled to interpreted.
      CompiledStaticCall::emit_to_interp_stub(buffer, _instructions->start() + pc_offset);
    }
  }

  _next_call_type = INVOKE_INVALID;

  if (debug_info != NULL) {
    _debug_recorder->end_safepoint(next_pc_offset);
  }
}

void CodeInstaller::site_DataPatch(CodeBuffer& buffer, jint pc_offset, oop site) {
  oop reference = CompilationResult_DataPatch::reference(site);
  if (reference->is_a(CompilationResult_ConstantReference::klass())) {
    Handle constant = CompilationResult_ConstantReference::constant(reference);
    if (constant->is_a(HotSpotObjectConstantImpl::klass())) {
      pd_patch_OopConstant(pc_offset, constant);
    } else if (constant->is_a(HotSpotMetaspaceConstantImpl::klass())) {
      pd_patch_MetaspaceConstant(pc_offset, constant);
    } else if (constant->is_a(HotSpotSentinelConstant::klass())) {
      fatal("sentinel constant unsupported");
    } else {
      fatal("unknown constant type in data patch");
    }
  } else if (reference->is_a(CompilationResult_DataSectionReference::klass())) {
    int data_offset = CompilationResult_DataSectionReference::offset(reference);
    assert(0 <= data_offset && data_offset < _constants_size, "data offset 0x%X points outside data section (size 0x%X)", data_offset, _constants_size);
    pd_patch_DataSectionReference(pc_offset, data_offset);
  } else {
    fatal("unknown data patch type");
  }
}

void CodeInstaller::site_Mark(CodeBuffer& buffer, jint pc_offset, oop site) {
  oop id_obj = CompilationResult_Mark::id(site);

  if (id_obj != NULL) {
    assert(java_lang_boxing_object::is_instance(id_obj, T_INT), "Integer id expected");
    jint id = id_obj->int_field(java_lang_boxing_object::value_offset_in_bytes(T_INT));

    address pc = _instructions->start() + pc_offset;

    switch (id) {
      case UNVERIFIED_ENTRY:
        _offsets.set_value(CodeOffsets::Entry, pc_offset);
        break;
      case VERIFIED_ENTRY:
        _offsets.set_value(CodeOffsets::Verified_Entry, pc_offset);
        break;
      case OSR_ENTRY:
        _offsets.set_value(CodeOffsets::OSR_Entry, pc_offset);
        break;
      case EXCEPTION_HANDLER_ENTRY:
        _offsets.set_value(CodeOffsets::Exceptions, pc_offset);
        break;
      case DEOPT_HANDLER_ENTRY:
        _offsets.set_value(CodeOffsets::Deopt, pc_offset);
        break;
      case INVOKEVIRTUAL:
      case INVOKEINTERFACE:
      case INLINE_INVOKE:
      case INVOKESTATIC:
      case INVOKESPECIAL:
        _next_call_type = (MarkId) id;
        _invoke_mark_pc = pc;
        break;
      case POLL_NEAR:
      case POLL_FAR:
      case POLL_RETURN_NEAR:
      case POLL_RETURN_FAR:
        pd_relocate_poll(pc, id);
        break;
      case CARD_TABLE_SHIFT:
      case CARD_TABLE_ADDRESS:
      case HEAP_TOP_ADDRESS:
      case HEAP_END_ADDRESS:
      case NARROW_KLASS_BASE_ADDRESS:
      case CRC_TABLE_ADDRESS:
        break;
      default:
        ShouldNotReachHere();
        break;
    }
  }
}

