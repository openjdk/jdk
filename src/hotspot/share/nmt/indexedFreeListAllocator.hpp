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
  };
  static constexpr const I nil = I{-1};

private:
  // A free list allocator element is either a link to the next free space
  // Or an actual element.
  union alignas(E) BackingElement {
    I link;
    char e[sizeof(E)];

    BackingElement() {
      this->link = nil;
    }

    BackingElement(I link) {
      this->link = link;
    }
  };

  GrowableArrayCHeap<BackingElement, flag> backing_storage;
  I free_start;

public:
  IndexedFreeListAllocator()
  : backing_storage(8),
  free_start(I{0}) {}

  template<typename... Args>
  I allocate(Args... args) {
    int32_t i = free_start._idx;
    backing_storage.at_grow(i);
    BackingElement& be = backing_storage.at(i);
    if (be.link == nil) {
      // Must be at end, simply increment
      free_start._idx += 1;
    } else {
      // Follow the link to the next free element
      free_start = be.link;
    }
    ::new (&be) E(args...);
    return I{i DEBUG_ONLY(COMMA this)};
  }

  void free(I i) {
    assert(i == nil || i._owner == this, "attempt to free via wrong allocator");

    BackingElement& be_freed = backing_storage.at(i._idx);
    be_freed.link = free_start;
    free_start = i;
  }

  E& at(I i) {
    assert(i != nil, "null pointer dereference");
    assert(i._owner == this, "attempt to access via wrong allocator");
    return reinterpret_cast<E&>(backing_storage.at(i._idx).e);
  }

  const E& at(I i) const {
    assert(i != nil, "null pointer dereference");
    assert(i._owner == this, "attempt to access via wrong allocator");
    return reinterpret_cast<const E&>(backing_storage.at(i._idx).e);
  }
};

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
