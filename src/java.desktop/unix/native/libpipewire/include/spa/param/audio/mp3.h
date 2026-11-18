/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2023 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_AUDIO_MP3_H
#define SPA_AUDIO_MP3_H

#include <spa/param/audio/raw.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * \addtogroup spa_param
 * \{
 */

enum spa_audio_mp3_channel_mode {
	SPA_AUDIO_MP3_CHANNEL_MODE_UNKNOWN,
	/** Mono mode, only used if channel count is 1 */
	SPA_AUDIO_MP3_CHANNEL_MODE_MONO,
	/** Regular stereo mode with two independent channels */
	SPA_AUDIO_MP3_CHANNEL_MODE_STEREO,
	/**
	 * Joint stereo mode, exploiting the similarities between channels
	 * using techniques like mid-side coding
	 */
	SPA_AUDIO_MP3_CHANNEL_MODE_JOINTSTEREO,
	/**
	 * Two mono tracks, different from stereo in that each channel
	 * contains entirely different content (like two different mono songs)
	 */
	SPA_AUDIO_MP3_CHANNEL_MODE_DUAL,
};

struct spa_audio_info_mp3 {
	uint32_t rate;					/*< sample rate in Hz */
	uint32_t channels;				/*< number of channels */
	enum spa_audio_mp3_channel_mode channel_mode;	/*< MP3 channel mode */
};

#define SPA_AUDIO_INFO_MP3_INIT(...)		((struct spa_audio_info_mp3) { __VA_ARGS__ })

/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_AUDIO_MP3_H */
