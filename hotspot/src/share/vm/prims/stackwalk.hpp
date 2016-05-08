/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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


#ifndef SHARE_VM_PRIMS_STACKWALK_HPP
#define SHARE_VM_PRIMS_STACKWALK_HPP

#include "oops/oop.hpp"
#include "runtime/vframe.hpp"

class StackWalkAnchor : public StackObj {
private:
  enum {
    magic_pos = 0
  };

  JavaThread*           _thread;
  vframeStream          _vfst;
  jlong                 _anchor;
public:
  StackWalkAnchor(JavaThread* thread)
    : _thread(thread), _vfst(thread), _anchor(0L) {}

  vframeStream&   vframe_stream()         { return _vfst; }
  JavaThread*     thread()                { return _thread; }

  void setup_magic_on_entry(objArrayHandle frames_array);
  bool check_magic(objArrayHandle frames_array);
  bool cleanup_magic_on_exit(objArrayHandle frames_array);

  bool is_valid_in(Thread* thread, objArrayHandle frames_array) {
    return (_thread == thread && check_magic(frames_array));
  }

  jlong address_value() {
    return (jlong) castable_address(this);
  }

  static StackWalkAnchor* from_current(JavaThread* thread, jlong anchor, objArrayHandle frames_array);
};

class StackWalk : public AllStatic {
private:
  static int fill_in_frames(jlong mode, vframeStream& vfst,
                            int max_nframes, int start_index,
                            objArrayHandle frames_array,
                            int& end_index, TRAPS);

  static void fill_stackframe(Handle stackFrame, const methodHandle& method, int bci);

  static void fill_live_stackframe(Handle stackFrame, const methodHandle& method, int bci,
                                   javaVFrame* jvf, TRAPS);

  static inline bool skip_hidden_frames(int mode) {
    return (mode & JVM_STACKWALK_SHOW_HIDDEN_FRAMES) == 0;
  }
  static inline bool need_method_info(int mode) {
    return (mode & JVM_STACKWALK_FILL_CLASS_REFS_ONLY) == 0;
  }
  static inline bool live_frame_info(int mode) {
    return (mode & JVM_STACKWALK_FILL_LIVE_STACK_FRAMES) != 0;
  }

public:
  static inline bool use_frames_array(int mode) {
    return (mode & JVM_STACKWALK_FILL_CLASS_REFS_ONLY) == 0;
  }
  static oop walk(Handle stackStream, jlong mode,
                  int skip_frames, int frame_count, int start_index,
                  objArrayHandle frames_array,
                  TRAPS);

  static jint moreFrames(Handle stackStream, jlong mode, jlong magic,
                         int frame_count, int start_index,
                         objArrayHandle frames_array,
                         TRAPS);
};
#endif // SHARE_VM_PRIMS_STACKWALK_HPP
