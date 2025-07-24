#include "gc/shared/gcId.hpp"
#include "gc/shared/objectCountClosure.hpp"
#include "gc/shared/objectCountEventSender.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/heapInspection.hpp"
#include "memory/universe.hpp"
#include "utilities/macros.hpp"
#include "utilities/ticks.hpp"


KlassInfoTable* ObjectCountClosure::cit = nullptr;

void ObjectCountClosure::reset_table() {
    if (!check_table_exists()) {
        return;
    }
    cit->clear_entries();
  }


bool ObjectCountClosure::check_table_exists() {
    if (cit == nullptr && Universe::is_fully_initialized()) {
        static KlassInfoTable temp_table(false);
        cit = &temp_table;
    }
    return !cit->allocation_failed();
}

bool ObjectCountClosure::record_object(oop o) {
    if (!check_table_exists()) {
        return false;
    }
    return cit->record_instance(o);
}

KlassInfoTable* ObjectCountClosure::get_table() {
    return check_table_exists() ? cit : nullptr;
}


#if INCLUDE_SERVICES
template <class Event>
bool ObjectCountClosure::should_send_event() {
    return ObjectCountEventSender::should_send_event<Event>();
}

template bool ObjectCountClosure::should_send_event<EventObjectCount>();
template bool ObjectCountClosure::should_send_event<EventObjectCountAfterGC>();
#endif // INCLUDE_SERVICES
