/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2018 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_EVENT_NODE_H
#define SPA_EVENT_NODE_H

#include <spa/pod/event.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * \addtogroup spa_node
 * \{
 */

/* object id of SPA_TYPE_EVENT_Node */
enum spa_node_event {
	SPA_NODE_EVENT_Error,
	SPA_NODE_EVENT_Buffering,
	SPA_NODE_EVENT_RequestRefresh,
	SPA_NODE_EVENT_RequestProcess,		/*< Ask the driver to start processing
						 *  the graph */
	SPA_NODE_EVENT_User,			/* User defined event */
};

#define SPA_NODE_EVENT_ID(ev)	SPA_EVENT_ID(ev, SPA_TYPE_EVENT_Node)
#define SPA_NODE_EVENT_INIT(id) SPA_EVENT_INIT(SPA_TYPE_EVENT_Node, id)

/* properties for SPA_TYPE_EVENT_Node */
enum spa_event_node {
	SPA_EVENT_NODE_START,

	SPA_EVENT_NODE_START_User 	= 0x1000,
	SPA_EVENT_NODE_extra,		/** extra info (String) */

	SPA_EVENT_NODE_START_CUSTOM   	= 0x1000000,
};

/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_EVENT_NODE_H */
