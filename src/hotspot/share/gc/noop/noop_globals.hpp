#ifndef SHARE_GC_NOOP_NOOP_GLOBALS_HPP
#define SHARE_GC_NOOP_NOOP_GLOBALS_HPP

#include "runtime/globals_shared.hpp"

//
// Defines all globals flags used by the Noop GC.
//

#define GC_NOOP_FLAGS(develop,                                              \
                         develop_pd,                                        \
                         product,                                           \
                         product_pd,                                        \
                         notproduct,                                        \
                         range,                                             \
                         constraint)                                        \
                                                                            \
  product(size_t, NoopPrintHeapSteps, 20, EXPERIMENTAL,                     \
          "Print heap occupancy stats with this number of steps. "          \
          "0 turns the printing off.")                                      \
          range(0, max_intx)                                                \
                                                                            \
  product(size_t, NoopUpdateCountersStep, 1 * M, EXPERIMENTAL,              \
          "Update heap occupancy counters after allocating this much "      \
          "memory. Higher values would make allocations faster at "         \
          "the expense of lower resolution in heap counters.")              \
          range(1, max_intx)                                                \
                                                                            \
  product(size_t, NoopMaxTLABSize, 4 * M, EXPERIMENTAL,                     \
          "Max TLAB size to use with Noop GC. Larger value improves "       \
          "performance at the expense of per-thread memory waste. This "    \
          "asks TLAB machinery to cap TLAB sizes at this value.")           \
          range(1, max_intx)                                                \
                                                                            \
  product(size_t, NoopTLABDecayTime, 1000, EXPERIMENTAL,                    \
          "TLAB sizing policy decays to initial size after thread had not " \
          "allocated for this long. Time is in milliseconds. Lower value "  \
          "improves memory footprint, but penalizes actively allocating "   \
          "threads.")                                                       \
          range(1, max_intx)                                                \
                                                                            \
  product(size_t, NoopMinHeapExpand, 128 * M, EXPERIMENTAL,                 \
          "Min expansion step for heap. Larger value improves performance " \
          "at the potential expense of memory waste.")                      \
          range(1, max_intx)

// end of GC_NOOP_FLAGS

#endif // SHARE_GC_NOOP_NOOP_GLOBALS_HPP
