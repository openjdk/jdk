// Singleton class used by VirtualMemoryView.
#include "utilities/growableArray.hpp"
#include "utilities/nativeCallStack.hpp"
class NativeCallStackStorage : public CHeapObj<mtNMT> {
  struct RefCountedNCS {
    NativeCallStack stack;
    int ref_count;
    RefCountedNCS() : stack(), ref_count(0) {}
    RefCountedNCS(NativeCallStack stack, int ref_count)
    : stack(stack), ref_count(ref_count) {}
  };
  GrowableArrayCHeap<RefCountedNCS, mtNMT> all_the_stacks;
  GrowableArrayCHeap<int, mtNMT> unused_indices;
  bool is_detailed_mode;
public:
  static constexpr const int static_stack_size = 1024;

  int push(const NativeCallStack& stack) {
    if (!is_detailed_mode) {
      all_the_stacks.at_put_grow(0, RefCountedNCS{});
      return 0;
    }
    int len = all_the_stacks.length();
    int idx = stack.calculate_hash() % static_stack_size;
    if (len < idx) {
      all_the_stacks.at_put_grow(idx, RefCountedNCS{stack, 1});
      return idx;
    }
    // Exists and already there? No need for double storage
    RefCountedNCS& pre_existing = all_the_stacks.at(idx);
    if (pre_existing.stack.is_empty()) {
      all_the_stacks.at_put(idx, RefCountedNCS{stack, 1});
      return idx;
    } else if (pre_existing.stack.equals(stack)) {
      pre_existing.ref_count++;
      return idx;
    }
    // There was a collision, check for empty index
    if (unused_indices.length() > 0) {
      int reused_idx = unused_indices.pop();
      all_the_stacks.at(reused_idx) = RefCountedNCS{stack, 1};
      return reused_idx;
    }
    // Just push it
    all_the_stacks.push(RefCountedNCS{stack, 1});
    return len;
  }

  const NativeCallStack& get(int idx) {
    return all_the_stacks.at(idx).stack;
  }

  void increment(int idx) {
    if (idx >= static_stack_size) {
      all_the_stacks.at(idx).ref_count++;
    }
  }

  void decrement(int idx) {
    if (idx < static_stack_size) {
      return;
    }
    RefCountedNCS& rncs = all_the_stacks.at(idx);
    if (rncs.ref_count == 0) {
      return;
    }
    rncs.ref_count--;
    if (rncs.ref_count == 0) {
      unused_indices.push(idx);
    }

    if ((double)unused_indices.length() / (double)all_the_stacks.length() > 0.3) {
      struct {
        void for_each(void* f) {
        }
      } iterator;
      compact(iterator);
    }
  }
  NativeCallStackStorage(int capacity = static_stack_size) : all_the_stacks{capacity}, unused_indices() {
  }

private:
  // Compact the stack storage by reassigning the indices stored in the reserved and committed memory regions.
  template<typename MemoryRegionIterator>
    void compact(MemoryRegionIterator iter) {
      ResourceMark rm;
      // remap[i] = x => stack index i+static_stack_size needs to be remapped to index x
      // side-condition: x > 0
      GrowableArray<int> remap{all_the_stacks.length() - static_stack_size};
      int start = static_stack_size;
      int end = all_the_stacks.length();
      while (end > start) {
        if (all_the_stacks.at(start).ref_count > 0) {
          start++;
          continue;
        }
        if (all_the_stacks.at(end).ref_count == 0) {
          end--;
          continue;
        }
        remap.at_put_grow(end, start, 0);
      }
      // Compute the new size.
      int new_size;
      for (new_size = static_stack_size; all_the_stacks.at(new_size).ref_count > 0; new_size++);
      for (auto thing = iter.begin(); thing != iter.end(); iter++) {
        const int remap_idx = remap.at(*thing);
        if (remap_idx > 0) {
          *thing = remap_idx;
        }
      }
      unused_indices.clear_and_deallocate();
      all_the_stacks.trunc_to(new_size);
      all_the_stacks.shrink_to_fit();
    }
};
