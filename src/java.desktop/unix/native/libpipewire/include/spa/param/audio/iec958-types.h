/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2018 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_AUDIO_IEC958_TYPES_H
#define SPA_AUDIO_IEC958_TYPES_H

#ifdef __cplusplus
extern "C" {
#endif

#include <spa/utils/type.h>
#include <spa/param/audio/iec958.h>

/**
 * \addtogroup spa_param
 * \{
 */

#ifndef SPA_API_AUDIO_IEC958_TYPES
 #ifdef SPA_API_IMPL
  #define SPA_API_AUDIO_IEC958_TYPES SPA_API_IMPL
 #else
  #define SPA_API_AUDIO_IEC958_TYPES static inline
 #endif
#endif

#define SPA_TYPE_INFO_AudioIEC958Codec        SPA_TYPE_INFO_ENUM_BASE "AudioIEC958Codec"
#define SPA_TYPE_INFO_AUDIO_IEC958_CODEC_BASE    SPA_TYPE_INFO_AudioIEC958Codec ":"

static const struct spa_type_info spa_type_audio_iec958_codec[] = {
    { SPA_AUDIO_IEC958_CODEC_UNKNOWN, SPA_TYPE_Int, SPA_TYPE_INFO_AUDIO_IEC958_CODEC_BASE "UNKNOWN", NULL },
    { SPA_AUDIO_IEC958_CODEC_PCM, SPA_TYPE_Int, SPA_TYPE_INFO_AUDIO_IEC958_CODEC_BASE "PCM", NULL },
    { SPA_AUDIO_IEC958_CODEC_DTS, SPA_TYPE_Int, SPA_TYPE_INFO_AUDIO_IEC958_CODEC_BASE "DTS", NULL },
    { SPA_AUDIO_IEC958_CODEC_AC3, SPA_TYPE_Int, SPA_TYPE_INFO_AUDIO_IEC958_CODEC_BASE "AC3", NULL },
    { SPA_AUDIO_IEC958_CODEC_MPEG, SPA_TYPE_Int, SPA_TYPE_INFO_AUDIO_IEC958_CODEC_BASE "MPEG", NULL },
    { SPA_AUDIO_IEC958_CODEC_MPEG2_AAC, SPA_TYPE_Int, SPA_TYPE_INFO_AUDIO_IEC958_CODEC_BASE "MPEG2-AAC", NULL },
    { SPA_AUDIO_IEC958_CODEC_EAC3, SPA_TYPE_Int, SPA_TYPE_INFO_AUDIO_IEC958_CODEC_BASE "EAC3", NULL },
    { SPA_AUDIO_IEC958_CODEC_TRUEHD, SPA_TYPE_Int, SPA_TYPE_INFO_AUDIO_IEC958_CODEC_BASE "TrueHD", NULL },
    { SPA_AUDIO_IEC958_CODEC_DTSHD, SPA_TYPE_Int, SPA_TYPE_INFO_AUDIO_IEC958_CODEC_BASE "DTS-HD", NULL },
    { 0, 0, NULL, NULL },
};

SPA_API_AUDIO_IEC958_TYPES uint32_t spa_type_audio_iec958_codec_from_short_name(const char *name)
{
    return spa_type_from_short_name(name, spa_type_audio_iec958_codec, SPA_AUDIO_IEC958_CODEC_UNKNOWN);
}
SPA_API_AUDIO_IEC958_TYPES const char * spa_type_audio_iec958_codec_to_short_name(uint32_t type)
{
    return spa_type_to_short_name(type, spa_type_audio_iec958_codec, "UNKNOWN");
}
/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_AUDIO_RAW_IEC958_TYPES_H */
