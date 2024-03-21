/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "code/compiledIC.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compilerThread.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "jvmci/jvmciCodeInstaller.hpp"
#include "jvmci/jvmciCompilerToVM.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "memory/universe.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "oops/klass.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/arguments.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/align.hpp"

// frequently used constants
// Allocate them with new so they are never destroyed (otherwise, a
// forced exit could destroy these objects while they are still in
// use).
ConstantOopWriteValue* CodeInstaller::_oop_null_scope_value = new (mtJVMCI) ConstantOopWriteValue(nullptr);
ConstantIntValue*      CodeInstaller::_int_m1_scope_value = new (mtJVMCI) ConstantIntValue(-1);
ConstantIntValue*      CodeInstaller::_int_0_scope_value =  new (mtJVMCI) ConstantIntValue((jint)0);
ConstantIntValue*      CodeInstaller::_int_1_scope_value =  new (mtJVMCI) ConstantIntValue(1);
ConstantIntValue*      CodeInstaller::_int_2_scope_value =  new (mtJVMCI) ConstantIntValue(2);
LocationValue*         CodeInstaller::_illegal_value = new (mtJVMCI) LocationValue(Location());
MarkerValue*           CodeInstaller::_virtual_byte_array_marker = new (mtJVMCI) MarkerValue();

static bool is_set(u1 flags, u1 bit) {
  return flags & bit;
}

oop HotSpotCompiledCodeStream::get_oop(int id, JVMCI_TRAPS) const {
  if (_object_pool.is_null()) {
    JVMCI_ERROR_NULL("object pool is null%s", context());
  }
  if (!_object_pool.is_null() && 0 <= id && id < _object_pool->length()) {
    return _object_pool->obj_at(id);
  }
  JVMCI_ERROR_NULL("unknown direct object id %d%s", id, context());
}

u4 HotSpotCompiledCodeStream::offset() const {
  u4 res = 0;
  for (Chunk* c = _head; c != nullptr; c = c->next()) {
    if (c == _chunk) {
      res += _pos - c->data();
      break;
    } else {
      res += c->size();
    }
  }
  return res;
}

bool HotSpotCompiledCodeStream::available() const {
  u4 rem = _chunk->data_end() - _pos;
  for (Chunk* c = _chunk->next(); c != nullptr; c = c->next()) {
    rem += c->size();
  }
  return rem;
}

void HotSpotCompiledCodeStream::dump_buffer(outputStream* st) const {
  st->print_cr("HotSpotCompiledCode stream for %s:", code_desc());
  int chunk_index = 0;
  for (Chunk* c = _head; c != nullptr; c = c->next()) {
    const u1* data     = c->data();
    const u1* data_end = c->data_end();

    int to_dump = data_end - data;
    st->print_cr("# chunk %d, %d bytes", chunk_index, to_dump);
    st->print_data((void*) data, to_dump, true, false);
    chunk_index++;
  }
}

void HotSpotCompiledCodeStream::dump_buffer_tail(int len, outputStream* st) const {
  const u1* start;
  int avail = _pos - _chunk->data();
  if (len >= avail) {
    len = avail;
    start = _chunk->data();
  } else {
    start = _pos - len;

    // Ensure start is 16-byte aligned wrt chunk start
    int start_offset = start - _chunk->data();
    start -= (start_offset % 16);
    len = _pos - start;
  }

  st->print_cr("Last %d bytes up to current read position " INTPTR_FORMAT " in HotSpotCompiledCode stream for %s:", len, p2i(_pos), code_desc());
  st->print_data((void*) start, len, true, false);
}

const char* HotSpotCompiledCodeStream::context() const {
  stringStream st;
  st.cr();
  st.print_cr("at " INTPTR_FORMAT " in HotSpotCompiledCode stream", p2i(_pos));
  dump_buffer_tail(100, &st);
  return st.as_string();
}

void HotSpotCompiledCodeStream::before_read(u1 size) {
  if (_pos + size > _chunk->data_end()) {
    Chunk* next = _chunk->next();
    if (next == nullptr || size > next->size()) {
      dump_buffer();
      fatal("%s: reading %d bytes overflows buffer at " INTPTR_FORMAT, code_desc(), size, p2i(_pos));
    }
    _chunk = next;
    _pos = _chunk->data();
  }
}

// Reads a size followed by an ascii string from the stream and
// checks that they match `expect_size` and `expect_name` respectively. This
// implements a rudimentary type checking of the stream between the stream producer
// (Java) and the consumer (C++).
void HotSpotCompiledCodeStream::check_data(u2 expect_size, const char* expect_name) {
  u2 actual_size = get_u1();
  u2 ascii_len = get_u1();
  const char* actual_name = (const char*) _pos;
  char* end = (char*) _pos + ascii_len;
  _pos = (const u1*) end;
  if (strlen(expect_name) != ascii_len || strncmp(expect_name, actual_name, ascii_len) != 0) {
    dump_buffer();
    fatal("%s: expected \"%s\" at " INTPTR_FORMAT ", got \"%.*s\" (len: %d)",
        code_desc(), expect_name, p2i(actual_name), ascii_len, actual_name, ascii_len);
  }
  if (actual_size != expect_size) {
    dump_buffer();
    fatal("%s: expected \"%s\" at " INTPTR_FORMAT " to have size %u, got %u",
        code_desc(), expect_name, p2i(actual_name), expect_size, actual_size);
  }
}

const char* HotSpotCompiledCodeStream::read_utf8(const char* name, JVMCI_TRAPS) {
  jint utf_len = read_s4(name);
  if (utf_len == -1) {
    return nullptr;
  }
  guarantee(utf_len >= 0, "bad utf_len: %d", utf_len);

  const char* utf = (const char*) _pos;
  char* end = (char*) _pos + utf_len;
  _pos = (const u1*) (end + 1);
  if (*end != '\0') {
    JVMCI_ERROR_NULL("UTF8 string at " INTPTR_FORMAT " of length %d missing 0 terminator: \"%.*s\"%s",
        p2i(utf), utf_len, utf_len, utf, context());
  }
  return utf;
}

Method* HotSpotCompiledCodeStream::read_method(const char* name) {
  return (Method*) read_u8(name);
}

Klass* HotSpotCompiledCodeStream::read_klass(const char* name) {
  return (Klass*) read_u8(name);
}

ScopeValue* HotSpotCompiledCodeStream::virtual_object_at(int id, JVMCI_TRAPS) const {
  if (_virtual_objects == nullptr) {
    JVMCI_ERROR_NULL("virtual object id %d read outside scope of decoding DebugInfo%s", id, context());
  }
  if (id < 0 || id >= _virtual_objects->length()) {
    JVMCI_ERROR_NULL("invalid virtual object id %d%s", id, context());
  }
  return _virtual_objects->at(id);
}

#ifndef PRODUCT
void CodeInstaller::verify_bci_constants(JVMCIEnv* env) {
#define CHECK_IN_SYNC(name) do { \
  int expect = env->get_BytecodeFrame_ ## name ##_BCI(); \
  int actual = name##_BCI; \
  if (expect != actual) fatal("CodeInstaller::" #name "_BCI(%d) != BytecodeFrame." #name "_BCI(%d)", expect, actual); \
} while(0)

  CHECK_IN_SYNC(UNWIND);
  CHECK_IN_SYNC(BEFORE);
  CHECK_IN_SYNC(AFTER);
  CHECK_IN_SYNC(AFTER_EXCEPTION);
  CHECK_IN_SYNC(UNKNOWN);
  CHECK_IN_SYNC(INVALID_FRAMESTATE);
#undef CHECK_IN_SYNC
}
#endif

VMReg CodeInstaller::getVMRegFromLocation(HotSpotCompiledCodeStream* stream, int total_frame_size, JVMCI_TRAPS) {
  u2 reg = stream->read_u2("register");
  u2 offset = stream->read_u2("offset");

  if (reg != NO_REGISTER) {
    VMReg vmReg = CodeInstaller::get_hotspot_reg(reg, JVMCI_CHECK_NULL);
    if (offset % 4 == 0) {
      return vmReg->next(offset / 4);
    } else {
      JVMCI_ERROR_NULL("unaligned subregister offset %d in oop map%s", offset, stream->context());
    }
  } else {
    if (offset % 4 == 0) {
      VMReg vmReg = VMRegImpl::stack2reg(offset / 4);
      if (!OopMapValue::legal_vm_reg_name(vmReg)) {
        // This restriction only applies to VMRegs that are used in OopMap but
        // since that's the only use of VMRegs it's simplest to put this test
        // here.  This test should also be equivalent legal_vm_reg_name but JVMCI
        // clients can use max_oop_map_stack_stack_offset to detect this problem
        // directly.  The asserts just ensure that the tests are in agreement.
        assert(offset > CompilerToVM::Data::max_oop_map_stack_offset(), "illegal VMReg");
        JVMCI_ERROR_NULL("stack offset %d is too large to be encoded in OopMap (max %d)%s",
                         offset, CompilerToVM::Data::max_oop_map_stack_offset(), stream->context());
      }
      assert(OopMapValue::legal_vm_reg_name(vmReg), "illegal VMReg");
      return vmReg;
    } else {
      JVMCI_ERROR_NULL("unaligned stack offset %d in oop map%s", offset, stream->context());
    }
  }
}

OopMap* CodeInstaller::create_oop_map(HotSpotCompiledCodeStream* stream, u1 debug_info_flags, JVMCI_TRAPS) {
  assert(is_set(debug_info_flags, DI_HAS_REFERENCE_MAP), "must be");
  u2 max_register_size = stream->read_u2("maxRegisterSize");
  if (!_has_wide_vector && SharedRuntime::is_wide_vector(max_register_size)) {
    if (SharedRuntime::polling_page_vectors_safepoint_handler_blob() == nullptr) {
      JVMCI_ERROR_NULL("JVMCI is producing code using vectors larger than the runtime supports%s", stream->context());
    }
    _has_wide_vector = true;
  }
  u2 length = stream->read_u2("referenceMap:length");

  OopMap* map = new OopMap(_total_frame_size, _parameter_count);
  for (int i = 0; i < length; i++) {
    bool has_derived = stream->read_bool("hasDerived");
    u2 bytes = stream->read_u2("sizeInBytes");
    VMReg vmReg = getVMRegFromLocation(stream, _total_frame_size, JVMCI_CHECK_NULL);
    if (has_derived) {
      // derived oop
      if (bytes == LP64_ONLY(8) NOT_LP64(4)) {
        VMReg baseReg = getVMRegFromLocation(stream, _total_frame_size, JVMCI_CHECK_NULL);
        map->set_derived_oop(vmReg, baseReg);
      } else {
        JVMCI_ERROR_NULL("invalid derived oop size in ReferenceMap: %d%s", bytes, stream->context());
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
      JVMCI_ERROR_NULL("invalid oop size in ReferenceMap: %d%s", bytes, stream->context());
    }
  }

  if (is_set(debug_info_flags, DI_HAS_CALLEE_SAVE_INFO)) {
    length = stream->read_u2("calleeSaveInfo:length");
    for (jint i = 0; i < length; i++) {
      u2 jvmci_reg_number = stream->read_u2("register");
      VMReg hotspot_reg = CodeInstaller::get_hotspot_reg(jvmci_reg_number, JVMCI_CHECK_NULL);
      // HotSpot stack slots are 4 bytes
      u2 jvmci_slot = stream->read_u2("slot");
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

void* CodeInstaller::record_metadata_reference(CodeSection* section, address dest, HotSpotCompiledCodeStream* stream, u1 tag, JVMCI_TRAPS) {
  /*
   * This method needs to return a raw (untyped) pointer, since the value of a pointer to the base
   * class is in general not equal to the pointer of the subclass. When patching metaspace pointers,
   * the compiler expects a direct pointer to the subclass (Klass* or Method*), not a pointer to the
   * base class (Metadata* or MetaspaceObj*).
   */
  if (tag == PATCH_KLASS) {
    Klass* klass = stream->read_klass("patch:klass");
    int index = _oop_recorder->find_index(klass);
    section->relocate(dest, metadata_Relocation::spec(index));
    JVMCI_event_3("metadata[%d of %d] = %s", index, _oop_recorder->metadata_count(), klass->name()->as_C_string());
    return klass;
  } else if (tag == PATCH_METHOD) {
    Method* method = stream->read_method("patch:method");
    int index = _oop_recorder->find_index(method);
    section->relocate(dest, metadata_Relocation::spec(index));
    JVMCI_event_3("metadata[%d of %d] = %s", index, _oop_recorder->metadata_count(), method->name()->as_C_string());
    return method;
  } else {
    JVMCI_ERROR_NULL("unexpected metadata reference tag: %d%s", tag, stream->context());
  }
}

#ifdef _LP64
narrowKlass CodeInstaller::record_narrow_metadata_reference(CodeSection* section, address dest, HotSpotCompiledCodeStream* stream, u1 tag, JVMCI_TRAPS) {
  if (tag != PATCH_NARROW_KLASS) {
    JVMCI_ERROR_0("unexpected compressed pointer tag %d%s", tag, stream->context());
  }
  Klass* klass = stream->read_klass("patch:klass");
  int index = _oop_recorder->find_index(klass);
  section->relocate(dest, metadata_Relocation::spec(index));
  JVMCI_event_3("narrowKlass[%d of %d] = %s", index, _oop_recorder->metadata_count(), klass->name()->as_C_string());
  return CompressedKlassPointers::encode(klass);
}
#endif

ScopeValue* CodeInstaller::to_primitive_value(HotSpotCompiledCodeStream* stream, jlong raw, BasicType type, ScopeValue* &second, JVMCI_TRAPS) {
  if (type == T_INT || type == T_FLOAT) {
    jint prim = (jint) raw;
    switch (prim) {
      case -1: return _int_m1_scope_value;
      case  0: return _int_0_scope_value;
      case  1: return _int_1_scope_value;
      case  2: return _int_2_scope_value;
      default: return new ConstantIntValue(prim);
    }
  } else if (type == T_LONG || type == T_DOUBLE) {
    jlong prim = raw;
    second = _int_1_scope_value;
    return new ConstantLongValue(prim);
  } else {
    JVMCI_ERROR_NULL("unexpected primitive constant type %s%s", basictype_to_str(type), stream->context());
  }
}

Handle CodeInstaller::read_oop(HotSpotCompiledCodeStream* stream, u1 tag, JVMCI_TRAPS) {
  oop obj;
  if (tag == OBJECT_ID) {
    obj = stream->get_oop(stream->read_u1("id"), JVMCI_CHECK_(Handle()));
  } else if (tag == OBJECT_ID2) {
    obj = stream->get_oop(stream->read_u2("id:2"), JVMCI_CHECK_(Handle()));
  } else if (tag == JOBJECT) {
    jlong object_handle = stream->read_u8("jobject");
    obj = jvmci_env()->resolve_oop_handle(object_handle);
  } else {
    JVMCI_ERROR_(Handle(), "unexpected oop tag: %d", tag)
  }
  if (obj == nullptr) {
    JVMCI_THROW_MSG_(InternalError, "Constant was unexpectedly null", Handle());
  } else {
    guarantee(oopDesc::is_oop_or_null(obj), "invalid oop: " INTPTR_FORMAT, p2i((oopDesc*) obj));
  }
  return Handle(stream->thread(), obj);
}

ScopeValue* CodeInstaller::get_scope_value(HotSpotCompiledCodeStream* stream, u1 tag, BasicType type, ScopeValue* &second, JVMCI_TRAPS) {
  second = nullptr;
  switch (tag) {
    case ILLEGAL: {
      if (type != T_ILLEGAL) {
        JVMCI_ERROR_NULL("unexpected illegal value, expected %s%s", basictype_to_str(type), stream->context());
      }
      return _illegal_value;
    }
    case REGISTER_PRIMITIVE:
    case REGISTER_NARROW_OOP:
    case REGISTER_OOP:
    case REGISTER_VECTOR: {
      u2 number = stream->read_u2("register");
      VMReg hotspotRegister = get_hotspot_reg(number, JVMCI_CHECK_NULL);
      if (is_general_purpose_reg(hotspotRegister)) {
        Location::Type locationType;
        if (type == T_OBJECT) {
          locationType = tag == REGISTER_NARROW_OOP ? Location::narrowoop : Location::oop;
        } else if (type == T_LONG) {
          locationType = Location::lng;
        } else if (type == T_INT || type == T_FLOAT || type == T_SHORT || type == T_CHAR || type == T_BYTE || type == T_BOOLEAN) {
          locationType = Location::int_in_long;
        } else {
          JVMCI_ERROR_NULL("unexpected type %s in CPU register%s", basictype_to_str(type), stream->context());
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
        } else if (type == T_OBJECT && tag == REGISTER_VECTOR) {
          locationType = Location::vector;
        } else {
          JVMCI_ERROR_NULL("unexpected type %s in floating point register%s", basictype_to_str(type), stream->context());
        }
        ScopeValue* value = new LocationValue(Location::new_reg_loc(locationType, hotspotRegister));
        if (type == T_DOUBLE) {
          second = value;
        }
        return value;
      }
    }
    case STACK_SLOT_PRIMITIVE:
    case STACK_SLOT_NARROW_OOP:
    case STACK_SLOT_OOP:
    case STACK_SLOT_VECTOR: {
      jint offset = (jshort) stream->read_s2("offset");
      if (stream->read_bool("addRawFrameSize")) {
        offset += _total_frame_size;
      }
      Location::Type locationType;
      if (type == T_OBJECT) {
        locationType = tag == STACK_SLOT_VECTOR ? Location::vector : tag == STACK_SLOT_NARROW_OOP ? Location::narrowoop : Location::oop;
      } else if (type == T_LONG) {
        locationType = Location::lng;
      } else if (type == T_DOUBLE) {
        locationType = Location::dbl;
      } else if (type == T_INT || type == T_FLOAT || type == T_SHORT || type == T_CHAR || type == T_BYTE || type == T_BOOLEAN) {
        locationType = Location::normal;
      } else {
        JVMCI_ERROR_NULL("unexpected type %s in stack slot%s", basictype_to_str(type), stream->context());
      }
      ScopeValue* value = new LocationValue(Location::new_stk_loc(locationType, offset));
      if (type == T_DOUBLE || type == T_LONG) {
        second = value;
      }
      return value;
    }
    case NULL_CONSTANT:      { return _oop_null_scope_value; }
    case RAW_CONSTANT:       { return new ConstantLongValue(stream->read_u8("primitive")); }
    case PRIMITIVE_0:        { ScopeValue* v = to_primitive_value(stream, 0, type, second, JVMCI_CHECK_NULL); return v; }
    case PRIMITIVE4:         { ScopeValue* v = to_primitive_value(stream, stream->read_s4("primitive4"), type, second, JVMCI_CHECK_NULL); return v; }
    case PRIMITIVE8:         { ScopeValue* v = to_primitive_value(stream, stream->read_s8("primitive8"), type, second, JVMCI_CHECK_NULL); return v; }
    case VIRTUAL_OBJECT_ID:  { ScopeValue* v = stream->virtual_object_at(stream->read_u1("id"),   JVMCI_CHECK_NULL); return v; }
    case VIRTUAL_OBJECT_ID2: { ScopeValue* v = stream->virtual_object_at(stream->read_u2("id:2"), JVMCI_CHECK_NULL); return v; }

    case OBJECT_ID:
    case OBJECT_ID2:
    case JOBJECT: {
      Handle obj = read_oop(stream, tag, JVMCI_CHECK_NULL);
      return new ConstantOopWriteValue(JNIHandles::make_local(obj()));
    }
    default: {
      JVMCI_ERROR_NULL("unexpected tag in scope: %d%s", tag, stream->context())
    }
  }
}

void CodeInstaller::record_object_value(ObjectValue* sv, HotSpotCompiledCodeStream* stream, JVMCI_TRAPS) {
  oop javaMirror = JNIHandles::resolve(sv->klass()->as_ConstantOopWriteValue()->value());
  Klass* klass = java_lang_Class::as_Klass(javaMirror);
  bool isLongArray = klass == Universe::longArrayKlassObj();
  bool isByteArray = klass == Universe::byteArrayKlassObj();

  u2 length = stream->read_u2("values:length");
  for (jint i = 0; i < length; i++) {
    ScopeValue* cur_second = nullptr;
    BasicType type = (BasicType) stream->read_u1("basicType");
    ScopeValue* value;
    u1 tag = stream->read_u1("tag");
    if (tag == ILLEGAL) {
      if (isByteArray && type == T_ILLEGAL) {
        /*
         * The difference between a virtualized large access and a deferred write is the kind stored in the slotKinds
         * of the virtual object: in the virtualization case, the kind is illegal, in the deferred write case, the kind
         * is access stack kind (an int).
         */
        value = _virtual_byte_array_marker;
      } else {
        value = _illegal_value;
        if (type == T_DOUBLE || type == T_LONG) {
            cur_second = _illegal_value;
        }
      }
    } else {
      value = get_scope_value(stream, tag, type, cur_second, JVMCI_CHECK);
    }

    if (isLongArray && cur_second == nullptr) {
      // we're trying to put ints into a long array... this isn't really valid, but it's used for some optimizations.
      // add an int 0 constant
      cur_second = _int_0_scope_value;
    }

    if (isByteArray && cur_second != nullptr && (type == T_DOUBLE || type == T_LONG)) {
      // we are trying to write a long in a byte Array. We will need to count the illegals to restore the type of
      // the thing we put inside.
      cur_second = nullptr;
    }

    if (cur_second != nullptr) {
      sv->field_values()->append(cur_second);
    }
    assert(value != nullptr, "missing value");
    sv->field_values()->append(value);
  }
}

GrowableArray<ScopeValue*>* CodeInstaller::read_local_or_stack_values(HotSpotCompiledCodeStream* stream, u1 frame_flags, bool is_locals, JVMCI_TRAPS) {
  u2 length;
  if (is_locals) {
    if (!is_set(frame_flags, DIF_HAS_LOCALS)) {
      return nullptr;
    }
    length = stream->read_u2("numLocals");
  } else {
    if (!is_set(frame_flags, DIF_HAS_STACK)) {
      return nullptr;
    }
    length = stream->read_u2("numStack");
  }
  GrowableArray<ScopeValue*>* values = new GrowableArray<ScopeValue*> (length);
  for (int i = 0; i < length; i++) {
    ScopeValue* second = nullptr;
    BasicType type = (BasicType) stream->read_u1("basicType");
    u1 tag = stream->read_u1("tag");
    ScopeValue* first = get_scope_value(stream, tag, type, second, JVMCI_CHECK_NULL);
    if (second != nullptr) {
      if (i == length) {
        JVMCI_ERROR_NULL("double-slot value not followed by Value.ILLEGAL%s", stream->context());
      }
      i++;
      stream->read_u1("basicType");
      tag = stream->read_u1("tag");
      if (tag != ILLEGAL) {
        JVMCI_ERROR_NULL("double-slot value not followed by Value.ILLEGAL%s", stream->context());
      }
      values->append(second);
    }
    values->append(first);
  }
  return values;
}

GrowableArray<MonitorValue*>* CodeInstaller::read_monitor_values(HotSpotCompiledCodeStream* stream, u1 frame_flags, JVMCI_TRAPS) {
  if (!is_set(frame_flags, DIF_HAS_LOCKS)) {
    return nullptr;
  }
  if (!_has_monitors) {
    _has_monitors = true;
  }
  u2 length = stream->read_u2("numLocks");
  GrowableArray<MonitorValue*>* monitors = new GrowableArray<MonitorValue*>(length);
  for (int i = 0; i < length; i++) {
    bool eliminated = stream->read_bool("isEliminated");
    ScopeValue* second = nullptr;
    ScopeValue* owner_value = get_scope_value(stream, stream->read_u1("tag"), T_OBJECT, second, JVMCI_CHECK_NULL);
    assert(second == nullptr, "monitor cannot occupy two stack slots");

    ScopeValue* lock_data_value = get_scope_value(stream, stream->read_u1("tag"), T_LONG, second, JVMCI_CHECK_NULL);
    assert(second == lock_data_value, "monitor is LONG value that occupies two stack slots");
    assert(lock_data_value->is_location(), "invalid monitor location");
    Location lock_data_loc = ((LocationValue*) lock_data_value)->location();

    monitors->append(new MonitorValue(owner_value, lock_data_loc, eliminated));
  }
  return monitors;
}

void CodeInstaller::initialize_dependencies(HotSpotCompiledCodeStream* stream, u1 code_flags, OopRecorder* oop_recorder, JVMCI_TRAPS) {
  JavaThread* thread = stream->thread();
  CompilerThread* compilerThread = thread->is_Compiler_thread() ? CompilerThread::cast(thread) : nullptr;
  _oop_recorder = oop_recorder;
  _dependencies = new Dependencies(&_arena, _oop_recorder, compilerThread != nullptr ? compilerThread->log() : nullptr);
  if (is_set(code_flags, HCC_HAS_ASSUMPTIONS)) {
    u2 length = stream->read_u2("assumptions:length");
    for (int i = 0; i < length; ++i) {
      u1 tag = stream->read_u1("tag");
      switch (tag) {
        case NO_FINALIZABLE_SUBCLASS: {
          Klass* receiver_type = stream->read_klass("receiverType");
          _dependencies->assert_has_no_finalizable_subclasses(receiver_type);
          break;
        }
        case CONCRETE_SUBTYPE: {
          Klass* context = stream->read_klass("context");
          Klass* subtype = stream->read_klass("subtype");
          assert(context->is_abstract(), "must be");
          _dependencies->assert_abstract_with_unique_concrete_subtype(context, subtype);
          break;
        }
        case LEAF_TYPE: {
          Klass* context = stream->read_klass("context");
          _dependencies->assert_leaf_type(context);
          break;
        }
        case CONCRETE_METHOD: {
          Klass* context = stream->read_klass("context");
          Method* impl = stream->read_method("impl");
          _dependencies->assert_unique_concrete_method(context, impl);
          break;
        }
        case CALLSITE_TARGET_VALUE: {
          u1 obj_tag = stream->read_u1("tag");
          Handle callSite = read_oop(stream, obj_tag, JVMCI_CHECK);
          obj_tag = stream->read_u1("tag");
          Handle methodHandle = read_oop(stream, obj_tag, JVMCI_CHECK);
          _dependencies->assert_call_site_target_value(callSite(), methodHandle());
          break;
        }
        default: {
          JVMCI_ERROR("unexpected assumption tag %d%s", tag, stream->context());
        }
      }
    }
  }
  if (is_set(code_flags, HCC_HAS_METHODS)) {
    u2 length = stream->read_u2("methods:length");
    for (int i = 0; i < length; ++i) {
      Method* method = stream->read_method("method");
      if (JvmtiExport::can_hotswap_or_post_breakpoint()) {
        _dependencies->assert_evol_method(method);
      }
    }
  }
}

JVMCI::CodeInstallResult CodeInstaller::install_runtime_stub(CodeBlob*& cb,
                                                             const char* name,
                                                             CodeBuffer* buffer,
                                                             int stack_slots,
                                                             JVMCI_TRAPS) {
  if (name == nullptr) {
    JVMCI_ERROR_OK("stub should have a name");
  }

  name = os::strdup(name);
  GrowableArray<RuntimeStub*> *stubs_to_free = nullptr;
#ifdef ASSERT
  const char* val = Arguments::PropertyList_get_value(Arguments::system_properties(), "test.jvmci.forceRuntimeStubAllocFail");
  if (val != nullptr && strstr(name , val) != 0) {
    stubs_to_free = new GrowableArray<RuntimeStub*>();
    JVMCI_event_1("forcing allocation of %s in code cache to fail", name);
  }
#endif

  do {
    RuntimeStub* stub = RuntimeStub::new_runtime_stub(name,
                                       buffer,
                                       _offsets.value(CodeOffsets::Frame_Complete),
                                       stack_slots,
                                       _debug_recorder->_oopmaps,
                                       /* caller_must_gc_arguments */ false,
                                       /* alloc_fail_is_fatal */ false);
    cb = stub;
    if (stub == nullptr) {
      // Allocation failed
#ifdef ASSERT
      if (stubs_to_free != nullptr) {
        JVMCI_event_1("allocation of %s in code cache failed, freeing %d stubs", name, stubs_to_free->length());
        for (GrowableArrayIterator<RuntimeStub*> iter = stubs_to_free->begin(); iter != stubs_to_free->end(); ++iter) {
          RuntimeStub::free(*iter);
        }
      }
#endif
      return JVMCI::cache_full;
    }
    if (stubs_to_free == nullptr) {
      return JVMCI::ok;
    }
    stubs_to_free->append(stub);
  } while (true);
}

JVMCI::CodeInstallResult CodeInstaller::install(JVMCICompiler* compiler,
    jlong compiled_code_buffer,
    bool with_type_info,
    JVMCIObject compiled_code,
    objArrayHandle object_pool,
    CodeBlob*& cb,
    JVMCIObject installed_code,
    FailedSpeculation** failed_speculations,
    char* speculations,
    int speculations_len,
    JVMCI_TRAPS) {

  JavaThread* thread = JavaThread::current();
  HotSpotCompiledCodeStream* stream = new HotSpotCompiledCodeStream(thread, (const u1*) compiled_code_buffer, with_type_info, object_pool);

  u1 code_flags = stream->read_u1("code:flags");
  bool is_nmethod = is_set(code_flags, HCC_IS_NMETHOD);
  const char* name = stream->read_utf8("name", JVMCI_CHECK_OK);

  methodHandle method;
  jint entry_bci = -1;
  JVMCICompileState* compile_state = nullptr;
  bool has_unsafe_access = false;
  jint id = -1;

  if (is_nmethod) {
    method = methodHandle(thread, stream->read_method("method"));
    entry_bci = is_nmethod ? stream->read_s4("entryBCI") : -1;
    compile_state = (JVMCICompileState*) stream->read_u8("compileState");
    has_unsafe_access = stream->read_bool("hasUnsafeAccess");
    id = stream->read_s4("id");
  }
  stream->set_code_desc(name, method);

  CodeBuffer buffer("JVMCI Compiler CodeBuffer");
  OopRecorder* recorder = new OopRecorder(&_arena, true);
  initialize_dependencies(stream, code_flags, recorder, JVMCI_CHECK_OK);

  // Get instructions and constants CodeSections early because we need it.
  _instructions = buffer.insts();
  _constants = buffer.consts();

  initialize_fields(stream, code_flags, method, buffer, JVMCI_CHECK_OK);
  JVMCI::CodeInstallResult result = initialize_buffer(compiled_code, buffer, stream, code_flags, JVMCI_CHECK_OK);

  u4 available = stream->available();
  if (result == JVMCI::ok && available != 0) {
    JVMCI_ERROR_OK("%d bytes remaining in stream%s", available, stream->context());
  }

  if (result != JVMCI::ok) {
    return result;
  }

  int stack_slots = _total_frame_size / HeapWordSize; // conversion to words

  if (!is_nmethod) {
    return install_runtime_stub(cb, name, &buffer, stack_slots, JVMCI_CHECK_OK);
  } else {
    if (compile_state != nullptr) {
      jvmci_env()->set_compile_state(compile_state);
    }

    if (id == -1) {
      // Make sure a valid compile_id is associated with every compile
      id = CompileBroker::assign_compile_id_unlocked(thread, method, entry_bci);
      jvmci_env()->set_HotSpotCompiledNmethod_id(compiled_code, id);
    }
    if (!jvmci_env()->isa_HotSpotNmethod(installed_code)) {
      JVMCI_THROW_MSG_(IllegalArgumentException, "InstalledCode object must be a HotSpotNmethod when installing a HotSpotCompiledNmethod", JVMCI::ok);
    }

    // We would like to be strict about the nmethod entry barrier but there are various test
    // configurations which generate assembly without being a full compiler. So for now we enforce
    // that JIT compiled methods must have an nmethod barrier.
    bool install_default = JVMCIENV->get_HotSpotNmethod_isDefault(installed_code) != 0;
    if (_nmethod_entry_patch_offset == -1 && install_default) {
      JVMCI_THROW_MSG_(IllegalArgumentException, "nmethod entry barrier is missing", JVMCI::ok);
    }

    JVMCIObject mirror = installed_code;
    nmethod* nm = nullptr; // nm is an out parameter of register_method
    result = runtime()->register_method(jvmci_env(),
                                        method,
                                        nm,
                                        entry_bci,
                                        &_offsets,
                                        _orig_pc_offset,
                                        &buffer,
                                        stack_slots,
                                        _debug_recorder->_oopmaps,
                                        &_exception_handler_table,
                                        &_implicit_exception_table,
                                        compiler,
                                        _debug_recorder,
                                        _dependencies,
                                        id,
                                        _has_monitors,
                                        has_unsafe_access,
                                        _has_wide_vector,
                                        compiled_code,
                                        mirror,
                                        failed_speculations,
                                        speculations,
                                        speculations_len,
                                        _nmethod_entry_patch_offset);
    if (result == JVMCI::ok) {
      cb = nm;
      if (compile_state == nullptr) {
        // This compile didn't come through the CompileBroker so perform the printing here
        DirectiveSet* directive = DirectivesStack::getMatchingDirective(method, compiler);
        nm->maybe_print_nmethod(directive);
        DirectivesStack::release(directive);
      }

      if (nm != nullptr) {
        if (_nmethod_entry_patch_offset != -1) {
          err_msg msg("");
          BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();

          if (!bs_nm->verify_barrier(nm, msg)) {
            JVMCI_THROW_MSG_(IllegalArgumentException, err_msg("nmethod entry barrier is malformed: %s", msg.buffer()), JVMCI::ok);
          }
        }
      }
    }
  }

  if (cb != nullptr) {
    // Make sure the pre-calculated constants section size was correct.
    guarantee((cb->code_begin() - cb->content_begin()) >= _constants_size, "%d < %d", (int)(cb->code_begin() - cb->content_begin()), _constants_size);
  }
  return result;
}

void CodeInstaller::initialize_fields(HotSpotCompiledCodeStream* stream, u1 code_flags, methodHandle& method, CodeBuffer& buffer, JVMCI_TRAPS) {
  if (!method.is_null()) {
    _parameter_count = method->size_of_parameters();
    JVMCI_event_2("installing code for %s", method->name_and_sig_as_C_string());
  } else {
    // Must be a HotSpotCompiledCode for a stub.
    // Only used in OopMap constructor for non-product builds
    _parameter_count = 0;
  }
  _sites_count = stream->read_s4("sites:length");
  _code_size = stream->read_s4("targetCodeSize");
  _total_frame_size = stream->read_s4("totalFrameSize");
  if (!is_set(code_flags, HCC_HAS_DEOPT_RESCUE_SLOT)) {
    _orig_pc_offset = -1;
  } else {
    _orig_pc_offset = stream->read_s2("offset");
    if (stream->read_bool("addRawFrameSize")) {
      _orig_pc_offset += _total_frame_size;
    }
    if (_orig_pc_offset < 0) {
      JVMCI_ERROR("invalid deopt rescue slot: %d%s", _orig_pc_offset, stream->context());
    }
  }

  // Pre-calculate the constants section size.  This is required for PC-relative addressing.
  u4 data_section_size = stream->read_u4("dataSectionSize");
  u1 data_section_alignment = stream->read_u1("dataSectionAlignment");
  buffer.set_const_section_alignment(data_section_alignment);
  if ((_constants->alignment() % data_section_alignment) != 0) {
    JVMCI_ERROR("invalid data section alignment: %d [constants alignment: %d]%s",
        data_section_alignment, _constants->alignment(), stream->context());
  }
  _constants_size = data_section_size;
  _next_call_type = INVOKE_INVALID;
  _has_monitors = false;
  _has_wide_vector = false;
  _nmethod_entry_patch_offset = -1;
}

u1 CodeInstaller::as_read_oop_tag(HotSpotCompiledCodeStream* stream, u1 patch_object_tag, JVMCI_TRAPS) {
  switch (patch_object_tag) {
    case PATCH_OBJECT_ID:
    case PATCH_NARROW_OBJECT_ID: {
      return OBJECT_ID;
    }
    case PATCH_OBJECT_ID2:
    case PATCH_NARROW_OBJECT_ID2: {
      return OBJECT_ID2;
    }
    case PATCH_NARROW_JOBJECT:
    case PATCH_JOBJECT: {
      return JOBJECT;
    }
    default: {
      JVMCI_ERROR_0("unknown object patch tag: %d%s", patch_object_tag, stream->context());
    }
  }
}

int CodeInstaller::estimate_stubs_size(HotSpotCompiledCodeStream* stream, JVMCI_TRAPS) {
  // Estimate the number of static call stubs that might be emitted.
  u2 static_call_stubs = stream->read_u2("numStaticCallStubs");
  u2 trampoline_stubs = stream->read_u2("numTrampolineStubs");
  int size = static_call_stubs * CompiledDirectCall::to_interp_stub_size();
  size += trampoline_stubs * CompiledDirectCall::to_trampoline_stub_size();
  return size;
}

// perform data and call relocation on the CodeBuffer
JVMCI::CodeInstallResult CodeInstaller::initialize_buffer(JVMCIObject compiled_code, CodeBuffer& buffer, HotSpotCompiledCodeStream* stream, u1 code_flags, JVMCI_TRAPS) {
  JavaThread* thread = stream->thread();
  HandleMark hm(thread);
  int locs_buffer_size = _sites_count * (relocInfo::length_limit + sizeof(relocInfo));


  // Allocate enough space in the stub section for the static call
  // stubs.  Stubs have extra relocs but they are managed by the stub
  // section itself so they don't need to be accounted for in the
  // locs_buffer above.
  int stubs_size = estimate_stubs_size(stream, JVMCI_CHECK_OK);

  assert((CodeBuffer::SECT_INSTS == CodeBuffer::SECT_STUBS - 1) &&
         (CodeBuffer::SECT_CONSTS == CodeBuffer::SECT_INSTS - 1), "sections order: consts, insts, stubs");
  // buffer content: [constants + code_align] + [code + stubs_align] + [stubs]
  int total_size = align_up(_constants_size, buffer.insts()->alignment()) +
                   align_up(_code_size, buffer.stubs()->alignment()) +
                   stubs_size;

  if (total_size > JVMCINMethodSizeLimit) {
    return JVMCI::code_too_large;
  }

  buffer.initialize(total_size, locs_buffer_size);
  if (buffer.blob() == nullptr) {
    return JVMCI::cache_full;
  }
  buffer.initialize_stubs_size(stubs_size);
  buffer.initialize_consts_size(_constants_size);

  _debug_recorder = new DebugInformationRecorder(_oop_recorder);
  _debug_recorder->set_oopmaps(new OopMapSet());

  buffer.initialize_oop_recorder(_oop_recorder);

  // copy the constant data into the newly created CodeBuffer
  address end_data = _constants->start() + _constants_size;
  JVMCIObject data_section = jvmci_env()->get_HotSpotCompiledCode_dataSection(compiled_code);
  JVMCIENV->copy_bytes_to(data_section, (jbyte*) _constants->start(), 0, _constants_size);
  _constants->set_end(end_data);

  // copy the code into the newly created CodeBuffer
  address end_pc = _instructions->start() + _code_size;
  guarantee(_instructions->allocates2(end_pc), "initialize should have reserved enough space for all the code");

  JVMCIPrimitiveArray code = jvmci_env()->get_HotSpotCompiledCode_targetCode(compiled_code);
  JVMCIENV->copy_bytes_to(code, (jbyte*) _instructions->start(), 0, _code_size);
  _instructions->set_end(end_pc);


  u2 length = stream->read_u2("dataSectionPatches:length");
  for (int i = 0; i < length; i++) {
    address dest = _constants->start() + stream->read_u4("patch:pcOffset");
    u1 tag = stream->read_u1("tag");

    switch (tag) {
      case PATCH_METHOD:
      case PATCH_KLASS: {
        *((void**) dest) = record_metadata_reference(_constants, dest, stream, tag, JVMCI_CHECK_OK);
        break;
      }
      case PATCH_NARROW_KLASS: {
#ifdef _LP64
        *((narrowKlass*) dest) = record_narrow_metadata_reference(_constants, dest, stream, tag, JVMCI_CHECK_OK);
#else
        JVMCI_ERROR_OK("unexpected compressed Klass* in 32-bit mode");
#endif
        break;
      }
      case PATCH_OBJECT_ID:
      case PATCH_OBJECT_ID2:
      case PATCH_NARROW_OBJECT_ID:
      case PATCH_NARROW_OBJECT_ID2:
      case PATCH_JOBJECT:
      case PATCH_NARROW_JOBJECT: {
        bool narrow = tag == PATCH_NARROW_OBJECT_ID || tag == PATCH_NARROW_OBJECT_ID2  || tag == PATCH_NARROW_JOBJECT;
        u1 read_tag = as_read_oop_tag(stream, tag, JVMCI_CHECK_OK);
        record_oop_patch(stream, dest, read_tag, narrow, JVMCI_CHECK_OK);
        break;
      }
      default: {
        JVMCI_ERROR_OK("invalid constant tag: %d%s", tag, stream->context());
        break;
      }
    }
  }

  jint last_pc_offset = -1;
  for (int i = 0; i < _sites_count; i++) {
    u4 pc_offset = stream->read_s4("site:pcOffset");
    u1 tag = stream->read_u1("tag");
    switch (tag) {
      case SITE_FOREIGN_CALL:
      case SITE_FOREIGN_CALL_NO_DEBUG_INFO:
      case SITE_CALL: {
        site_Call(buffer, tag, pc_offset, stream, JVMCI_CHECK_OK);
        break;
      }
      case SITE_SAFEPOINT:
      case SITE_IMPLICIT_EXCEPTION:
      case SITE_IMPLICIT_EXCEPTION_DISPATCH: {
        site_Safepoint(buffer, pc_offset, stream, tag, JVMCI_CHECK_OK);
        break;
      }
      case SITE_INFOPOINT: {
        site_Infopoint(buffer, pc_offset, stream, JVMCI_CHECK_OK);
        break;
      }
      case SITE_MARK: {
        site_Mark(buffer, pc_offset, stream, JVMCI_CHECK_OK);
        break;
      }
      case SITE_DATA_PATCH: {
        site_DataPatch(buffer, pc_offset, stream, JVMCI_CHECK_OK);
        break;
      }
      case SITE_EXCEPTION_HANDLER: {
        site_ExceptionHandler(pc_offset, stream);
        break;
      }
      default: {
        JVMCI_ERROR_OK("unexpected site tag at " INTPTR_FORMAT ": %d", p2i(stream->pos() - 1), tag);
      }
    }

    last_pc_offset = pc_offset;

    if ((i % 32 == 0) && SafepointMechanism::should_process(thread)) {
      // Force a safepoint to mitigate pause time installing large code
      ThreadToNativeFromVM ttnfv(thread);
    }
  }

  if (is_set(code_flags, HCC_HAS_COMMENTS)) {
    u2 length = stream->read_u2("comments:length");
    for (int i = 0; i < length; i++) {
      u4 pc_offset = stream->read_u4("comment:pcOffset");
      const char* text = stream->read_utf8("comment:text", JVMCI_CHECK_OK);
#ifndef PRODUCT
      buffer.block_comment(pc_offset, text);
#endif
    }
  }
  if (_has_auto_box) {
    JavaThread* THREAD = thread; // For exception macros.
    JVMCI::ensure_box_caches_initialized(CHECK_(JVMCI::ok));
  }
  return JVMCI::ok;
}

void CodeInstaller::record_oop_patch(HotSpotCompiledCodeStream* stream, address dest, u1 read_tag, bool narrow, JVMCI_TRAPS) {
  Handle obj = read_oop(stream, read_tag, JVMCI_CHECK);
  jobject value = JNIHandles::make_local(obj());
  int oop_index = _oop_recorder->find_index(value);
  if (narrow) {
#ifdef _LP64
    _constants->relocate(dest, oop_Relocation::spec(oop_index), relocInfo::narrow_oop_in_const);
#else
    JVMCI_ERROR("unexpected compressed oop in 32-bit mode");
#endif
  } else {
    _constants->relocate(dest, oop_Relocation::spec(oop_index));
  }
}

void CodeInstaller::site_ExceptionHandler(jint pc_offset, HotSpotCompiledCodeStream* stream) {
  u4 handler_offset = stream->read_u4("site:handlerPos");

  // Subtable header
  _exception_handler_table.add_entry(HandlerTableEntry(1, pc_offset, 0));

  // Subtable entry
  _exception_handler_table.add_entry(HandlerTableEntry(-1, handler_offset, 0));
}

void CodeInstaller::read_virtual_objects(HotSpotCompiledCodeStream* stream, JVMCI_TRAPS) {
  u2 length = stream->read_u2("virtualObjects:length");
  if (length == 0) {
    return;
  }
  GrowableArray<ScopeValue*> *objects = new GrowableArray<ScopeValue*>(length, length, nullptr);
  stream->set_virtual_objects(objects);
  // Create the unique ObjectValues
  JavaThread* thread = stream->thread();
  for (int id = 0; id < length; id++) {
    Klass* klass = stream->read_klass("type");
    bool is_auto_box = stream->read_bool("isAutoBox");
    if (is_auto_box) {
      _has_auto_box = true;
    }
    oop javaMirror = klass->java_mirror();
    ScopeValue *klass_sv = new ConstantOopWriteValue(JNIHandles::make_local(javaMirror));
    ObjectValue* sv = is_auto_box ? new AutoBoxObjectValue(id, klass_sv) : new ObjectValue(id, klass_sv);
    objects->at_put(id, sv);
  }
  // All the values which could be referenced by the VirtualObjects
  // exist, so now describe all the VirtualObjects themselves.
  for (int id = 0; id < length; id++) {
    record_object_value(objects->at(id)->as_ObjectValue(), stream, JVMCI_CHECK);
  }
  _debug_recorder->dump_object_pool(objects);

  stream->set_virtual_objects(objects);
}

int CodeInstaller::map_jvmci_bci(int bci) {
  if (bci < 0) {
    switch (bci) {
      case BEFORE_BCI: return BeforeBci;
      case AFTER_BCI: return AfterBci;
      case UNWIND_BCI: return UnwindBci;
      case AFTER_EXCEPTION_BCI: return AfterExceptionBci;
      case UNKNOWN_BCI: return UnknownBci;
      case INVALID_FRAMESTATE_BCI: return InvalidFrameStateBci;
    }
    ShouldNotReachHere();
  }
  return bci;
}

void CodeInstaller::record_scope(jint pc_offset, HotSpotCompiledCodeStream* stream, u1 debug_info_flags, bool full_info, bool is_mh_invoke, bool return_oop, JVMCI_TRAPS) {
  if (full_info) {
    read_virtual_objects(stream, JVMCI_CHECK);
  }
  if (is_set(debug_info_flags, DI_HAS_FRAMES)) {
    u2 depth = stream->read_u2("depth");
    for (int i = 0; i < depth; i++) {
      Thread* thread = Thread::current();
      methodHandle method(thread, stream->read_method("method"));
      jint bci = map_jvmci_bci(stream->read_s4("bci"));
      if (bci == BEFORE_BCI) {
        bci = SynchronizationEntryBCI;
      }

      JVMCI_event_2("Recording scope pc_offset=%d bci=%d method=%s", pc_offset, bci, method->name_and_sig_as_C_string());

      bool reexecute = false;
      bool rethrow_exception = false;

      DebugToken* locals_token = nullptr;
      DebugToken* stack_token = nullptr;
      DebugToken* monitors_token = nullptr;

      if (full_info) {
        u1 frame_flags = stream->read_u1("flags");
        rethrow_exception = is_set(frame_flags, DIF_RETHROW_EXCEPTION);

        if (bci >= 0) {
          reexecute = !is_set(frame_flags, DIF_DURING_CALL);
        }

        GrowableArray<ScopeValue*>* locals = read_local_or_stack_values(stream, frame_flags, true, JVMCI_CHECK);
        GrowableArray<ScopeValue*>* stack = read_local_or_stack_values(stream, frame_flags, false, JVMCI_CHECK);
        GrowableArray<MonitorValue*>* monitors = read_monitor_values(stream, frame_flags, JVMCI_CHECK);

        locals_token = _debug_recorder->create_scope_values(locals);
        stack_token = _debug_recorder->create_scope_values(stack);
        monitors_token = _debug_recorder->create_monitor_values(monitors);
      }

      // has_ea_local_in_scope and arg_escape should be added to JVMCI
      const bool has_ea_local_in_scope = false;
      const bool arg_escape            = false;
      _debug_recorder->describe_scope(pc_offset, method, nullptr, bci, reexecute, rethrow_exception, is_mh_invoke, return_oop,
                                      has_ea_local_in_scope, arg_escape,
                                      locals_token, stack_token, monitors_token);
    }
  }
  if (full_info) {
    // Clear the virtual objects as they are specific to one DebugInfo
    stream->set_virtual_objects(nullptr);
  }
}

void CodeInstaller::site_Safepoint(CodeBuffer& buffer, jint pc_offset, HotSpotCompiledCodeStream* stream, u1 tag, JVMCI_TRAPS) {
  u1 flags = stream->read_u1("debugInfo:flags");
  OopMap *map = create_oop_map(stream, flags, JVMCI_CHECK);
  _debug_recorder->add_safepoint(pc_offset, map);
  record_scope(pc_offset, stream, flags, true, JVMCI_CHECK);
  _debug_recorder->end_safepoint(pc_offset);
  if (_orig_pc_offset < 0) {
    JVMCI_ERROR("method contains safepoint, but has no deopt rescue slot");
  }
  if (tag == SITE_IMPLICIT_EXCEPTION_DISPATCH) {
    jint dispatch_offset = stream->read_s4("dispatchOffset");
    _implicit_exception_table.append(pc_offset, dispatch_offset);
  } else if (tag == SITE_IMPLICIT_EXCEPTION) {
    _implicit_exception_table.add_deoptimize(pc_offset);
  }
}

void CodeInstaller::site_Infopoint(CodeBuffer& buffer, jint pc_offset, HotSpotCompiledCodeStream* stream, JVMCI_TRAPS) {
  u1 flags = stream->read_u1("debugInfo:flags");
  _debug_recorder->add_non_safepoint(pc_offset);
  record_scope(pc_offset, stream, flags, false, JVMCI_CHECK);
  _debug_recorder->end_non_safepoint(pc_offset);
}

void CodeInstaller::site_Call(CodeBuffer& buffer, u1 tag, jint pc_offset, HotSpotCompiledCodeStream* stream, JVMCI_TRAPS) {
  JavaThread* thread = stream->thread();
  jlong target = stream->read_u8("target");
  methodHandle method;
  bool direct_call = false;
  if (tag == SITE_CALL) {
    method = methodHandle(thread, (Method*) target);
    assert(Method::is_valid_method(method()), "invalid method");
    direct_call = stream->read_bool("direct");
    if (method.is_null()) {
      JVMCI_THROW(NullPointerException);
    }
  }

  NativeInstruction* inst = nativeInstruction_at(_instructions->start() + pc_offset);
  jint next_pc_offset = CodeInstaller::pd_next_offset(inst, pc_offset, JVMCI_CHECK);

  if (tag != SITE_FOREIGN_CALL_NO_DEBUG_INFO) {
    u1 flags = stream->read_u1("debugInfo:flags");
    OopMap *map = create_oop_map(stream, flags, JVMCI_CHECK);
    _debug_recorder->add_safepoint(next_pc_offset, map);

    if (!method.is_null()) {
      vmIntrinsics::ID iid = method->intrinsic_id();
      bool is_mh_invoke = false;
      if (direct_call) {
        is_mh_invoke = !method->is_static() && (iid == vmIntrinsics::_compiledLambdaForm ||
                (MethodHandles::is_signature_polymorphic(iid) && MethodHandles::is_signature_polymorphic_intrinsic(iid)));
      }
      bool return_oop = method->is_returning_oop();
      record_scope(next_pc_offset, stream, flags, true, is_mh_invoke, return_oop, JVMCI_CHECK);
    } else {
      record_scope(next_pc_offset, stream, flags, true, JVMCI_CHECK);
    }
  }

  if (tag != SITE_CALL) {
    jlong foreign_call_destination = target;
    CodeInstaller::pd_relocate_ForeignCall(inst, foreign_call_destination, JVMCI_CHECK);
  } else {
    CodeInstaller::pd_relocate_JavaMethod(buffer, method, pc_offset, JVMCI_CHECK);
    if (_next_call_type == INVOKESTATIC || _next_call_type == INVOKESPECIAL) {
      // Need a static call stub for transitions from compiled to interpreted.
      if (CompiledDirectCall::emit_to_interp_stub(buffer, _instructions->start() + pc_offset) == nullptr) {
        JVMCI_ERROR("could not emit to_interp stub - code cache is full");
      }
    }
  }

  _next_call_type = INVOKE_INVALID;

  if (tag != SITE_FOREIGN_CALL_NO_DEBUG_INFO) {
    _debug_recorder->end_safepoint(next_pc_offset);
  }
}

void CodeInstaller::site_DataPatch(CodeBuffer& buffer, jint pc_offset, HotSpotCompiledCodeStream* stream, JVMCI_TRAPS) {
  u1 tag = stream->read_u1("tag");
  switch (tag) {
    case PATCH_OBJECT_ID:
    case PATCH_OBJECT_ID2:
    case PATCH_NARROW_OBJECT_ID:
    case PATCH_NARROW_OBJECT_ID2:
    case PATCH_JOBJECT:
    case PATCH_NARROW_JOBJECT: {
      bool narrow = tag == PATCH_NARROW_OBJECT_ID || tag == PATCH_NARROW_OBJECT_ID2  || tag == PATCH_NARROW_JOBJECT;
      u1 read_tag = as_read_oop_tag(stream, tag, JVMCI_CHECK);
      Handle obj = read_oop(stream, read_tag, JVMCI_CHECK);
      pd_patch_OopConstant(pc_offset, obj, narrow, JVMCI_CHECK);
      break;
    }
    case PATCH_METHOD:
    case PATCH_KLASS:
    case PATCH_NARROW_KLASS: {
      pd_patch_MetaspaceConstant(pc_offset, stream, tag, JVMCI_CHECK);
      break;
    }
    case PATCH_DATA_SECTION_REFERENCE: {
      int data_offset = stream->read_u4("data:offset");
      if (0 <= data_offset && data_offset < _constants_size) {
        if (!is_aligned(data_offset, CompilerToVM::Data::get_data_section_item_alignment())) {
          JVMCI_ERROR("data offset 0x%x is not %d-byte aligned%s", data_offset, relocInfo::addr_unit(), stream->context());
        }
        pd_patch_DataSectionReference(pc_offset, data_offset, JVMCI_CHECK);
      } else {
        JVMCI_ERROR("data offset 0x%x points outside data section (size 0x%x)%s", data_offset, _constants_size, stream->context());
      }
      break;
    }
    default: {
      JVMCI_ERROR("unknown data patch tag: %d%s", tag, stream->context());
    }
  }
}

void CodeInstaller::site_Mark(CodeBuffer& buffer, jint pc_offset, HotSpotCompiledCodeStream* stream, JVMCI_TRAPS) {
  u1 id = stream->read_u1("mark:id");
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
    case DEOPT_MH_HANDLER_ENTRY:
      _offsets.set_value(CodeOffsets::DeoptMH, pc_offset);
      break;
    case FRAME_COMPLETE:
      _offsets.set_value(CodeOffsets::Frame_Complete, pc_offset);
      break;
    case ENTRY_BARRIER_PATCH:
      _nmethod_entry_patch_offset = pc_offset;
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
      pd_relocate_poll(pc, id, JVMCI_CHECK);
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
    case VERIFY_OOPS:
    case VERIFY_OOP_BITS:
    case VERIFY_OOP_MASK:
    case VERIFY_OOP_COUNT_ADDRESS:
      break;
    default:
      JVMCI_ERROR("invalid mark id: %d%s", id, stream->context());
      break;
  }
}
