/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2018 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_COMMAND_NODE_H
#define SPA_COMMAND_NODE_H

#include <spa/pod/command.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * \addtogroup spa_node
 * \{
 */

/* object id of SPA_TYPE_COMMAND_Node */
enum spa_node_command {
	SPA_NODE_COMMAND_Suspend,	/**< suspend a node, this removes all configured
					  * formats and closes any devices */
	SPA_NODE_COMMAND_Pause,		/**< pause a node. this makes it stop emitting
					  *  scheduling events */
	SPA_NODE_COMMAND_Start,		/**< start a node, this makes it start emitting
					  *  scheduling events */
	SPA_NODE_COMMAND_Enable,
	SPA_NODE_COMMAND_Disable,
	SPA_NODE_COMMAND_Flush,
	SPA_NODE_COMMAND_Drain,
	SPA_NODE_COMMAND_Marker,
	SPA_NODE_COMMAND_ParamBegin,	/**< begin a set of parameter enumerations or
					  *  configuration that require the device to
					  *  remain opened, like query formats and then
					  *  set a format */
	SPA_NODE_COMMAND_ParamEnd,	/**< end a transaction */
	SPA_NODE_COMMAND_RequestProcess,/**< Sent to a driver when some other node emitted
					  *  the RequestProcess event. */
	SPA_NODE_COMMAND_User,		/**< User defined command */
};

#define SPA_NODE_COMMAND_ID(cmd)	SPA_COMMAND_ID(cmd, SPA_TYPE_COMMAND_Node)
#define SPA_NODE_COMMAND_INIT(id)	SPA_COMMAND_INIT(SPA_TYPE_COMMAND_Node, id)


/* properties for SPA_TYPE_COMMAND_Node */
enum spa_command_node {
	SPA_COMMAND_NODE_START,

	SPA_COMMAND_NODE_START_User 		= 0x1000,
	SPA_COMMAND_NODE_extra,			/** extra info (String) */

	SPA_COMMAND_NODE_START_CUSTOM   	= 0x1000000,
};


/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_COMMAND_NODE_H */
