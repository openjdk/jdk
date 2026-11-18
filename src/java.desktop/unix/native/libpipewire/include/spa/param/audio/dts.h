/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2025 Carlos Rafael Giani */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_AUDIO_DTS_H
#define SPA_AUDIO_DTS_H

#include <spa/param/audio/raw.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * \addtogroup spa_param
 * \{
 */

/**
 * Possible high-definition DTS extensions on top of core DTS.
 */
enum spa_audio_dts_ext_type {
	SPA_AUDIO_DTS_EXT_UNKNOWN,
	SPA_AUDIO_DTS_EXT_NONE,		/**< No extension present; this is just regular DTS data */
	SPA_AUDIO_DTS_EXT_HD_HRA,	/**< DTS-HD High Resolution Audio (lossy HD audio extension) */
	SPA_AUDIO_DTS_EXT_HD_MA,	/**< DTS-HD Master Audio (lossless HD audio extension) */
};

/**
 * DTS Coherent Acoustics audio info. Optional extensions on top
 * of the DTS content can be present, resulting in what is known
 * as DTS-HD. \a ext_type specifies which extension is used in
 * combination with the core DTS content (if any).
 */
struct spa_audio_info_dts {
	uint32_t rate;					/*< sample rate */
	uint32_t channels;				/*< number of channels */
	enum spa_audio_dts_ext_type ext_type;		/*< DTS-HD extension type */
};

#define SPA_AUDIO_INFO_DTS_INIT(...)		((struct spa_audio_info_dts) { __VA_ARGS__ })

/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_AUDIO_DTS_H */
