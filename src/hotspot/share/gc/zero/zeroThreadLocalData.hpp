#ifndef SHARE_GC_ZERO_ZEROTHREADLOCALDATA_HPP
#define SHARE_GC_ZERO_ZEROTHREADLOCALDATA_HPP

#include "gc/shared/gc_globals.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/debug.hpp"

class ZeroThreadLocalData {
private:
    size_t _ergo_tlab_size;
    int64_t _last_tlab_time;

    ZeroThreadLocalData() :
            _ergo_tlab_size(0),
            _last_tlab_time(0) {}

    static ZeroThreadLocalData* data(Thread* thread) {
        assert(UseZeroGC, "Sanity");
        return thread->gc_data<ZeroThreadLocalData>();
    }

public:
    static void create(Thread* thread) {
        new (data(thread)) ZeroThreadLocalData();
    }

    static void destroy(Thread* thread) {
        data(thread)->~ZeroThreadLocalData();
    }

    static size_t ergo_tlab_size(Thread *thread) {
        return data(thread)->_ergo_tlab_size;
    }

    static int64_t last_tlab_time(Thread *thread) {
        return data(thread)->_last_tlab_time;
    }

    static void set_ergo_tlab_size(Thread *thread, size_t val) {
        data(thread)->_ergo_tlab_size = val;
    }

    static void set_last_tlab_time(Thread *thread, int64_t time) {
        data(thread)->_last_tlab_time = time;
    }
};

#endif // SHARE_GC_ZERO_ZEROTHREADLOCALDATA_HPP