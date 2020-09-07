#ifndef SHARE_RUNTIME_SAMPLERSUPPORT_HPP
#define SHARE_RUNTIME_SAMPLERSUPPORT_HPP

class SamplerSupport : public CHeapObj<mtInternal> {
  private:
  static bool _log_table_initialized;

  // Statics for the fast log
  static const int FastLogNumBits = 10;
  static const int FastLogMask = (1 << FastLogNumBits) - 1;

  static double _log_table[1<<FastLogNumBits];  // Constant

  static double fast_log2(const double& d);
  static bool init_log_table();
  static uint64_t next_random(uint64_t rnd);

  uint64_t next_random();

  const bool _no_sync;
    // Cheap random number generator
  volatile uint64_t _rnd;

  public:
  SamplerSupport(bool no_sync = true) : _no_sync(no_sync) {
    _rnd = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(this));
    if (_rnd == 0) {
      _rnd = 1;
    }
  }

  size_t pick_next_geometric_sample(size_t mean);
  
  double next_random_uniform();
};

#endif // SHARE_RUNTIME_SAMPLERSUPPORT_HPP
