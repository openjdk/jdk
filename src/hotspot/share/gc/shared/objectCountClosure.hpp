#ifndef SHARE_GC_SHARED_OBJECTCOUNTCLOSURE_HPP
#define SHARE_GC_SHARED_OBJECTCOUNTCLOSURE_HPP

#include "gc/shared/gcId.hpp"
#include "memory/allocation.hpp"
#include "memory/heapInspection.hpp"
#include "gc/shared/objectCountEventSender.hpp"
#include "jfr/jfrEvents.hpp"
#include "utilities/macros.hpp"
#include "utilities/ticks.hpp"

class KlassInfoEntry;
class Klass;

#if INCLUDE_SERVICES
class ObjectCountClosure : public AllStatic {
    static KlassInfoTable* cit;
    // static KlassInfoTable cit;

public:

    static bool check_table_exists();
    static bool record_object(oop o);
    static KlassInfoTable* get_table();
    static void reset_table();

    template <class Event>
    static bool should_send_event();
};
#endif // INCLUDE_SERVICES

#endif // SHARE_GC_SHARED_OBJECTCOUNTCLOSURE_HPP
