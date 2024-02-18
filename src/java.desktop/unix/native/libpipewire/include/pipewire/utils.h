/* PipeWire */
/* SPDX-FileCopyrightText: Copyright © 2018 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef PIPEWIRE_UTILS_H
#define PIPEWIRE_UTILS_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdlib.h>
#include <string.h>
#include <sys/un.h>
#ifndef _POSIX_C_SOURCE
# include <sys/mount.h>
#endif

#ifndef ENODATA
#define ENODATA 9919
#endif

#include <spa/utils/defs.h>
#include <spa/pod/pod.h>

/** \defgroup pw_utils Utilities
 *
 * Various utility functions
 */

/**
 * \addtogroup pw_utils
 * \{
 */

/** a function to destroy an item */
typedef void (*pw_destroy_t) (void *object);

const char *
pw_split_walk(const char *str, const char *delimiter, size_t *len, const char **state);

char **
pw_split_strv(const char *str, const char *delimiter, int max_tokens, int *n_tokens);

int
pw_split_ip(char *str, const char *delimiter, int max_tokens, char *tokens[]);

void
pw_free_strv(char **str);

char *
pw_strip(char *str, const char *whitespace);

#if !defined(strndupa)
# define strndupa(s, n)                                      \
    ({                                          \
        const char *__old = (s);                          \
        size_t __len = strnlen(__old, (n));                      \
        char *__new = (char *) __builtin_alloca(__len + 1);              \
        memcpy(__new, __old, __len);                          \
        __new[__len] = '\0';                              \
        __new;                                      \
    })
#endif

#if !defined(strdupa)
# define strdupa(s)                                      \
    ({                                          \
        const char *__old = (s);                          \
        size_t __len = strlen(__old) + 1;                      \
        char *__new = (char *) alloca(__len);                      \
        (char *) memcpy(__new, __old, __len);                      \
    })
#endif

SPA_WARN_UNUSED_RESULT
ssize_t pw_getrandom(void *buf, size_t buflen, unsigned int flags);

void pw_random(void *buf, size_t buflen);

#define pw_rand32() ({ uint32_t val; pw_random(&val, sizeof(val)); val; })

void* pw_reallocarray(void *ptr, size_t nmemb, size_t size);

#ifdef PW_ENABLE_DEPRECATED
#define PW_DEPRECATED(v)        (v)
#else
#define PW_DEPRECATED(v)    ({ __typeof__(v) _v SPA_DEPRECATED = (v); (void)_v; (v); })
#endif /* PW_ENABLE_DEPRECATED */

/**
 * \}
 */

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* PIPEWIRE_UTILS_H */
