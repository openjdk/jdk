#ifndef SHARE_GC_NOOP_NOOPARGUMENTS_HPP
#define SHARE_GC_NOOP_NOOPARGUMENTS_HPP

#include "gc/shared/gcArguments.hpp"

class CollectedHeap;

class NoopArguments : public GCArguments {
protected:
    virtual void initialize_alignments();

public:
    virtual void initialize();
    virtual size_t conservative_max_heap_alignment();
    virtual CollectedHeap* create_heap();
};

#endif // SHARE_GC_NOOP_NOOPARGUMENTS_HPP
