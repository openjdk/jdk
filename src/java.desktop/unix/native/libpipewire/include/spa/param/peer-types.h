/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2018 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_PARAM_PEER_TYPES_H
#define SPA_PARAM_PEER_TYPES_H

#include <spa/utils/enum-types.h>
#include <spa/param/param-types.h>
#include <spa/param/peer.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * \addtogroup spa_param
 * \{
 */

#define SPA_TYPE_INFO_PeerParam		SPA_TYPE_INFO_OBJECT_BASE "PeerParam"
#define SPA_TYPE_INFO_PEER_PARAM_BASE	SPA_TYPE_INFO_PeerParam ":"

static const struct spa_type_info spa_type_peer_param[] = {
	{ SPA_PEER_PARAM_START, SPA_TYPE_Id, SPA_TYPE_INFO_PEER_PARAM_BASE, spa_type_param, },
	{ SPA_ID_INVALID, SPA_TYPE_Id, SPA_TYPE_INFO_PEER_PARAM_BASE, NULL, },
	{ 0, 0, NULL, NULL },
};

/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_PARAM_PEER_TYPES_H */
