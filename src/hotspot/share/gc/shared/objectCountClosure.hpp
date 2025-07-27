#ifndef SHARE_GC_SHARED_OBJECTCOUNTCLOSURE_HPP
#define SHARE_GC_SHARED_OBJECTCOUNTCLOSURE_HPP

#include "gc/shared/gcId.hpp"
#include "gc/shared/objectCountEventSender.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/allStatic.hpp"
#include "runtime/mutex.hpp"
#include "memory/heapInspection.hpp"
#include "utilities/macros.hpp"
#include "utilities/ticks.hpp"

class KlassInfoEntry;
class Klass;

class ObjectCountClosure : public AllStatic {
    static KlassInfoTable* cit;

public:
    // Return false if allocation of KlassInfoTable failed.
    static bool check_table_exists();
    // Return false if object could not be recorded in the KlassInfoTable.
    static bool record_object(oop o);
    // Returns the KlassInfoTable if it exists, otherwise returns nullptr.
    static KlassInfoTable* get_table();
    // Clear entries of the KlassInfoTable
    static void reset_table(KlassInfoEntry* entry);

    // Returns true if event is enabled
    template <class Event>
    static bool should_send_event();
};

#endif // SHARE_GC_SHARED_OBJECTCOUNTCLOSURE_HPP
