#include "precompiled.hpp"
#include "runtime/atomic.hpp"
#include "runtime/samplerSupport.hpp"

// Ordering here is important: _log_table first, _log_table_initialized second.
double SamplerSupport::_log_table[1 << SamplerSupport::FastLogNumBits] = {};

// Force initialization of the log_table.
bool SamplerSupport::_log_table_initialized = init_log_table();

bool SamplerSupport::init_log_table() {
  for (int i = 0; i < (1 << FastLogNumBits); i++) {
    _log_table[i] = (log(1.0 + static_cast<double>(i+0.5) / (1 << FastLogNumBits))
                    / log(2.0));
  }
  return true;
}

// Returns the next prng value.
// pRNG is: aX+b mod c with a = 0x5DEECE66D, b =  0xB, c = 1<<48
// This is the lrand64 generator.
uint64_t SamplerSupport::next_random(uint64_t rnd) {
  const uint64_t PrngMult = 0x5DEECE66DLL;
  const uint64_t PrngAdd = 0xB;
  const uint64_t PrngModPower = 48;
  const uint64_t PrngModMask = ((uint64_t)1 << PrngModPower) - 1;
  //assert(IS_SAFE_SIZE_MUL(PrngMult, rnd), "Overflow on multiplication.");
  //assert(IS_SAFE_SIZE_ADD(PrngMult * rnd, PrngAdd), "Overflow on addition.");
  return (PrngMult * rnd + PrngAdd) & PrngModMask;
}

uint64_t SamplerSupport::next_random() {
  if (_no_sync) {
    // quick path for no-thread safe usage
    _rnd = next_random(_rnd);
    return _rnd;
  }

  uint64_t n_rand = Atomic::load_acquire(&_rnd);
  uint64_t target = 0;
  do {
    target = next_random(n_rand);
    n_rand = Atomic::cmpxchg(&_rnd, n_rand, target, memory_order_acq_rel);
  } while (target != n_rand);
  return n_rand;
}

double SamplerSupport::next_random_uniform() {
  uint64_t n_rand = next_random();
  // Take the top 26 bits as the random number
  // (This plus a 1<<58 sampling bound gives a max possible step of
  // 5194297183973780480 bytes.  In this case,
  // for sample_parameter = 1<<19, max possible step is
  // 9448372 bytes (24 bits).
  const uint64_t PrngModPower = 48;  // Number of bits in prng
  // The uint32_t cast is to prevent a (hard-to-reproduce) NAN
  // under piii debug for some binaries.
  // the n_rand value is between 0 and 2**26-1 so it needs to be normalized by dividing by 2**26 (67108864)
  return (static_cast<uint32_t>(n_rand >> (PrngModPower - 26)) / (double)67108864);
}

double SamplerSupport::fast_log2(const double& d) {
  assert(d>0, "bad value passed to assert");
  uint64_t x = 0;
  assert(sizeof(d) == sizeof(x),
         "double and uint64_t do not have the same size");
  x = *reinterpret_cast<const uint64_t*>(&d);
  const uint32_t x_high = x >> 32;
  assert(FastLogNumBits <= 20, "FastLogNumBits should be less than 20.");
  const uint32_t y = x_high >> (20 - FastLogNumBits) & FastLogMask;
  const int32_t exponent = ((x_high >> 20) & 0x7FF) - 1023;

  assert(_log_table_initialized, "log table should be initialized");
  return exponent + _log_table[y];
}

// Generates a geometric variable with the specified mean.
// This is done by generating a random number between 0 and 1 and applying
// the inverse cumulative distribution function for an exponential.
// Specifically: Let m be the inverse of the sample interval, then
// the probability distribution function is m*exp(-mx) so the CDF is
// p = 1 - exp(-mx), so
// q = 1 - p = exp(-mx)
// log_e(q) = -mx
// -log_e(q)/m = x
// log_2(q) * (-log_e(2) * 1/m) = x
// In the code, q is actually in the range 1 to 2**26, hence the -26 below
size_t SamplerSupport::pick_next_geometric_sample(size_t mean) {
  uint64_t n_rnd = next_random();
  // Take the top 26 bits as the random number
  // (This plus a 1<<58 sampling bound gives a max possible step of
  // 5194297183973780480 bytes.  In this case,
  // for sample_parameter = 1<<19, max possible step is
  // 9448372 bytes (24 bits).
  const uint64_t PrngModPower = 48;  // Number of bits in prng
  // The uint32_t cast is to prevent a (hard-to-reproduce) NAN
  // under piii debug for some binaries.
  double q = static_cast<uint32_t>(n_rnd >> (PrngModPower - 26)) + 1.0;
  // Put the computed p-value through the CDF of a geometric.
  // For faster performance (save ~1/20th exec time), replace
  // min(0.0, FastLog2(q) - 26)  by  (Fastlog2(q) - 26.000705)
  // The value 26.000705 is used rather than 26 to compensate
  // for inaccuracies in FastLog2 which otherwise result in a
  // negative answer.
  double log_val = (fast_log2(q) - 26.000705);
  double result =
      (0.0 < log_val ? 0.0 : log_val) * (-log(2.0) * (mean)) + 1;
  assert(result > 0 && result < SIZE_MAX, "Result is not in an acceptable range.");
  return static_cast<size_t>(result);
}
