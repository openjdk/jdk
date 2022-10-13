#include "oops/ResolvedInvokeDynamicInfo.hpp"

// ResolvedInvokeDynamicInfo
void ResolvedInvokeDynamicInfo::print_on(outputStream* st) const {
    /*st->print_cr(" - Method: " INTPTR_FORMAT " %s", p2i(method), method->external_name());
    st->print_cr(" - Resolved References Index: ", resolved_references_index);
    st->print_cr(" - CP Index: %d", cpool_index);
    st->print_cr(" - Num Parameters: %d", number_of_parameters);
    st->print_cr(" - Return type: %d", type2name(as_BasicType(return_type)));
    st->print_cr(" - Has Appendix: %d", has_appendix);*/

    // Something like this
    /*oop appendix = appendix_if_resolved(cph);
    if (appendix != NULL) {
        st->print("  appendix: ");
        appendix->print_on(st);
    }*/
}