#ifndef SHARE_OOPS_RESOLVEDINVOKEDYNAMICINFO_HPP
#define SHARE_OOPS_RESOLVEDINVOKEDYNAMICINFO_HPP

class Method;
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
        _has_appendix(false) {}
    ResolvedInvokeDynamicInfo(u2 resolved_references_index, u2 cpool_index) : 
                _method(nullptr),
                _resolved_references_index(resolved_references_index),
                _cpool_index(cpool_index),
                _number_of_parameters(0),
                _return_type(0), 
                _has_appendix(false) {}
    bool has_local_signature() const { return true; }
    bool is_final() const { return true; }
    bool is_resolved() const { return _method != nullptr; }
    void print_on(outputStream* st) const;
    void print() const;

    Method* get_method() const { return _method; }
    u2 resolved_references_index() const { return _resolved_references_index; }
    u2 cpool_index() const { return _cpool_index; }
    u2 num_parameters() const { return _number_of_parameters; }
    u1 return_type() const { return _return_type; }
    bool has_appendix() const { return _has_appendix; }

    void init(u2 resolved_references_index, u2 cpool_index) {
        _resolved_references_index = resolved_references_index;
        _cpool_index = cpool_index;
    }

    void fill_in(Method* m, u2 num_params, u1 return_type, bool has_appendix) {
        _method = m;
        _number_of_parameters = num_params; // might be parameter size()
        _return_type = return_type;
        _has_appendix = has_appendix;
     }
};

#endif // SHARE_OOPS_RESOLVEDINVOKEDYNAMICINFO_HPP