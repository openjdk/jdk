/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2018 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_DICT_H
#define SPA_DICT_H

#ifdef __cplusplus
extern "C" {
#endif

#include <string.h>

#include <spa/utils/defs.h>

#ifndef SPA_API_DICT
 #ifdef SPA_API_IMPL
  #define SPA_API_DICT SPA_API_IMPL
 #else
  #define SPA_API_DICT static inline
 #endif
#endif

/**
 * \defgroup spa_dict Dictionary
 * Dictionary data structure
 */

/**
 * \addtogroup spa_dict
 * \{
 */

struct spa_dict_item {
    const char *key;
    const char *value;
};

#define SPA_DICT_ITEM(key,value) ((struct spa_dict_item) { (key), (value) })
#define SPA_DICT_ITEM_INIT(key,value) SPA_DICT_ITEM(key,value)

struct spa_dict {
#define SPA_DICT_FLAG_SORTED    (1<<0)        /**< items are sorted */
    uint32_t flags;
    uint32_t n_items;
    const struct spa_dict_item *items;
};

#define SPA_DICT(items,n_items) ((struct spa_dict) { 0, (n_items), (items) })
#define SPA_DICT_ARRAY(items) SPA_DICT((items),SPA_N_ELEMENTS(items))
#define SPA_DICT_ITEMS(...) SPA_DICT_ARRAY(((struct spa_dict_item[]) { __VA_ARGS__}))

#define SPA_DICT_INIT(items,n_items) SPA_DICT(items,n_items)
#define SPA_DICT_INIT_ARRAY(items) SPA_DICT_ARRAY(items)

#define spa_dict_for_each(item, dict)                \
    for ((item) = (dict)->items;                \
         (item) < &(dict)->items[(dict)->n_items];        \
         (item)++)

SPA_API_DICT int spa_dict_item_compare(const void *i1, const void *i2)
{
    const struct spa_dict_item *it1 = (const struct spa_dict_item *)i1,
          *it2 = (const struct spa_dict_item *)i2;
    return strcmp(it1->key, it2->key);
}

SPA_API_DICT void spa_dict_qsort(struct spa_dict *dict)
{
    if (dict->n_items > 0)
        qsort((void*)dict->items, dict->n_items, sizeof(struct spa_dict_item),
                spa_dict_item_compare);
    SPA_FLAG_SET(dict->flags, SPA_DICT_FLAG_SORTED);
}

SPA_API_DICT const struct spa_dict_item *spa_dict_lookup_item(const struct spa_dict *dict,
                                   const char *key)
{
    const struct spa_dict_item *item;

    if (SPA_FLAG_IS_SET(dict->flags, SPA_DICT_FLAG_SORTED) &&
            dict->n_items > 0) {
        struct spa_dict_item k = SPA_DICT_ITEM_INIT(key, NULL);
        item = (const struct spa_dict_item *)bsearch(&k,
                (const void *) dict->items, dict->n_items,
                sizeof(struct spa_dict_item),
                spa_dict_item_compare);
        if (item != NULL)
            return item;
    } else {
        spa_dict_for_each(item, dict) {
            if (!strcmp(item->key, key))
                return item;
        }
    }
    return NULL;
}

SPA_API_DICT const char *spa_dict_lookup(const struct spa_dict *dict, const char *key)
{
    const struct spa_dict_item *item = spa_dict_lookup_item(dict, key);
    return item ? item->value : NULL;
}

/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_DICT_H */
