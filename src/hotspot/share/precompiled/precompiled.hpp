#include "classfile/javaClasses.inline.hpp"
#if INCLUDE_SHENANDOAHGC
#include "gc/shenandoah/shenandoahBarrierSet.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#endif
#if INCLUDE_ZGC
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#endif
#include "memory/allocation.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/instanceStackChunkKlass.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "utilities/globalDefinitions.hpp"
