/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2025 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_PARAM_PEER_PARAM_H
#define SPA_PARAM_PEER_PARAM_H

#include <spa/param/param.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * \addtogroup spa_param
 * \{
 */

/** properties for SPA_TYPE_OBJECT_PeerParam */
enum spa_peer_param {
	SPA_PEER_PARAM_START,	/**< id of peer as key, SPA_TYPE_Pod as value */
	SPA_PEER_PARAM_END = 0xfffffffe,
};

struct spa_peer_param_info {
	uint32_t peer_id;
	const struct spa_pod *param;
};
/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_PARAM_PEER_PARAM_H */
