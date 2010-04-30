/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * Copyright 2010 Red Hat, Inc.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// This function should match SharkStack::CreateStackOverflowCheck
inline void ZeroStack::overflow_check(int required_words, TRAPS) {
  JavaThread *thread = (JavaThread *) THREAD;

  // Check the Zero stack
  if (required_words > available_words()) {
    handle_overflow(THREAD);
    return;
  }

  // Check the ABI stack
  address stack_top = thread->stack_base() - thread->stack_size();
  int free_stack = ((address) &stack_top) - stack_top;
  if (free_stack < shadow_pages_size()) {
    handle_overflow(THREAD);
    return;
  }
}
