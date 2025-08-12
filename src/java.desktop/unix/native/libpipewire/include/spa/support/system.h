/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2019 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_SYSTEM_H
#define SPA_SYSTEM_H

#ifdef __cplusplus
extern "C" {
#endif

struct itimerspec;

#include <time.h>
#include <errno.h>
#include <sys/types.h>

#include <spa/utils/defs.h>
#include <spa/utils/hook.h>

#ifndef SPA_API_SYSTEM
 #ifdef SPA_API_IMPL
  #define SPA_API_SYSTEM SPA_API_IMPL
 #else
  #define SPA_API_SYSTEM static inline
 #endif
#endif

/** \defgroup spa_system System
 * I/O, clock, polling, timer, and signal interfaces
 */

/**
 * \addtogroup spa_system
 * \{
 */

/**
 * a collection of core system functions
 */
#define SPA_TYPE_INTERFACE_System    SPA_TYPE_INFO_INTERFACE_BASE "System"
#define SPA_TYPE_INTERFACE_DataSystem    SPA_TYPE_INFO_INTERFACE_BASE "DataSystem"

#define SPA_VERSION_SYSTEM        0
struct spa_system { struct spa_interface iface; };

/* IO events */
#define SPA_IO_IN    (1 << 0)
#define SPA_IO_OUT    (1 << 2)
#define SPA_IO_ERR    (1 << 3)
#define SPA_IO_HUP    (1 << 4)

/* flags */
#define SPA_FD_CLOEXEC            (1<<0)
#define SPA_FD_NONBLOCK            (1<<1)
#define SPA_FD_EVENT_SEMAPHORE        (1<<2)
#define SPA_FD_TIMER_ABSTIME        (1<<3)
#define SPA_FD_TIMER_CANCEL_ON_SET    (1<<4)

struct spa_poll_event {
    uint32_t events;
    void *data;
};

struct spa_system_methods {
#define SPA_VERSION_SYSTEM_METHODS    0
    uint32_t version;

    /* read/write/ioctl */
    ssize_t (*read) (void *object, int fd, void *buf, size_t count);
    ssize_t (*write) (void *object, int fd, const void *buf, size_t count);
    int (*ioctl) (void *object, int fd, unsigned long request, ...);
    int (*close) (void *object, int fd);

    /* clock */
    int (*clock_gettime) (void *object,
            int clockid, struct timespec *value);
    int (*clock_getres) (void *object,
            int clockid, struct timespec *res);

    /* poll */
    int (*pollfd_create) (void *object, int flags);
    int (*pollfd_add) (void *object, int pfd, int fd, uint32_t events, void *data);
    int (*pollfd_mod) (void *object, int pfd, int fd, uint32_t events, void *data);
    int (*pollfd_del) (void *object, int pfd, int fd);
    int (*pollfd_wait) (void *object, int pfd,
            struct spa_poll_event *ev, int n_ev, int timeout);

    /* timers */
    int (*timerfd_create) (void *object, int clockid, int flags);
    int (*timerfd_settime) (void *object,
            int fd, int flags,
            const struct itimerspec *new_value,
            struct itimerspec *old_value);
    int (*timerfd_gettime) (void *object,
            int fd, struct itimerspec *curr_value);
    int (*timerfd_read) (void *object, int fd, uint64_t *expirations);

    /* events */
    int (*eventfd_create) (void *object, int flags);
    int (*eventfd_write) (void *object, int fd, uint64_t count);
    int (*eventfd_read) (void *object, int fd, uint64_t *count);

    /* signals */
    int (*signalfd_create) (void *object, int signal, int flags);
    int (*signalfd_read) (void *object, int fd, int *signal);
};

SPA_API_SYSTEM ssize_t spa_system_read(struct spa_system *object, int fd, void *buf, size_t count)
{
    return spa_api_method_fast_r(ssize_t, -ENOTSUP, spa_system, &object->iface, read, 0, fd, buf, count);
}
SPA_API_SYSTEM ssize_t spa_system_write(struct spa_system *object, int fd, const void *buf, size_t count)
{
    return spa_api_method_fast_r(ssize_t, -ENOTSUP, spa_system, &object->iface, write, 0, fd, buf, count);
}
#define spa_system_ioctl(object,fd,request,...)    \
    spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, ioctl, 0, fd, request, ##__VA_ARGS__)

SPA_API_SYSTEM int spa_system_close(struct spa_system *object, int fd)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, close, 0, fd);
}
SPA_API_SYSTEM int spa_system_clock_gettime(struct spa_system *object,
            int clockid, struct timespec *value)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, clock_gettime, 0, clockid, value);
}
SPA_API_SYSTEM int spa_system_clock_getres(struct spa_system *object,
            int clockid, struct timespec *res)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, clock_getres, 0, clockid, res);
}

SPA_API_SYSTEM int spa_system_pollfd_create(struct spa_system *object, int flags)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, pollfd_create, 0, flags);
}
SPA_API_SYSTEM int spa_system_pollfd_add(struct spa_system *object, int pfd, int fd, uint32_t events, void *data)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, pollfd_add, 0, pfd, fd, events, data);
}
SPA_API_SYSTEM int spa_system_pollfd_mod(struct spa_system *object, int pfd, int fd, uint32_t events, void *data)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, pollfd_mod, 0, pfd, fd, events, data);
}
SPA_API_SYSTEM int spa_system_pollfd_del(struct spa_system *object, int pfd, int fd)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, pollfd_del, 0, pfd, fd);
}
SPA_API_SYSTEM int spa_system_pollfd_wait(struct spa_system *object, int pfd,
            struct spa_poll_event *ev, int n_ev, int timeout)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, pollfd_wait, 0, pfd, ev, n_ev, timeout);
}

SPA_API_SYSTEM int spa_system_timerfd_create(struct spa_system *object, int clockid, int flags)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, timerfd_create, 0, clockid, flags);
}

SPA_API_SYSTEM int spa_system_timerfd_settime(struct spa_system *object,
            int fd, int flags,
            const struct itimerspec *new_value,
            struct itimerspec *old_value)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, timerfd_settime, 0,
            fd, flags, new_value, old_value);
}

SPA_API_SYSTEM int spa_system_timerfd_gettime(struct spa_system *object,
            int fd, struct itimerspec *curr_value)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, timerfd_gettime, 0,
            fd, curr_value);
}
SPA_API_SYSTEM int spa_system_timerfd_read(struct spa_system *object, int fd, uint64_t *expirations)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, timerfd_read, 0,
            fd, expirations);
}

SPA_API_SYSTEM int spa_system_eventfd_create(struct spa_system *object, int flags)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, eventfd_create, 0, flags);
}
SPA_API_SYSTEM int spa_system_eventfd_write(struct spa_system *object, int fd, uint64_t count)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, eventfd_write, 0,
            fd, count);
}
SPA_API_SYSTEM int spa_system_eventfd_read(struct spa_system *object, int fd, uint64_t *count)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, eventfd_read, 0,
            fd, count);
}

SPA_API_SYSTEM int spa_system_signalfd_create(struct spa_system *object, int signal, int flags)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, signalfd_create, 0,
            signal, flags);
}

SPA_API_SYSTEM int spa_system_signalfd_read(struct spa_system *object, int fd, int *signal)
{
    return spa_api_method_fast_r(int, -ENOTSUP, spa_system, &object->iface, signalfd_read, 0,
            fd, signal);
}

/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_SYSTEM_H */
