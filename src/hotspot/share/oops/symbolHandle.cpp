#include "precompiled.hpp"
#include "oops/symbolHandle.hpp"
#include "runtime/atomic.hpp"

Symbol* volatile TempSymbolCleanupDelayer::_queue[QueueSize] = {};
volatile uint TempSymbolCleanupDelayer::_index = 0;

// Keep this symbol alive for some time to allow for reuse.
// Temp symbols for the same string can often be created in quick succession,
// and this queue allows them to be reused instead of churning.
void TempSymbolCleanupDelayer::delay_cleanup(Symbol* sym) {
  assert(sym != nullptr, "precondition");
  sym->increment_refcount();
  uint i = Atomic::add(&_index, 1u) % QueueSize;
  Symbol* old = Atomic::xchg(&_queue[i], sym);
  Symbol::maybe_decrement_refcount(old);
}

void TempSymbolCleanupDelayer::drain_queue() {
  for (uint i = 0; i < QueueSize; i++) {
    Symbol* sym = Atomic::xchg(&_queue[i], (Symbol*) nullptr);
    Symbol::maybe_decrement_refcount(sym);
  }
}
