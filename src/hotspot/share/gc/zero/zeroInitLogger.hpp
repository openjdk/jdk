#ifndef SHARE_GC_ZERO_ZEROINITLOGGER_HPP
#define SHARE_GC_ZERO_ZEROINITLOGGER_HPP

#include "gc/shared/gcInitLogger.hpp"

class ZeroInitLogger : public GCInitLogger {
protected:
    virtual void print_gc_specific();

public:
    static void print();
};

#endif // SHARE_GC_ZERO_ZEROINITLOGGER_HPP
