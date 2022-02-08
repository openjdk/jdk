/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_STACKOVERFLOW_HPP
#define SHARE_RUNTIME_STACKOVERFLOW_HPP

#include "utilities/align.hpp"
#include "utilities/debug.hpp"

class JavaThread;

// StackOverflow handling is encapsulated in this class.  This class contains state variables
// for each JavaThread that are used to detect stack overflow though explicit checks or through
// checks in the signal handler when stack banging into guard pages causes a trap.
// The state variables also record whether guard pages are enabled or disabled.

class StackOverflow {
  friend class JVMCIVMStructs;
  friend class JavaThread;
 public:
  // State of the stack guard pages for the containing thread.
  enum StackGuardState {
    stack_guard_unused,         // not needed
    stack_guard_reserved_disabled,
    stack_guard_yellow_reserved_disabled,// disabled (temporarily) after stack overflow
    stack_guard_enabled         // enabled
  };

  StackOverflow() :
    _stack_guard_state(stack_guard_unused),
    _stack_overflow_limit(nullptr),
    _reserved_stack_activation(nullptr),  // stack base not known yet
    _shadow_zone_safe_limit(nullptr),
    _shadow_zone_growth_watermark(nullptr),
    _stack_base(nullptr), _stack_end(nullptr) {}

  // Initialization after thread is started.
  void initialize(address base, address end) {
     _stack_base = base;
     _stack_end = end;
    set_stack_overflow_limit();
    set_shadow_zone_limits();
    set_reserved_stack_activation(base);
  }
 private:

  StackGuardState  _stack_guard_state;

  // Precompute the limit of the stack as used in stack overflow checks.
  // We load it from here to simplify the stack overflow check in assembly.
  address          _stack_overflow_limit;
  address          _reserved_stack_activation;
  address          _shadow_zone_safe_limit;
  address          _shadow_zone_growth_watermark;

  // Support for stack overflow handling, copied down from thread.
  address          _stack_base;
  address          _stack_end;

  address stack_end()  const           { return _stack_end; }
  address stack_base() const           { assert(_stack_base != nullptr, "Sanity check"); return _stack_base; }

  // Stack overflow support
  // --------------------------------------------------------------------------------
  //
  // The Java thread stack is structured as follows:
  //
  //  (low addresses)
  //
  //  --  <-- stack_end()                   ---
  //  |                                      |
  //  |  red zone                            |
  //  |                                      |
  //  --  <-- stack_red_zone_base()          |
  //  |                                      |
  //  |                                     guard
  //  |  yellow zone                        zone
  //  |                                      |
  //  |                                      |
  //  --  <-- stack_yellow_zone_base()       |
  //  |                                      |
  //  |                                      |
  //  |  reserved zone                       |
  //  |                                      |
  //  --  <-- stack_reserved_zone_base()    ---   ---
  //                                               ^
  //                                               |    <--  stack_overflow_limit() [somewhere in here]
  //                                               |  shadow
  //                                               |   zone
  //                                               |   size
  //                                               v
  //                                              ---   <--  shadow_zone_safe_limit()
  // (Here and below: not yet touched stack)
  //
  //
  // (Here and below: touched at least once)      ---
  //                                               ^
  //                                               |  shadow
  //                                               |   zone
  //                                               |   size
  //                                               v
  //                                              ---   <--  shadow_zone_growth_watermark()
  //
  //
  //  --
  //  |
  //  |  shadow zone
  //  |
  //  --
  //  x    frame n
  //  --
  //  x    frame n-1
  //  x
  //  --
  //  ...
  //
  //  --
  //  x    frame 0
  //  --  <-- stack_base()
  //
  //  (high addresses)
  //
  //
  // The stack overflow mechanism detects overflows by touching ("banging") the stack
  // ahead of current stack pointer (SP). The entirety of guard zone is memory protected,
  // therefore such access would trap when touching the guard zone, and one of the following
  // things would happen.
  //
  // Access in the red zone: unrecoverable stack overflow. Crash the VM, generate a report,
  // crash dump, and other diagnostics.
  //
  // Access in the yellow zone: recoverable, reportable stack overflow. Create and throw
  // a StackOverflowError, remove the protection of yellow zone temporarily to let exception
  // handlers run. If exception handlers themselves run out of stack, they will crash VM due
  // to access to red zone.
  //
  // Access in the reserved zone: recoverable, reportable, transparent for privileged methods
  // stack overflow. Perform a stack walk to check if there's a method annotated with
  // @ReservedStackAccess on the call stack. If such method is found, remove the protection of
  // reserved zone temporarily, and let the method run. If not, handle the access like a yellow
  // zone trap.
  //
  // The banging itself happens within the "shadow zone" that extends from the current SP.
  //
  // The goals for properly implemented shadow zone banging are:
  //
  //  a) Allow native/VM methods to run without stack overflow checks within some reasonable
  //     headroom. Default shadow zone size should accommodate the largest normally expected
  //     native/VM stack use.
  //  b) Guarantee the stack overflow checks work even if SP is dangerously close to guard zone.
  //     If SP is very low, banging at the edge of shadow zone (SP+shadow-zone-size) can slip
  //     into adjacent thread stack, or even into other readable memory. This would potentially
  //     pass the check by accident.
  //  c) Allow for incremental stack growth on some OSes. This is enabled by handling traps
  //     from not yet committed thread stacks, even outside the guard zone. The banging should
  //     not allow uncommitted "gaps" on thread stack. See for example the uses of
  //     os::map_stack_shadow_pages().
  //  d) Make sure the stack overflow trap happens in the code that is known to runtime, so
  //     the traps can be reasonably handled: handling a spurious trap from executing Java code
  //     is hard, while properly handling the trap from VM/native code is nearly impossible.
  //
  // The simplest code that satisfies all these requirements is banging the shadow zone
  // page by page at every Java/native method entry.
  //
  // While that code is sufficient, it comes with the large performance cost. This performance
  // cost can be reduced by several *optional* techniques:
  //
  // 1. Guarantee that stack would not take another page. If so, the current bang was
  // enough to verify we are not near the guard zone. This kind of insight is usually only
  // available for compilers that can know the size of the frame exactly.
  //
  // Examples: PhaseOutput::need_stack_bang.
  //
  // 2. Check the current SP in relation to shadow zone safe limit.
  //
  // Define "safe limit" as the highest SP where banging would not touch the guard zone.
  // Then, do the page-by-page bang only if current SP is above that safe limit, OR some
  // OS-es need it to get the stack mapped.
  //
  // Examples: AbstractAssembler::generate_stack_overflow_check, JavaCalls::call_helper,
  // os::stack_shadow_pages_available, os::map_stack_shadow_pages and their uses.
  //
  // 3. Check the current SP in relation to the shadow zone growth watermark.
  //
  // Define "shadow zone growth watermark" as the highest SP where we banged already.
  // Invariant: growth watermark is always above the safe limit, which allows testing
  // for watermark and safe limit at the same time in the most frequent case.
  //
  // Easy and overwhelmingly frequent case: SP is above the growth watermark, and
  // by extension above the safe limit. In this case, we know that the guard zone is far away
  // (safe limit), and that the stack was banged before for stack growth (growth watermark).
  // Therefore, we can skip the banging altogether.
  //
  // Harder cases: SP is below the growth watermark. In might be due to two things:
  // we have not banged the stack for growth (below growth watermark only), or we are
  // close to guard zone (also below safe limit). Do the full banging. Once done, we
  // can adjust the growth watermark, thus recording the bang for stack growth had
  // happened.
  //
  // Examples: TemplateInterpreterGenerator::bang_stack_shadow_pages on x86 and others.

 private:
  // These values are derived from flags StackRedPages, StackYellowPages,
  // StackReservedPages and StackShadowPages.
  static size_t _stack_red_zone_size;
  static size_t _stack_yellow_zone_size;
  static size_t _stack_reserved_zone_size;
  static size_t _stack_shadow_zone_size;

 public:
  static void initialize_stack_zone_sizes();

  static size_t stack_red_zone_size() {
    assert(_stack_red_zone_size > 0, "Don't call this before the field is initialized.");
    return _stack_red_zone_size;
  }

  // Returns base of red zone (one-beyond the highest red zone address, so
  //  itself outside red zone and the highest address of the yellow zone).
  address stack_red_zone_base() const {
    return (address)(stack_end() + stack_red_zone_size());
  }

  // Returns true if address points into the red zone.
  bool in_stack_red_zone(address a) const {
    return a < stack_red_zone_base() && a >= stack_end();
  }

  static size_t stack_yellow_zone_size() {
    assert(_stack_yellow_zone_size > 0, "Don't call this before the field is initialized.");
    return _stack_yellow_zone_size;
  }

  static size_t stack_reserved_zone_size() {
    // _stack_reserved_zone_size may be 0. This indicates the feature is off.
    return _stack_reserved_zone_size;
  }

  // Returns base of the reserved zone (one-beyond the highest reserved zone address).
  address stack_reserved_zone_base() const {
    return (address)(stack_end() +
                     (stack_red_zone_size() + stack_yellow_zone_size() + stack_reserved_zone_size()));
  }

  // Returns true if address points into the reserved zone.
  bool in_stack_reserved_zone(address a) const {
    return (a < stack_reserved_zone_base()) &&
           (a >= (address)((intptr_t)stack_reserved_zone_base() - stack_reserved_zone_size()));
  }

  static size_t stack_yellow_reserved_zone_size() {
    return _stack_yellow_zone_size + _stack_reserved_zone_size;
  }

  // Returns true if a points into either yellow or reserved zone.
  bool in_stack_yellow_reserved_zone(address a) const {
    return (a < stack_reserved_zone_base()) && (a >= stack_red_zone_base());
  }

  // Size of red + yellow + reserved zones.
  static size_t stack_guard_zone_size() {
    return stack_red_zone_size() + stack_yellow_reserved_zone_size();
  }

  static size_t stack_shadow_zone_size() {
    assert(_stack_shadow_zone_size > 0, "Don't call this before the field is initialized.");
    return _stack_shadow_zone_size;
  }

  address shadow_zone_safe_limit() const {
    assert(_shadow_zone_safe_limit != nullptr, "Don't call this before the field is initialized.");
    return _shadow_zone_safe_limit;
  }

  void create_stack_guard_pages();
  void remove_stack_guard_pages();

  void enable_stack_reserved_zone(bool check_if_disabled = false);
  void disable_stack_reserved_zone();
  void enable_stack_yellow_reserved_zone();
  void disable_stack_yellow_reserved_zone();
  void enable_stack_red_zone();
  void disable_stack_red_zone();

  bool stack_guard_zone_unused() const { return _stack_guard_state == stack_guard_unused; }

  bool stack_yellow_reserved_zone_disabled() const {
    return _stack_guard_state == stack_guard_yellow_reserved_disabled;
  }

  size_t stack_available(address cur_sp) const {
    // This code assumes java stacks grow down
    address low_addr; // Limit on the address for deepest stack depth
    if (_stack_guard_state == stack_guard_unused) {
      low_addr = stack_end();
    } else {
      low_addr = stack_reserved_zone_base();
    }
    return cur_sp > low_addr ? cur_sp - low_addr : 0;
  }

  bool stack_guards_enabled() const;

  address reserved_stack_activation() const { return _reserved_stack_activation; }
  void set_reserved_stack_activation(address addr) {
    assert(_reserved_stack_activation == stack_base()
            || _reserved_stack_activation == nullptr
            || addr == stack_base(), "Must not be set twice");
    _reserved_stack_activation = addr;
  }

  // Attempt to reguard the stack after a stack overflow may have occurred.
  // Returns true if (a) guard pages are not needed on this thread, (b) the
  // pages are already guarded, or (c) the pages were successfully reguarded.
  // Returns false if there is not enough stack space to reguard the pages, in
  // which case the caller should unwind a frame and try again.  The argument
  // should be the caller's (approximate) sp.
  bool reguard_stack(address cur_sp);
  // Similar to above but see if current stackpoint is out of the guard area
  // and reguard if possible.
  bool reguard_stack(void);
  bool reguard_stack_if_needed(void);

  void set_stack_overflow_limit() {
    _stack_overflow_limit =
      stack_end() + MAX2(stack_guard_zone_size(), stack_shadow_zone_size());
  }

  void set_shadow_zone_limits() {
    _shadow_zone_safe_limit =
      stack_end() + stack_guard_zone_size() + stack_shadow_zone_size();
    _shadow_zone_growth_watermark =
      stack_base();
  }
};

#endif // SHARE_RUNTIME_STACKOVERFLOW_HPP
