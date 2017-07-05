/*
 * Copyright 2003-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_serialize.cpp.incl"


// Serialize out the block offset shared array for the shared spaces.

void CompactingPermGenGen::serialize_bts(SerializeOopClosure* soc) {
  _ro_bts->serialize(soc, readonly_bottom, readonly_end);
  _rw_bts->serialize(soc, readwrite_bottom, readwrite_end);
}


// Read/write a data stream for restoring/preserving oop pointers and
// miscellaneous data from/to the shared archive file.

void CompactingPermGenGen::serialize_oops(SerializeOopClosure* soc) {
  int tag = 0;
  soc->do_tag(--tag);

  assert(!UseCompressedOops, "UseCompressedOops doesn't work with shared archive");
  // Verify the sizes of various oops in the system.
  soc->do_tag(sizeof(oopDesc));
  soc->do_tag(sizeof(instanceOopDesc));
  soc->do_tag(sizeof(methodOopDesc));
  soc->do_tag(sizeof(constMethodOopDesc));
  soc->do_tag(sizeof(methodDataOopDesc));
  soc->do_tag(arrayOopDesc::base_offset_in_bytes(T_BYTE));
  soc->do_tag(sizeof(constantPoolOopDesc));
  soc->do_tag(sizeof(constantPoolCacheOopDesc));
  soc->do_tag(objArrayOopDesc::base_offset_in_bytes());
  soc->do_tag(typeArrayOopDesc::base_offset_in_bytes(T_BYTE));
  soc->do_tag(sizeof(symbolOopDesc));
  soc->do_tag(sizeof(klassOopDesc));
  soc->do_tag(sizeof(markOopDesc));
  soc->do_tag(sizeof(compiledICHolderOopDesc));

  // Dump the block offset table entries.
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  CompactingPermGenGen* pg = (CompactingPermGenGen*)gch->perm_gen();
  pg->serialize_bts(soc);
  soc->do_tag(--tag);
  pg->ro_space()->serialize_block_offset_array_offsets(soc);
  soc->do_tag(--tag);
  pg->rw_space()->serialize_block_offset_array_offsets(soc);
  soc->do_tag(--tag);

  // Special case - this oop needed in oop->is_oop() assertions.
  soc->do_ptr((void**)&Universe::_klassKlassObj);
  soc->do_tag(--tag);

  // Dump/restore miscellaneous oops.
  Universe::oops_do(soc, true);
  soc->do_tag(--tag);

  vmSymbols::oops_do(soc, true);               soc->do_tag(--tag);
  CodeCache::oops_do(soc);                     soc->do_tag(--tag);
  soc->do_tag(666);
}
