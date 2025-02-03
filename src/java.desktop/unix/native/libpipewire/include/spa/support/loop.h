/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2018 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_LOOP_H
#define SPA_LOOP_H

#ifdef __cplusplus
extern "C" {
#endif

#include <errno.h>

#include <spa/utils/defs.h>
#include <spa/utils/hook.h>
#include <spa/support/system.h>

#ifndef SPA_API_LOOP
 #ifdef SPA_API_IMPL
  #define SPA_API_LOOP SPA_API_IMPL
 #else
  #define SPA_API_LOOP static inline
 #endif
#endif

/** \defgroup spa_loop Loop
 * Event loop interface
 */

/**
 * \addtogroup spa_loop
 * \{
 */

#define SPA_TYPE_INTERFACE_Loop        SPA_TYPE_INFO_INTERFACE_BASE "Loop"
#define SPA_TYPE_INTERFACE_DataLoop    SPA_TYPE_INFO_INTERFACE_BASE "DataLoop"
#define SPA_VERSION_LOOP        0
struct spa_loop { struct spa_interface iface; };

#define SPA_TYPE_INTERFACE_LoopControl    SPA_TYPE_INFO_INTERFACE_BASE "LoopControl"
#define SPA_VERSION_LOOP_CONTROL    1
struct spa_loop_control { struct spa_interface iface; };

#define SPA_TYPE_INTERFACE_LoopUtils    SPA_TYPE_INFO_INTERFACE_BASE "LoopUtils"
#define SPA_VERSION_LOOP_UTILS        0
struct spa_loop_utils { struct spa_interface iface; };

struct spa_source;

typedef void (*spa_source_func_t) (struct spa_source *source);

struct spa_source {
    struct spa_loop *loop;
    spa_source_func_t func;
    void *data;
    int fd;
    uint32_t mask;
    uint32_t rmask;
    /* private data for the loop implementer */
    void *priv;
};

typedef int (*spa_invoke_func_t) (struct spa_loop *loop,
                  bool async,
                  uint32_t seq,
                  const void *data,
                  size_t size,
                  void *user_data);

/**
 * Register sources and work items to an event loop
 */
struct spa_loop_methods {
    /* the version of this structure. This can be used to expand this
     * structure in the future */
#define SPA_VERSION_LOOP_METHODS    0
    uint32_t version;

    /** Add a source to the loop. Must be called from the loop's own thread.
     *
     * \param[in] object The callbacks data.
     * \param[in] source The source.
     * \return 0 on success, negative errno-style value on failure.
     */
    int (*add_source) (void *object,
               struct spa_source *source);

    /** Update the source io mask. Must be called from the loop's own thread.
     *
     * \param[in] object The callbacks data.
     * \param[in] source The source.
     * \return 0 on success, negative errno-style value on failure.
     */
    int (*update_source) (void *object,
            struct spa_source *source);

    /** Remove a source from the loop. Must be called from the loop's own thread.
     *
     * \param[in] object The callbacks data.
     * \param[in] source The source.
     * \return 0 on success, negative errno-style value on failure.
     */
    int (*remove_source) (void *object,
            struct spa_source *source);

    /** Invoke a function in the context of this loop.
     * May be called from any thread and multiple threads at the same time.
     * If called from the loop's thread, all callbacks previously queued with
     * invoke() will be run synchronously, which might cause unexpected
     * reentrancy problems.
     *
     * \param[in] object The callbacks data.
     * \param func The function to be invoked.
     * \param seq An opaque sequence number. This will be made
     *            available to func.
     * \param[in] data Data that will be copied into the internal ring buffer and made
     *             available to func. Because this data is copied, it is okay to
     *             pass a pointer to a local variable, but do not pass a pointer to
     *             an object that has identity.
     * \param size The size of data to copy.
     * \param block If \true, do not return until func has been called. Otherwise,
     *              returns immediately. Passing \true does not risk a deadlock because
     *              the data thread is never allowed to wait on any other thread.
     * \param user_data An opaque pointer passed to func.
     * \return `-EPIPE` if the internal ring buffer filled up,
     *         if block is \false, 0 if seq was SPA_ID_INVALID or
     *         seq with the ASYNC flag set
     *         or the return value of func otherwise. */
    int (*invoke) (void *object,
               spa_invoke_func_t func,
               uint32_t seq,
               const void *data,
               size_t size,
               bool block,
               void *user_data);
};

SPA_API_LOOP int spa_loop_add_source(struct spa_loop *object, struct spa_source *source)
{
    return spa_api_method_r(int, -ENOTSUP,
            spa_loop, &object->iface, add_source, 0, source);
}
SPA_API_LOOP int spa_loop_update_source(struct spa_loop *object, struct spa_source *source)
{
    return spa_api_method_r(int, -ENOTSUP,
            spa_loop, &object->iface, update_source, 0, source);
}
SPA_API_LOOP int spa_loop_remove_source(struct spa_loop *object, struct spa_source *source)
{
    return spa_api_method_r(int, -ENOTSUP,
            spa_loop, &object->iface, remove_source, 0, source);
}
SPA_API_LOOP int spa_loop_invoke(struct spa_loop *object,
        spa_invoke_func_t func, uint32_t seq, const void *data,
        size_t size, bool block, void *user_data)
{
    return spa_api_method_r(int, -ENOTSUP,
            spa_loop, &object->iface, invoke, 0, func, seq, data,
            size, block, user_data);
}

/** Control hooks. These hooks can't be removed from their
 *  callbacks and must be removed from a safe place (when the loop
 *  is not running or when it is locked). */
struct spa_loop_control_hooks {
#define SPA_VERSION_LOOP_CONTROL_HOOKS    0
    uint32_t version;
    /** Executed right before waiting for events. It is typically used to
     * release locks. */
    void (*before) (void *data);
    /** Executed right after waiting for events. It is typically used to
     * reacquire locks. */
    void (*after) (void *data);
};

SPA_API_LOOP void spa_loop_control_hook_before(struct spa_hook_list *l)
{
    struct spa_hook *h;
    spa_list_for_each_reverse(h, &l->list, link)
        spa_callbacks_call_fast(&h->cb, struct spa_loop_control_hooks, before, 0);
}

SPA_API_LOOP void spa_loop_control_hook_after(struct spa_hook_list *l)
{
    struct spa_hook *h;
    spa_list_for_each(h, &l->list, link)
        spa_callbacks_call_fast(&h->cb, struct spa_loop_control_hooks, after, 0);
}

/**
 * Control an event loop
 *
 * The event loop control function provide API to run the event loop.
 *
 * The below (pseudo)code is a minimal example outlining the use of the loop
 * control:
 * \code{.c}
 * spa_loop_control_enter(loop);
 * while (running) {
 *   spa_loop_control_iterate(loop, -1);
 * }
 * spa_loop_control_leave(loop);
 * \endcode
 *
 * It is also possible to add the loop to an existing event loop by using the
 * spa_loop_control_get_fd() call. This fd will become readable when activity
 * has been detected on the sources in the loop. spa_loop_control_iterate() with
 * a 0 timeout should be called to process the pending sources.
 *
 * spa_loop_control_enter() and spa_loop_control_leave() should be called once
 * from the thread that will run the iterate() function.
 */
struct spa_loop_control_methods {
    /* the version of this structure. This can be used to expand this
     * structure in the future */
#define SPA_VERSION_LOOP_CONTROL_METHODS    1
    uint32_t version;

    /** get the loop fd
     * \param object the control to query
     *
     * Get the fd of this loop control. This fd will be readable when a
     * source in the loop has activity. The user should call iterate()
     * with a 0 timeout to schedule one iteration of the loop and dispatch
     * the sources.
     * \return the fd of the loop
     */
    int (*get_fd) (void *object);

    /** Add a hook
     * \param object the control to change
     * \param hooks the hooks to add
     *
     * Adds hooks to the loop controlled by \a ctrl.
     */
    void (*add_hook) (void *object,
              struct spa_hook *hook,
              const struct spa_loop_control_hooks *hooks,
              void *data);

    /** Enter a loop
     * \param object the control
     *
     * This function should be called before calling iterate and is
     * typically used to capture the thread that this loop will run in.
     * It should ideally be called once from the thread that will run
     * the loop.
     */
    void (*enter) (void *object);
    /** Leave a loop
     * \param object the control
     *
     * It should ideally be called once after calling iterate when the loop
     * will no longer be iterated from the thread that called enter().
     */
    void (*leave) (void *object);

    /** Perform one iteration of the loop.
     * \param ctrl the control
     * \param timeout an optional timeout in milliseconds.
     *    0 for no timeout, -1 for infinite timeout.
     *
     * This function will block
     * up to \a timeout milliseconds and then dispatch the fds with activity.
     * The number of dispatched fds is returned.
     */
    int (*iterate) (void *object, int timeout);

    /** Check context of the loop
     * \param ctrl the control
     *
     * This function will check if the current thread is currently the
     * one that did the enter call. Since version 1:1.
     *
     * returns 1 on success, 0 or negative errno value on error.
     */
    int (*check) (void *object);
};

SPA_API_LOOP int spa_loop_control_get_fd(struct spa_loop_control *object)
{
    return spa_api_method_r(int, -ENOTSUP,
            spa_loop_control, &object->iface, get_fd, 0);
}
SPA_API_LOOP void spa_loop_control_add_hook(struct spa_loop_control *object,
        struct spa_hook *hook, const struct spa_loop_control_hooks *hooks,
        void *data)
{
    spa_api_method_v(spa_loop_control, &object->iface, add_hook, 0,
            hook, hooks, data);
}
SPA_API_LOOP void spa_loop_control_enter(struct spa_loop_control *object)
{
    spa_api_method_v(spa_loop_control, &object->iface, enter, 0);
}
SPA_API_LOOP void spa_loop_control_leave(struct spa_loop_control *object)
{
    spa_api_method_v(spa_loop_control, &object->iface, leave, 0);
}
SPA_API_LOOP int spa_loop_control_iterate(struct spa_loop_control *object,
        int timeout)
{
    return spa_api_method_r(int, -ENOTSUP,
            spa_loop_control, &object->iface, iterate, 0, timeout);
}
SPA_API_LOOP int spa_loop_control_iterate_fast(struct spa_loop_control *object,
        int timeout)
{
    return spa_api_method_fast_r(int, -ENOTSUP,
            spa_loop_control, &object->iface, iterate, 0, timeout);
}
SPA_API_LOOP int spa_loop_control_check(struct spa_loop_control *object)
{
    return spa_api_method_r(int, -ENOTSUP,
            spa_loop_control, &object->iface, check, 1);
}

typedef void (*spa_source_io_func_t) (void *data, int fd, uint32_t mask);
typedef void (*spa_source_idle_func_t) (void *data);
typedef void (*spa_source_event_func_t) (void *data, uint64_t count);
typedef void (*spa_source_timer_func_t) (void *data, uint64_t expirations);
typedef void (*spa_source_signal_func_t) (void *data, int signal_number);

/**
 * Create sources for an event loop
 */
struct spa_loop_utils_methods {
    /* the version of this structure. This can be used to expand this
     * structure in the future */
#define SPA_VERSION_LOOP_UTILS_METHODS    0
    uint32_t version;

    struct spa_source *(*add_io) (void *object,
                      int fd,
                      uint32_t mask,
                      bool close,
                      spa_source_io_func_t func, void *data);

    int (*update_io) (void *object, struct spa_source *source, uint32_t mask);

    struct spa_source *(*add_idle) (void *object,
                    bool enabled,
                    spa_source_idle_func_t func, void *data);
    int (*enable_idle) (void *object, struct spa_source *source, bool enabled);

    struct spa_source *(*add_event) (void *object,
                     spa_source_event_func_t func, void *data);
    int (*signal_event) (void *object, struct spa_source *source);

    struct spa_source *(*add_timer) (void *object,
                     spa_source_timer_func_t func, void *data);
    int (*update_timer) (void *object,
                 struct spa_source *source,
                 struct timespec *value,
                 struct timespec *interval,
                 bool absolute);
    struct spa_source *(*add_signal) (void *object,
                      int signal_number,
                      spa_source_signal_func_t func, void *data);

    /** destroy a source allocated with this interface. This function
     * should only be called when the loop is not running or from the
     * context of the running loop */
    void (*destroy_source) (void *object, struct spa_source *source);
};

SPA_API_LOOP struct spa_source *
spa_loop_utils_add_io(struct spa_loop_utils *object, int fd, uint32_t mask,
        bool close, spa_source_io_func_t func, void *data)
{
    return spa_api_method_r(struct spa_source *, NULL,
            spa_loop_utils, &object->iface, add_io, 0, fd, mask, close, func, data);
}
SPA_API_LOOP int spa_loop_utils_update_io(struct spa_loop_utils *object,
        struct spa_source *source, uint32_t mask)
{
    return spa_api_method_r(int, -ENOTSUP,
            spa_loop_utils, &object->iface, update_io, 0, source, mask);
}
SPA_API_LOOP struct spa_source *
spa_loop_utils_add_idle(struct spa_loop_utils *object, bool enabled,
        spa_source_idle_func_t func, void *data)
{
    return spa_api_method_r(struct spa_source *, NULL,
            spa_loop_utils, &object->iface, add_idle, 0, enabled, func, data);
}
SPA_API_LOOP int spa_loop_utils_enable_idle(struct spa_loop_utils *object,
        struct spa_source *source, bool enabled)
{
    return spa_api_method_r(int, -ENOTSUP,
            spa_loop_utils, &object->iface, enable_idle, 0, source, enabled);
}
SPA_API_LOOP struct spa_source *
spa_loop_utils_add_event(struct spa_loop_utils *object, spa_source_event_func_t func, void *data)
{
    return spa_api_method_r(struct spa_source *, NULL,
            spa_loop_utils, &object->iface, add_event, 0, func, data);
}
SPA_API_LOOP int spa_loop_utils_signal_event(struct spa_loop_utils *object,
        struct spa_source *source)
{
    return spa_api_method_r(int, -ENOTSUP,
            spa_loop_utils, &object->iface, signal_event, 0, source);
}
SPA_API_LOOP struct spa_source *
spa_loop_utils_add_timer(struct spa_loop_utils *object, spa_source_timer_func_t func, void *data)
{
    return spa_api_method_r(struct spa_source *, NULL,
            spa_loop_utils, &object->iface, add_timer, 0, func, data);
}
SPA_API_LOOP int spa_loop_utils_update_timer(struct spa_loop_utils *object,
        struct spa_source *source, struct timespec *value,
        struct timespec *interval, bool absolute)
{
    return spa_api_method_r(int, -ENOTSUP,
            spa_loop_utils, &object->iface, update_timer, 0, source,
            value, interval, absolute);
}
SPA_API_LOOP struct spa_source *
spa_loop_utils_add_signal(struct spa_loop_utils *object, int signal_number,
        spa_source_signal_func_t func, void *data)
{
    return spa_api_method_r(struct spa_source *, NULL,
            spa_loop_utils, &object->iface, add_signal, 0,
            signal_number, func, data);
}
SPA_API_LOOP void spa_loop_utils_destroy_source(struct spa_loop_utils *object,
        struct spa_source *source)
{
    spa_api_method_v(spa_loop_utils, &object->iface, destroy_source, 0, source);
}

/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_LOOP_H */
