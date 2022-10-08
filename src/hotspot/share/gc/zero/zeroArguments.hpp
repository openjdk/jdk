#ifndef SHARE_GC_ZERO_ZEROARGUMENTS_HPP
#define SHARE_GC_ZERO_ZEROARGUMENTS_HPP

#include "gc/shared/gcArguments.hpp"

class CollectedHeap;

class ZeroArguments : public GCArguments {
private:
    virtual void initialize_alignments();

    virtual void initialize();
    virtual size_t conservative_max_heap_alignment();
    virtual CollectedHeap* create_heap();
};

#endif // SHARE_GC_ZERO_ZEROARGUMENTS_HPP
