/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/register.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/compiledIC.hpp"
#include "code/vmreg.inline.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/disassembler.hpp"
#include "jvmci/jvmciEnv.hpp"
#include "jvmci/jvmciCompiler.hpp"
#include "jvmci/jvmciCodeInstaller.hpp"
#include "jvmci/jvmciJavaClasses.hpp"
#include "jvmci/jvmciCompilerToVM.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "oops/oop.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "runtime/javaCalls.hpp"

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

VMReg getVMRegFromLocation(Handle location, int total_frame_size, TRAPS) {
  if (location.is_null()) {
    THROW_NULL(vmSymbols::java_lang_NullPointerException());
  }

  Handle reg = code_Location::reg(location);
  jint offset = code_Location::offset(location);

  if (reg.not_null()) {
    // register
    jint number = code_Register::number(reg);
    VMReg vmReg = CodeInstaller::get_hotspot_reg(number, CHECK_NULL);
    if (offset % 4 == 0) {
      return vmReg->next(offset / 4);
    } else {
      JVMCI_ERROR_NULL("unaligned subregister offset %d in oop map", offset);
    }
  } else {
    // stack slot
    if (offset % 4 == 0) {
      VMReg vmReg = VMRegImpl::stack2reg(offset / 4);
      if (!OopMapValue::legal_vm_reg_name(vmReg)) {
        // This restriction only applies to VMRegs that are used in OopMap but
        // since that's the only use of VMRegs it's simplest to put this test
        // here.  This test should also be equivalent legal_vm_reg_name but JVMCI
        // clients can use max_oop_map_stack_stack_offset to detect this problem
        // directly.  The asserts just ensure that the tests are in agreement.
        assert(offset > CompilerToVM::Data::max_oop_map_stack_offset(), "illegal VMReg");
        JVMCI_ERROR_NULL("stack offset %d is too large to be encoded in OopMap (max %d)",
                         offset, CompilerToVM::Data::max_oop_map_stack_offset());
      }
      assert(OopMapValue::legal_vm_reg_name(vmReg), "illegal VMReg");
      return vmReg;
    } else {
      JVMCI_ERROR_NULL("unaligned stack offset %d in oop map", offset);
    }
  }
}

// creates a HotSpot oop map out of the byte arrays provided by DebugInfo
OopMap* CodeInstaller::create_oop_map(Handle debug_info, TRAPS) {
  Handle reference_map = DebugInfo::referenceMap(debug_info);
  if (reference_map.is_null()) {
    THROW_NULL(vmSymbols::java_lang_NullPointerException());
  }
  if (!reference_map->is_a(HotSpotReferenceMap::klass())) {
    JVMCI_ERROR_NULL("unknown reference map: %s", reference_map->klass()->signature_name());
  }
  if (HotSpotReferenceMap::maxRegisterSize(reference_map) > 16) {
    _has_wide_vector = true;
  }
  OopMap* map = new OopMap(_total_frame_size, _parameter_count);
  objArrayHandle objects = HotSpotReferenceMap::objects(reference_map);
  objArrayHandle derivedBase = HotSpotReferenceMap::derivedBase(reference_map);
  typeArrayHandle sizeInBytes = HotSpotReferenceMap::sizeInBytes(reference_map);
  if (objects.is_null() || derivedBase.is_null() || sizeInBytes.is_null()) {
    THROW_NULL(vmSymbols::java_lang_NullPointerException());
  }
  if (objects->length() != derivedBase->length() || objects->length() != sizeInBytes->length()) {
    JVMCI_ERROR_NULL("arrays in reference map have different sizes: %d %d %d", objects->length(), derivedBase->length(), sizeInBytes->length());
  }
  for (int i = 0; i < objects->length(); i++) {
    Handle location = objects->obj_at(i);
    Handle baseLocation = derivedBase->obj_at(i);
    int bytes = sizeInBytes->int_at(i);

    VMReg vmReg = getVMRegFromLocation(location, _total_frame_size, CHECK_NULL);
    if (baseLocation.not_null()) {
      // derived oop
#ifdef _LP64
      if (bytes == 8) {
#else
      if (bytes == 4) {
#endif
        VMReg baseReg = getVMRegFromLocation(baseLocation, _total_frame_size, CHECK_NULL);
        map->set_derived_oop(vmReg, baseReg);
      } else {
        JVMCI_ERROR_NULL("invalid derived oop size in ReferenceMap: %d", bytes);
      }
#ifdef _LP64
    } else if (bytes == 8) {
      // wide oop
      map->set_oop(vmReg);
    } else if (bytes == 4) {
      // narrow oop
      map->set_narrowoop(vmReg);
#else
    } else if (bytes == 4) {
      map->set_oop(vmReg);
#endif
    } else {
      JVMCI_ERROR_NULL("invalid oop size in ReferenceMap: %d", bytes);
    }
  }

  Handle callee_save_info = (oop) DebugInfo::calleeSaveInfo(debug_info);
  if (callee_save_info.not_null()) {
    objArrayHandle registers = RegisterSaveLayout::registers(callee_save_info);
    typeArrayHandle slots = RegisterSaveLayout::slots(callee_save_info);
    for (jint i = 0; i < slots->length(); i++) {
      Handle jvmci_reg = registers->obj_at(i);
      jint jvmci_reg_number = code_Register::number(jvmci_reg);
      VMReg hotspot_reg = CodeInstaller::get_hotspot_reg(jvmci_reg_number, CHECK_NULL);
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

AOTOopRecorder::AOTOopRecorder(Arena* arena, bool deduplicate) : OopRecorder(arena, deduplicate) {
  _meta_strings = new GrowableArray<const char*>();
}

int AOTOopRecorder::nr_meta_strings() const {
  return _meta_strings->length();
}

const char* AOTOopRecorder::meta_element(int pos) const {
  return _meta_strings->at(pos);
}

int AOTOopRecorder::find_index(Metadata* h) {
  int index =  this->OopRecorder::find_index(h);

  Klass* klass = NULL;
  if (h->is_klass()) {
    klass = (Klass*) h;
    record_meta_string(klass->signature_name(), index);
  } else if (h->is_method()) {
    Method* method = (Method*) h;
    // Need klass->signature_name() in method name
    klass = method->method_holder();
    const char* klass_name = klass->signature_name();
    int klass_name_len  = (int)strlen(klass_name);
    Symbol* method_name = method->name();
    Symbol* signature   = method->signature();
    int method_name_len = method_name->utf8_length();
    int method_sign_len = signature->utf8_length();
    int len             = klass_name_len + 1 + method_name_len + method_sign_len;
    char* dest          = NEW_RESOURCE_ARRAY(char, len + 1);
    strcpy(dest, klass_name);
    dest[klass_name_len] = '.';
    strcpy(&dest[klass_name_len + 1], method_name->as_C_string());
    strcpy(&dest[klass_name_len + 1 + method_name_len], signature->as_C_string());
    dest[len] = 0;
    record_meta_string(dest, index);
  }

  return index;
}

int AOTOopRecorder::find_index(jobject h) {
  if (h == NULL) {
    return 0;
  }
  oop javaMirror = JNIHandles::resolve(h);
  Klass* klass = java_lang_Class::as_Klass(javaMirror);
  return find_index(klass);
}

void AOTOopRecorder::record_meta_string(const char* name, int index) {
  assert(index > 0, "must be 1..n");
  index -= 1; // reduce by one to convert to array index

  if (index < _meta_strings->length()) {
    assert(strcmp(name, _meta_strings->at(index)) == 0, "must match");
  } else {
    assert(index == _meta_strings->length(), "must be last");
    _meta_strings->append(name);
  }
}

void* CodeInstaller::record_metadata_reference(CodeSection* section, address dest, Handle constant, TRAPS) {
  /*
   * This method needs to return a raw (untyped) pointer, since the value of a pointer to the base
   * class is in general not equal to the pointer of the subclass. When patching metaspace pointers,
   * the compiler expects a direct pointer to the subclass (Klass* or Method*), not a pointer to the
   * base class (Metadata* or MetaspaceObj*).
   */
  oop obj = HotSpotMetaspaceConstantImpl::metaspaceObject(constant);
  if (obj->is_a(HotSpotResolvedObjectTypeImpl::klass())) {
    Klass* klass = java_lang_Class::as_Klass(HotSpotResolvedObjectTypeImpl::javaClass(obj));
    assert(!HotSpotMetaspaceConstantImpl::compressed(constant), "unexpected compressed klass pointer %s @ " INTPTR_FORMAT, klass->name()->as_C_string(), p2i(klass));
    int index = _oop_recorder->find_index(klass);
    section->relocate(dest, metadata_Relocation::spec(index));
    TRACE_jvmci_3("metadata[%d of %d] = %s", index, _oop_recorder->metadata_count(), klass->name()->as_C_string());
    return klass;
  } else if (obj->is_a(HotSpotResolvedJavaMethodImpl::klass())) {
    Method* method = (Method*) (address) HotSpotResolvedJavaMethodImpl::metaspaceMethod(obj);
    assert(!HotSpotMetaspaceConstantImpl::compressed(constant), "unexpected compressed method pointer %s @ " INTPTR_FORMAT, method->name()->as_C_string(), p2i(method));
    int index = _oop_recorder->find_index(method);
    section->relocate(dest, metadata_Relocation::spec(index));
    TRACE_jvmci_3("metadata[%d of %d] = %s", index, _oop_recorder->metadata_count(), method->name()->as_C_string());
    return method;
  } else {
    JVMCI_ERROR_NULL("unexpected metadata reference for constant of type %s", obj->klass()->signature_name());
  }
}

#ifdef _LP64
narrowKlass CodeInstaller::record_narrow_metadata_reference(CodeSection* section, address dest, Handle constant, TRAPS) {
  oop obj = HotSpotMetaspaceConstantImpl::metaspaceObject(constant);
  assert(HotSpotMetaspaceConstantImpl::compressed(constant), "unexpected uncompressed pointer");

  if (!obj->is_a(HotSpotResolvedObjectTypeImpl::klass())) {
    JVMCI_ERROR_0("unexpected compressed pointer of type %s", obj->klass()->signature_name());
  }

  Klass* klass = java_lang_Class::as_Klass(HotSpotResolvedObjectTypeImpl::javaClass(obj));
  int index = _oop_recorder->find_index(klass);
  section->relocate(dest, metadata_Relocation::spec(index));
  TRACE_jvmci_3("narrowKlass[%d of %d] = %s", index, _oop_recorder->metadata_count(), klass->name()->as_C_string());
  return Klass::encode_klass(klass);
}
#endif

Location::Type CodeInstaller::get_oop_type(Handle value) {
  Handle valueKind = Value::valueKind(value);
  Handle platformKind = ValueKind::platformKind(valueKind);

  if (platformKind == word_kind()) {
    return Location::oop;
  } else {
    return Location::narrowoop;
  }
}

ScopeValue* CodeInstaller::get_scope_value(Handle value, BasicType type, GrowableArray<ScopeValue*>* objects, ScopeValue* &second, TRAPS) {
  second = NULL;
  if (value.is_null()) {
    THROW_NULL(vmSymbols::java_lang_NullPointerException());
  } else if (value == Value::ILLEGAL()) {
    if (type != T_ILLEGAL) {
      JVMCI_ERROR_NULL("unexpected illegal value, expected %s", basictype_to_str(type));
    }
    return _illegal_value;
  } else if (value->is_a(RegisterValue::klass())) {
    Handle reg = RegisterValue::reg(value);
    jint number = code_Register::number(reg);
    VMReg hotspotRegister = get_hotspot_reg(number, CHECK_NULL);
    if (is_general_purpose_reg(hotspotRegister)) {
      Location::Type locationType;
      if (type == T_OBJECT) {
        locationType = get_oop_type(value);
      } else if (type == T_LONG) {
        locationType = Location::lng;
      } else if (type == T_INT || type == T_FLOAT || type == T_SHORT || type == T_CHAR || type == T_BYTE || type == T_BOOLEAN) {
        locationType = Location::int_in_long;
      } else {
        JVMCI_ERROR_NULL("unexpected type %s in cpu register", basictype_to_str(type));
      }
      ScopeValue* value = new LocationValue(Location::new_reg_loc(locationType, hotspotRegister));
      if (type == T_LONG) {
        second = value;
      }
      return value;
    } else {
      Location::Type locationType;
      if (type == T_FLOAT) {
        // this seems weird, but the same value is used in c1_LinearScan
        locationType = Location::normal;
      } else if (type == T_DOUBLE) {
        locationType = Location::dbl;
      } else {
        JVMCI_ERROR_NULL("unexpected type %s in floating point register", basictype_to_str(type));
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
    } else if (type == T_INT || type == T_FLOAT || type == T_SHORT || type == T_CHAR || type == T_BYTE || type == T_BOOLEAN) {
      locationType = Location::normal;
    } else {
      JVMCI_ERROR_NULL("unexpected type %s in stack slot", basictype_to_str(type));
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
        BasicType constantType = JVMCIRuntime::kindToBasicType(PrimitiveConstant::kind(value), CHECK_NULL);
        if (type != constantType) {
          JVMCI_ERROR_NULL("primitive constant type doesn't match, expected %s but got %s", basictype_to_str(type), basictype_to_str(constantType));
        }
        if (type == T_INT || type == T_FLOAT) {
          jint prim = (jint)PrimitiveConstant::primitive(value);
          switch (prim) {
            case -1: return _int_m1_scope_value;
            case  0: return _int_0_scope_value;
            case  1: return _int_1_scope_value;
            case  2: return _int_2_scope_value;
            default: return new ConstantIntValue(prim);
          }
        } else if (type == T_LONG || type == T_DOUBLE) {
          jlong prim = PrimitiveConstant::primitive(value);
          second = _int_1_scope_value;
          return new ConstantLongValue(prim);
        } else {
          JVMCI_ERROR_NULL("unexpected primitive constant type %s", basictype_to_str(type));
        }
      }
    } else if (value->is_a(NullConstant::klass()) || value->is_a(HotSpotCompressedNullConstant::klass())) {
      if (type == T_OBJECT) {
        return _oop_null_scope_value;
      } else {
        JVMCI_ERROR_NULL("unexpected null constant, expected %s", basictype_to_str(type));
      }
    } else if (value->is_a(HotSpotObjectConstantImpl::klass())) {
      if (type == T_OBJECT) {
        oop obj = HotSpotObjectConstantImpl::object(value);
        if (obj == NULL) {
          JVMCI_ERROR_NULL("null value must be in NullConstant");
        }
        return new ConstantOopWriteValue(JNIHandles::make_local(obj));
      } else {
        JVMCI_ERROR_NULL("unexpected object constant, expected %s", basictype_to_str(type));
      }
    }
  } else if (value->is_a(VirtualObject::klass())) {
    if (type == T_OBJECT) {
      int id = VirtualObject::id(value);
      if (0 <= id && id < objects->length()) {
        ScopeValue* object = objects->at(id);
        if (object != NULL) {
          return object;
        }
      }
      JVMCI_ERROR_NULL("unknown virtual object id %d", id);
    } else {
      JVMCI_ERROR_NULL("unexpected virtual object, expected %s", basictype_to_str(type));
    }
  }

  JVMCI_ERROR_NULL("unexpected value in scope: %s", value->klass()->signature_name())
}

void CodeInstaller::record_object_value(ObjectValue* sv, Handle value, GrowableArray<ScopeValue*>* objects, TRAPS) {
  Handle type = VirtualObject::type(value);
  int id = VirtualObject::id(value);
  oop javaMirror = HotSpotResolvedObjectTypeImpl::javaClass(type);
  Klass* klass = java_lang_Class::as_Klass(javaMirror);
  bool isLongArray = klass == Universe::longArrayKlassObj();

  objArrayHandle values = VirtualObject::values(value);
  objArrayHandle slotKinds = VirtualObject::slotKinds(value);
  for (jint i = 0; i < values->length(); i++) {
    ScopeValue* cur_second = NULL;
    Handle object = values->obj_at(i);
    BasicType type = JVMCIRuntime::kindToBasicType(slotKinds->obj_at(i), CHECK);
    ScopeValue* value = get_scope_value(object, type, objects, cur_second, CHECK);

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

MonitorValue* CodeInstaller::get_monitor_value(Handle value, GrowableArray<ScopeValue*>* objects, TRAPS) {
  if (value.is_null()) {
    THROW_NULL(vmSymbols::java_lang_NullPointerException());
  }
  if (!value->is_a(StackLockValue::klass())) {
    JVMCI_ERROR_NULL("Monitors must be of type StackLockValue, got %s", value->klass()->signature_name());
  }

  ScopeValue* second = NULL;
  ScopeValue* owner_value = get_scope_value(StackLockValue::owner(value), T_OBJECT, objects, second, CHECK_NULL);
  assert(second == NULL, "monitor cannot occupy two stack slots");

  ScopeValue* lock_data_value = get_scope_value(StackLockValue::slot(value), T_LONG, objects, second, CHECK_NULL);
  assert(second == lock_data_value, "monitor is LONG value that occupies two stack slots");
  assert(lock_data_value->is_location(), "invalid monitor location");
  Location lock_data_loc = ((LocationValue*)lock_data_value)->location();

  bool eliminated = false;
  if (StackLockValue::eliminated(value)) {
    eliminated = true;
  }

  return new MonitorValue(owner_value, lock_data_loc, eliminated);
}

void CodeInstaller::initialize_dependencies(oop compiled_code, OopRecorder* recorder, TRAPS) {
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
          JVMCI_ERROR("unexpected Assumption subclass %s", assumption->klass()->signature_name());
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

JVMCIEnv::CodeInstallResult CodeInstaller::gather_metadata(Handle target, Handle compiled_code, CodeMetadata& metadata, TRAPS) {
  CodeBuffer buffer("JVMCI Compiler CodeBuffer for Metadata");
  jobject compiled_code_obj = JNIHandles::make_local(compiled_code());
  AOTOopRecorder* recorder = new AOTOopRecorder(&_arena, true);
  initialize_dependencies(JNIHandles::resolve(compiled_code_obj), recorder, CHECK_OK);

  metadata.set_oop_recorder(recorder);

  // Get instructions and constants CodeSections early because we need it.
  _instructions = buffer.insts();
  _constants = buffer.consts();

  initialize_fields(target(), JNIHandles::resolve(compiled_code_obj), CHECK_OK);
  JVMCIEnv::CodeInstallResult result = initialize_buffer(buffer, false, CHECK_OK);
  if (result != JVMCIEnv::ok) {
    return result;
  }

  _debug_recorder->pcs_size(); // create the sentinel record

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
JVMCIEnv::CodeInstallResult CodeInstaller::install(JVMCICompiler* compiler, Handle target, Handle compiled_code, CodeBlob*& cb, Handle installed_code, Handle speculation_log, TRAPS) {
  CodeBuffer buffer("JVMCI Compiler CodeBuffer");
  jobject compiled_code_obj = JNIHandles::make_local(compiled_code());
  OopRecorder* recorder = new OopRecorder(&_arena, true);
  initialize_dependencies(JNIHandles::resolve(compiled_code_obj), recorder, CHECK_OK);

  // Get instructions and constants CodeSections early because we need it.
  _instructions = buffer.insts();
  _constants = buffer.consts();

  initialize_fields(target(), JNIHandles::resolve(compiled_code_obj), CHECK_OK);
  JVMCIEnv::CodeInstallResult result = initialize_buffer(buffer, true, CHECK_OK);
  if (result != JVMCIEnv::ok) {
    return result;
  }

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
    result = JVMCIEnv::register_method(method, nm, entry_bci, &_offsets, _orig_pc_offset, &buffer,
                                       stack_slots, _debug_recorder->_oopmaps, &_exception_handler_table,
                                       compiler, _debug_recorder, _dependencies, env, id,
                                       has_unsafe_access, _has_wide_vector, installed_code, compiled_code, speculation_log);
    cb = nm->as_codeblob_or_null();
    if (nm != NULL && env == NULL) {
      DirectiveSet* directive = DirectivesStack::getMatchingDirective(method, compiler);
      bool printnmethods = directive->PrintAssemblyOption || directive->PrintNMethodsOption;
      if (printnmethods || PrintDebugInfo || PrintRelocations || PrintDependencies || PrintExceptionHandlers) {
        nm->print_nmethod(printnmethods);
      }
      DirectivesStack::release(directive);
    }
  }

  if (cb != NULL) {
    // Make sure the pre-calculated constants section size was correct.
    guarantee((cb->code_begin() - cb->content_begin()) >= _constants_size, "%d < %d", (int)(cb->code_begin() - cb->content_begin()), _constants_size);
  }
  return result;
}

void CodeInstaller::initialize_fields(oop target, oop compiled_code, TRAPS) {
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

  _code_handle = JNIHandles::make_local(HotSpotCompiledCode::targetCode(compiled_code));
  _code_size = HotSpotCompiledCode::targetCodeSize(compiled_code);
  _total_frame_size = HotSpotCompiledCode::totalFrameSize(compiled_code);

  oop deoptRescueSlot = HotSpotCompiledCode::deoptRescueSlot(compiled_code);
  if (deoptRescueSlot == NULL) {
    _orig_pc_offset = -1;
  } else {
    _orig_pc_offset = StackSlot::offset(deoptRescueSlot);
    if (StackSlot::addFrameSize(deoptRescueSlot)) {
      _orig_pc_offset += _total_frame_size;
    }
    if (_orig_pc_offset < 0) {
      JVMCI_ERROR("invalid deopt rescue slot: %d", _orig_pc_offset);
    }
  }

  // Pre-calculate the constants section size.  This is required for PC-relative addressing.
  _data_section_handle = JNIHandles::make_local(HotSpotCompiledCode::dataSection(compiled_code));
  if ((_constants->alignment() % HotSpotCompiledCode::dataSectionAlignment(compiled_code)) != 0) {
    JVMCI_ERROR("invalid data section alignment: %d", HotSpotCompiledCode::dataSectionAlignment(compiled_code));
  }
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

int CodeInstaller::estimate_stubs_size(TRAPS) {
  // Estimate the number of static and aot call stubs that might be emitted.
  int static_call_stubs = 0;
  int aot_call_stubs = 0;
  objArrayOop sites = this->sites();
  for (int i = 0; i < sites->length(); i++) {
    oop site = sites->obj_at(i);
    if (site != NULL) {
      if (site->is_a(site_Mark::klass())) {
        oop id_obj = site_Mark::id(site);
        if (id_obj != NULL) {
          if (!java_lang_boxing_object::is_instance(id_obj, T_INT)) {
            JVMCI_ERROR_0("expected Integer id, got %s", id_obj->klass()->signature_name());
          }
          jint id = id_obj->int_field(java_lang_boxing_object::value_offset_in_bytes(T_INT));
          if (id == INVOKESTATIC || id == INVOKESPECIAL) {
            static_call_stubs++;
          }
        }
      }
      if (UseAOT && site->is_a(site_Call::klass())) {
        oop target = site_Call::target(site);
        InstanceKlass* target_klass = InstanceKlass::cast(target->klass());
        if (!target_klass->is_subclass_of(SystemDictionary::HotSpotForeignCallTarget_klass())) {
          // Add far aot trampolines.
          aot_call_stubs++;
        }
      }
    }
  }
  int size = static_call_stubs * CompiledStaticCall::to_interp_stub_size();
#if INCLUDE_AOT
  size += aot_call_stubs * CompiledStaticCall::to_aot_stub_size();
#endif
  return size;
}

// perform data and call relocation on the CodeBuffer
JVMCIEnv::CodeInstallResult CodeInstaller::initialize_buffer(CodeBuffer& buffer, bool check_size, TRAPS) {
  HandleMark hm;
  objArrayHandle sites = this->sites();
  int locs_buffer_size = sites->length() * (relocInfo::length_limit + sizeof(relocInfo));

  // Allocate enough space in the stub section for the static call
  // stubs.  Stubs have extra relocs but they are managed by the stub
  // section itself so they don't need to be accounted for in the
  // locs_buffer above.
  int stubs_size = estimate_stubs_size(CHECK_OK);
  int total_size = round_to(_code_size, buffer.insts()->alignment()) + round_to(_constants_size, buffer.consts()->alignment()) + round_to(stubs_size, buffer.stubs()->alignment());

  if (check_size && total_size > JVMCINMethodSizeLimit) {
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
    if (patch.is_null()) {
      THROW_(vmSymbols::java_lang_NullPointerException(), JVMCIEnv::ok);
    }
    Handle reference = site_DataPatch::reference(patch);
    if (reference.is_null()) {
      THROW_(vmSymbols::java_lang_NullPointerException(), JVMCIEnv::ok);
    }
    if (!reference->is_a(site_ConstantReference::klass())) {
      JVMCI_ERROR_OK("invalid patch in data section: %s", reference->klass()->signature_name());
    }
    Handle constant = site_ConstantReference::constant(reference);
    if (constant.is_null()) {
      THROW_(vmSymbols::java_lang_NullPointerException(), JVMCIEnv::ok);
    }
    address dest = _constants->start() + site_Site::pcOffset(patch);
    if (constant->is_a(HotSpotMetaspaceConstantImpl::klass())) {
      if (HotSpotMetaspaceConstantImpl::compressed(constant)) {
#ifdef _LP64
        *((narrowKlass*) dest) = record_narrow_metadata_reference(_constants, dest, constant, CHECK_OK);
#else
        JVMCI_ERROR_OK("unexpected compressed Klass* in 32-bit mode");
#endif
      } else {
        *((void**) dest) = record_metadata_reference(_constants, dest, constant, CHECK_OK);
      }
    } else if (constant->is_a(HotSpotObjectConstantImpl::klass())) {
      Handle obj = HotSpotObjectConstantImpl::object(constant);
      jobject value = JNIHandles::make_local(obj());
      int oop_index = _oop_recorder->find_index(value);

      if (HotSpotObjectConstantImpl::compressed(constant)) {
#ifdef _LP64
        _constants->relocate(dest, oop_Relocation::spec(oop_index), relocInfo::narrow_oop_in_const);
#else
        JVMCI_ERROR_OK("unexpected compressed oop in 32-bit mode");
#endif
      } else {
        _constants->relocate(dest, oop_Relocation::spec(oop_index));
      }
    } else {
      JVMCI_ERROR_OK("invalid constant in data section: %s", constant->klass()->signature_name());
    }
  }
  jint last_pc_offset = -1;
  for (int i = 0; i < sites->length(); i++) {
    Handle site = sites->obj_at(i);
    if (site.is_null()) {
      THROW_(vmSymbols::java_lang_NullPointerException(), JVMCIEnv::ok);
    }

    jint pc_offset = site_Site::pcOffset(site);

    if (site->is_a(site_Call::klass())) {
      TRACE_jvmci_4("call at %i", pc_offset);
      site_Call(buffer, pc_offset, site, CHECK_OK);
    } else if (site->is_a(site_Infopoint::klass())) {
      // three reasons for infopoints denote actual safepoints
      oop reason = site_Infopoint::reason(site);
      if (site_InfopointReason::SAFEPOINT() == reason || site_InfopointReason::CALL() == reason || site_InfopointReason::IMPLICIT_EXCEPTION() == reason) {
        TRACE_jvmci_4("safepoint at %i", pc_offset);
        site_Safepoint(buffer, pc_offset, site, CHECK_OK);
        if (_orig_pc_offset < 0) {
          JVMCI_ERROR_OK("method contains safepoint, but has no deopt rescue slot");
        }
      } else {
        TRACE_jvmci_4("infopoint at %i", pc_offset);
        site_Infopoint(buffer, pc_offset, site, CHECK_OK);
      }
    } else if (site->is_a(site_DataPatch::klass())) {
      TRACE_jvmci_4("datapatch at %i", pc_offset);
      site_DataPatch(buffer, pc_offset, site, CHECK_OK);
    } else if (site->is_a(site_Mark::klass())) {
      TRACE_jvmci_4("mark at %i", pc_offset);
      site_Mark(buffer, pc_offset, site, CHECK_OK);
    } else if (site->is_a(site_ExceptionHandler::klass())) {
      TRACE_jvmci_4("exceptionhandler at %i", pc_offset);
      site_ExceptionHandler(pc_offset, site);
    } else {
      JVMCI_ERROR_OK("unexpected site subclass: %s", site->klass()->signature_name());
    }
    last_pc_offset = pc_offset;

    if (SafepointSynchronize::do_call_back()) {
      // this is a hacky way to force a safepoint check but nothing else was jumping out at me.
      ThreadToNativeFromVM ttnfv(JavaThread::current());
    }
  }

#ifndef PRODUCT
  if (comments() != NULL) {
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

void CodeInstaller::site_ExceptionHandler(jint pc_offset, Handle exc) {
  jint handler_offset = site_ExceptionHandler::handlerPos(exc);

  // Subtable header
  _exception_handler_table.add_entry(HandlerTableEntry(1, pc_offset, 0));

  // Subtable entry
  _exception_handler_table.add_entry(HandlerTableEntry(-1, handler_offset, 0));
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

GrowableArray<ScopeValue*>* CodeInstaller::record_virtual_objects(Handle debug_info, TRAPS) {
  objArrayHandle virtualObjects = DebugInfo::virtualObjectMapping(debug_info);
  if (virtualObjects.is_null()) {
    return NULL;
  }
  GrowableArray<ScopeValue*>* objects = new GrowableArray<ScopeValue*>(virtualObjects->length(), virtualObjects->length(), NULL);
  // Create the unique ObjectValues
  for (int i = 0; i < virtualObjects->length(); i++) {
    Handle value = virtualObjects->obj_at(i);
    int id = VirtualObject::id(value);
    Handle type = VirtualObject::type(value);
    oop javaMirror = HotSpotResolvedObjectTypeImpl::javaClass(type);
    ObjectValue* sv = new ObjectValue(id, new ConstantOopWriteValue(JNIHandles::make_local(Thread::current(), javaMirror)));
    if (id < 0 || id >= objects->length()) {
      JVMCI_ERROR_NULL("virtual object id %d out of bounds", id);
    }
    if (objects->at(id) != NULL) {
      JVMCI_ERROR_NULL("duplicate virtual object id %d", id);
    }
    objects->at_put(id, sv);
  }
  // All the values which could be referenced by the VirtualObjects
  // exist, so now describe all the VirtualObjects themselves.
  for (int i = 0; i < virtualObjects->length(); i++) {
    Handle value = virtualObjects->obj_at(i);
    int id = VirtualObject::id(value);
    record_object_value(objects->at(id)->as_ObjectValue(), value, objects, CHECK_NULL);
  }
  _debug_recorder->dump_object_pool(objects);
  return objects;
}

void CodeInstaller::record_scope(jint pc_offset, Handle debug_info, ScopeMode scope_mode, bool return_oop, TRAPS) {
  Handle position = DebugInfo::bytecodePosition(debug_info);
  if (position.is_null()) {
    // Stubs do not record scope info, just oop maps
    return;
  }

  GrowableArray<ScopeValue*>* objectMapping;
  if (scope_mode == CodeInstaller::FullFrame) {
    objectMapping = record_virtual_objects(debug_info, CHECK);
  } else {
    objectMapping = NULL;
  }
  record_scope(pc_offset, position, scope_mode, objectMapping, return_oop, CHECK);
}

void CodeInstaller::record_scope(jint pc_offset, Handle position, ScopeMode scope_mode, GrowableArray<ScopeValue*>* objects, bool return_oop, TRAPS) {
  Handle frame;
  if (scope_mode == CodeInstaller::FullFrame) {
    if (!position->is_a(BytecodeFrame::klass())) {
      JVMCI_ERROR("Full frame expected for debug info at %i", pc_offset);
    }
    frame = position;
  }
  Handle caller_frame = BytecodePosition::caller(position);
  if (caller_frame.not_null()) {
    record_scope(pc_offset, caller_frame, scope_mode, objects, return_oop, CHECK);
  }

  Handle hotspot_method = BytecodePosition::method(position);
  Method* method = getMethodFromHotSpotMethod(hotspot_method());
  jint bci = BytecodePosition::bci(position);
  if (bci == BytecodeFrame::BEFORE_BCI()) {
    bci = SynchronizationEntryBCI;
  }

  TRACE_jvmci_2("Recording scope pc_offset=%d bci=%d method=%s", pc_offset, bci, method->name_and_sig_as_C_string());

  bool reexecute = false;
  if (frame.not_null()) {
    if (bci == SynchronizationEntryBCI){
       reexecute = false;
    } else {
      Bytecodes::Code code = Bytecodes::java_code_at(method, method->bcp_from(bci));
      reexecute = bytecode_should_reexecute(code);
      if (frame.not_null()) {
        reexecute = (BytecodeFrame::duringCall(frame) == JNI_FALSE);
      }
    }
  }

  DebugToken* locals_token = NULL;
  DebugToken* expressions_token = NULL;
  DebugToken* monitors_token = NULL;
  bool throw_exception = false;

  if (frame.not_null()) {
    jint local_count = BytecodeFrame::numLocals(frame);
    jint expression_count = BytecodeFrame::numStack(frame);
    jint monitor_count = BytecodeFrame::numLocks(frame);
    objArrayHandle values = BytecodeFrame::values(frame);
    objArrayHandle slotKinds = BytecodeFrame::slotKinds(frame);

    if (values.is_null() || slotKinds.is_null()) {
      THROW(vmSymbols::java_lang_NullPointerException());
    }
    if (local_count + expression_count + monitor_count != values->length()) {
      JVMCI_ERROR("unexpected values length %d in scope (%d locals, %d expressions, %d monitors)", values->length(), local_count, expression_count, monitor_count);
    }
    if (local_count + expression_count != slotKinds->length()) {
      JVMCI_ERROR("unexpected slotKinds length %d in scope (%d locals, %d expressions)", slotKinds->length(), local_count, expression_count);
    }

    GrowableArray<ScopeValue*>* locals = local_count > 0 ? new GrowableArray<ScopeValue*> (local_count) : NULL;
    GrowableArray<ScopeValue*>* expressions = expression_count > 0 ? new GrowableArray<ScopeValue*> (expression_count) : NULL;
    GrowableArray<MonitorValue*>* monitors = monitor_count > 0 ? new GrowableArray<MonitorValue*> (monitor_count) : NULL;

    TRACE_jvmci_2("Scope at bci %d with %d values", bci, values->length());
    TRACE_jvmci_2("%d locals %d expressions, %d monitors", local_count, expression_count, monitor_count);

    for (jint i = 0; i < values->length(); i++) {
      ScopeValue* second = NULL;
      Handle value = values->obj_at(i);
      if (i < local_count) {
        BasicType type = JVMCIRuntime::kindToBasicType(slotKinds->obj_at(i), CHECK);
        ScopeValue* first = get_scope_value(value, type, objects, second, CHECK);
        if (second != NULL) {
          locals->append(second);
        }
        locals->append(first);
      } else if (i < local_count + expression_count) {
        BasicType type = JVMCIRuntime::kindToBasicType(slotKinds->obj_at(i), CHECK);
        ScopeValue* first = get_scope_value(value, type, objects, second, CHECK);
        if (second != NULL) {
          expressions->append(second);
        }
        expressions->append(first);
      } else {
        MonitorValue *monitor = get_monitor_value(value, objects, CHECK);
        monitors->append(monitor);
      }
      if (second != NULL) {
        i++;
        if (i >= values->length() || values->obj_at(i) != Value::ILLEGAL()) {
          JVMCI_ERROR("double-slot value not followed by Value.ILLEGAL");
        }
      }
    }

    locals_token = _debug_recorder->create_scope_values(locals);
    expressions_token = _debug_recorder->create_scope_values(expressions);
    monitors_token = _debug_recorder->create_monitor_values(monitors);

    throw_exception = BytecodeFrame::rethrowException(frame) == JNI_TRUE;
  }

  _debug_recorder->describe_scope(pc_offset, method, NULL, bci, reexecute, throw_exception, false, return_oop,
                                  locals_token, expressions_token, monitors_token);
}

void CodeInstaller::site_Safepoint(CodeBuffer& buffer, jint pc_offset, Handle site, TRAPS) {
  Handle debug_info = site_Infopoint::debugInfo(site);
  if (debug_info.is_null()) {
    JVMCI_ERROR("debug info expected at safepoint at %i", pc_offset);
  }

  // address instruction = _instructions->start() + pc_offset;
  // jint next_pc_offset = Assembler::locate_next_instruction(instruction) - _instructions->start();
  OopMap *map = create_oop_map(debug_info, CHECK);
  _debug_recorder->add_safepoint(pc_offset, map);
  record_scope(pc_offset, debug_info, CodeInstaller::FullFrame, CHECK);
  _debug_recorder->end_safepoint(pc_offset);
}

void CodeInstaller::site_Infopoint(CodeBuffer& buffer, jint pc_offset, Handle site, TRAPS) {
  Handle debug_info = site_Infopoint::debugInfo(site);
  if (debug_info.is_null()) {
    JVMCI_ERROR("debug info expected at infopoint at %i", pc_offset);
  }

  // We'd like to check that pc_offset is greater than the
  // last pc recorded with _debug_recorder (raising an exception if not)
  // but DebugInformationRecorder doesn't have sufficient public API.

  _debug_recorder->add_non_safepoint(pc_offset);
  record_scope(pc_offset, debug_info, CodeInstaller::BytecodePosition, CHECK);
  _debug_recorder->end_non_safepoint(pc_offset);
}

void CodeInstaller::site_Call(CodeBuffer& buffer, jint pc_offset, Handle site, TRAPS) {
  Handle target = site_Call::target(site);
  InstanceKlass* target_klass = InstanceKlass::cast(target->klass());

  Handle hotspot_method; // JavaMethod
  Handle foreign_call;

  if (target_klass->is_subclass_of(SystemDictionary::HotSpotForeignCallTarget_klass())) {
    foreign_call = target;
  } else {
    hotspot_method = target;
  }

  Handle debug_info = site_Call::debugInfo(site);

  assert(hotspot_method.not_null() ^ foreign_call.not_null(), "Call site needs exactly one type");

  NativeInstruction* inst = nativeInstruction_at(_instructions->start() + pc_offset);
  jint next_pc_offset = CodeInstaller::pd_next_offset(inst, pc_offset, hotspot_method, CHECK);

  if (debug_info.not_null()) {
    OopMap *map = create_oop_map(debug_info, CHECK);
    _debug_recorder->add_safepoint(next_pc_offset, map);

    bool return_oop = hotspot_method.not_null() && getMethodFromHotSpotMethod(hotspot_method())->is_returning_oop();

    record_scope(next_pc_offset, debug_info, CodeInstaller::FullFrame, return_oop, CHECK);
  }

  if (foreign_call.not_null()) {
    jlong foreign_call_destination = HotSpotForeignCallTarget::address(foreign_call);
    if (_immutable_pic_compilation) {
      // Use fake short distance during PIC compilation.
      foreign_call_destination = (jlong)(_instructions->start() + pc_offset);
    }
    CodeInstaller::pd_relocate_ForeignCall(inst, foreign_call_destination, CHECK);
  } else { // method != NULL
    if (debug_info.is_null()) {
      JVMCI_ERROR("debug info expected at call at %i", pc_offset);
    }

    TRACE_jvmci_3("method call");
    CodeInstaller::pd_relocate_JavaMethod(hotspot_method, pc_offset, CHECK);
    if (_next_call_type == INVOKESTATIC || _next_call_type == INVOKESPECIAL) {
      // Need a static call stub for transitions from compiled to interpreted.
      CompiledStaticCall::emit_to_interp_stub(buffer, _instructions->start() + pc_offset);
    }
#if INCLUDE_AOT
    // Trampoline to far aot code.
    CompiledStaticCall::emit_to_aot_stub(buffer, _instructions->start() + pc_offset);
#endif
  }

  _next_call_type = INVOKE_INVALID;

  if (debug_info.not_null()) {
    _debug_recorder->end_safepoint(next_pc_offset);
  }
}

void CodeInstaller::site_DataPatch(CodeBuffer& buffer, jint pc_offset, Handle site, TRAPS) {
  Handle reference = site_DataPatch::reference(site);
  if (reference.is_null()) {
    THROW(vmSymbols::java_lang_NullPointerException());
  } else if (reference->is_a(site_ConstantReference::klass())) {
    Handle constant = site_ConstantReference::constant(reference);
    if (constant.is_null()) {
      THROW(vmSymbols::java_lang_NullPointerException());
    } else if (constant->is_a(HotSpotObjectConstantImpl::klass())) {
      if (!_immutable_pic_compilation) {
        // Do not patch during PIC compilation.
        pd_patch_OopConstant(pc_offset, constant, CHECK);
      }
    } else if (constant->is_a(HotSpotMetaspaceConstantImpl::klass())) {
      if (!_immutable_pic_compilation) {
        pd_patch_MetaspaceConstant(pc_offset, constant, CHECK);
      }
    } else if (constant->is_a(HotSpotSentinelConstant::klass())) {
      if (!_immutable_pic_compilation) {
        JVMCI_ERROR("sentinel constant not supported for normal compiles: %s", constant->klass()->signature_name());
      }
    } else {
      JVMCI_ERROR("unknown constant type in data patch: %s", constant->klass()->signature_name());
    }
  } else if (reference->is_a(site_DataSectionReference::klass())) {
    int data_offset = site_DataSectionReference::offset(reference);
    if (0 <= data_offset && data_offset < _constants_size) {
      pd_patch_DataSectionReference(pc_offset, data_offset, CHECK);
    } else {
      JVMCI_ERROR("data offset 0x%X points outside data section (size 0x%X)", data_offset, _constants_size);
    }
  } else {
    JVMCI_ERROR("unknown data patch type: %s", reference->klass()->signature_name());
  }
}

void CodeInstaller::site_Mark(CodeBuffer& buffer, jint pc_offset, Handle site, TRAPS) {
  Handle id_obj = site_Mark::id(site);

  if (id_obj.not_null()) {
    if (!java_lang_boxing_object::is_instance(id_obj(), T_INT)) {
      JVMCI_ERROR("expected Integer id, got %s", id_obj->klass()->signature_name());
    }
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
        pd_relocate_poll(pc, id, CHECK);
        break;
      case CARD_TABLE_SHIFT:
      case CARD_TABLE_ADDRESS:
      case HEAP_TOP_ADDRESS:
      case HEAP_END_ADDRESS:
      case NARROW_KLASS_BASE_ADDRESS:
      case NARROW_OOP_BASE_ADDRESS:
      case CRC_TABLE_ADDRESS:
      case LOG_OF_HEAP_REGION_GRAIN_BYTES:
      case INLINE_CONTIGUOUS_ALLOCATION_SUPPORTED:
        break;
      default:
        JVMCI_ERROR("invalid mark id: %d", id);
        break;
    }
  }
}

