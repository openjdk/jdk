class InvokeDynamicResolvedInfo {
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
    virtual void print_on(outputStream* st) const;
};