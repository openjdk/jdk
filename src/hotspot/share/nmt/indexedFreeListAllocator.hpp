#include "memory/allocation.hpp"
#include "memory/arena.hpp"
#include "utilities/growableArray.hpp"
#include "runtime/os.hpp"

// A free list, growth only, allocator for a specific type E.
// The allocator returns 'pointers' of 4-bytes in size, allowing for
// memory savings if a pointer-heavy self-referential structure is used.
// It is "indexed" as a reference is base + index * sizeof(E).
// It never returns any memory to the system.
template<typename E, MEMFLAGS flag>
class IndexedFreeListAllocator {
public:
  class I {
    friend IndexedFreeListAllocator<E, flag>;
    int32_t _idx;
#ifdef ASSERT
    IndexedFreeListAllocator<E, flag>* _owner;
#endif

  public:
    bool operator !=(I other) {
      return _idx != other._idx;
    }
    bool operator==(I other) {
      return _idx == other._idx;
    }

    I(int32_t idx DEBUG_ONLY(COMMA IndexedFreeListAllocator<E COMMA flag>* owner))
    : _idx(idx), _owner(owner) {}
  };
  static const I nil;

private:
  // A free list allocator element is either a link to the next free space
  // Or an actual element.
  union alignas(E) BackingElement {
    I link;
    char e[sizeof(E)];

    BackingElement() {
      this->link = nil;
    }
  };

  GrowableArrayCHeap<BackingElement, flag> _backing_storage;
  I _free_start;

public:
  IndexedFreeListAllocator(int initial_capacity = 8)
    : _backing_storage(initial_capacity),
      _free_start(I{nil._idx, this}) {}

  template<typename... Args>
  I allocate(Args... args) {
    BackingElement* be;
    int i = -1;
    if (_free_start != nil) {
      // Must point to already existing index
      be = &_backing_storage.at(_free_start._idx);
      i = _free_start._idx;
      _free_start = be->link;
    } else {
      // There are no free elements, allocate a new one.
      i = _backing_storage.append(BackingElement());
      be = _backing_storage.adr_at(i);
    }

    ::new (be) E(args...);
    return I{i DEBUG_ONLY(COMMA this)};
  }

  void free(I i) {
    assert(i == nil || i._owner == this, "attempt to free via wrong allocator");

    BackingElement& be_freed = _backing_storage.at(i._idx);
    be_freed.link = _free_start;
    _free_start = i;
  }

  E& at(I i) {
    assert(i != nil, "null pointer dereference");
    assert(i._owner == this, "attempt to access via wrong allocator");
    return reinterpret_cast<E&>(_backing_storage.at(i._idx).e);
  }

  const E& at(I i) const {
    assert(i != nil, "null pointer dereference");
    assert(i._owner == this, "attempt to access via wrong allocator");
    return reinterpret_cast<const E&>(_backing_storage.at(i._idx).e);
  }
};

template<typename E, MEMFLAGS flag>
const typename IndexedFreeListAllocator<E, flag>::I
    IndexedFreeListAllocator<E, flag>::nil(-1 DEBUG_ONLY(COMMA nullptr));

// A CHeap allocator
template<typename E, MEMFLAGS flag>
class CHeapAllocator {
public:
  struct I {
    E* e;
    bool operator !=(I other) {
      return e != other.e;
    }
    bool operator==(I other) {
      return e == other.e;
    }
  };
  static constexpr const I nil = {nullptr};

  template<typename... Args>
  I allocate(Args... args) {
    void* place = os::malloc(sizeof(E), flag);
    ::new (place) E(args...);
    return I{static_cast<E*>(place)};
  }

  void free(I i) {
    return os::free(i.e);
  }

  E& at(I i) {
    return *i.e;
  };

  const E& at(I i) const {
    return *i.e;
  };
};

// An Arena allocator
template<typename E, MEMFLAGS flag>
class ArenaAllocator {
  Arena _arena;
public:
  ArenaAllocator() : _arena(flag) {}

  struct I {
    E* e;
    bool operator !=(I other) {
      return e != other.e;
    }
    bool operator==(I other) {
      return e == other.e;
    }
  };
  static constexpr const I nil = {nullptr};

  template<typename... Args>
  I allocate(Args... args) {
    void* place = _arena.Amalloc(sizeof(E));
    ::new (place) E(args...);
    return I{static_cast<E*>(place)};
  }

  void free(I i) {
    _arena.Afree(i.e, sizeof(E));
  }

  E& at(I i) {
    return *i.e;
  };

  const E& at(I i) const {
    return *i.e;
  };
};
