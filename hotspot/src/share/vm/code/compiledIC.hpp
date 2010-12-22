/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CODE_COMPILEDIC_HPP
#define SHARE_VM_CODE_COMPILEDIC_HPP

#include "interpreter/linkResolver.hpp"
#include "oops/compiledICHolderKlass.hpp"
#include "oops/compiledICHolderOop.hpp"
#include "oops/klassOop.hpp"
#ifdef TARGET_ARCH_x86
# include "nativeInst_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "nativeInst_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "nativeInst_zero.hpp"
#endif

//-----------------------------------------------------------------------------
// The CompiledIC represents a compiled inline cache.
//
// In order to make patching of the inline cache MT-safe, we only allow the following
// transitions (when not at a safepoint):
//
//
//         [1] --<--  Clean -->---  [1]
//            /       (null)      \
//           /                     \      /-<-\
//          /          [2]          \    /     \
//      Interpreted  ---------> Monomorphic     | [3]
//  (compiledICHolderOop)        (klassOop)     |
//          \                        /   \     /
//       [4] \                      / [4] \->-/
//            \->-  Megamorphic -<-/
//                  (methodOop)
//
// The text in paranteses () refere to the value of the inline cache receiver (mov instruction)
//
// The numbers in square brackets refere to the kind of transition:
// [1]: Initial fixup. Receiver it found from debug information
// [2]: Compilation of a method
// [3]: Recompilation of a method (note: only entry is changed. The klassOop must stay the same)
// [4]: Inline cache miss. We go directly to megamorphic call.
//
// The class automatically inserts transition stubs (using the InlineCacheBuffer) when an MT-unsafe
// transition is made to a stub.
//
class CompiledIC;

class CompiledICInfo {
  friend class CompiledIC;
 private:
  address _entry;              // entry point for call
  Handle  _cached_oop;         // Value of cached_oop (either in stub or inline cache)
  bool    _is_optimized;       // it is an optimized virtual call (i.e., can be statically bound)
  bool    _to_interpreter;     // Call it to interpreter
 public:
  address entry() const        { return _entry; }
  Handle  cached_oop() const   { return _cached_oop; }
  bool    is_optimized() const { return _is_optimized; }
};

class CompiledIC: public ResourceObj {
  friend class InlineCacheBuffer;
  friend class ICStub;


 private:
  NativeCall*   _ic_call;       // the call instruction
  oop*          _oop_addr;      // patchable oop cell for this IC
  RelocIterator _oops;          // iteration over any and all set-oop instructions
  bool          _is_optimized;  // an optimized virtual call (i.e., no compiled IC)

  CompiledIC(NativeCall* ic_call);
  CompiledIC(Relocation* ic_reloc);    // Must be of virtual_call_type/opt_virtual_call_type

  // low-level inline-cache manipulation. Cannot be accessed directly, since it might not be MT-safe
  // to change an inline-cache. These changes the underlying inline-cache directly. They *newer* make
  // changes to a transition stub.
  void set_ic_destination(address entry_point);
  void set_cached_oop(oop cache);

  // Reads the location of the transition stub. This will fail with an assertion, if no transition stub is
  // associated with the inline cache.
  address stub_address() const;
  bool is_in_transition_state() const;  // Use InlineCacheBuffer

 public:
  // conversion (machine PC to CompiledIC*)
  friend CompiledIC* CompiledIC_before(address return_addr);
  friend CompiledIC* CompiledIC_at(address call_site);
  friend CompiledIC* CompiledIC_at(Relocation* call_site);

  // Return the cached_oop/destination associated with this inline cache. If the cache currently points
  // to a transition stub, it will read the values from the transition stub.
  oop  cached_oop() const;
  address ic_destination() const;

  bool is_optimized() const   { return _is_optimized; }

  // State
  bool is_clean() const;
  bool is_megamorphic() const;
  bool is_call_to_compiled() const;
  bool is_call_to_interpreted() const;

  address end_of_call() { return  _ic_call->return_address(); }

  // MT-safe patching of inline caches. Note: Only safe to call is_xxx when holding the CompiledIC_ock
  // so you are guaranteed that no patching takes place. The same goes for verify.
  //
  // Note: We do not provide any direct access to the stub code, to prevent parts of the code
  // to manipulate the inline cache in MT-unsafe ways.
  //
  // They all takes a TRAP argument, since they can cause a GC if the inline-cache buffer is full.
  //
  void set_to_clean();  // Can only be called during a safepoint operation
  void set_to_monomorphic(const CompiledICInfo& info);
  void set_to_megamorphic(CallInfo* call_info, Bytecodes::Code bytecode, TRAPS);

  static void compute_monomorphic_entry(methodHandle method, KlassHandle receiver_klass,
                                        bool is_optimized, bool static_bound, CompiledICInfo& info, TRAPS);

  // Location
  address instruction_address() const { return _ic_call->instruction_address(); }

  // Misc
  void print()             PRODUCT_RETURN;
  void print_compiled_ic() PRODUCT_RETURN;
  void verify()            PRODUCT_RETURN;
};

inline CompiledIC* CompiledIC_before(address return_addr) {
  CompiledIC* c_ic = new CompiledIC(nativeCall_before(return_addr));
  c_ic->verify();
  return c_ic;
}

inline CompiledIC* CompiledIC_at(address call_site) {
  CompiledIC* c_ic = new CompiledIC(nativeCall_at(call_site));
  c_ic->verify();
  return c_ic;
}

inline CompiledIC* CompiledIC_at(Relocation* call_site) {
  CompiledIC* c_ic = new CompiledIC(call_site);
  c_ic->verify();
  return c_ic;
}


//-----------------------------------------------------------------------------
// The CompiledStaticCall represents a call to a static method in the compiled
//
// Transition diagram of a static call site is somewhat simpler than for an inlined cache:
//
//
//           -----<----- Clean ----->-----
//          /                             \
//         /                               \
//    compilled code <------------> interpreted code
//
//  Clean:            Calls directly to runtime method for fixup
//  Compiled code:    Calls directly to compiled code
//  Interpreted code: Calls to stub that set methodOop reference
//
//
class CompiledStaticCall;

class StaticCallInfo {
 private:
  address      _entry;          // Entrypoint
  methodHandle _callee;         // Callee (used when calling interpreter)
  bool         _to_interpreter; // call to interpreted method (otherwise compiled)

  friend class CompiledStaticCall;
 public:
  address      entry() const    { return _entry;  }
  methodHandle callee() const   { return _callee; }
};


class CompiledStaticCall: public NativeCall {
  friend class CompiledIC;

  // Also used by CompiledIC
  void set_to_interpreted(methodHandle callee, address entry);
  bool is_optimized_virtual();

 public:
  friend CompiledStaticCall* compiledStaticCall_before(address return_addr);
  friend CompiledStaticCall* compiledStaticCall_at(address native_call);
  friend CompiledStaticCall* compiledStaticCall_at(Relocation* call_site);

  // State
  bool is_clean() const;
  bool is_call_to_compiled() const;
  bool is_call_to_interpreted() const;

  // Clean static call (will force resolving on next use)
  void set_to_clean();

  // Set state. The entry must be the same, as computed by compute_entry.
  // Computation and setting is split up, since the actions are separate during
  // a OptoRuntime::resolve_xxx.
  void set(const StaticCallInfo& info);

  // Compute entry point given a method
  static void compute_entry(methodHandle m, StaticCallInfo& info);

  // Stub support
  address find_stub();
  static void set_stub_to_clean(static_stub_Relocation* static_stub);

  // Misc.
  void print()  PRODUCT_RETURN;
  void verify() PRODUCT_RETURN;
};


inline CompiledStaticCall* compiledStaticCall_before(address return_addr) {
  CompiledStaticCall* st = (CompiledStaticCall*)nativeCall_before(return_addr);
  st->verify();
  return st;
}

inline CompiledStaticCall* compiledStaticCall_at(address native_call) {
  CompiledStaticCall* st = (CompiledStaticCall*)native_call;
  st->verify();
  return st;
}

inline CompiledStaticCall* compiledStaticCall_at(Relocation* call_site) {
  return compiledStaticCall_at(call_site->addr());
}

#endif // SHARE_VM_CODE_COMPILEDIC_HPP
