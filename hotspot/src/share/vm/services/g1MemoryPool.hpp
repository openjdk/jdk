/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

class G1CollectedHeap;

// This file contains the three classes that represent the memory
// pools of the G1 spaces: G1EdenPool, G1SurvivorPool, and
// G1OldGenPool. In G1, unlike our other GCs, we do not have a
// physical space for each of those spaces. Instead, we allocate
// regions for all three spaces out of a single pool of regions (that
// pool basically covers the entire heap). As a result, the eden,
// survivor, and old gen are considered logical spaces in G1, as each
// is a set of non-contiguous regions. This is also reflected in the
// way we map them to memory pools here. The easiest way to have done
// this would have been to map the entire G1 heap to a single memory
// pool. However, it's helpful to show how large the eden and survivor
// get, as this does affect the performance and behavior of G1. Which
// is why we introduce the three memory pools implemented here.
//
// The above approach inroduces a couple of challenging issues in the
// implementation of the three memory pools:
//
// 1) The used space calculation for a pool is not necessarily
// independent of the others. We can easily get from G1 the overall
// used space in the entire heap, the number of regions in the young
// generation (includes both eden and survivors), and the number of
// survivor regions. So, from that we calculate:
//
//  survivor_used = survivor_num * region_size
//  eden_used     = young_region_num * region_size - survivor_used
//  old_gen_used  = overall_used - eden_used - survivor_used
//
// Note that survivor_used and eden_used are upper bounds. To get the
// actual value we would have to iterate over the regions and add up
// ->used(). But that'd be expensive. So, we'll accept some lack of
// accuracy for those two. But, we have to be careful when calculating
// old_gen_used, in case we subtract from overall_used more then the
// actual number and our result goes negative.
//
// 2) Calculating the used space is straightforward, as described
// above. However, how do we calculate the committed space, given that
// we allocate space for the eden, survivor, and old gen out of the
// same pool of regions? One way to do this is to use the used value
// as also the committed value for the eden and survivor spaces and
// then calculate the old gen committed space as follows:
//
//  old_gen_committed = overall_committed - eden_committed - survivor_committed
//
// Maybe a better way to do that would be to calculate used for eden
// and survivor as a sum of ->used() over their regions and then
// calculate committed as region_num * region_size (i.e., what we use
// to calculate the used space now). This is something to consider
// in the future.
//
// 3) Another decision that is again not straightforward is what is
// the max size that each memory pool can grow to. Right now, we set
// that the committed size for the eden and the survivors and
// calculate the old gen max as follows (basically, it's a similar
// pattern to what we use for the committed space, as described
// above):
//
//  old_gen_max = overall_max - eden_max - survivor_max
//
// 4) Now, there is a very subtle issue with all the above. The
// framework will call get_memory_usage() on the three pools
// asynchronously. As a result, each call might get a different value
// for, say, survivor_num which will yield inconsistent values for
// eden_used, survivor_used, and old_gen_used (as survivor_num is used
// in the calculation of all three). This would normally be
// ok. However, it's possible that this might cause the sum of
// eden_used, survivor_used, and old_gen_used to go over the max heap
// size and this seems to sometimes cause JConsole (and maybe other
// clients) to get confused. There's not a really an easy / clean
// solution to this problem, due to the asynchrounous nature of the
// framework.


// This class is shared by the three G1 memory pool classes
// (G1EdenPool, G1SurvivorPool, G1OldGenPool). Given that the way we
// calculate used / committed bytes for these three pools is related
// (see comment above), we put the calculations in this class so that
// we can easily share them among the subclasses.
class G1MemoryPoolSuper : public CollectedMemoryPool {
private:
  G1CollectedHeap* _g1h;

  // It returns x - y if x > y, 0 otherwise.
  // As described in the comment above, some of the inputs to the
  // calculations we have to do are obtained concurrently and hence
  // may be inconsistent with each other. So, this provides a
  // defensive way of performing the subtraction and avoids the value
  // going negative (which would mean a very large result, given that
  // the parameter are size_t).
  static size_t subtract_up_to_zero(size_t x, size_t y) {
    if (x > y) {
      return x - y;
    } else {
      return 0;
    }
  }

protected:
  // Would only be called from subclasses.
  G1MemoryPoolSuper(G1CollectedHeap* g1h,
                    const char* name,
                    size_t init_size,
                    size_t max_size,
                    bool support_usage_threshold);

  // The reason why all the code is in static methods is so that it
  // can be safely called from the constructors of the subclasses.

  static size_t overall_committed(G1CollectedHeap* g1h) {
    return g1h->capacity();
  }
  static size_t overall_used(G1CollectedHeap* g1h) {
    return g1h->used_unlocked();
  }

  static size_t eden_space_committed(G1CollectedHeap* g1h);
  static size_t eden_space_used(G1CollectedHeap* g1h);
  static size_t eden_space_max(G1CollectedHeap* g1h);

  static size_t survivor_space_committed(G1CollectedHeap* g1h);
  static size_t survivor_space_used(G1CollectedHeap* g1h);
  static size_t survivor_space_max(G1CollectedHeap* g1h);

  static size_t old_space_committed(G1CollectedHeap* g1h);
  static size_t old_space_used(G1CollectedHeap* g1h);
  static size_t old_space_max(G1CollectedHeap* g1h);

  // The non-static versions are included for convenience.

  size_t eden_space_committed() {
    return eden_space_committed(_g1h);
  }
  size_t eden_space_used() {
    return eden_space_used(_g1h);
  }
  size_t eden_space_max() {
    return eden_space_max(_g1h);
  }

  size_t survivor_space_committed() {
    return survivor_space_committed(_g1h);
  }
  size_t survivor_space_used() {
    return survivor_space_used(_g1h);
  }
  size_t survivor_space_max() {
    return survivor_space_max(_g1h);
  }

  size_t old_space_committed() {
    return old_space_committed(_g1h);
  }
  size_t old_space_used() {
    return old_space_used(_g1h);
  }
  size_t old_space_max() {
    return old_space_max(_g1h);
  }
};

// Memory pool that represents the G1 eden.
class G1EdenPool : public G1MemoryPoolSuper {
public:
  G1EdenPool(G1CollectedHeap* g1h);

  size_t used_in_bytes() {
    return eden_space_used();
  }
  size_t max_size() {
    return eden_space_max();
  }
  MemoryUsage get_memory_usage();
};

// Memory pool that represents the G1 survivor.
class G1SurvivorPool : public G1MemoryPoolSuper {
public:
  G1SurvivorPool(G1CollectedHeap* g1h);

  size_t used_in_bytes() {
    return survivor_space_used();
  }
  size_t max_size() {
    return survivor_space_max();
  }
  MemoryUsage get_memory_usage();
};

// Memory pool that represents the G1 old gen.
class G1OldGenPool : public G1MemoryPoolSuper {
public:
  G1OldGenPool(G1CollectedHeap* g1h);

  size_t used_in_bytes() {
    return old_space_used();
  }
  size_t max_size() {
    return old_space_max();
  }
  MemoryUsage get_memory_usage();
};
