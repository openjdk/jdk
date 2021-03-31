/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_SCOPEGUARD_HPP
#define SHARE_UTILITIES_SCOPEGUARD_HPP

#include <utility>

// The ScopeGuard class is an RAII utility, calling the associated exit
// function when the scope of the guard object ends.  The exit function must
// be copy constructible.
//
// See also: http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2019/p0052r10.pdf
// This ScopeGuard class is loosely based on the scope_exit class in that paper.
template<typename F>
class ScopeGuard {
  F _exit_function;
  bool _enabled;

public:
  // Construct an enabled guard object with the given exit function.
  ScopeGuard(F&& exit_function) :
    _exit_function(std::forward<F>(exit_function)),
    _enabled(true)
  {}

  // Call the exit function if the guard is enabled.
  ~ScopeGuard() { if (_enabled) _exit_function(); }

  // Construct a guard object with the same exit function and enabled state
  // as the moved from object.  The moved from object is implicitly disabled.
  ScopeGuard(ScopeGuard&& from) :
    _exit_function(std::move(from._exit_function)),
    _enabled(from._enabled)
  {
    from.release();
  }

  ScopeGuard(const ScopeGuard&) = delete;
  ScopeGuard& operator=(const ScopeGuard&) = delete;
  ScopeGuard& operator=(ScopeGuard&&) = delete;

  // Disable invocation of the exit function.
  void release() { _enabled = false; }
};

// Factory function for a ScopeGuard object with the indicated exit
// function.  Typical usage is
//   auto g = make_guard([&] { ... cleanup ... });
template<typename F>
inline ScopeGuard<F> make_guard(F&& exit_function) {
  return ScopeGuard<F>(std::forward<F>(exit_function));
}

#endif // SHARE_UTILITIES_SCOPEGUARD_HPP
