#ifndef SHARE_GC_ZERO_ZEROBARRIERSET_HPP
#define SHARE_GC_ZERO_ZEROBARRIERSET_HPP

#include "gc/shared/barrierSet.hpp"

// The barrier set is empty.
class ZeroBarrierSet: public BarrierSet {
    friend class VMStructs;

public:
    ZeroBarrierSet();

    virtual void print_on(outputStream *st) const {}

    virtual void on_thread_create(Thread* thread);
    virtual void on_thread_destroy(Thread* thread);

    template <DecoratorSet decorators, typename BarrierSetT = ZeroBarrierSet>
    class AccessBarrier: public BarrierSet::AccessBarrier<decorators, BarrierSetT> {};
};

template<>
struct BarrierSet::GetName<ZeroBarrierSet> {
    static const BarrierSet::Name value = BarrierSet::ZeroBarrierSet;
};

template<>
struct BarrierSet::GetType<BarrierSet::ZeroBarrierSet> {
    typedef ::ZeroBarrierSet type;
};

#endif // SHARE_GC_ZERO_ZEROBARRIERSET_HPP
