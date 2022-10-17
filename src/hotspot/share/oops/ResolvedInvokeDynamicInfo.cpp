#include "code/compressedStream.hpp"
#include "oops/method.hpp"
#include "oops/ResolvedInvokeDynamicInfo.hpp"

// ResolvedInvokeDynamicInfo
void ResolvedInvokeDynamicInfo::print_on(outputStream* st) const {
    st->print_cr("ResolvedInvokeDynamicInfo:");
    st->print_cr(" - Method: " INTPTR_FORMAT " %s", p2i(get_method()), get_method()->external_name());
    st->print_cr(" - Resolved References Index: %d", resolved_references_index());
    st->print_cr(" - CP Index: %d", cpool_index());
    st->print_cr(" - Num Parameters: %d", num_parameters());
    st->print_cr(" - Return type: %s", type2name(as_BasicType((TosState)return_type())));
    st->print_cr(" - Has Appendix: %d", has_appendix());

    // Something like this
    /*oop appendix = appendix_if_resolved(cph);
    if (appendix != NULL) {
        st->print("  appendix: ");
        appendix->print_on(st);
    }*/
}