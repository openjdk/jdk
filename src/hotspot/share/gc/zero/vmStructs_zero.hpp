#ifndef SHARE_GC_ZERO_VMSTRUCTS_ZERO_HPP
#define SHARE_GC_ZERO_VMSTRUCTS_ZERO_HPP

#include "gc/zero/zeroHeap.hpp"
#include "gc/shared/space.hpp"
#include "memory/virtualspace.hpp"

#define VM_STRUCTS_ZEROGC(nonstatic_field,                       \
                            volatile_nonstatic_field,               \
                            static_field)                           \
  nonstatic_field(ZeroHeap, _virtual_space, VirtualSpace)        \
  nonstatic_field(ZeroHeap, _space, ContiguousSpace*)

#define VM_TYPES_ZEROGC(declare_type,                            \
                          declare_toplevel_type,                    \
                          declare_integer_type)                     \
  declare_type(ZeroHeap, CollectedHeap)

#define VM_INT_CONSTANTS_ZEROGC(declare_constant,                \
                                  declare_constant_with_value)

#endif // SHARE_GC_ZERO_VMSTRUCTS_ZERO_HPP