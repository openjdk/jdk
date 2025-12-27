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

#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoadInfo.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "jfr/jfrEvents.hpp"
#include "jni.h"
#include "jvm.h"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/fieldStreams.inline.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/unsafe.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/reflection.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/vm_version.hpp"
#include "runtime/vmOperations.hpp"
#include "sanitizers/ub.hpp"
#include "services/threadService.hpp"
#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/macros.hpp"

/**
 * Implementation of the jdk.internal.misc.Unsafe class
 */

#define MAX_OBJECT_SIZE \
  ( arrayOopDesc::base_offset_in_bytes(T_DOUBLE) \
    + ((julong)max_jint * sizeof(double)) )

#define UNSAFE_ENTRY(result_type, header) \
  JVM_ENTRY(static result_type, header)

#define UNSAFE_LEAF(result_type, header) \
  JVM_LEAF(static result_type, header)

// All memory access methods (e.g. getInt, copyMemory) must use this macro.
// (Except, methods which read or write managed pointers use another path.)
// We call these methods "scoped" methods, as access to these methods is
// typically governed by a "scope" (a MemorySessionImpl object), and no
// access is allowed when the scope is no longer alive.
//
// Closing a scope object (cf. scopedMemoryAccess.cpp) can install
// an async exception during a safepoint. When that happens,
// scoped methods are not allowed to touch the underlying memory (as that
// memory might have been released). Therefore, when entering a scoped method
// we check if an async exception has been installed, and return immediately
// if that is the case.
//
// As a rule, we disallow safepoints in the middle of a scoped method.
// If an async exception handshake were installed in such a safepoint,
// memory access might still occur before the handshake is honored by
// the accessing thread.
//
// Corollary: as threads in native state are considered to be at a safepoint,
// scoped methods must NOT be executed while in the native thread state.
// Because of this, there can be no UNSAFE_LEAF_SCOPED.
#define UNSAFE_ENTRY_SCOPED(result_type, header) \
  JVM_ENTRY(static result_type, header) \
  if (thread->has_async_exception_condition()) {return (result_type)0;}

#define UNSAFE_END JVM_END


static inline void* addr_from_java(jlong addr) {
  // This assert fails in a variety of ways on 32-bit systems.
  // It is impossible to predict whether native code that converts
  // pointers to longs will sign-extend or zero-extend the addresses.
  //assert(addr == (uintptr_t)addr, "must not be odd high bits");
  return (void*)(uintptr_t)addr;
}

static inline jlong addr_to_java(void* p) {
  assert(p == (void*)(uintptr_t)p, "must not be odd high bits");
  return (uintptr_t)p;
}


// Note: The VM's obj_field and related accessors use byte-scaled
// ("unscaled") offsets, just as the unsafe methods do.

// However, the method Unsafe.fieldOffset explicitly declines to
// guarantee this.  The field offset values manipulated by the Java user
// through the Unsafe API are opaque cookies that just happen to be byte
// offsets.  We represent this state of affairs by passing the cookies
// through conversion functions when going between the VM and the Unsafe API.
// The conversion functions just happen to be no-ops at present.

static inline jlong field_offset_to_byte_offset(jlong field_offset) {
  return field_offset;
}

static inline int field_offset_from_byte_offset(int byte_offset) {
  return byte_offset;
}

static inline void assert_field_offset_sane(oop p, jlong field_offset) {
#ifdef ASSERT
  jlong byte_offset = field_offset_to_byte_offset(field_offset);

  if (p != nullptr) {
    assert(byte_offset >= 0 && byte_offset <= (jlong)MAX_OBJECT_SIZE, "sane offset");
    if (byte_offset == (jint)byte_offset) {
      void* ptr_plus_disp = cast_from_oop<address>(p) + byte_offset;
      assert(p->field_addr<void>((jint)byte_offset) == ptr_plus_disp,
             "raw [ptr+disp] must be consistent with oop::field_addr");
    }
    jlong p_size = HeapWordSize * (jlong)(p->size());
    assert(byte_offset < p_size, "Unsafe access: offset " INT64_FORMAT " > object's size " INT64_FORMAT, (int64_t)byte_offset, (int64_t)p_size);
  }
#endif
}

static inline void* index_oop_from_field_offset_long(oop p, jlong field_offset) {
  assert_field_offset_sane(p, field_offset);
  uintptr_t base_address = cast_from_oop<uintptr_t>(p);
  uintptr_t byte_offset  = (uintptr_t)field_offset_to_byte_offset(field_offset);
  return (void*)(base_address + byte_offset);
}

// Externally callable versions:
// (Use these in compiler intrinsics which emulate unsafe primitives.)
jlong Unsafe_field_offset_to_byte_offset(jlong field_offset) {
  return field_offset;
}
jlong Unsafe_field_offset_from_byte_offset(jlong byte_offset) {
  return byte_offset;
}


///// Data read/writes on the Java heap and in native (off-heap) memory

/**
 * Helper class to wrap memory accesses in JavaThread::doing_unsafe_access()
 */
class GuardUnsafeAccess {
  JavaThread* _thread;

public:
  GuardUnsafeAccess(JavaThread* thread) : _thread(thread) {
    // native/off-heap access which may raise SIGBUS if accessing
    // memory mapped file data in a region of the file which has
    // been truncated and is now invalid.
    _thread->set_doing_unsafe_access(true);
  }

  ~GuardUnsafeAccess() {
    _thread->set_doing_unsafe_access(false);
  }
};

/**
 * Helper macro to implement variable-size operations in polymorphic
 * methods that manipulate primitive values for Unsafe.
 * Executes the body (whatever that may be) with var_t defined as
 * an unsigned integral type of size 1, 2, 4, or 8, and the same
 * bit-size as the basic type bt.
 *
 * The processing for T_BYTE and T_BOOLEAN are the same, as are
 * T_LONG and T_DOUBLE, T_INT and T_FLOAT, and TSHORT and T_CHAR.
 * It is up to the caller to ensure that no other T-values appear
 * here, and that special handling of types (e.g., boolean fixups)
 * is performed elsewhere.
 */
#define TYPE_SIZE_SWITCH(bt, var_t, body) {                             \
    switch ((bt) & vmIntrinsics::PRIMITIVE_SIZE_MASK) {                 \
    case 0:  { using var_t = jubyte;   {body;}  break; }                \
    case 1:  { using var_t = jushort;  {body;}  break; }                \
    case 2:  { using var_t = juint;    {body;}  break; }                \
    default: { using var_t = julong;   {body;}  break; }                \
    }                                                                   \
  } /*end*/

static size_t bt_size(BasicType bt) {
  return vmIntrinsics::primitive_type_size(bt);
}

template<typename val_t>
static julong maybe_pad_with_garbage(val_t v) {
  julong bits = v;
#ifdef ASSERT
  // inject some garbage as padding, to stress-test surrounding layers
  // e.g., 0x42 pads up as 0xFFFFFFCE00000042
  if (sizeof(val_t) <= sizeof(bits)/2) {
    bits ^= ~bits << (sizeof(bits)/2 * BitsPerByte);
  }
#endif //ASSERT
  return bits;
}

/**
 * Helper class for accessing memory.
 *
 * Normalizes values and wraps accesses in
 * JavaThread::doing_unsafe_access() if needed.
 */
class MemoryAccess : StackObj {
  JavaThread* _thread;
  oop _obj;
  ptrdiff_t _offset;
  BasicType _basic_type;

  // Resolves and returns the address of the memory access.
  // This raw memory access may fault, so we make sure it happens within the
  // guarded scope by making the access volatile at least. Since the store
  // of Thread::set_doing_unsafe_access() is also volatile, these accesses
  // can not be reordered by the compiler. Therefore, if the access triggers
  // a fault, we will know that Thread::doing_unsafe_access() returns true.
  template<typename T>
  volatile T* addr() {
    void* addr = index_oop_from_field_offset_long(_obj, _offset);
    return static_cast<volatile T*>(addr);
  }

  static julong get_via_bytes(size_t size, address addr) {
    switch (size) {
    case 1:  return *(jubyte*) addr;
    case 2:  return Bytes::get_native_u2(addr);
    case 4:  return Bytes::get_native_u4(addr);
    default: return Bytes::get_native_u8(addr);
    }
  }

  static void put_via_bytes(size_t size, address addr, julong x) {
    switch (size) {
    case 1:  *(jubyte*) addr =          (jubyte) x;
    case 2:  Bytes::put_native_u2(addr, (jushort)x);
    case 4:  Bytes::put_native_u4(addr, (juint)  x);
    default: Bytes::put_native_u8(addr, x);
    }
  }

  // Note: We do not normalize booleans at this level.  That is done
  // by strongly-typed VM access methods like oopDesc::bool_field, but
  // not by this C++ code, because it is not strongly typed.  Instead,
  // the next layer up, the Java class Unsafe, handles the sanitizing
  // of booleans.  See bool2byte and byte2bool in that class.  With
  // this division of labor, the unsafe native layer (with related JIT
  // intrinsics) can concentrate on correctly sized and sequenced
  // access, without adding in extra data type requirements.

public:
  MemoryAccess(JavaThread* thread, jobject obj, jlong offset, int basic_type)
    : _thread(thread),
      _obj(JNIHandles::resolve(obj)), _offset((ptrdiff_t)offset),
      _basic_type((BasicType)basic_type)
  {
    //assert_field_offset_sane(_obj, offset); -- done later in addr()
    assert(is_java_primitive(_basic_type), "caller resp");
    assert((1 << ((int)_basic_type & vmIntrinsics::PRIMITIVE_SIZE_MASK))
           == type2aelembytes(_basic_type), "must be");
    assert((int)bt_size(_basic_type) == type2aelembytes(_basic_type), "");
  }

  julong get() {
    GuardUnsafeAccess guard(_thread);
    TYPE_SIZE_SWITCH(_basic_type, val_t, {
        val_t v = *addr<val_t>();
        return maybe_pad_with_garbage(v);
      });
  }

  // we use this method at some places for writing to 0 e.g. to cause a crash;
  // ubsan does not know that this is the desired behavior
  ATTRIBUTE_NO_UBSAN
  void put(julong x) {
    GuardUnsafeAccess guard(_thread);
    TYPE_SIZE_SWITCH(_basic_type, val_t, {
        *addr<val_t>() = (val_t)x;
      });
  }

  julong get_unaligned() {
    GuardUnsafeAccess guard(_thread);
    TYPE_SIZE_SWITCH(_basic_type, val_t, {
        address va = (address)addr<val_t>();
        val_t v = get_via_bytes(sizeof(val_t), va);
        return maybe_pad_with_garbage(v);
      });
  }

  void put_unaligned(julong x) {
    GuardUnsafeAccess guard(_thread);
    TYPE_SIZE_SWITCH(_basic_type, val_t, {
        address va = (address)addr<val_t>();
        put_via_bytes(sizeof(val_t), va, x);
      });
  }

  julong get_volatile() {
    GuardUnsafeAccess guard(_thread);
    TYPE_SIZE_SWITCH(_basic_type, val_t, {
        val_t v = RawAccess<MO_SEQ_CST>::load(addr<val_t>());
        return maybe_pad_with_garbage(v);
      });
  }

  void put_volatile(julong x) {
    GuardUnsafeAccess guard(_thread);
    TYPE_SIZE_SWITCH(_basic_type, val_t, {
        RawAccess<MO_SEQ_CST>::store(addr<val_t>(), (val_t)x);
      });
  }
};

// These functions allow a null base pointer with an arbitrary address.
// But if the base pointer is non-null, the offset should make some sense.
// That is, it should be in the range [0, MAX_OBJECT_SIZE].
UNSAFE_ENTRY(jobject, Unsafe_GetReferenceMO(JNIEnv *env, jobject unsafe,
                                            jbyte memory_order,
                                            jobject obj, jlong offset)) {
  oop p = JNIHandles::resolve(obj);
  assert_field_offset_sane(p, offset);
  assert(vmIntrinsics::is_valid_memory_order(memory_order,
                                             vmIntrinsics::UNSAFE_MO_RELEASE),
         "bad MO bits from Java: 0x%02x", memory_order & 0xFF);
  oop v;
  switch (memory_order) {
    case vmIntrinsics::UNSAFE_MO_PLAIN:
      v = HeapAccess<ON_UNKNOWN_OOP_REF>::oop_load_at(p, offset);
      break;

    default:
      // MO_VOLATILE is a conservative approximation for acquire & release
      v = HeapAccess<MO_SEQ_CST | ON_UNKNOWN_OOP_REF>::oop_load_at(p, offset);
      break;
  }
  return JNIHandles::make_local(THREAD, v);
} UNSAFE_END

UNSAFE_ENTRY(void, Unsafe_PutReferenceMO(JNIEnv *env, jobject unsafe,
                                         jbyte memory_order,
                                         jobject obj, jlong offset, jobject x_h)) {
  oop x = JNIHandles::resolve(x_h);
  oop p = JNIHandles::resolve(obj);
  assert_field_offset_sane(p, offset);
  assert(vmIntrinsics::is_valid_memory_order(memory_order,
                                             vmIntrinsics::UNSAFE_MO_ACQUIRE),
         "bad MO bits from Java: 0x%02x", memory_order & 0xFF);
  switch (memory_order) {
    case vmIntrinsics::UNSAFE_MO_PLAIN:
      HeapAccess<ON_UNKNOWN_OOP_REF>::oop_store_at(p, offset, x);
      break;

    default:
      // MO_VOLATILE is a conservative approximation for acquire & release
      HeapAccess<MO_SEQ_CST | ON_UNKNOWN_OOP_REF>::oop_store_at(p, offset, x);
      break;
  }
} UNSAFE_END

UNSAFE_ENTRY(jobject, Unsafe_GetUncompressedObject(JNIEnv *env, jobject unsafe,
                                                   jlong addr)) {
  oop v = *(oop*) (address) addr;
  return JNIHandles::make_local(THREAD, v);
} UNSAFE_END

UNSAFE_ENTRY_SCOPED(jlong, Unsafe_GetPrimitiveBitsMO(JNIEnv *env, jobject unsafe,
                                                     jbyte memory_order, jbyte basic_type,
                                                     jobject obj, jlong offset)) {
  assert(vmIntrinsics::is_valid_memory_order(memory_order & ~vmIntrinsics::UNSAFE_MO_UNALIGNED,
                                             vmIntrinsics::UNSAFE_MO_RELEASE),
         "bad MO bits from Java: 0x%02x", memory_order & 0xFF);
  assert(vmIntrinsics::is_valid_primitive_type(basic_type),
         "bad BT bits from Java: 0x%02x", basic_type & 0xFF);
  julong result;
  auto ma = MemoryAccess(thread, obj, offset, basic_type);
  switch (memory_order) {
  case vmIntrinsics::UNSAFE_MO_PLAIN | vmIntrinsics::UNSAFE_MO_UNALIGNED:
    if (!UseUnalignedAccesses && (offset & (bt_size((BasicType)basic_type) - 1)) != 0) {
      result = ma.get_unaligned();
      break;
    }
    // else fall through

  case vmIntrinsics::UNSAFE_MO_PLAIN:
    // Note:  This says, "plain" but there is in fact a volatile load inside.
    result = ma.get();
    break;

  default:
    // MO_VOLATILE is a conservative approximation for acquire & release
    result = ma.get_volatile();
  }
  return result;
} UNSAFE_END

UNSAFE_ENTRY_SCOPED(void, Unsafe_PutPrimitiveBitsMO(JNIEnv *env, jobject unsafe,
                                                    jbyte memory_order, jbyte basic_type,
                                                    jobject obj, jlong offset, jlong x)) {
  assert(vmIntrinsics::is_valid_memory_order(memory_order & ~vmIntrinsics::UNSAFE_MO_UNALIGNED,
                                             vmIntrinsics::UNSAFE_MO_ACQUIRE),
         "bad MO bits from Java: 0x%02x", memory_order & 0xFF);
  assert(vmIntrinsics::is_valid_primitive_type(basic_type),
         "bad BT bits from Java: 0x%02x", basic_type & 0xFF);
  auto ma = MemoryAccess(thread, obj, offset, basic_type);
  switch (memory_order) {
  case vmIntrinsics::UNSAFE_MO_PLAIN | vmIntrinsics::UNSAFE_MO_UNALIGNED:
    if (!UseUnalignedAccesses && (offset & (bt_size((BasicType)basic_type) - 1)) != 0) {
      ma.put_unaligned(x);
      break;
    }
    // else fall through

  case vmIntrinsics::UNSAFE_MO_PLAIN:
    // Note:  This says, "plain" but there is in fact a volatile store inside.
    ma.put(x);
    break;

  default:
    // MO_VOLATILE is a conservative approximation for acquire & release
    ma.put_volatile(x);
    break;
  }
} UNSAFE_END

UNSAFE_LEAF(void, Unsafe_FullFence(JNIEnv *env, jobject unsafe)) {
  OrderAccess::fence();
} UNSAFE_END

////// Allocation requests

UNSAFE_ENTRY(jobject, Unsafe_AllocateInstance(JNIEnv *env, jobject unsafe, jclass cls)) {
  JvmtiVMObjectAllocEventCollector oam;
  instanceOop i = InstanceKlass::allocate_instance(JNIHandles::resolve_non_null(cls), CHECK_NULL);
  return JNIHandles::make_local(THREAD, i);
} UNSAFE_END

UNSAFE_LEAF(jlong, Unsafe_AllocateMemory0(JNIEnv *env, jobject unsafe, jlong size)) {
  size_t sz = (size_t)size;

  assert(is_aligned(sz, HeapWordSize), "sz not aligned");

  void* x = os::malloc(sz, mtOther);

  return addr_to_java(x);
} UNSAFE_END

UNSAFE_LEAF(jlong, Unsafe_ReallocateMemory0(JNIEnv *env, jobject unsafe, jlong addr, jlong size)) {
  void* p = addr_from_java(addr);
  size_t sz = (size_t)size;

  assert(is_aligned(sz, HeapWordSize), "sz not aligned");

  void* x = os::realloc(p, sz, mtOther);

  return addr_to_java(x);
} UNSAFE_END

UNSAFE_LEAF(void, Unsafe_FreeMemory0(JNIEnv *env, jobject unsafe, jlong addr)) {
  void* p = addr_from_java(addr);

  os::free(p);
} UNSAFE_END

UNSAFE_ENTRY_SCOPED(void, Unsafe_SetMemory0(JNIEnv *env, jobject unsafe, jobject obj, jlong offset, jlong size, jbyte value)) {
  size_t sz = (size_t)size;

  oop base = JNIHandles::resolve(obj);
  void* p = index_oop_from_field_offset_long(base, offset);

  {
    GuardUnsafeAccess guard(thread);
    if (StubRoutines::unsafe_setmemory() != nullptr) {
      MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXExec, thread));
      StubRoutines::UnsafeSetMemory_stub()(p, sz, value);
    } else {
      Copy::fill_to_memory_atomic(p, sz, value);
    }
  }
} UNSAFE_END

UNSAFE_ENTRY_SCOPED(void, Unsafe_CopyMemory0(JNIEnv *env, jobject unsafe, jobject srcObj, jlong srcOffset, jobject dstObj, jlong dstOffset, jlong size)) {
  size_t sz = (size_t)size;

  oop srcp = JNIHandles::resolve(srcObj);
  oop dstp = JNIHandles::resolve(dstObj);

  void* src = index_oop_from_field_offset_long(srcp, srcOffset);
  void* dst = index_oop_from_field_offset_long(dstp, dstOffset);
  {
    GuardUnsafeAccess guard(thread);
    if (StubRoutines::unsafe_arraycopy() != nullptr) {
      MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXExec, thread));
      StubRoutines::UnsafeArrayCopy_stub()(src, dst, sz);
    } else {
      Copy::conjoint_memory_atomic(src, dst, sz);
    }
  }
} UNSAFE_END

UNSAFE_ENTRY_SCOPED(void, Unsafe_CopySwapMemory0(JNIEnv *env, jobject unsafe, jobject srcObj, jlong srcOffset, jobject dstObj, jlong dstOffset, jlong size, jlong elemSize)) {
  size_t sz = (size_t)size;
  size_t esz = (size_t)elemSize;

  oop srcp = JNIHandles::resolve(srcObj);
  oop dstp = JNIHandles::resolve(dstObj);

  address src = (address)index_oop_from_field_offset_long(srcp, srcOffset);
  address dst = (address)index_oop_from_field_offset_long(dstp, dstOffset);

  {
    GuardUnsafeAccess guard(thread);
    Copy::conjoint_swap(src, dst, sz, esz);
  }
} UNSAFE_END

UNSAFE_LEAF (void, Unsafe_WriteBack0(JNIEnv *env, jobject unsafe, jlong line)) {
  assert(VM_Version::supports_data_cache_line_flush(), "should not get here");
#ifdef ASSERT
  if (TraceMemoryWriteback) {
    tty->print_cr("Unsafe: writeback 0x%p", addr_from_java(line));
  }
#endif

  MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXExec, Thread::current()));
  assert(StubRoutines::data_cache_writeback() != nullptr, "sanity");
  (StubRoutines::DataCacheWriteback_stub())(addr_from_java(line));
} UNSAFE_END

static void doWriteBackSync0(bool is_pre)
{
  MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXExec, Thread::current()));
  assert(StubRoutines::data_cache_writeback_sync() != nullptr, "sanity");
  (StubRoutines::DataCacheWritebackSync_stub())(is_pre);
}

UNSAFE_LEAF (void, Unsafe_WriteBackPreSync0(JNIEnv *env, jobject unsafe)) {
  assert(VM_Version::supports_data_cache_line_flush(), "should not get here");
#ifdef ASSERT
  if (TraceMemoryWriteback) {
      tty->print_cr("Unsafe: writeback pre-sync");
  }
#endif

  doWriteBackSync0(true);
} UNSAFE_END

UNSAFE_LEAF (void, Unsafe_WriteBackPostSync0(JNIEnv *env, jobject unsafe)) {
  assert(VM_Version::supports_data_cache_line_flush(), "should not get here");
#ifdef ASSERT
  if (TraceMemoryWriteback) {
    tty->print_cr("Unsafe: writeback pre-sync");
  }
#endif

  doWriteBackSync0(false);
} UNSAFE_END

////// Random queries

// Finds the object field offset of a field with the matching name, or an error code
// Error code -1 is not found, -2 is static field
static jlong find_known_instance_field_offset(jclass clazz, jstring name, TRAPS) {
  assert(clazz != nullptr, "clazz must not be null");
  assert(name != nullptr, "name must not be null");

  ResourceMark rm(THREAD);
  char *utf_name = java_lang_String::as_utf8_string(JNIHandles::resolve_non_null(name));

  InstanceKlass* k = java_lang_Class::as_InstanceKlass(JNIHandles::resolve_non_null(clazz));

  jint offset = -1; // Not found
  for (JavaFieldStream fs(k); !fs.done(); fs.next()) {
    Symbol *name = fs.name();
    if (name->equals(utf_name)) {
      if (!fs.access_flags().is_static()) {
        offset = fs.offset();
      } else {
        offset = -2; // A static field
      }
      break;
    }
  }
  if (offset < 0) {
    return offset; // Error code
  }
  return field_offset_from_byte_offset(offset);
}

static jlong find_field_offset(jobject field, int must_be_static, TRAPS) {
  assert(field != nullptr, "field must not be null");

  oop reflected   = JNIHandles::resolve_non_null(field);
  oop mirror      = java_lang_reflect_Field::clazz(reflected);
  Klass* k        = java_lang_Class::as_Klass(mirror);
  int slot        = java_lang_reflect_Field::slot(reflected);
  int modifiers   = java_lang_reflect_Field::modifiers(reflected);

  if (must_be_static >= 0) {
    int really_is_static = ((modifiers & JVM_ACC_STATIC) != 0);
    if (must_be_static != really_is_static) {
      THROW_0(vmSymbols::java_lang_IllegalArgumentException());
    }
  }

  int offset = InstanceKlass::cast(k)->field_offset(slot);
  return field_offset_from_byte_offset(offset);
}

UNSAFE_ENTRY(jlong, Unsafe_ObjectFieldOffset0(JNIEnv *env, jobject unsafe, jobject field)) {
  return find_field_offset(field, 0, THREAD);
} UNSAFE_END

UNSAFE_ENTRY(jlong, Unsafe_KnownObjectFieldOffset0(JNIEnv *env, jobject unsafe, jclass c, jstring name)) {
  return find_known_instance_field_offset(c, name, THREAD);
} UNSAFE_END

UNSAFE_ENTRY(jlong, Unsafe_StaticFieldOffset0(JNIEnv *env, jobject unsafe, jobject field)) {
  return find_field_offset(field, 1, THREAD);
} UNSAFE_END

UNSAFE_ENTRY(jobject, Unsafe_StaticFieldBase0(JNIEnv *env, jobject unsafe, jobject field)) {
  assert(field != nullptr, "field must not be null");

  // Note:  In this VM implementation, a field address is always a short
  // offset from the base of a klass metaobject.  Thus, the full dynamic
  // range of the return type is never used.  However, some implementations
  // might put the static field inside an array shared by many classes,
  // or even at a fixed address, in which case the address could be quite
  // large.  In that last case, this function would return null, since
  // the address would operate alone, without any base pointer.

  oop reflected   = JNIHandles::resolve_non_null(field);
  oop mirror      = java_lang_reflect_Field::clazz(reflected);
  int modifiers   = java_lang_reflect_Field::modifiers(reflected);

  if ((modifiers & JVM_ACC_STATIC) == 0) {
    THROW_NULL(vmSymbols::java_lang_IllegalArgumentException());
  }

  return JNIHandles::make_local(THREAD, mirror);
} UNSAFE_END

UNSAFE_ENTRY(void, Unsafe_EnsureClassInitialized0(JNIEnv *env, jobject unsafe, jobject clazz)) {
  assert(clazz != nullptr, "clazz must not be null");

  oop mirror = JNIHandles::resolve_non_null(clazz);

  Klass* klass = java_lang_Class::as_Klass(mirror);
  if (klass != nullptr && klass->should_be_initialized()) {
    InstanceKlass* k = InstanceKlass::cast(klass);
    k->initialize(CHECK);
  }
}
UNSAFE_END

UNSAFE_ENTRY(jboolean, Unsafe_ShouldBeInitialized0(JNIEnv *env, jobject unsafe, jobject clazz)) {
  assert(clazz != nullptr, "clazz must not be null");

  oop mirror = JNIHandles::resolve_non_null(clazz);
  Klass* klass = java_lang_Class::as_Klass(mirror);

  if (klass != nullptr && klass->should_be_initialized()) {
    return true;
  }

  return false;
}
UNSAFE_END

static void getBaseAndScale(int& base, int& scale, jclass clazz, TRAPS) {
  assert(clazz != nullptr, "clazz must not be null");

  oop mirror = JNIHandles::resolve_non_null(clazz);
  Klass* k = java_lang_Class::as_Klass(mirror);

  if (k == nullptr || !k->is_array_klass()) {
    THROW(vmSymbols::java_lang_InvalidClassException());
  } else if (k->is_objArray_klass()) {
    base  = arrayOopDesc::base_offset_in_bytes(T_OBJECT);
    scale = heapOopSize;
  } else if (k->is_typeArray_klass()) {
    TypeArrayKlass* tak = TypeArrayKlass::cast(k);
    base  = tak->array_header_in_bytes();
    assert(base == arrayOopDesc::base_offset_in_bytes(tak->element_type()), "array_header_size semantics ok");
    scale = (1 << tak->log2_element_size());
  } else {
    ShouldNotReachHere();
  }
}

UNSAFE_ENTRY(jint, Unsafe_ArrayBaseOffset0(JNIEnv *env, jobject unsafe, jclass clazz)) {
  int base = 0, scale = 0;
  getBaseAndScale(base, scale, clazz, CHECK_0);

  return field_offset_from_byte_offset(base);
} UNSAFE_END


UNSAFE_ENTRY(jint, Unsafe_ArrayIndexScale0(JNIEnv *env, jobject unsafe, jclass clazz)) {
  int base = 0, scale = 0;
  getBaseAndScale(base, scale, clazz, CHECK_0);

  // This VM packs both fields and array elements down to the byte.
  // But watch out:  If this changes, so that array references for
  // a given primitive type (say, T_BOOLEAN) use different memory units
  // than fields, this method MUST return zero for such arrays.
  // For example, the VM used to store sub-word sized fields in full
  // words in the object layout, so that accessors like getByte(Object,int)
  // did not really do what one might expect for arrays.  Therefore,
  // this function used to report a zero scale factor, so that the user
  // would know not to attempt to access sub-word array elements.
  // // Code for unpacked fields:
  // if (scale < wordSize)  return 0;

  // The following allows for a pretty general fieldOffset cookie scheme,
  // but requires it to be linear in byte offset.
  return field_offset_from_byte_offset(scale) - field_offset_from_byte_offset(0);
} UNSAFE_END


static inline void throw_new(JNIEnv *env, const char *ename) {
  jclass cls = env->FindClass(ename);
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    tty->print_cr("Unsafe: cannot throw %s because FindClass has failed", ename);
    return;
  }

  env->ThrowNew(cls, nullptr);
}

static jclass Unsafe_DefineClass_impl(JNIEnv *env, jstring name, jbyteArray data, int offset, int length, jobject loader, jobject pd) {
  // Code lifted from JDK 1.3 ClassLoader.c

  jbyte *body;
  char *utfName = nullptr;
  jclass result = nullptr;
  char buf[128];

  assert(data != nullptr, "Class bytes must not be null");
  assert(length >= 0, "length must not be negative: %d", length);

  if (UsePerfData) {
    ClassLoader::unsafe_defineClassCallCounter()->inc();
  }

  body = NEW_C_HEAP_ARRAY_RETURN_NULL(jbyte, length, mtInternal);
  if (body == nullptr) {
    throw_new(env, "java/lang/OutOfMemoryError");
    return nullptr;
  }

  env->GetByteArrayRegion(data, offset, length, body);
  if (env->ExceptionCheck()) {
    goto free_body;
  }

  if (name != nullptr) {
    uint len = env->GetStringUTFLength(name);
    int unicode_len = env->GetStringLength(name);

    if (len >= sizeof(buf)) {
      utfName = NEW_C_HEAP_ARRAY_RETURN_NULL(char, len + 1, mtInternal);
      if (utfName == nullptr) {
        throw_new(env, "java/lang/OutOfMemoryError");
        goto free_body;
      }
    } else {
      utfName = buf;
    }

    env->GetStringUTFRegion(name, 0, unicode_len, utfName);

    for (uint i = 0; i < len; i++) {
      if (utfName[i] == '.')   utfName[i] = '/';
    }
  }

  result = JVM_DefineClass(env, utfName, loader, body, length, pd);

  if (utfName && utfName != buf) {
    FREE_C_HEAP_ARRAY(char, utfName);
  }

 free_body:
  FREE_C_HEAP_ARRAY(jbyte, body);
  return result;
}


UNSAFE_ENTRY(jclass, Unsafe_DefineClass0(JNIEnv *env, jobject unsafe, jstring name, jbyteArray data, int offset, int length, jobject loader, jobject pd)) {
  ThreadToNativeFromVM ttnfv(thread);

  return Unsafe_DefineClass_impl(env, name, data, offset, length, loader, pd);
} UNSAFE_END


UNSAFE_ENTRY(void, Unsafe_ThrowException(JNIEnv *env, jobject unsafe, jthrowable thr)) {
  ThreadToNativeFromVM ttnfv(thread);
  env->Throw(thr);
} UNSAFE_END

// JSR166 ------------------------------------------------------------------

UNSAFE_ENTRY(jobject, Unsafe_CompareAndExchangeReferenceMO(JNIEnv *env, jobject unsafe,
                                                           jbyte memory_order,
                                                           jobject obj, jlong offset, jobject e_h, jobject x_h)) {
  assert(vmIntrinsics::is_valid_memory_order(memory_order),
         "bad MO bits from Java: 0x%02x", memory_order & 0xFF);
  oop x = JNIHandles::resolve(x_h);
  oop e = JNIHandles::resolve(e_h);
  oop p = JNIHandles::resolve(obj);
  assert_field_offset_sane(p, offset);
  // just use MO_VOLATILE for all MO inputs
  oop res = HeapAccess<ON_UNKNOWN_OOP_REF>::oop_atomic_cmpxchg_at(p, (ptrdiff_t)offset, e, x);
  return JNIHandles::make_local(THREAD, res);
} UNSAFE_END

UNSAFE_ENTRY_SCOPED(jlong, Unsafe_CompareAndExchangePrimitiveBitsMO(JNIEnv *env, jobject unsafe,
                                                                    jbyte memory_order, jbyte basic_type,
                                                                    jobject obj, jlong offset,
                                                                    jlong e, jlong x)) {
  assert(vmIntrinsics::is_valid_memory_order(memory_order),
         "bad MO bits from Java: 0x%02x", memory_order & 0xFF);
  assert(vmIntrinsics::is_valid_primitive_type(basic_type),
         "bad BT bits from Java: 0x%02x", basic_type & 0xFF);
  oop p = JNIHandles::resolve(obj);
  auto addr = index_oop_from_field_offset_long(p, offset);
  // just use MO_VOLATILE for all MO inputs
  TYPE_SIZE_SWITCH(basic_type, val_t, {
      auto expect = static_cast<val_t>(e);
      auto update = static_cast<val_t>(x);
      return AtomicAccess::cmpxchg(static_cast<volatile val_t*>(addr), expect, update);
    });
} UNSAFE_END

UNSAFE_ENTRY(jboolean, Unsafe_CompareAndSetReferenceMO(JNIEnv *env, jobject unsafe,
                                                       jbyte memory_order,
                                                       jobject obj, jlong offset, jobject e_h, jobject x_h)) {
  // ignore MO_WEAK_CAS here; the JIT might use it
  assert(vmIntrinsics::is_valid_memory_order(memory_order & ~vmIntrinsics::UNSAFE_MO_WEAK_CAS),
         "bad MO bits from Java: 0x%02x", memory_order & 0xFF);
  oop x = JNIHandles::resolve(x_h);
  oop e = JNIHandles::resolve(e_h);
  oop p = JNIHandles::resolve(obj);
  assert_field_offset_sane(p, offset);
  // just use MO_VOLATILE for all MO inputs
  oop ret = HeapAccess<ON_UNKNOWN_OOP_REF>::oop_atomic_cmpxchg_at(p, (ptrdiff_t)offset, e, x);
 return ret == e;
} UNSAFE_END

UNSAFE_ENTRY_SCOPED(jboolean, Unsafe_CompareAndSetPrimitiveBitsMO(JNIEnv *env, jobject unsafe,
                                                                  jbyte memory_order, jbyte basic_type,
                                                                  jobject obj, jlong offset, jlong e, jlong x)) {
  // ignore MO_WEAK_CAS here; the JIT might use it
  assert(vmIntrinsics::is_valid_memory_order(memory_order & ~vmIntrinsics::UNSAFE_MO_WEAK_CAS),
         "bad MO bits from Java: 0x%02x", memory_order & 0xFF);
  assert(vmIntrinsics::is_valid_primitive_type(basic_type),
         "bad BT bits from Java: 0x%02x", basic_type & 0xFF);
  oop p = JNIHandles::resolve(obj);
  auto addr = index_oop_from_field_offset_long(p, offset);
  // just use MO_VOLATILE for all MO inputs
  TYPE_SIZE_SWITCH(basic_type, val_t, {
      auto expect = static_cast<val_t>(e);
      auto update = static_cast<val_t>(x);
      auto actual = AtomicAccess::cmpxchg(static_cast<volatile val_t*>(addr), expect, update);
      return actual == expect;
    });
} UNSAFE_END

static void post_thread_park_event(EventThreadPark* event, const oop obj, jlong timeout_nanos, jlong until_epoch_millis) {
  assert(event != nullptr, "invariant");
  event->set_parkedClass((obj != nullptr) ? obj->klass() : nullptr);
  event->set_timeout(timeout_nanos);
  event->set_until(until_epoch_millis);
  event->set_address((obj != nullptr) ? (u8)cast_from_oop<uintptr_t>(obj) : 0);
  event->commit();
}

UNSAFE_ENTRY(void, Unsafe_Park(JNIEnv *env, jobject unsafe, jboolean isAbsolute, jlong time)) {
  HOTSPOT_THREAD_PARK_BEGIN((uintptr_t) thread->parker(), (int) isAbsolute, time);
  EventThreadPark event;

  JavaThreadParkedState jtps(thread, time != 0);
  thread->parker()->park(isAbsolute != 0, time);
  if (event.should_commit()) {
    const oop obj = thread->current_park_blocker();
    if (time == 0) {
      post_thread_park_event(&event, obj, min_jlong, min_jlong);
    } else {
      if (isAbsolute != 0) {
        post_thread_park_event(&event, obj, min_jlong, time);
      } else {
        post_thread_park_event(&event, obj, time, min_jlong);
      }
    }
  }
  HOTSPOT_THREAD_PARK_END((uintptr_t) thread->parker());
} UNSAFE_END

UNSAFE_ENTRY(void, Unsafe_Unpark(JNIEnv *env, jobject unsafe, jobject jthread)) {
  if (jthread != nullptr) {
    oop thread_oop = JNIHandles::resolve_non_null(jthread);
    // Get the JavaThread* stored in the java.lang.Thread object _before_
    // the embedded ThreadsListHandle is constructed so we know if the
    // early life stage of the JavaThread* is protected. We use acquire
    // here to ensure that if we see a non-nullptr value, then we also
    // see the main ThreadsList updates from the JavaThread* being added.
    FastThreadsListHandle ftlh(thread_oop, java_lang_Thread::thread_acquire(thread_oop));
    JavaThread* thr = ftlh.protected_java_thread();
    if (thr != nullptr) {
      // The still live JavaThread* is protected by the FastThreadsListHandle
      // so it is safe to access.
      Parker* p = thr->parker();
      HOTSPOT_THREAD_UNPARK((uintptr_t) p);
      p->unpark();
    }
  } // FastThreadsListHandle is destroyed here.
} UNSAFE_END

UNSAFE_ENTRY(jint, Unsafe_GetLoadAverage0(JNIEnv *env, jobject unsafe, jdoubleArray loadavg, jint nelem)) {
  const int max_nelem = 3;
  double la[max_nelem];
  jint ret;

  typeArrayOop a = typeArrayOop(JNIHandles::resolve_non_null(loadavg));
  assert(a->is_typeArray(), "must be type array");

  ret = os::loadavg(la, nelem);
  if (ret == -1) {
    return -1;
  }

  // if successful, ret is the number of samples actually retrieved.
  assert(ret >= 0 && ret <= max_nelem, "Unexpected loadavg return value");
  switch(ret) {
    case 3: a->double_at_put(2, (jdouble)la[2]); // fall through
    case 2: a->double_at_put(1, (jdouble)la[1]); // fall through
    case 1: a->double_at_put(0, (jdouble)la[0]); break;
  }

  return ret;
} UNSAFE_END


/// JVM_RegisterUnsafeMethods

#define ADR "J"

#define LANG "Ljava/lang/"

#define OBJ LANG "Object;"
#define CLS LANG "Class;"
#define FLD LANG "reflect/Field;"
#define THR LANG "Throwable;"

#define DC_Args  LANG "String;[BII" LANG "ClassLoader;" "Ljava/security/ProtectionDomain;"
#define DAC_Args CLS "[B[" OBJ

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

static JNINativeMethod jdk_internal_misc_Unsafe_methods[] = {
    {CC "getReferenceMO",           CC  "(B" OBJ "J)" OBJ "",  FN_PTR(Unsafe_GetReferenceMO)},
    {CC "getPrimitiveBitsMONative", CC "(BB" OBJ "J)" "J",     FN_PTR(Unsafe_GetPrimitiveBitsMO)},
    {CC "putReferenceMO",           CC  "(B" OBJ "J" OBJ ")V", FN_PTR(Unsafe_PutReferenceMO)},
    {CC "putPrimitiveBitsMONative", CC "(BB" OBJ "J" "J" ")V", FN_PTR(Unsafe_PutPrimitiveBitsMO)},

    {CC "compareAndSetReferenceMO",                CC  "(B" OBJ "J" OBJ "" OBJ ")" "Z", FN_PTR(Unsafe_CompareAndSetReferenceMO)},
    {CC "compareAndSetPrimitiveBitsMONative",      CC "(BB" OBJ "J" "J"   "J"  ")" "Z", FN_PTR(Unsafe_CompareAndSetPrimitiveBitsMO)},
    {CC "compareAndExchangeReferenceMO",           CC  "(B" OBJ "J" OBJ "" OBJ ")" OBJ, FN_PTR(Unsafe_CompareAndExchangeReferenceMO)},
    {CC "compareAndExchangePrimitiveBitsMONative", CC "(BB" OBJ "J" "J"   "J"  ")" "J", FN_PTR(Unsafe_CompareAndExchangePrimitiveBitsMO)},
    //  "getAndOperatePrimitiveBitsMO" has a portable fallback coded in Java

    {CC "getUncompressedObject", CC "(" ADR ")" OBJ,  FN_PTR(Unsafe_GetUncompressedObject)},

    {CC "allocateMemory0",    CC "(J)" ADR,              FN_PTR(Unsafe_AllocateMemory0)},
    {CC "reallocateMemory0",  CC "(" ADR "J)" ADR,       FN_PTR(Unsafe_ReallocateMemory0)},
    {CC "freeMemory0",        CC "(" ADR ")V",           FN_PTR(Unsafe_FreeMemory0)},

    {CC "objectFieldOffset0", CC "(" FLD ")J",           FN_PTR(Unsafe_ObjectFieldOffset0)},
    {CC "knownObjectFieldOffset0", CC "(" CLS LANG "String;)J", FN_PTR(Unsafe_KnownObjectFieldOffset0)},
    {CC "staticFieldOffset0", CC "(" FLD ")J",           FN_PTR(Unsafe_StaticFieldOffset0)},
    {CC "staticFieldBase0",   CC "(" FLD ")" OBJ,        FN_PTR(Unsafe_StaticFieldBase0)},
    {CC "ensureClassInitialized0", CC "(" CLS ")V",      FN_PTR(Unsafe_EnsureClassInitialized0)},
    {CC "arrayBaseOffset0",   CC "(" CLS ")I",           FN_PTR(Unsafe_ArrayBaseOffset0)},
    {CC "arrayIndexScale0",   CC "(" CLS ")I",           FN_PTR(Unsafe_ArrayIndexScale0)},

    {CC "defineClass0",       CC "(" DC_Args ")" CLS,    FN_PTR(Unsafe_DefineClass0)},
    {CC "allocateInstance",   CC "(" CLS ")" OBJ,        FN_PTR(Unsafe_AllocateInstance)},
    {CC "throwException",     CC "(" THR ")V",           FN_PTR(Unsafe_ThrowException)},

    {CC "park",               CC "(ZJ)V",                FN_PTR(Unsafe_Park)},
    {CC "unpark",             CC "(" OBJ ")V",           FN_PTR(Unsafe_Unpark)},

    {CC "getLoadAverage0",    CC "([DI)I",               FN_PTR(Unsafe_GetLoadAverage0)},

    {CC "copyMemory0",        CC "(" OBJ "J" OBJ "JJ)V", FN_PTR(Unsafe_CopyMemory0)},
    {CC "copySwapMemory0",    CC "(" OBJ "J" OBJ "JJJ)V", FN_PTR(Unsafe_CopySwapMemory0)},
    {CC "writeback0",         CC "(" "J" ")V",           FN_PTR(Unsafe_WriteBack0)},
    {CC "writebackPreSync0",  CC "()V",                  FN_PTR(Unsafe_WriteBackPreSync0)},
    {CC "writebackPostSync0", CC "()V",                  FN_PTR(Unsafe_WriteBackPostSync0)},
    {CC "setMemory0",         CC "(" OBJ "JJB)V",        FN_PTR(Unsafe_SetMemory0)},

    {CC "shouldBeInitialized0", CC "(" CLS ")Z",         FN_PTR(Unsafe_ShouldBeInitialized0)},

    {CC "fullFence",          CC "()V",                  FN_PTR(Unsafe_FullFence)},
};

#undef CC
#undef FN_PTR

#undef ADR
#undef LANG
#undef OBJ
#undef CLS
#undef FLD
#undef THR
#undef DC_Args
#undef DAC_Args

#undef DECLARE_GETPUTOOP


// This function is exported, used by NativeLookup.
// The Unsafe_xxx functions above are called only from the interpreter.
// The optimizer looks at names and signatures to recognize
// individual functions.

static void check_static_constant(JavaThread* thread, InstanceKlass* uk,
                                  const char* name, int value) {
  if (strncmp(name, "UNSAFE_", 7) == 0)
    name += 7;  // skip that prefix
  int fieldcv = value == -1 ? 0 : -1;  // force mismatch if not changed
  TempNewSymbol fname = SymbolTable::probe(name, strlen(name));
  if (fname != nullptr) {
    fieldDescriptor fd;
    if (uk->find_local_field(fname, vmSymbols::byte_signature(), &fd)) {
      if (fd.has_initial_value()) {
        fieldcv = fd.int_initial_value();
      }
    }
  }
  guarantee(fieldcv == value,
            "mismatch on Unsafe.%s, %d vs. %d", name, value, fieldcv);
}

static void check_unsafe_constants(JavaThread* thread, jclass unsafeclass) {
  InstanceKlass* uk = java_lang_Class::as_InstanceKlass(JNIHandles::resolve_non_null(unsafeclass));

  #define UNSAFE_BASIC_TYPE_CHECK(bt) \
    check_static_constant(thread, uk, "B" #bt, bt)
  UNSAFE_BASIC_TYPE_CHECK(T_BYTE);
  UNSAFE_BASIC_TYPE_CHECK(T_BOOLEAN);
  UNSAFE_BASIC_TYPE_CHECK(T_CHAR);
  UNSAFE_BASIC_TYPE_CHECK(T_FLOAT);
  UNSAFE_BASIC_TYPE_CHECK(T_DOUBLE);
  UNSAFE_BASIC_TYPE_CHECK(T_BYTE);
  UNSAFE_BASIC_TYPE_CHECK(T_SHORT);
  UNSAFE_BASIC_TYPE_CHECK(T_INT);
  UNSAFE_BASIC_TYPE_CHECK(T_LONG);

  #define VMI_MEMORY_ORDER_CHECK(mo, ignore) \
    check_static_constant(thread, uk, #mo, vmIntrinsics::mo);
  VMI_MEMORY_ORDERS_DO(VMI_MEMORY_ORDER_CHECK)

  #define VMI_PRIMITIVE_BITS_OPERATION_CHECK(op, ignore) \
    check_static_constant(thread, uk, #op, vmIntrinsics::op);
  VMI_PRIMITIVE_BITS_OPERATIONS_DO(VMI_PRIMITIVE_BITS_OPERATION_CHECK)

  check_static_constant(thread, uk, "PRIMITIVE_SIZE_MASK", vmIntrinsics::PRIMITIVE_SIZE_MASK);
}

JVM_ENTRY(void, JVM_RegisterJDKInternalMiscUnsafeMethods(JNIEnv *env, jclass unsafeclass)) {
  ThreadToNativeFromVM ttnfv(thread);

  int ok = env->RegisterNatives(unsafeclass, jdk_internal_misc_Unsafe_methods, sizeof(jdk_internal_misc_Unsafe_methods)/sizeof(JNINativeMethod));
  guarantee(ok == 0, "register jdk.internal.misc.Unsafe natives");

  {
    ThreadInVMfromNative tivfn(thread);
    check_unsafe_constants(thread, unsafeclass);  // do this bit in VM mode
  }
} JVM_END
