#include "precompiled.hpp"
#include "gc/noop/noopHeap.hpp"
#include "gc/noop/noopInitLogger.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/globalDefinitions.hpp"

void NoopInitLogger::print_gc_specific() {
    // Warn users that non-resizable heap might be better for some configurations.
    // We are not adjusting the heap size by ourselves, because it affects startup time.
    if (InitialHeapSize != MaxHeapSize) {
        log_warning(gc, init)("Consider setting -Xms equal to -Xmx to avoid resizing hiccups");
    }

    // Warn users that AlwaysPreTouch might be better for some configurations.
    // We are not turning this on by ourselves, because it affects startup time.
    if (FLAG_IS_DEFAULT(AlwaysPreTouch) && !AlwaysPreTouch) {
        log_warning(gc, init)("Consider enabling -XX:+AlwaysPreTouch to avoid memory commit hiccups");
    }

    if (UseTLAB) {
        size_t max_tlab = NoopHeap::heap()->max_tlab_size() * HeapWordSize;
        log_info(gc, init)("TLAB Size Max: " SIZE_FORMAT "%s",
                byte_size_in_exact_unit(max_tlab), exact_unit_for_byte_size(max_tlab));
    } else {
        log_info(gc, init)("TLAB: Disabled");
    }
}

void NoopInitLogger::print() {
    NoopInitLogger init_log;
    init_log.print_all();
}
