/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2025 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_AUDIO_LAYOUT_TYPES_H
#define SPA_AUDIO_LAYOUT_TYPES_H

#include <spa/utils/type.h>
#include <spa/utils/string.h>
#include <spa/param/audio/layout.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * \addtogroup spa_param
 * \{
 */

#ifndef SPA_API_AUDIO_LAYOUT_TYPES
 #ifdef SPA_API_IMPL
  #define SPA_API_AUDIO_LAYOUT_TYPES SPA_API_IMPL
 #else
  #define SPA_API_AUDIO_LAYOUT_TYPES static inline
 #endif
#endif

static const struct spa_type_audio_layout_info {
	const char *name;
	struct spa_audio_layout_info layout;
} spa_type_audio_layout_info[] = {
	{ "Mono",  { SPA_AUDIO_LAYOUT_Mono } },
	{ "Stereo",  { SPA_AUDIO_LAYOUT_Stereo } },
	{ "Quad", { SPA_AUDIO_LAYOUT_Quad } },
	{ "Pentagonal", { SPA_AUDIO_LAYOUT_Pentagonal } },
	{ "Hexagonal", { SPA_AUDIO_LAYOUT_Hexagonal } },
	{ "Octagonal", { SPA_AUDIO_LAYOUT_Octagonal } },
	{ "Cube", { SPA_AUDIO_LAYOUT_Cube } },
	{ "MPEG-1.0", { SPA_AUDIO_LAYOUT_MPEG_1_0 } },
	{ "MPEG-2.0", { SPA_AUDIO_LAYOUT_MPEG_2_0 } },
	{ "MPEG-3.0A", { SPA_AUDIO_LAYOUT_MPEG_3_0A } },
	{ "MPEG-3.0B", { SPA_AUDIO_LAYOUT_MPEG_3_0B } },
	{ "MPEG-4.0A", { SPA_AUDIO_LAYOUT_MPEG_4_0A } },
	{ "MPEG-4.0B", { SPA_AUDIO_LAYOUT_MPEG_4_0B } },
	{ "MPEG-5.0A", { SPA_AUDIO_LAYOUT_MPEG_5_0A } },
	{ "MPEG-5.0B", { SPA_AUDIO_LAYOUT_MPEG_5_0B } },
	{ "MPEG-5.0C", { SPA_AUDIO_LAYOUT_MPEG_5_0C } },
	{ "MPEG-5.0D", { SPA_AUDIO_LAYOUT_MPEG_5_0D } },
	{ "MPEG-5.1A", { SPA_AUDIO_LAYOUT_MPEG_5_1A } },
	{ "MPEG-5.1B", { SPA_AUDIO_LAYOUT_MPEG_5_1B } },
	{ "MPEG-5.1C", { SPA_AUDIO_LAYOUT_MPEG_5_1C } },
	{ "MPEG-5.1D", { SPA_AUDIO_LAYOUT_MPEG_5_1D } },
	{ "MPEG-6.1A", { SPA_AUDIO_LAYOUT_MPEG_6_1A } },
	{ "MPEG-7.1A", { SPA_AUDIO_LAYOUT_MPEG_7_1A } },
	{ "MPEG-7.1B", { SPA_AUDIO_LAYOUT_MPEG_7_1B } },
	{ "MPEG-7.1C", { SPA_AUDIO_LAYOUT_MPEG_7_1C } },
	{ "2.1", { SPA_AUDIO_LAYOUT_2_1 } },
	{ "2RC", { SPA_AUDIO_LAYOUT_2RC } },
	{ "2FC", { SPA_AUDIO_LAYOUT_2FC } },
	{ "3.1", { SPA_AUDIO_LAYOUT_3_1 } },
	{ "4.0", { SPA_AUDIO_LAYOUT_4_0 } },
	{ "2.2", { SPA_AUDIO_LAYOUT_2_2 } },
	{ "4.1", { SPA_AUDIO_LAYOUT_4_1 } },
	{ "5.0", { SPA_AUDIO_LAYOUT_5_0 } },
	{ "5.0R", { SPA_AUDIO_LAYOUT_5_0R } },
	{ "5.1", { SPA_AUDIO_LAYOUT_5_1 } },
	{ "5.1R", { SPA_AUDIO_LAYOUT_5_1R } },
	{ "6.0", { SPA_AUDIO_LAYOUT_6_0 } },
	{ "6.0F", { SPA_AUDIO_LAYOUT_6_0F } },
	{ "6.1", { SPA_AUDIO_LAYOUT_6_1 } },
	{ "6.1F", { SPA_AUDIO_LAYOUT_6_1F } },
	{ "7.0", { SPA_AUDIO_LAYOUT_7_0 } },
	{ "7.0F", { SPA_AUDIO_LAYOUT_7_0F } },
	{ "7.1", { SPA_AUDIO_LAYOUT_7_1 } },
	{ "7.1W", { SPA_AUDIO_LAYOUT_7_1W } },
	{ "7.1WR", { SPA_AUDIO_LAYOUT_7_1WR } },
	{ NULL, { 0, { SPA_AUDIO_CHANNEL_UNKNOWN } } },
};

SPA_API_AUDIO_LAYOUT_TYPES int
spa_audio_layout_info_parse_name(struct spa_audio_layout_info *layout, size_t size,
		const char *name)
{
	uint32_t max_position = SPA_AUDIO_LAYOUT_INFO_MAX_POSITION(size);
	if (spa_strstartswith(name, "AUX")) {
		uint32_t i, n_pos;
		if (spa_atou32(name+3, &n_pos, 10)) {
			if (n_pos > max_position)
				return -ECHRNG;
			for (i = 0; i < 0x1000 && i < n_pos; i++)
				layout->position[i] = SPA_AUDIO_CHANNEL_AUX0 + i;
			for (; i < n_pos; i++)
				layout->position[i] = SPA_AUDIO_CHANNEL_UNKNOWN;
			layout->n_channels = n_pos;
			return n_pos;
		}
	}
	SPA_FOR_EACH_ELEMENT_VAR(spa_type_audio_layout_info, i) {
		if (spa_streq(name, i->name)) {
			if (i->layout.n_channels > max_position)
				return -ECHRNG;
			*layout = i->layout;
			return i->layout.n_channels;
		}
	}
	return -ENOTSUP;
}

/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_AUDIO_LAYOUT_TYPES_H */
