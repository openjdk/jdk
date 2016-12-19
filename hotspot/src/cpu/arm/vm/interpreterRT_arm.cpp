/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/universe.inline.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/icache.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/signature.hpp"

#define __ _masm->

#ifdef SHARING_FAST_NATIVE_FINGERPRINTS
// mapping from SignatureIterator param to (common) type of parsing
static const u1 shared_type[] = {
  (u1) SignatureIterator::int_parm, // bool
  (u1) SignatureIterator::int_parm, // byte
  (u1) SignatureIterator::int_parm, // char
  (u1) SignatureIterator::int_parm, // short
  (u1) SignatureIterator::int_parm, // int
  (u1) SignatureIterator::long_parm, // long
#ifndef __ABI_HARD__
  (u1) SignatureIterator::int_parm, // float, passed as int
  (u1) SignatureIterator::long_parm, // double, passed as long
#else
  (u1) SignatureIterator::float_parm, // float
  (u1) SignatureIterator::double_parm, // double
#endif
  (u1) SignatureIterator::obj_parm, // obj
  (u1) SignatureIterator::done_parm // done
};

uint64_t InterpreterRuntime::normalize_fast_native_fingerprint(uint64_t fingerprint) {
  if (fingerprint == UCONST64(-1)) {
    // special signature used when the argument list cannot be encoded in a 64 bits value
    return fingerprint;
  }
  int shift = SignatureIterator::static_feature_size;
  uint64_t result = fingerprint & ((1 << shift) - 1);
  fingerprint >>= shift;

  BasicType ret_type = (BasicType) (fingerprint & SignatureIterator::result_feature_mask);
  // For ARM, the fast signature handler only needs to know whether
  // the return value must be unboxed. T_OBJECT and T_ARRAY need not
  // be distinguished from each other and all other return values
  // behave like integers with respect to the handler.
  bool unbox = (ret_type == T_OBJECT) || (ret_type == T_ARRAY);
  if (unbox) {
    ret_type = T_OBJECT;
  } else {
    ret_type = T_INT;
  }
  result |= ((uint64_t) ret_type) << shift;
  shift += SignatureIterator::result_feature_size;
  fingerprint >>= SignatureIterator::result_feature_size;

  while (true) {
    uint32_t type = (uint32_t) (fingerprint & SignatureIterator::parameter_feature_mask);
    if (type == SignatureIterator::done_parm) {
      result |= ((uint64_t) SignatureIterator::done_parm) << shift;
      return result;
    }
    assert((type >= SignatureIterator::bool_parm) && (type <= SignatureIterator::obj_parm), "check fingerprint encoding");
    int shared = shared_type[type - SignatureIterator::bool_parm];
    result |= ((uint64_t) shared) << shift;
    shift += SignatureIterator::parameter_feature_size;
    fingerprint >>= SignatureIterator::parameter_feature_size;
  }
}
#endif // SHARING_FAST_NATIVE_FINGERPRINTS

// Implementation of SignatureHandlerGenerator
void InterpreterRuntime::SignatureHandlerGenerator::pass_int() {
  if (_ireg < GPR_PARAMS) {
    Register dst = as_Register(_ireg);
    __ ldr_s32(dst, Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
    _ireg++;
  } else {
    __ ldr_s32(Rtemp, Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
    __ str_32(Rtemp, Address(SP, _abi_offset * wordSize));
    _abi_offset++;
  }
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_long() {
#ifdef AARCH64
  if (_ireg < GPR_PARAMS) {
    Register dst = as_Register(_ireg);
    __ ldr(dst, Address(Rlocals, Interpreter::local_offset_in_bytes(offset() + 1)));
    _ireg++;
  } else {
    __ ldr(Rtemp, Address(Rlocals, Interpreter::local_offset_in_bytes(offset() + 1)));
    __ str(Rtemp, Address(SP, _abi_offset * wordSize));
    _abi_offset++;
  }
#else
  if (_ireg <= 2) {
#if (ALIGN_WIDE_ARGUMENTS == 1)
    if ((_ireg & 1) != 0) {
      // 64-bit values should be 8-byte aligned
      _ireg++;
    }
#endif
    Register dst1 = as_Register(_ireg);
    Register dst2 = as_Register(_ireg+1);
    __ ldr(dst1, Address(Rlocals, Interpreter::local_offset_in_bytes(offset()+1)));
    __ ldr(dst2, Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
    _ireg += 2;
#if (ALIGN_WIDE_ARGUMENTS == 0)
  } else if (_ireg == 3) {
    // uses R3 + one stack slot
    Register dst1 = as_Register(_ireg);
    __ ldr(Rtemp, Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
    __ ldr(dst1, Address(Rlocals, Interpreter::local_offset_in_bytes(offset()+1)));
    __ str(Rtemp, Address(SP, _abi_offset * wordSize));
    _ireg += 1;
    _abi_offset += 1;
#endif
  } else {
#if (ALIGN_WIDE_ARGUMENTS == 1)
    if(_abi_offset & 1) _abi_offset++;
#endif
    __ ldr(Rtemp, Address(Rlocals, Interpreter::local_offset_in_bytes(offset()+1)));
    __ str(Rtemp, Address(SP, (_abi_offset) * wordSize));
    __ ldr(Rtemp, Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
    __ str(Rtemp, Address(SP, (_abi_offset+1) * wordSize));
    _abi_offset += 2;
    _ireg = 4;
  }
#endif // AARCH64
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_object() {
#ifdef AARCH64
  __ ldr(Rtemp, Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
  __ cmp(Rtemp, 0);
  __ sub(Rtemp, Rlocals, -Interpreter::local_offset_in_bytes(offset()));
  if (_ireg < GPR_PARAMS) {
    Register dst = as_Register(_ireg);
    __ csel(dst, ZR, Rtemp, eq);
    _ireg++;
  } else {
    __ csel(Rtemp, ZR, Rtemp, eq);
    __ str(Rtemp, Address(SP, _abi_offset * wordSize));
    _abi_offset++;
  }
#else
  if (_ireg < 4) {
    Register dst = as_Register(_ireg);
    __ ldr(dst, Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
    __ cmp(dst, 0);
    __ sub(dst, Rlocals, -Interpreter::local_offset_in_bytes(offset()), ne);
    _ireg++;
  } else {
    __ ldr(Rtemp, Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
    __ cmp(Rtemp, 0);
    __ sub(Rtemp, Rlocals, -Interpreter::local_offset_in_bytes(offset()), ne);
    __ str(Rtemp, Address(SP, _abi_offset * wordSize));
    _abi_offset++;
  }
#endif // AARCH64
}

#ifndef __ABI_HARD__
void InterpreterRuntime::SignatureHandlerGenerator::pass_float() {
  if (_ireg < 4) {
    Register dst = as_Register(_ireg);
    __ ldr(dst, Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
    _ireg++;
  } else {
    __ ldr(Rtemp, Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
    __ str(Rtemp, Address(SP, _abi_offset * wordSize));
    _abi_offset++;
  }
}

#else
#ifndef __SOFTFP__
void InterpreterRuntime::SignatureHandlerGenerator::pass_float() {
#ifdef AARCH64
    if (_freg < FPR_PARAMS) {
      FloatRegister dst = as_FloatRegister(_freg);
      __ ldr_s(dst, Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
      _freg++;
    } else {
      __ ldr_u32(Rtemp, Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
      __ str_32(Rtemp, Address(SP, _abi_offset * wordSize));
      _abi_offset++;
    }
#else
    if((_fp_slot < 16) || (_single_fpr_slot & 1)) {
      if ((_single_fpr_slot & 1) == 0) {
        _single_fpr_slot = _fp_slot;
        _fp_slot += 2;
      }
      __ flds(as_FloatRegister(_single_fpr_slot), Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
      _single_fpr_slot++;
    } else {
      __ ldr(Rtemp, Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
      __ str(Rtemp, Address(SP, _abi_offset * wordSize));
      _abi_offset++;
    }
#endif // AARCH64
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_double() {
#ifdef AARCH64
    if (_freg < FPR_PARAMS) {
      FloatRegister dst = as_FloatRegister(_freg);
      __ ldr_d(dst, Address(Rlocals, Interpreter::local_offset_in_bytes(offset() + 1)));
      _freg++;
    } else {
      __ ldr(Rtemp, Address(Rlocals, Interpreter::local_offset_in_bytes(offset() + 1)));
      __ str(Rtemp, Address(SP, _abi_offset * wordSize));
      _abi_offset++;
    }
#else
    if(_fp_slot <= 14) {
      __ fldd(as_FloatRegister(_fp_slot), Address(Rlocals, Interpreter::local_offset_in_bytes(offset()+1)));
      _fp_slot += 2;
    } else {
      __ ldr(Rtemp, Address(Rlocals, Interpreter::local_offset_in_bytes(offset()+1)));
      __ str(Rtemp, Address(SP, (_abi_offset) * wordSize));
      __ ldr(Rtemp, Address(Rlocals, Interpreter::local_offset_in_bytes(offset())));
      __ str(Rtemp, Address(SP, (_abi_offset+1) * wordSize));
      _abi_offset += 2;
      _single_fpr_slot = 16;
    }
#endif // AARCH64
}
#endif // __SOFTFP__
#endif // __ABI_HARD__

void InterpreterRuntime::SignatureHandlerGenerator::generate(uint64_t fingerprint) {
  iterate(fingerprint);

  BasicType result_type = SignatureIterator::return_type(fingerprint);

  address result_handler = Interpreter::result_handler(result_type);

#ifdef AARCH64
  __ mov_slow(R0, (address)result_handler);
#else
  // Check that result handlers are not real handler on ARM (0 or -1).
  // This ensures the signature handlers do not need symbolic information.
  assert((result_handler == NULL)||(result_handler==(address)0xffffffff),"");
  __ mov_slow(R0, (intptr_t)result_handler);
#endif

  __ ret();
}


// Implementation of SignatureHandlerLibrary

void SignatureHandlerLibrary::pd_set_handler(address handler) {}

class SlowSignatureHandler: public NativeSignatureIterator {
 private:
  address   _from;
  intptr_t* _to;

#ifndef __ABI_HARD__
  virtual void pass_int() {
    *_to++ = *(jint *)(_from+Interpreter::local_offset_in_bytes(0));
    _from -= Interpreter::stackElementSize;
  }

  virtual void pass_float() {
    *_to++ = *(jint *)(_from+Interpreter::local_offset_in_bytes(0));
    _from -= Interpreter::stackElementSize;
  }

  virtual void pass_long() {
#if (ALIGN_WIDE_ARGUMENTS == 1)
    if (((intptr_t)_to & 7) != 0) {
      // 64-bit values should be 8-byte aligned
      _to++;
    }
#endif
    _to[0] = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(1));
    _to[1] = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(0));
    _to += 2;
    _from -= 2*Interpreter::stackElementSize;
  }

  virtual void pass_object() {
    intptr_t from_addr = (intptr_t)(_from + Interpreter::local_offset_in_bytes(0));
    *_to++ = (*(intptr_t*)from_addr == 0) ? (intptr_t)NULL : from_addr;
    _from -= Interpreter::stackElementSize;
   }

#else

  intptr_t* _toFP;
  intptr_t* _toGP;
  int       _last_gp;
  int       _last_fp;
#ifndef AARCH64
  int       _last_single_fp;
#endif // !AARCH64

  virtual void pass_int() {
    if(_last_gp < GPR_PARAMS) {
      _toGP[_last_gp++] = *(jint *)(_from+Interpreter::local_offset_in_bytes(0));
    } else {
      *_to++ = *(jint *)(_from+Interpreter::local_offset_in_bytes(0));
    }
    _from -= Interpreter::stackElementSize;
  }

  virtual void pass_long() {
#ifdef AARCH64
    if(_last_gp < GPR_PARAMS) {
      _toGP[_last_gp++] = *(jlong *)(_from+Interpreter::local_offset_in_bytes(1));
    } else {
      *_to++ = *(jlong *)(_from+Interpreter::local_offset_in_bytes(1));
    }
#else
    assert(ALIGN_WIDE_ARGUMENTS == 1, "ABI_HARD not supported with unaligned wide arguments");
    if (_last_gp <= 2) {
      if(_last_gp & 1) _last_gp++;
      _toGP[_last_gp++] = *(jint *)(_from+Interpreter::local_offset_in_bytes(1));
      _toGP[_last_gp++] = *(jint *)(_from+Interpreter::local_offset_in_bytes(0));
    } else {
      if (((intptr_t)_to & 7) != 0) {
        // 64-bit values should be 8-byte aligned
        _to++;
      }
      _to[0] = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(1));
      _to[1] = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(0));
      _to += 2;
      _last_gp = 4;
    }
#endif // AARCH64
    _from -= 2*Interpreter::stackElementSize;
  }

  virtual void pass_object() {
    intptr_t from_addr = (intptr_t)(_from + Interpreter::local_offset_in_bytes(0));
    if(_last_gp < GPR_PARAMS) {
      _toGP[_last_gp++] = (*(intptr_t*)from_addr == 0) ? NULL : from_addr;
    } else {
      *_to++ = (*(intptr_t*)from_addr == 0) ? NULL : from_addr;
    }
    _from -= Interpreter::stackElementSize;
  }

  virtual void pass_float() {
#ifdef AARCH64
    if(_last_fp < FPR_PARAMS) {
      _toFP[_last_fp++] = *(jint *)(_from+Interpreter::local_offset_in_bytes(0));
    } else {
      *_to++ = *(jint *)(_from+Interpreter::local_offset_in_bytes(0));
    }
#else
    if((_last_fp < 16) || (_last_single_fp & 1)) {
      if ((_last_single_fp & 1) == 0) {
        _last_single_fp = _last_fp;
        _last_fp += 2;
      }

      _toFP[_last_single_fp++] = *(jint *)(_from+Interpreter::local_offset_in_bytes(0));
    } else {
      *_to++ = *(jint *)(_from+Interpreter::local_offset_in_bytes(0));
    }
#endif // AARCH64
    _from -= Interpreter::stackElementSize;
  }

  virtual void pass_double() {
#ifdef AARCH64
    if(_last_fp < FPR_PARAMS) {
      _toFP[_last_fp++] = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(1));
    } else {
      *_to++ = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(1));
    }
#else
    assert(ALIGN_WIDE_ARGUMENTS == 1, "ABI_HARD not supported with unaligned wide arguments");
    if(_last_fp <= 14) {
      _toFP[_last_fp++] = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(1));
      _toFP[_last_fp++] = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(0));
    } else {
      if (((intptr_t)_to & 7) != 0) {      // 64-bit values should be 8-byte aligned
        _to++;
      }
      _to[0] = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(1));
      _to[1] = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(0));
      _to += 2;
      _last_single_fp = 16;
    }
#endif // AARCH64
    _from -= 2*Interpreter::stackElementSize;
  }

#endif // !__ABI_HARD__

 public:
  SlowSignatureHandler(methodHandle method, address from, intptr_t* to) :
    NativeSignatureIterator(method) {
    _from = from;

#ifdef __ABI_HARD__
    _toGP  = to;
    _toFP = _toGP + GPR_PARAMS;
    _to   = _toFP + AARCH64_ONLY(FPR_PARAMS) NOT_AARCH64(8*2);
    _last_gp = (is_static() ? 2 : 1);
    _last_fp = 0;
#ifndef AARCH64
    _last_single_fp = 0;
#endif // !AARCH64
#else
    _to   = to + (is_static() ? 2 : 1);
#endif // __ABI_HARD__
  }
};

IRT_ENTRY(address, InterpreterRuntime::slow_signature_handler(JavaThread* thread, Method* method, intptr_t* from, intptr_t* to))
  methodHandle m(thread, (Method*)method);
  assert(m->is_native(), "sanity check");
  SlowSignatureHandler(m, (address)from, to).iterate(UCONST64(-1));
  return Interpreter::result_handler(m->result_type());
IRT_END
