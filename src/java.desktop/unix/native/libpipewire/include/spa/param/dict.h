/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2025 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_PARAM_DICT_H
#define SPA_PARAM_DICT_H

#include <spa/param/param.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * \addtogroup spa_param
 * \{
 */

/** properties for SPA_TYPE_OBJECT_ParamDict */
enum spa_param_dict {
	SPA_PARAM_DICT_START,
	SPA_PARAM_DICT_info,		/**< Struct(
					  *      Int: n_items
					  *      (String: key
					  *       String: value)*
					  *  ) */
};

/** helper structure for managing info objects */
struct spa_param_dict_info {
	const struct spa_pod *info;
};

/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_PARAM_DICT_H */
