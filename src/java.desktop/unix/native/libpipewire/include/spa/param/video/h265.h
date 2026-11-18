/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2025 Wim Taymans */
/* SPDX-FileCopyrightText: Copyright © 2025 Arun Raghavan */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_VIDEO_H265_H
#define SPA_VIDEO_H265_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * \addtogroup spa_param
 * \{
 */

#include <spa/param/format.h>

enum spa_h265_stream_format {
	SPA_H265_STREAM_FORMAT_UNKNOWN = 0,
	SPA_H265_STREAM_FORMAT_HVC1,
	SPA_H265_STREAM_FORMAT_HEV1,
	SPA_H265_STREAM_FORMAT_BYTESTREAM
};

enum spa_h265_alignment {
	SPA_H265_ALIGNMENT_UNKNOWN = 0,
	SPA_H265_ALIGNMENT_AU,
	SPA_H265_ALIGNMENT_NAL
};

struct spa_video_info_h265 {
	struct spa_rectangle size;
	struct spa_fraction framerate;
	struct spa_fraction max_framerate;
	enum spa_h265_stream_format stream_format;
	enum spa_h265_alignment alignment;
};

/**
 * \}
 */

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* SPA_VIDEO_H265_H */
