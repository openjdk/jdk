
#include "gc/shared/objectCountClosure.hpp"
#include "gc/shared/objectCountEventSender.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/heapInspection.hpp"
#include "utilities/macros.hpp"
#include "utilities/ticks.hpp"

#if INCLUDE_SERVICES

KlassInfoTable* ObjectCountClosure::cit = nullptr;

bool ObjectCountClosure::initialize_table() {
    if (cit == nullptr) {
        static KlassInfoTable temp_table(false);
        cit = &temp_table;
    }
    return !cit->allocation_failed();
}

bool ObjectCountClosure::check_table_exists() {
    if (cit == nullptr) {
        initialize_table();
    }
    return cit != nullptr && !cit->allocation_failed();
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

void ObjectCountClosure::reset_table(KlassInfoEntry* entry) {
    if (!check_table_exists()) {
        return;
    }
    cit->delete_entry(entry);
}

template <class Event>
bool ObjectCountClosure::should_send_event() {
    return ObjectCountEventSender::should_send_event<Event>();
}

template bool ObjectCountClosure::should_send_event<EventObjectCount>();
template bool ObjectCountClosure::should_send_event<EventObjectCountAfterGC>();

#endif // INCLUDE_SERVICES
