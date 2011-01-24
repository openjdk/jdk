/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_REFERENCEPOLICY_HPP
#define SHARE_VM_MEMORY_REFERENCEPOLICY_HPP

// referencePolicy is used to determine when soft reference objects
// should be cleared.


class ReferencePolicy : public CHeapObj {
 public:
  virtual bool should_clear_reference(oop p)       { ShouldNotReachHere(); return true; }
  // Capture state (of-the-VM) information needed to evaluate the policy
  virtual void setup() { /* do nothing */ }
};

class NeverClearPolicy : public ReferencePolicy {
 public:
  bool should_clear_reference(oop p) { return false; }
};

class AlwaysClearPolicy : public ReferencePolicy {
 public:
  bool should_clear_reference(oop p) { return true; }
};

class LRUCurrentHeapPolicy : public ReferencePolicy {
 private:
  jlong _max_interval;

 public:
  LRUCurrentHeapPolicy();

  // Capture state (of-the-VM) information needed to evaluate the policy
  void setup();
  bool should_clear_reference(oop p);
};

class LRUMaxHeapPolicy : public ReferencePolicy {
 private:
  jlong _max_interval;

 public:
  LRUMaxHeapPolicy();

  // Capture state (of-the-VM) information needed to evaluate the policy
  void setup();
  bool should_clear_reference(oop p);
};

#endif // SHARE_VM_MEMORY_REFERENCEPOLICY_HPP
