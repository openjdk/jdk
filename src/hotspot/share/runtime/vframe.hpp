/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_VFRAME_HPP
#define SHARE_RUNTIME_VFRAME_HPP

#include "code/debugInfo.hpp"
#include "code/debugInfoRec.hpp"
#include "code/location.hpp"
#include "oops/oop.hpp"
#include "runtime/frame.hpp"
#include "runtime/handles.hpp"
#include "runtime/registerMap.hpp"
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

class StackFrameStream;
class ContinuationEntry;

class vframe: public ResourceObj {
 protected:
  frame        _fr;      // Raw frame behind the virtual frame.
  RegisterMap  _reg_map; // Register map for the raw frame (used to handle callee-saved registers).
  JavaThread*  _thread;  // The thread owning the raw frame.
  stackChunkHandle _chunk;

  vframe(const frame* fr, const RegisterMap* reg_map, JavaThread* thread);

 public:
  // Factory methods for creating vframes
  static vframe* new_vframe(const frame* f, const RegisterMap *reg_map, JavaThread* thread);

  // Accessors
  frame             fr() const { return _fr;       }
  CodeBlob*         cb() const { return _fr.cb();  }

// ???? Does this need to be a copy?
  frame*             frame_pointer() { return &_fr;       }
  const RegisterMap* register_map() const { return &_reg_map; }
  JavaThread*        thread()       const { return _thread;   }
  stackChunkOop      stack_chunk()  const { return _chunk(); /*_reg_map.stack_chunk();*/ }

  // Returns the sender vframe
  virtual vframe* sender() const;

  // Returns the next javaVFrame on the stack (skipping all other kinds of frame)
  javaVFrame *java_sender() const;

  // Is the current frame the entry to a virtual thread's stack
  bool is_vthread_entry() const;

  // Answers if the this is the top vframe in the frame, i.e., if the sender vframe
  // is in the caller frame
  virtual bool is_top() const { return true; }

  // Type testing operations
  virtual bool is_entry_frame()       const { return false; }
  virtual bool is_java_frame()        const { return false; }
  virtual bool is_interpreted_frame() const { return false; }
  virtual bool is_compiled_frame()    const { return false; }

#ifndef PRODUCT
  // printing operations
  virtual void print_value(outputStream* output = tty) const;
  virtual void print(outputStream* output = tty);
#endif
};

class MonitorInfo;

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

 public:
  // casting
  static javaVFrame* cast(vframe* vf) {
    assert(vf == nullptr || vf->is_java_frame(), "must be java frame");
    return (javaVFrame*) vf;
  }

  // Return an array of monitors locked by this frame in the youngest to oldest order
  GrowableArray<MonitorInfo*>* locked_monitors();

  // printing used during stack dumps and diagnostics
  static void print_locked_object_class_name(outputStream* st, Handle obj, const char* lock_state);
  void print_lock_info_on(outputStream* st, int frame_count);
  void print_lock_info(int frame_count) { print_lock_info_on(tty, frame_count); }

#ifndef PRODUCT
 public:
  // printing operations
  void print(outputStream* output = tty);
  void print_value(outputStream* output = tty) const;
  void print_activation(int index, outputStream* output = tty) const;
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

  // casting
  static interpretedVFrame* cast(vframe* vf) {
    assert(vf == nullptr || vf->is_interpreted_frame(), "must be interpreted frame");
    return (interpretedVFrame*) vf;
  }

 private:
  static const int bcp_offset;
  intptr_t* locals_addr_at(int offset) const;
  StackValueCollection* stack_data(bool expressions) const;

  friend class vframe;
};


class externalVFrame: public vframe {
 protected:
  externalVFrame(const frame* fr, const RegisterMap* reg_map, JavaThread* thread) : vframe(fr, reg_map, thread) {}

#ifndef PRODUCT
 public:
  // printing operations
  void print_value(outputStream* output = tty) const;
  void print(outputStream* output = tty);
#endif
  friend class vframe;
};

class entryVFrame: public externalVFrame {
 public:
  bool is_entry_frame() const { return true; }

 protected:
  entryVFrame(const frame* fr, const RegisterMap* reg_map, JavaThread* thread);

#ifndef PRODUCT
 public:
  // printing
  void print_value(outputStream* output = tty) const;
  void print(outputStream* output = tty);
#endif
  friend class vframe;
};


// A MonitorInfo is a ResourceObject that describes the pair:
// 1) the owner of the monitor
// 2) the monitor lock
class MonitorInfo : public ResourceObj {
 private:
  Handle     _owner; // the object owning the monitor
  BasicLock* _lock;
  Handle     _owner_klass; // klass (mirror) if owner was scalar replaced
  bool       _eliminated;
  bool       _owner_is_scalar_replaced;
 public:
  // Constructor
  MonitorInfo(oop owner, BasicLock* lock, bool eliminated, bool owner_is_scalar_replaced);
  // Accessors
  oop owner() const {
    assert(!_owner_is_scalar_replaced, "should not be called for scalar replaced object");
    return _owner();
  }
  oop owner_klass() const {
    assert(_owner_is_scalar_replaced, "should not be called for not scalar replaced object");
    return _owner_klass();
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

  // For compiled_mode
  int _decode_offset;
  int _sender_decode_offset;
  int _vframe_id;

  // Cached information
  Method* _method;
  int       _bci;
  ContinuationEntry* _cont_entry;

  // Should VM activations be ignored or not
  bool _stop_at_java_call_stub;
  Handle _continuation_scope; // stop at bottom of continuation with this scope

  bool fill_in_compiled_inlined_sender();
  void fill_from_compiled_frame(int decode_offset);
  void fill_from_compiled_native_frame();

  void fill_from_interpreter_frame();
  bool fill_from_frame();

  // Helper routine for security_get_caller_frame
  void skip_prefixed_method_and_wrappers();

  DEBUG_ONLY(void found_bad_method_frame() const;)

 public:
  // Constructor
  inline vframeStreamCommon(RegisterMap reg_map);

  // Accessors
  Method* method() const { return _method; }
  int bci() const { return _bci; }
  inline intptr_t* frame_id() const;
  address frame_pc() const { return _frame.pc(); }
  inline int vframe_id() const;
  inline int decode_offset() const;
  inline oop continuation() const;

  CodeBlob* cb() const { return _frame.cb();  }
  nmethod*  nm() const {
    assert(cb() != nullptr, "usage");
    return cb()->as_nmethod();
  }

  const RegisterMap* reg_map() { return &_reg_map; }

  javaVFrame* asJavaVFrame();

  // Frame type
  inline bool is_interpreted_frame() const;

  // Iteration
  inline void next();
  void security_next();

  bool at_end() const { return _mode == at_end_mode; }

  // Implements security traversal. Skips depth no. of frame including
  // special security frames and prefixed native methods
  void security_get_caller_frame(int depth);
};

class vframeStream : public vframeStreamCommon {
 public:
  // Constructors
  vframeStream(JavaThread* thread, bool stop_at_java_call_stub = false, bool process_frames = true, bool vthread_carrier = false);

  vframeStream(JavaThread* thread, Handle continuation_scope, bool stop_at_java_call_stub = false);

  vframeStream(oop continuation, Handle continuation_scope = Handle());
};

#endif // SHARE_RUNTIME_VFRAME_HPP
