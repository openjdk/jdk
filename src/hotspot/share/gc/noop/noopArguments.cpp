#include "precompiled.hpp"
#include "gc/noop/noopArguments.hpp"
#include "gc/noop/noopHeap.hpp"
#include "gc/shared/gcArguments.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"

size_t NoopArguments::conservative_max_heap_alignment() {
    return UseLargePages ? os::large_page_size() : os::vm_page_size();
}

void NoopArguments::initialize() {
    GCArguments::initialize();

    assert(UseNoopGC, "Sanity");

    // Forcefully exit when OOME is detected. Nothing we can do at that point.
    if (FLAG_IS_DEFAULT(ExitOnOutOfMemoryError)) {
        FLAG_SET_DEFAULT(ExitOnOutOfMemoryError, true);
    }

    if (NoopMaxTLABSize < MinTLABSize) {
        log_warning(gc)("NoopMaxTLABSize < MinTLABSize, adjusting it to " SIZE_FORMAT, MinTLABSize);
        NoopMaxTLABSize = MinTLABSize;
    }

#ifdef COMPILER2
    // Enable loop strip mining: there are still non-GC safepoints, no need to make it worse
  if (FLAG_IS_DEFAULT(UseCountedLoopSafepoints)) {
    FLAG_SET_DEFAULT(UseCountedLoopSafepoints, true);
    if (FLAG_IS_DEFAULT(LoopStripMiningIter)) {
      FLAG_SET_DEFAULT(LoopStripMiningIter, 1000);
    }
  }
#endif
}

void NoopArguments::initialize_alignments() {
    size_t page_size = UseLargePages ? os::large_page_size() : os::vm_page_size();
    size_t align = MAX2((size_t)os::vm_allocation_granularity(), page_size);
    SpaceAlignment = align;
    HeapAlignment  = align;
}

CollectedHeap* NoopArguments::create_heap() {
    return new NoopHeap();
}