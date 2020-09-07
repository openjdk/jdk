#include "precompiled.hpp"
#include "jfr/recorder/jfrEventSetting.inline.hpp"
#include "jfr/recorder/service/jfrEventSampler.hpp"

jlong JfrEventSampler::MIN_SAMPLES_PER_WINDOW = 20;
JfrEventSamplers<JfrEventSampler>* JfrEventSampler::_samplers;

SamplerWindowParams JfrEventSampler::new_window_params() {
  SamplerWindowParams params;
  jlong limit = JfrEventSetting::ratelimit(_event_id);
  // a simple heuristic to derive the window size and number of samples per window from the provided rate limit
  double duration = 10; // start with 10ms window
  double samples = (duration * limit) / (double)1000; // duration is in milliseconds and limit in samples per second
  if (samples < MIN_SAMPLES_PER_WINDOW) {
    duration *= (MIN_SAMPLES_PER_WINDOW / samples);
    samples = MIN_SAMPLES_PER_WINDOW;
  }
  params.window_duration = (jlong)duration;
  params.samples_per_window = (jlong)samples;
  
  return params;
}

void JfrEventSampler::initialize() {
  // needs to be called when VM/JFR is ready
  _samplers = new JfrEventSamplers<JfrEventSampler>();
}

JfrEventSampler* JfrEventSampler::for_event(JfrEventId event_id) {
  assert(_samplers != NULL, "JfrEventSampler has not been properly initialized");
  return _samplers != NULL ? _samplers->get_sampler(event_id) : NULL;
}