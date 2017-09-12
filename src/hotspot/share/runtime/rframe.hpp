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

#ifndef SHARE_VM_RUNTIME_RFRAME_HPP
#define SHARE_VM_RUNTIME_RFRAME_HPP

#include "memory/allocation.hpp"
#include "runtime/frame.inline.hpp"

// rframes ("recompiler frames") decorate stack frames with some extra information
// needed by the recompiler.  The recompiler views the stack (at the time of recompilation)
// as a list of rframes.

class RFrame : public ResourceObj {
 protected:
  const frame _fr;                  // my frame
  JavaThread* const _thread;        // thread where frame resides.
  RFrame* _caller;                  // caller / callee rframes (or NULL)
  RFrame*const _callee;
  const int _num;                   // stack frame number (0 = most recent)
  int _invocations;                 // current invocation estimate (for this frame)
                                    // (i.e., how often was this frame called)
  int _distance;                    // recompilation search "distance" (measured in # of interpreted frames)

  RFrame(frame fr, JavaThread* thread, RFrame*const callee);
  virtual void init() = 0;          // compute invocations, loopDepth, etc.
  void print(const char* name);

 public:

  static RFrame* new_RFrame(frame fr, JavaThread* thread, RFrame*const callee);

  virtual bool is_interpreted() const     { return false; }
  virtual bool is_compiled() const        { return false; }
  int distance() const                    { return _distance; }
  void set_distance(int d);
  int invocations() const                 { return _invocations; }
  int num() const                         { return _num; }
  frame fr() const                        { return _fr; }
  JavaThread* thread() const              { return _thread; }
  virtual int cost() const = 0;           // estimated inlining cost (size)
  virtual Method* top_method() const  = 0;
  virtual javaVFrame* top_vframe() const = 0;
  virtual nmethod* nm() const             { ShouldNotCallThis(); return NULL; }

  RFrame* caller();
  RFrame* callee() const                  { return _callee; }
  RFrame* parent() const;                 // rframe containing lexical scope (if any)
  virtual void print()                    = 0;

  static int computeSends(Method* m);
  static int computeSends(nmethod* nm);
  static int computeCumulSends(Method* m);
  static int computeCumulSends(nmethod* nm);
};

class CompiledRFrame : public RFrame {    // frame containing a compiled method
 protected:
  nmethod*    _nm;
  javaVFrame* _vf;                        // top vframe; may be NULL (for most recent frame)
  Method* _method;                        // top method

  CompiledRFrame(frame fr, JavaThread* thread, RFrame*const  callee);
  void init();
  friend class RFrame;

 public:
  CompiledRFrame(frame fr, JavaThread* thread); // for nmethod triggering its counter (callee == NULL)
  bool is_compiled() const                 { return true; }
  Method* top_method() const               { return _method; }
  javaVFrame* top_vframe() const           { return _vf; }
  nmethod* nm() const                      { return _nm; }
  int cost() const;
  void print();
};

class InterpretedRFrame : public RFrame {    // interpreter frame
 protected:
  javaVFrame* _vf;                           // may be NULL (for most recent frame)
  Method* _method;

  InterpretedRFrame(frame fr, JavaThread* thread, RFrame*const  callee);
  void init();
  friend class RFrame;

 public:
  InterpretedRFrame(frame fr, JavaThread* thread, Method* m); // constructor for method triggering its invocation counter
  bool is_interpreted() const                { return true; }
  Method* top_method() const                 { return _method; }
  javaVFrame* top_vframe() const             { return _vf; }
  int cost() const;
  void print();
};

// treat deoptimized frames as interpreted
class DeoptimizedRFrame : public InterpretedRFrame {
 protected:
  DeoptimizedRFrame(frame fr, JavaThread* thread, RFrame*const  callee);
  friend class RFrame;
 public:
  void print();
};

#endif // SHARE_VM_RUNTIME_RFRAME_HPP
