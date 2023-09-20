/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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


#ifndef SHARE_PRIMS_STACKWALK_HPP
#define SHARE_PRIMS_STACKWALK_HPP

#include "jvm.h"
#include "oops/oop.hpp"
#include "runtime/continuation.hpp"
#include "runtime/continuationEntry.hpp"
#include "runtime/vframe.hpp"

// BaseFrameStream is an abstract base class for encapsulating the VM-side
// implementation of the StackWalker API.  There are two concrete subclasses:
// - JavaFrameStream:
//     -based on vframeStream; used in most instances
// - LiveFrameStream:
//     -based on javaVFrame; used for retrieving locals/monitors/operands for
//      LiveStackFrame
class BaseFrameStream : public StackObj {
private:
  enum {
    magic_pos = 0
  };

  JavaThread*           _thread;
  Handle                _continuation;
  jlong                 _anchor;

protected:
  void fill_stackframe(Handle stackFrame, const methodHandle& method, TRAPS);
public:
  BaseFrameStream(JavaThread* thread, Handle continuation);

  virtual void    next()=0;
  virtual bool    at_end()=0;

  virtual Method* method()=0;
  virtual int     bci()=0;
  virtual oop     cont()=0; // returns the current continuation (even when walking a thread)

  virtual const RegisterMap* reg_map()=0;

  virtual void    fill_frame(int index, objArrayHandle  frames_array,
                             const methodHandle& method, TRAPS)=0;

  oop continuation() { return _continuation(); }
  void set_continuation(Handle cont);

  void setup_magic_on_entry(objArrayHandle frames_array);
  bool check_magic(objArrayHandle frames_array);
  bool cleanup_magic_on_exit(objArrayHandle frames_array);

  bool is_valid_in(Thread* thread, objArrayHandle frames_array) {
    return (_thread == thread && check_magic(frames_array));
  }

  jlong address_value() {
    return (jlong) this;
  }

  static BaseFrameStream* from_current(JavaThread* thread, jlong magic, objArrayHandle frames_array);
};

class JavaFrameStream : public BaseFrameStream {
private:
  vframeStream          _vfst;
  bool                  _need_method_info;

public:
  JavaFrameStream(JavaThread* thread, jint mode, Handle cont_scope, Handle cont);

  const RegisterMap* reg_map() override { return _vfst.reg_map(); };

  void next()   override;
  bool at_end() override { return _vfst.at_end(); }

  Method* method() override { return _vfst.method(); }
  int bci()        override { return _vfst.bci(); }
  oop cont()       override { return _vfst.continuation(); }

  void fill_frame(int index, objArrayHandle  frames_array,
                  const methodHandle& method, TRAPS) override;
};

class LiveFrameStream : public BaseFrameStream {
private:
  enum {
    MODE_INTERPRETED = 0x01,
    MODE_COMPILED    = 0x02
  };

  Handle              _cont_scope;  // the delimitation of this walk

  RegisterMap*        _map;
  javaVFrame*         _jvf;
  ContinuationEntry*  _cont_entry;

  void fill_live_stackframe(Handle stackFrame, const methodHandle& method, TRAPS);
  static oop create_primitive_slot_instance(StackValueCollection* values,
                                            int i, BasicType type, TRAPS);
  static objArrayHandle monitors_to_object_array(GrowableArray<MonitorInfo*>* monitors,
                                                 TRAPS);
  static objArrayHandle values_to_object_array(StackValueCollection* values, TRAPS);
public:
  LiveFrameStream(JavaThread* thread, RegisterMap* rm, Handle cont_scope, Handle cont);

  const RegisterMap* reg_map() override { return _map; };

  void next()   override;
  bool at_end() override { return _jvf == nullptr; }

  Method* method() override { return _jvf->method(); }
  int bci()        override { return _jvf->bci(); }
  oop cont()       override { return continuation() != nullptr ? continuation(): ContinuationEntry::cont_oop_or_null(_cont_entry, _map->thread()); }

  void fill_frame(int index, objArrayHandle  frames_array,
                  const methodHandle& method, TRAPS) override;
};

class StackWalk : public AllStatic {
private:
  static int fill_in_frames(jint mode, BaseFrameStream& stream,
                            int max_nframes, int start_index,
                            objArrayHandle frames_array,
                            int& end_index, TRAPS);

  static inline bool skip_hidden_frames(jint mode) {
    return (mode & JVM_STACKWALK_SHOW_HIDDEN_FRAMES) == 0;
  }
  static inline bool live_frame_info(jint mode) {
    return (mode & JVM_STACKWALK_FILL_LIVE_STACK_FRAMES) != 0;
  }

public:
  static inline bool need_method_info(jint mode) {
    return (mode & JVM_STACKWALK_CLASS_INFO_ONLY) == 0;
  }

  static oop walk(Handle stackStream, jint mode, int skip_frames, Handle cont_scope, Handle cont,
                  int frame_count, int start_index, objArrayHandle frames_array,
                  TRAPS);

  static oop fetchFirstBatch(BaseFrameStream& stream, Handle stackStream,
                             jint mode, int skip_frames, int frame_count,
                             int start_index, objArrayHandle frames_array, TRAPS);

  static jint fetchNextBatch(Handle stackStream, jint mode, jlong magic,
                             int frame_count, int start_index,
                             objArrayHandle frames_array, TRAPS);

  static void setContinuation(Handle stackStream, jlong magic, objArrayHandle frames_array,
                              Handle cont, TRAPS);
};
#endif // SHARE_PRIMS_STACKWALK_HPP
