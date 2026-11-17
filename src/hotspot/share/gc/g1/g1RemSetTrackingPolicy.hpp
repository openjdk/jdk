/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1REMSETTRACKINGPOLICY_HPP
#define SHARE_GC_G1_G1REMSETTRACKINGPOLICY_HPP

#include "memory/allocation.hpp"

class G1CardSetGroup;
class G1HeapRegion;

class G1RemSetTrackingPolicy : public CHeapObj<mtGC> {
  void log_card_set_group_after_rebuild(G1CardSetGroup* gr);

public:
  // Return whether the given humongous (starts) region's card set group should be rebuilt after the
  // Remark pause.
  bool should_rebuild_humongous(G1HeapRegion* r);
  // Return whether the given old region's card set group should be rebuilt after the Remark pause.
  bool should_rebuild_old(G1HeapRegion* r);
  // Update remembered set tracking state after rebuild is complete, i.e. the Cleanup
  // pause. Returns true if the card set group should be kept, false if not. In the latter case the
  // caller is responsible for deleting it. Called at safepoint.
  bool update_humongous_after_rebuild(G1CardSetGroup* gr);
  // Set the card set group state to complete after rebuilding.
  void update_old_after_rebuild(G1CardSetGroup* gr);

};

#endif // SHARE_GC_G1_G1REMSETTRACKINGPOLICY_HPP
