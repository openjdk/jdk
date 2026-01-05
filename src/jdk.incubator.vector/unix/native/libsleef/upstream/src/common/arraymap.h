//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#ifndef __ARRAYMAP_H__
#define __ARRAYMAP_H__
typedef struct ArrayMap ArrayMap;

ArrayMap *initArrayMap();
void ArrayMap_dispose(ArrayMap *thiz);
int ArrayMap_size(ArrayMap *thiz);
void *ArrayMap_remove(ArrayMap *thiz, uint64_t key);
void *ArrayMap_put(ArrayMap *thiz, uint64_t key, void *value);
void *ArrayMap_get(ArrayMap *thiz, uint64_t key);

uint64_t *ArrayMap_keyArray(ArrayMap *thiz);
void **ArrayMap_valueArray(ArrayMap *thiz);
int ArrayMap_save(ArrayMap *thiz, const char *fn, const char *prefix, const char *idstr);
ArrayMap *ArrayMap_load(const char *fn, const char *prefix, const char *idstr, int doLock);
#endif
