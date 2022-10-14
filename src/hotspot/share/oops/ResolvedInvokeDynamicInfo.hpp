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
     Method* _method;
     u2 _resolved_references_index;
     u2 _cpool_index;
     u2 _number_of_parameters;
     u1 _return_type;
     bool _has_appendix;
public:
    ResolvedInvokeDynamicInfo() :
        _method(nullptr),
        _resolved_references_index(0),
        _cpool_index(0),
        _number_of_parameters(0),
        _return_type(0), 
        _has_appendix(0) {}
    ResolvedInvokeDynamicInfo(u2 resolved_references_index, u2 cpool_index) : 
                _method(nullptr),
                _resolved_references_index(resolved_references_index),
                _cpool_index(cpool_index),
                _number_of_parameters(0),
                _return_type(0), 
                _has_appendix(0) {}
    bool has_local_signature() const { return true; }
    bool is_final() const { return true; }
    bool is_resolved() const { return _method != nullptr; }
    void print_on(outputStream* st) const;
};

#endif // SHARE_OOPS_RESOLVEDINVOKEDYNAMICINFO_HPP