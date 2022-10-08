#include "precompiled.hpp"
#include "gc/zero/zeroBarrierSet.hpp"
#include "gc/zero/zeroThreadLocalData.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#ifdef COMPILER1
#include "gc/shared/c1/barrierSetC1.hpp"
#endif
#ifdef COMPILER2
#include "gc/shared/c2/barrierSetC2.hpp"
#endif
#include "runtime/javaThread.hpp"

ZeroBarrierSet::ZeroBarrierSet() : BarrierSet(
        make_barrier_set_assembler<BarrierSetAssembler>(),
        make_barrier_set_c1<BarrierSetC1>(),
        make_barrier_set_c2<BarrierSetC2>(),
        NULL /* barrier_set_nmethod */,
        BarrierSet::FakeRtti(BarrierSet::ZeroBarrierSet)) {}

void ZeroBarrierSet::on_thread_create(Thread *thread) {
    ZeroThreadLocalData::create(thread);
}

void ZeroBarrierSet::on_thread_destroy(Thread *thread) {
    ZeroThreadLocalData::destroy(thread);
}
