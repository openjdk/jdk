#ifndef SHARE_GC_NOOP_NOOPBARRIERSET_HPP
#define SHARE_GC_NOOP_NOOPBARRIERSET_HPP

#include "gc/shared/barrierSet.hpp"

// The barrier set is empty.
class NoopBarrierSet: public BarrierSet {
    friend class VMStructs;

public:
    NoopBarrierSet();

    virtual void print_on(outputStream *st) const {}

    virtual void on_thread_create(Thread* thread);
    virtual void on_thread_destroy(Thread* thread);

    template <DecoratorSet decorators, typename BarrierSetT = NoopBarrierSet>
    class AccessBarrier: public BarrierSet::AccessBarrier<decorators, BarrierSetT> {};
};

template<>
struct BarrierSet::GetName<NoopBarrierSet> {
    static const BarrierSet::Name value = BarrierSet::NoopBarrierSet;
};

template<>
struct BarrierSet::GetType<BarrierSet::NoopBarrierSet> {
    typedef ::NoopBarrierSet type;
};

#endif // SHARE_GC_NOOP_NOOPBARRIERSET_HPP
