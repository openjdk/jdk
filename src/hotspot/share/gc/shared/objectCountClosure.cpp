#include "gc/shared/gcId.hpp"
#include "gc/shared/objectCountClosure.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/heapInspection.hpp"
#include "utilities/macros.hpp"
#include "utilities/ticks.hpp"

class KlassInfoEntry;
class Klass;

KlassInfoTable ObjectCountClosure::cit(false);

void ObjectCountClosure::reset_table() {
    if (!check_table_exists()) {
        return;
    }
    cit.~KlassInfoTable();
    ::new((void*)&cit) KlassInfoTable(false);
}

bool ObjectCountClosure::check_table_exists() {
    return !cit.allocation_failed();
}

bool ObjectCountClosure::record_object(oop o) {
    if (!check_table_exists()) {
        return false;
    }
    cit.record_instance(o);
    return true;
}

KlassInfoTable* ObjectCountClosure::get_table() {
    return check_table_exists() ? &cit : nullptr;
}
