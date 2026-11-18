/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2018 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_PARAM_DICT_TYPES_H
#define SPA_PARAM_DICT_TYPES_H

#include <spa/utils/enum-types.h>
#include <spa/param/param-types.h>
#include <spa/param/dict.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * \addtogroup spa_param
 * \{
 */

#define SPA_TYPE_INFO_PARAM_Dict	SPA_TYPE_INFO_PARAM_BASE "Dict"
#define SPA_TYPE_INFO_PARAM_DICT_BASE	SPA_TYPE_INFO_PARAM_Dict ":"

static const struct spa_type_info spa_type_param_dict[] = {
	{ SPA_PARAM_DICT_START, SPA_TYPE_Id, SPA_TYPE_INFO_PARAM_DICT_BASE, spa_type_param, },
	{ SPA_PARAM_DICT_info, SPA_TYPE_Struct, SPA_TYPE_INFO_PARAM_DICT_BASE "info", NULL, },
	{ 0, 0, NULL, NULL },
};

/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_PARAM_DICT_TYPES_H */
