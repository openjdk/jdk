#ifndef SHARE_JFR_SUPPORT_JFREVENTSAMPLER_HPP
#define SHARE_JFR_SUPPORT_JFREVENTSAMPLER_HPP

#include "jfr/support/jfrAdaptiveSampler.hpp"

template <typename T>
class JfrEventSamplers : public CHeapObj<mtInternal> {
  private:
  T* _samplers[LAST_EVENT_ID + 1];

  public:
  JfrEventSamplers() {
    for (int i = FIRST_EVENT_ID; i <= LAST_EVENT_ID; i++) {
      _samplers[i] = new T((JfrEventId)i);
    }
  }
  ~JfrEventSamplers() {
    for (int i = 0; i <= LAST_EVENT_ID; i++) {
      delete _samplers[i];
      _samplers[i] = NULL;
    }
  }

  T* get_sampler(JfrEventId event_id) {
    return _samplers[event_id];
  }
};

class JfrEventSampler : public AdaptiveSampler {
  private:
  static jlong MIN_SAMPLES_PER_WINDOW;
  static JfrEventSamplers<JfrEventSampler>* _samplers;

  JfrEventId _event_id;

  public:
  JfrEventSampler(JfrEventId event_id) : AdaptiveSampler(80, 160), _event_id(event_id) {}

  SamplerWindowParams new_window_params();

  static JfrEventSampler* for_event(JfrEventId event_id);
  static void initialize();
};

#endif // SHARE_JFR_SUPPORT_JFREVENTSAMPLER_HPP