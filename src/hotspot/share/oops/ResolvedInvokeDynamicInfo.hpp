#ifndef SHARE_OOPS_RESOLVEDINVOKEDYNAMICINFO_HPP
#define SHARE_OOPS_RESOLVEDINVOKEDYNAMICINFO_HPP

#include "code/compressedStream.hpp"
#include "interpreter/bytecodes.hpp"
#include "memory/allocation.hpp"
#include "oops/array.hpp"
#include "oops/oopHandle.hpp"
#include "runtime/handles.hpp"
#include "utilities/align.hpp"
#include "utilities/constantTag.hpp"
#include "utilities/growableArray.hpp"

class ResolvedInvokeDynamicInfo : public MetaspaceObj {
     Method* method;
     u2 resolved_references_index;
     u2 cpool_index;
     u2 number_of_parameters;
     u1 return_type;
     bool has_appendix;
public:
    bool has_local_signature() const { return true; }
    bool is_final() const { return true; }
    bool is_resolved() const { return method != nullptr; }
    void print_on(outputStream* st) const;
};

#endif // SHARE_OOPS_RESOLVEDINVOKEDYNAMICINFO_HPP