/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_VFRAME_HPP
#define SHARE_VM_RUNTIME_VFRAME_HPP

#include "code/debugInfo.hpp"
#include "code/debugInfoRec.hpp"
#include "code/location.hpp"
#include "oops/oop.hpp"
#include "runtime/frame.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/stackValue.hpp"
#include "runtime/stackValueCollection.hpp"
#include "utilities/growableArray.hpp"

// vframes are virtual stack frames representing source level activations.
// A single frame may hold several source level activations in the case of
// optimized code. The debugging stored with the optimized code enables
// us to unfold a frame as a stack of vframes.
// A cVFrame represents an activation of a non-java method.

// The vframe inheritance hierarchy:
// - vframe
//   - javaVFrame
//     - interpretedVFrame
//     - compiledVFrame     ; (used for both compiled Java methods and native stubs)
//   - externalVFrame
//     - entryVFrame        ; special frame created when calling Java from C

// - BasicLock

class vframe: public ResourceObj {
 protected:
  frame        _fr;      // Raw frame behind the virtual frame.
  RegisterMap  _reg_map; // Register map for the raw frame (used to handle callee-saved registers).
  JavaThread*  _thread;  // The thread owning the raw frame.

  vframe(const frame* fr, const RegisterMap* reg_map, JavaThread* thread);
  vframe(const frame* fr, JavaThread* thread);
 public:
  // Factory method for creating vframes
  static vframe* new_vframe(const frame* f, const RegisterMap *reg_map, JavaThread* thread);

  // Accessors
  frame              fr()           const { return _fr;       }
  CodeBlob*          cb()         const { return _fr.cb();  }
  nmethod*           nm()         const {
      assert( cb() != NULL && cb()->is_nmethod(), "usage");
      return (nmethod*) cb();
  }

// ???? Does this need to be a copy?
  frame*             frame_pointer() { return &_fr;       }
  const RegisterMap* register_map() const { return &_reg_map; }
  JavaThread*        thread()       const { return _thread;   }

  // Returns the sender vframe
  virtual vframe* sender() const;

  // Returns the next javaVFrame on the stack (skipping all other kinds of frame)
  javaVFrame *java_sender() const;

  // Answers if the this is the top vframe in the frame, i.e., if the sender vframe
  // is in the caller frame
  virtual bool is_top() const { return true; }

  // Returns top vframe within same frame (see is_top())
  virtual vframe* top() const;

  // Type testing operations
  virtual bool is_entry_frame()       const { return false; }
  virtual bool is_java_frame()        const { return false; }
  virtual bool is_interpreted_frame() const { return false; }
  virtual bool is_compiled_frame()    const { return false; }

#ifndef PRODUCT
  // printing operations
  virtual void print_value() const;
  virtual void print();
#endif
};


class javaVFrame: public vframe {
 public:
  // JVM state
  virtual Method*                      method()         const = 0;
  virtual int                          bci()            const = 0;
  virtual StackValueCollection*        locals()         const = 0;
  virtual StackValueCollection*        expressions()    const = 0;
  // the order returned by monitors() is from oldest -> youngest#4418568
  virtual GrowableArray<MonitorInfo*>* monitors()       const = 0;

  // Debugging support via JVMTI.
  // NOTE that this is not guaranteed to give correct results for compiled vframes.
  // Deoptimize first if necessary.
  virtual void set_locals(StackValueCollection* values) const = 0;

  // Test operation
  bool is_java_frame() const { return true; }

 protected:
  javaVFrame(const frame* fr, const RegisterMap* reg_map, JavaThread* thread) : vframe(fr, reg_map, thread) {}
  javaVFrame(const frame* fr, JavaThread* thread) : vframe(fr, thread) {}

 public:
  // casting
  static javaVFrame* cast(vframe* vf) {
    assert(vf == NULL || vf->is_java_frame(), "must be java frame");
    return (javaVFrame*) vf;
  }

  // Return an array of monitors locked by this frame in the youngest to oldest order
  GrowableArray<MonitorInfo*>* locked_monitors();

  // printing used during stack dumps
  void print_lock_info_on(outputStream* st, int frame_count);
  void print_lock_info(int frame_count) { print_lock_info_on(tty, frame_count); }

#ifndef PRODUCT
 public:
  // printing operations
  void print();
  void print_value() const;
  void print_activation(int index) const;

  // verify operations
  virtual void verify() const;

  // Structural compare
  bool structural_compare(javaVFrame* other);
#endif
  friend class vframe;
};

class interpretedVFrame: public javaVFrame {
 public:
  // JVM state
  Method*                      method()         const;
  int                          bci()            const;
  StackValueCollection*        locals()         const;
  StackValueCollection*        expressions()    const;
  GrowableArray<MonitorInfo*>* monitors()       const;

  void set_locals(StackValueCollection* values) const;

  // Test operation
  bool is_interpreted_frame() const { return true; }

 protected:
  interpretedVFrame(const frame* fr, const RegisterMap* reg_map, JavaThread* thread) : javaVFrame(fr, reg_map, thread) {};

 public:
  // Accessors for Byte Code Pointer
  u_char* bcp() const;
  void set_bcp(u_char* bcp);

  // casting
  static interpretedVFrame* cast(vframe* vf) {
    assert(vf == NULL || vf->is_interpreted_frame(), "must be interpreted frame");
    return (interpretedVFrame*) vf;
  }

 private:
  static const int bcp_offset;
  intptr_t* locals_addr_at(int offset) const;

  // returns where the parameters starts relative to the frame pointer
  int start_of_parameters() const;

#ifndef PRODUCT
 public:
  // verify operations
  void verify() const;
#endif
  friend class vframe;
};


class externalVFrame: public vframe {
 protected:
  externalVFrame(const frame* fr, const RegisterMap* reg_map, JavaThread* thread) : vframe(fr, reg_map, thread) {}

#ifndef PRODUCT
 public:
  // printing operations
  void print_value() const;
  void print();
#endif
  friend class vframe;
};

class entryVFrame: public externalVFrame {
 public:
  bool is_entry_frame() const { return true; }

 protected:
  entryVFrame(const frame* fr, const RegisterMap* reg_map, JavaThread* thread);

 public:
  // casting
  static entryVFrame* cast(vframe* vf) {
    assert(vf == NULL || vf->is_entry_frame(), "must be entry frame");
    return (entryVFrame*) vf;
  }

#ifndef PRODUCT
 public:
  // printing
  void print_value() const;
  void print();
#endif
  friend class vframe;
};


// A MonitorInfo is a ResourceObject that describes a the pair:
// 1) the owner of the monitor
// 2) the monitor lock
class MonitorInfo : public ResourceObj {
 private:
  oop        _owner; // the object owning the monitor
  BasicLock* _lock;
  oop        _owner_klass; // klass (mirror) if owner was scalar replaced
  bool       _eliminated;
  bool       _owner_is_scalar_replaced;
 public:
  // Constructor
  MonitorInfo(oop owner, BasicLock* lock, bool eliminated, bool owner_is_scalar_replaced) {
    if (!owner_is_scalar_replaced) {
      _owner = owner;
      _owner_klass = NULL;
    } else {
      assert(eliminated, "monitor should be eliminated for scalar replaced object");
      _owner = NULL;
      _owner_klass = owner;
    }
    _lock  = lock;
    _eliminated = eliminated;
    _owner_is_scalar_replaced = owner_is_scalar_replaced;
  }
  // Accessors
  oop        owner() const {
    assert(!_owner_is_scalar_replaced, "should not be called for scalar replaced object");
    return _owner;
  }
  oop   owner_klass() const {
    assert(_owner_is_scalar_replaced, "should not be called for not scalar replaced object");
    return _owner_klass;
  }
  BasicLock* lock()  const { return _lock;  }
  bool eliminated()  const { return _eliminated; }
  bool owner_is_scalar_replaced()  const { return _owner_is_scalar_replaced; }
};

class vframeStreamCommon : StackObj {
 protected:
  // common
  frame        _frame;
  JavaThread*  _thread;
  RegisterMap  _reg_map;
  enum { interpreted_mode, compiled_mode, at_end_mode } _mode;

  int _sender_decode_offset;

  // Cached information
  Method* _method;
  int       _bci;

  // Should VM activations be ignored or not
  bool _stop_at_java_call_stub;

  bool fill_in_compiled_inlined_sender();
  void fill_from_compiled_frame(int decode_offset);
  void fill_from_compiled_native_frame();

  void found_bad_method_frame();

  void fill_from_interpreter_frame();
  bool fill_from_frame();

  // Helper routine for security_get_caller_frame
  void skip_prefixed_method_and_wrappers();

 public:
  // Constructor
  vframeStreamCommon(JavaThread* thread) : _reg_map(thread, false) {
    _thread = thread;
  }

  // Accessors
  Method* method() const { return _method; }
  int bci() const { return _bci; }
  intptr_t* frame_id() const { return _frame.id(); }
  address frame_pc() const { return _frame.pc(); }

  CodeBlob*          cb()         const { return _frame.cb();  }
  nmethod*           nm()         const {
      assert( cb() != NULL && cb()->is_nmethod(), "usage");
      return (nmethod*) cb();
  }

  // Frame type
  bool is_interpreted_frame() const { return _frame.is_interpreted_frame(); }
  bool is_entry_frame() const       { return _frame.is_entry_frame(); }

  // Iteration
  void next() {
    // handle frames with inlining
    if (_mode == compiled_mode    && fill_in_compiled_inlined_sender()) return;

    // handle general case
    do {
      _frame = _frame.sender(&_reg_map);
    } while (!fill_from_frame());
  }
  void security_next();

  bool at_end() const { return _mode == at_end_mode; }

  // Implements security traversal. Skips depth no. of frame including
  // special security frames and prefixed native methods
  void security_get_caller_frame(int depth);

  // Helper routine for JVM_LatestUserDefinedLoader -- needed for 1.4
  // reflection implementation
  void skip_reflection_related_frames();
};

class vframeStream : public vframeStreamCommon {
 public:
  // Constructors
  vframeStream(JavaThread* thread, bool stop_at_java_call_stub = false)
    : vframeStreamCommon(thread) {
    _stop_at_java_call_stub = stop_at_java_call_stub;

    if (!thread->has_last_Java_frame()) {
      _mode = at_end_mode;
      return;
    }

    _frame = _thread->last_frame();
    while (!fill_from_frame()) {
      _frame = _frame.sender(&_reg_map);
    }
  }

  // top_frame may not be at safepoint, start with sender
  vframeStream(JavaThread* thread, frame top_frame, bool stop_at_java_call_stub = false);
};


inline bool vframeStreamCommon::fill_in_compiled_inlined_sender() {
  if (_sender_decode_offset == DebugInformationRecorder::serialized_null) {
    return false;
  }
  fill_from_compiled_frame(_sender_decode_offset);
  return true;
}


inline void vframeStreamCommon::fill_from_compiled_frame(int decode_offset) {
  _mode = compiled_mode;

  // Range check to detect ridiculous offsets.
  if (decode_offset == DebugInformationRecorder::serialized_null ||
      decode_offset < 0 ||
      decode_offset >= nm()->scopes_data_size()) {
    // 6379830 AsyncGetCallTrace sometimes feeds us wild frames.
    // If we attempt to read nmethod::scopes_data at serialized_null (== 0),
    // or if we read some at other crazy offset,
    // we will decode garbage and make wild references into the heap,
    // leading to crashes in product mode.
    // (This isn't airtight, of course, since there are internal
    // offsets which are also crazy.)
#ifdef ASSERT
    if (WizardMode) {
      tty->print_cr("Error in fill_from_frame: pc_desc for "
                    INTPTR_FORMAT " not found or invalid at %d",
                    _frame.pc(), decode_offset);
      nm()->print();
      nm()->method()->print_codes();
      nm()->print_code();
      nm()->print_pcs();
    }
#endif
    // Provide a cheap fallback in product mode.  (See comment above.)
    found_bad_method_frame();
    fill_from_compiled_native_frame();
    return;
  }

  // Decode first part of scopeDesc
  DebugInfoReadStream buffer(nm(), decode_offset);
  _sender_decode_offset = buffer.read_int();
  _method               = buffer.read_method();
  _bci                  = buffer.read_bci();

  assert(_method->is_method(), "checking type of decoded method");
}

// The native frames are handled specially. We do not rely on ScopeDesc info
// since the pc might not be exact due to the _last_native_pc trick.
inline void vframeStreamCommon::fill_from_compiled_native_frame() {
  _mode = compiled_mode;
  _sender_decode_offset = DebugInformationRecorder::serialized_null;
  _method = nm()->method();
  _bci = 0;
}

inline bool vframeStreamCommon::fill_from_frame() {
  // Interpreted frame
  if (_frame.is_interpreted_frame()) {
    fill_from_interpreter_frame();
    return true;
  }

  // Compiled frame

  if (cb() != NULL && cb()->is_nmethod()) {
    if (nm()->is_native_method()) {
      // Do not rely on scopeDesc since the pc might be unprecise due to the _last_native_pc trick.
      fill_from_compiled_native_frame();
    } else {
      PcDesc* pc_desc = nm()->pc_desc_at(_frame.pc());
      int decode_offset;
      if (pc_desc == NULL) {
        // Should not happen, but let fill_from_compiled_frame handle it.

        // If we are trying to walk the stack of a thread that is not
        // at a safepoint (like AsyncGetCallTrace would do) then this is an
        // acceptable result. [ This is assuming that safe_for_sender
        // is so bullet proof that we can trust the frames it produced. ]
        //
        // So if we see that the thread is not safepoint safe
        // then simply produce the method and a bci of zero
        // and skip the possibility of decoding any inlining that
        // may be present. That is far better than simply stopping (or
        // asserting. If however the thread is safepoint safe this
        // is the sign of a compiler bug  and we'll let
        // fill_from_compiled_frame handle it.


        JavaThreadState state = _thread->thread_state();

        // in_Java should be good enough to test safepoint safety
        // if state were say in_Java_trans then we'd expect that
        // the pc would have already been slightly adjusted to
        // one that would produce a pcDesc since the trans state
        // would be one that might in fact anticipate a safepoint

        if (state == _thread_in_Java ) {
          // This will get a method a zero bci and no inlining.
          // Might be nice to have a unique bci to signify this
          // particular case but for now zero will do.

          fill_from_compiled_native_frame();

          // There is something to be said for setting the mode to
          // at_end_mode to prevent trying to walk further up the
          // stack. There is evidence that if we walk any further
          // that we could produce a bad stack chain. However until
          // we see evidence that allowing this causes us to find
          // frames bad enough to cause segv's or assertion failures
          // we don't do it as while we may get a bad call chain the
          // probability is much higher (several magnitudes) that we
          // get good data.

          return true;
        }
        decode_offset = DebugInformationRecorder::serialized_null;
      } else {
        decode_offset = pc_desc->scope_decode_offset();
      }
      fill_from_compiled_frame(decode_offset);
    }
    return true;
  }

  // End of stack?
  if (_frame.is_first_frame() || (_stop_at_java_call_stub && _frame.is_entry_frame())) {
    _mode = at_end_mode;
    return true;
  }

  return false;
}


inline void vframeStreamCommon::fill_from_interpreter_frame() {
  Method* method = _frame.interpreter_frame_method();
  intptr_t  bcx    = _frame.interpreter_frame_bcx();
  int       bci    = method->validate_bci_from_bcx(bcx);
  // 6379830 AsyncGetCallTrace sometimes feeds us wild frames.
  if (bci < 0) {
    found_bad_method_frame();
    bci = 0;  // pretend it's on the point of entering
  }
  _mode   = interpreted_mode;
  _method = method;
  _bci    = bci;
}

#endif // SHARE_VM_RUNTIME_VFRAME_HPP
