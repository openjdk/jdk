#ifndef SHARE_GC_NOOP_NOOPINITLOGGER_HPP
#define SHARE_GC_NOOP_NOOPINITLOGGER_HPP

#include "gc/shared/gcInitLogger.hpp"

class NoopInitLogger : public GCInitLogger {
protected:
    virtual void print_gc_specific();

public:
    static void print();
};

#endif // SHARE_GC_NOOP_NOOPINITLOGGER_HPP
