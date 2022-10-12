#ifndef SHARE_GC_NOOP_VMSTRUCTS_NOOP_HPP
#define SHARE_GC_NOOP_VMSTRUCTS_NOOP_HPP

#include "gc/noop/noopHeap.hpp"
#include "gc/shared/space.hpp"
#include "memory/virtualspace.hpp"

#define VM_STRUCTS_NOOPGC(nonstatic_field,                       \
                            volatile_nonstatic_field,               \
                            static_field)                           \
  nonstatic_field(NoopHeap, _virtual_space, VirtualSpace)        \
  nonstatic_field(NoopHeap, _space, ContiguousSpace*)

#define VM_TYPES_NOOPGC(declare_type,                            \
                          declare_toplevel_type,                    \
                          declare_integer_type)                     \
  declare_type(NoopHeap, CollectedHeap)

#define VM_INT_CONSTANTS_NOOPGC(declare_constant,                \
                                  declare_constant_with_value)

#endif // SHARE_GC_NOOP_VMSTRUCTS_NOOP_HPP