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
  // Make the index opaque.
  struct I {
    int32_t idx;
    bool operator !=(I other) {
      return idx != other.idx;
    }
    bool operator==(I other) {
      return idx == other.idx;
    }
  };
  static constexpr const I nil = I{-1};
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

  IndexedFreeListAllocator()
  : backing_storage(8),
  free_start(I{0}) {}

  template<typename... Args>
  I allocate(Args... args) {
    int32_t i = free_start.idx;
    backing_storage.at_grow(i);
    BackingElement& be = backing_storage.at(i);
    if (be.link == nil) {
      // Must be at end, simply increment
      free_start.idx += 1;
    } else {
      // Follow the link to the next free element
      free_start = be.link;
    }
    ::new (&be) E(args...);
    return I{i};
  }

  void free(I i) {
    BackingElement& be_freed = backing_storage.at(i.idx);
    be_freed.link = free_start;
    free_start = i;
  }

  E& operator[](I i) {
    return backing_storage.at(i.idx).e;
  }

  E& at(I i) {
    return reinterpret_cast<E>(backing_storage.at(i.idx).e);
  }

  const E& at(I i) const {
    return reinterpret_cast<E>(backing_storage.at(i.idx).e);
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

  E& operator[](I i) {
    return *i.e;
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

  E& operator[](I i) {
    return *i.e;
  }

  E& at(I i) {
    return *i.e;
  };

  const E& at(I i) const {
    return *i.e;
  };
};
