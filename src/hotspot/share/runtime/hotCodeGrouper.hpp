#ifndef SHARE_RUNTIME_HOTCODEGROUPER_HPP
#define SHARE_RUNTIME_HOTCODEGROUPER_HPP

#include "memory/allStatic.hpp"
#include "runtime/nonJavaThread.hpp"
#include "utilities/linkedlist.hpp"
#include "utilities/pair.hpp"

using NMethodList = LinkedListImpl<nmethod*>;
using NMethodListIterator = LinkedListIterator<nmethod*>;
using NMethodPair = Pair<nmethod*, uint64_t>;
using NMethodPairArray = GrowableArray<NMethodPair>;
using NMethodMap = ResizeableHashTable<nmethod*, uint64_t, AnyObj::C_HEAP, mtCode>;

class ThreadSampler;

class HotCodeGrouper : public AllStatic {
 private:
  static void group_nmethods(ThreadSampler& sampler);
  static bool is_code_cache_unstable() {
    // Placeholder for actual implementation to check if the code cache is unstable.
    return false; // For now, we assume the code cache is stable.
  }

  static NonJavaThread *_nmethod_grouper_thread;
  static NMethodList _unregistered_nmethods;
  static bool _is_initialized;

 public:
  static void group_nmethods_loop();
  static void initialize();
  static void unregister_nmethod(nmethod* nm);
  static void register_nmethod(nmethod* nm);

  static NMethodMap _hot_nmethods;
};

#endif // SHARE_RUNTIME_HOTCODEGROUPER_HPP