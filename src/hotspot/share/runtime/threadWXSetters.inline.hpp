/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Azul Systems, Inc. All rights reserved.
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

#ifndef SHARE_RUNTIME_THREADWXSETTERS_INLINE_HPP
#define SHARE_RUNTIME_THREADWXSETTERS_INLINE_HPP

// No threadWXSetters.hpp

#if INCLUDE_WX

#include "runtime/thread.inline.hpp"
#include "utilities/events.hpp"

#if INCLUDE_WX_OLD

class ThreadWXEnable  {
  Thread* _thread;
  WXMode _old_mode;
public:
  ThreadWXEnable(WXMode new_mode, Thread* thread) :
    _thread(thread),
    _old_mode(_thread ? _thread->enable_wx(new_mode) : WXWrite)
  { }
  ~ThreadWXEnable() {
    if (_thread) {
      _thread->enable_wx(_old_mode);
    }
  }
};
#endif

#if INCLUDE_WX_NEW
class Thread::WXEnable : public StackObj {
  Thread* _thread;
  WXState _old_state;
  WXState _new_state;
  uint   _wx_writes_required;
  const WXEnable* _parent;
#ifdef ASSERT
  const char* _old_file;
  int         _old_line;
#endif

  friend class Thread;

  WXEnable() : _old_state(WXExec), _new_state(WXExec) {
    _thread = nullptr;
    _wx_writes_required = 0;
#if 0
    _parent = nullptr;
#endif
#ifdef ASSERT
    _old_file = __FILE__;
    _old_line = __LINE__;
#endif
  }

public:
  WXEnable(Thread* thread, WXState new_state, const char* FILE, int LINE, bool speculative = false) :
    _thread(thread), _old_state(thread->wx_state()), _new_state(new_state)
  {
#if 0
// FIXME: use array instead, to avoid dangling pointer error.
   WXScope* scope = new (&thread->_wx_scopes[_wx_scope_depth]) WXScope(_wx_scope_depth);
#endif
#if 0
    _parent = thread->wx_scope();
    thread->set_wx_scope(this);
#endif
    WXState old_state = _old_state;
#if 1
    if (old_state == new_state) {
      os::breakpoint();
    } else if (old_state.wx_mode() == new_state.wx_mode()) {
      os::breakpoint();
    }
#endif

#if 0
    assert(old_state.is_lazy() == _parent->_new_state.is_lazy(),
           "lazy state (%s) does not match parent (%s)",
           wx_state_name(old_state),
           wx_state_name(_parent->_new_state));
#endif

#ifdef ASSERT
    _old_file = thread->last_wx_change_file();
    _old_line = thread->last_wx_change_line();
#endif

    // FIXME TODO use state transition table + asserts to verify?
    assert(!new_state.is_lazy() || new_state.wx_mode() == old_state.wx_mode(), "lazy request changed mode");

#ifdef ASSERT
    // Outermost scope?
    if (AssertWX) {
      _wx_writes_required = _thread->wx_writes_required();
      if (old_state == WXWrite && new_state == WXExec) {
        guarantee(_thread->wx_writes_required() > _thread->wx_writes_required_at_last_x2w(),
                  "Unused outer write scope");
      } else if (new_state == WXWrite && old_state != WXWrite) {
        _thread->set_wx_writes_required_at_last_x2w();
      }
    } else {
      _wx_writes_required = 0;
    }
    // TODO: also check for X(W,W) that should be Lazy(W,W)
    // and check for W(X()) where the X is not required
#endif
#if 0
    // FIXME
    if (old_state.wx_mode() != new_state.wx_mode() && !new_state.is_lazy() && !old_state.is_lazy()) {
      thread->inc_wx_depth(1);
    }
#endif
    if (old_state != new_state) {
      _thread->set_wx_state(new_state, FILE, LINE);
    }
    if (speculative) {
      // Here we simulate a single write to make sure the write scope is marked as
      // needed, which satisfies the debug check above for unneeded write scopes.
      assert(new_state == WXWrite, "unexpected state");
      // REQUIRE_THREAD_WX_MODE_WRITE
      _thread->require_wx_mode(WXWrite, FILE, LINE); // inc_wx_writes_required();
    }
  }

  ~WXEnable() {
    Thread* thread = _thread;
    if (thread == nullptr) {
      // root scope
      return;
    }
    WXState cur_state = thread->wx_state();
    WXState new_state = _new_state;

    assert(new_state == cur_state || (new_state.is_lazy() && cur_state.is_lazy()),
           "state not restored by inner scope?");

#ifdef ASSERT
    if (AssertWX) {
      if (new_state == WXWrite) {
        guarantee(thread->wx_writes_required() > _wx_writes_required, "no writes required, use lazy mode?");
      }
    }
#endif

    WXState old_state = _old_state;
    if (old_state.is_lazy()) {
      old_state.set_wx_mode(cur_state.wx_mode());
    }

#if 0
    // FIXME
    if (old_state.wx_mode() != cur_state.wx_mode() && !old_state.is_lazy() && !cur_state.is_lazy()) {
      thread->inc_wx_depth(-1);
    }
#endif

    if (old_state != cur_state) {
      thread->set_wx_state(old_state, __FILE__, __LINE__);
    }
#ifdef ASSERT
    thread->set_last_wx_change_loc(_old_file, _old_line);
#endif
#if 0
// FIXME: use array instead, to avoid dangling pointer error.
#endif
#if 0
    thread->set_wx_scope(_parent);
#endif

#if 0
    assert(old_state.is_lazy() == _parent->_new_state.is_lazy(),
           "lazy state (%s) does not match parent (%s)",
           old_state.name(),
           _parent->_new_state.name());
#endif
  }
};

typedef Thread::WXEnable WXMark;

inline WXMark WXLazyMark(Thread* t, const char* FILE, int LINE) {
  return WXMark(t, t->wx_lazy_state(), FILE, LINE);
}

inline WXMark WXConditionalWriteMark(Thread* t, bool cond, const char* FILE, int LINE) {
  return WXMark(t, cond ? WXWrite : t->wx_state(), FILE, LINE);
}

// This variant is used when we want to set write mode, expecting writes
// to happen, but we can't guarantee it.  We might use this outside a loop
// when there are conditional writes inside the loop, and we don't want to
// slow down the loop with additional scopes.
inline WXMark WXSpeculativeWriteMark(Thread* t, bool cond, const char* FILE, int LINE) {
  return WXMark(t, cond ? WXWrite : t->wx_state(), FILE, LINE, true /* speculative */);
}

#define WXExecMark(t)  WXMark(t, WXExec, __FILE__, __LINE__)
#define WXWriteMark(t) WXMark(t, WXWrite, __FILE__, __LINE__)
#define WXLazyMark(t)  WXLazyMark(t, __FILE__, __LINE__)
#define WXConditionalWriteMark(t, cond)  WXConditionalWriteMark(t, cond, __FILE__, __LINE__)
#define WXSpeculativeWriteMark(t, cond)  WXSpeculativeWriteMark(t, cond, __FILE__, __LINE__)

#ifdef ASSERT
inline void Thread::require_wx_mode(WXMode expected, const char* FILE, int LINE) {
  assert(this == Thread::current(), "should only be called for current thread");
  if (AssertWX) {
    if (wx_state().is_lazy()) {
#if 1
      if (VMError::is_error_reported_in_current_thread()) {
        abort();
      }
#endif
      guarantee(!wx_state().is_lazy(), "definite state required");
    }
    if (expected == WXWrite) {
      inc_wx_writes_required();
    }
    guarantee(wx_state().wx_mode() == expected,
             "unexpected state %s (expected %s) at %s:%d, last set at %s:%d",
              wx_state().name(),
              expected == WXExec ? "WXExec" : "WXWrite",
              FILE, LINE,
              last_wx_change_file(), last_wx_change_line());
  }
}
#endif

#define require_wx_mode(mode) require_wx_mode((mode), __FILE__, __LINE__)

#define REQUIRE_THREAD_WX_MODE_EXEC  Thread::current()->require_wx_mode(WXExec);
#define REQUIRE_THREAD_WX_MODE_WRITE Thread::current()->require_wx_mode(WXWrite);

#endif // INCLUDE_WX_NEW

#else

#define WXMark(t, m)   StackObj()
#define WXExecMark(t)  StackObj()
#define WXWriteMark(t) StackObj()
#define WXLazyMark(t)  StackObj()

#define REQUIRE_THREAD_WX_MODE_EXEC
#define REQUIRE_THREAD_WX_MODE_WRITE

#endif // INCLUDE_WX

#endif // SHARE_RUNTIME_THREADWXSETTERS_INLINE_HPP

