/* PipeWire */
/* SPDX-FileCopyrightText: Copyright © 2018 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef PIPEWIRE_LOOP_H
#define PIPEWIRE_LOOP_H

#ifdef __cplusplus
extern "C" {
#endif

#include <spa/support/loop.h>
#include <spa/utils/dict.h>

/** \defgroup pw_loop Loop
 *
 * PipeWire loop object provides an implementation of
 * the spa loop interfaces. It can be used to implement various
 * event loops.
 *
 * The members of \ref pw_loop are read-only.
 */

/**
 * \addtogroup pw_loop
 * \{
 */

struct pw_loop {
    struct spa_system *system;        /**< system utils */
    struct spa_loop *loop;            /**< wrapped loop */
    struct spa_loop_control *control;    /**< loop control */
    struct spa_loop_utils *utils;        /**< loop utils */
    const char *name;
};

#ifndef PW_API_LOOP_IMPL
#define PW_API_LOOP_IMPL static inline
#endif

struct pw_loop *
pw_loop_new(const struct spa_dict *props);

void
pw_loop_destroy(struct pw_loop *loop);

int pw_loop_set_name(struct pw_loop *loop, const char *name);

PW_API_LOOP_IMPL int pw_loop_add_source(struct pw_loop *object, struct spa_source *source)
{
    return spa_loop_add_source(object->loop, source);
}
PW_API_LOOP_IMPL int pw_loop_update_source(struct pw_loop *object, struct spa_source *source)
{
    return spa_loop_update_source(object->loop, source);
}
PW_API_LOOP_IMPL int pw_loop_remove_source(struct pw_loop *object, struct spa_source *source)
{
    return spa_loop_remove_source(object->loop, source);
}
PW_API_LOOP_IMPL int pw_loop_invoke(struct pw_loop *object,
                spa_invoke_func_t func, uint32_t seq, const void *data,
                size_t size, bool block, void *user_data)
{
    return spa_loop_invoke(object->loop, func, seq, data, size, block, user_data);
}

PW_API_LOOP_IMPL int pw_loop_get_fd(struct pw_loop *object)
{
    return spa_loop_control_get_fd(object->control);
}
PW_API_LOOP_IMPL void pw_loop_add_hook(struct pw_loop *object,
                struct spa_hook *hook, const struct spa_loop_control_hooks *hooks,
                void *data)
{
    spa_loop_control_add_hook(object->control, hook, hooks, data);
}
PW_API_LOOP_IMPL void pw_loop_enter(struct pw_loop *object)
{
    spa_loop_control_enter(object->control);
}
PW_API_LOOP_IMPL void pw_loop_leave(struct pw_loop *object)
{
    spa_loop_control_leave(object->control);
}
PW_API_LOOP_IMPL int pw_loop_iterate(struct pw_loop *object,
                int timeout)
{
    return spa_loop_control_iterate_fast(object->control, timeout);
}

PW_API_LOOP_IMPL struct spa_source *
pw_loop_add_io(struct pw_loop *object, int fd, uint32_t mask,
                bool close, spa_source_io_func_t func, void *data)
{
    return spa_loop_utils_add_io(object->utils, fd, mask, close, func, data);
}
PW_API_LOOP_IMPL int pw_loop_update_io(struct pw_loop *object,
                struct spa_source *source, uint32_t mask)
{
    return spa_loop_utils_update_io(object->utils, source, mask);
}
PW_API_LOOP_IMPL struct spa_source *
pw_loop_add_idle(struct pw_loop *object, bool enabled,
                spa_source_idle_func_t func, void *data)
{
    return spa_loop_utils_add_idle(object->utils, enabled, func, data);
}
PW_API_LOOP_IMPL int pw_loop_enable_idle(struct pw_loop *object,
                struct spa_source *source, bool enabled)
{
    return spa_loop_utils_enable_idle(object->utils, source, enabled);
}
PW_API_LOOP_IMPL struct spa_source *
pw_loop_add_event(struct pw_loop *object, spa_source_event_func_t func, void *data)
{
    return spa_loop_utils_add_event(object->utils, func, data);
}
PW_API_LOOP_IMPL int pw_loop_signal_event(struct pw_loop *object,
                struct spa_source *source)
{
    return spa_loop_utils_signal_event(object->utils, source);
}
PW_API_LOOP_IMPL struct spa_source *
pw_loop_add_timer(struct pw_loop *object, spa_source_timer_func_t func, void *data)
{
    return spa_loop_utils_add_timer(object->utils, func, data);
}
PW_API_LOOP_IMPL int pw_loop_update_timer(struct pw_loop *object,
                struct spa_source *source, struct timespec *value,
                struct timespec *interval, bool absolute)
{
    return spa_loop_utils_update_timer(object->utils, source, value, interval, absolute);
}
PW_API_LOOP_IMPL struct spa_source *
pw_loop_add_signal(struct pw_loop *object, int signal_number,
                spa_source_signal_func_t func, void *data)
{
    return spa_loop_utils_add_signal(object->utils, signal_number, func, data);
}
PW_API_LOOP_IMPL void pw_loop_destroy_source(struct pw_loop *object,
                struct spa_source *source)
{
    return spa_loop_utils_destroy_source(object->utils, source);
}

/**
 * \}
 */

#ifdef __cplusplus
}
#endif

#endif /* PIPEWIRE_LOOP_H */
