/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2025 Carlos Rafael Giani */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_AUDIO_DTS_TYPES_H
#define SPA_AUDIO_DTS_TYPES_H

#include <spa/utils/type.h>
#include <spa/param/audio/dts.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * \addtogroup spa_param
 * \{
 */

#define SPA_TYPE_INFO_AudioDTSExtType		SPA_TYPE_INFO_ENUM_BASE "AudioDTSExtType"
#define SPA_TYPE_INFO_AUDIO_DTS_EXT_TYPE_BASE	SPA_TYPE_INFO_AudioDTSExtType ":"

static const struct spa_type_info spa_type_audio_dts_ext_type[] = {
	{ SPA_AUDIO_DTS_EXT_UNKNOWN, SPA_TYPE_Int, SPA_TYPE_INFO_AUDIO_DTS_EXT_TYPE_BASE "UNKNOWN", NULL },
	{ SPA_AUDIO_DTS_EXT_NONE, SPA_TYPE_Int, SPA_TYPE_INFO_AUDIO_DTS_EXT_TYPE_BASE "NONE", NULL },
	{ SPA_AUDIO_DTS_EXT_HD_HRA, SPA_TYPE_Int, SPA_TYPE_INFO_AUDIO_DTS_EXT_TYPE_BASE "HRA", NULL },
	{ SPA_AUDIO_DTS_EXT_HD_MA, SPA_TYPE_Int, SPA_TYPE_INFO_AUDIO_DTS_EXT_TYPE_BASE "MA", NULL },
	{ 0, 0, NULL, NULL },
};

/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_AUDIO_DTS_TYPES_H */
