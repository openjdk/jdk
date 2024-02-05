/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#ifdef HEADLESS
#error This file should not be included in headless library
#endif
#ifndef _FP_PIPEWIRE_H
#define _FP_PIPEWIRE_H


struct pw_buffer *(*fp_pw_stream_dequeue_buffer)(struct pw_stream *stream);
const char * (*fp_pw_stream_state_as_string)(enum pw_stream_state state);
int (*fp_pw_stream_queue_buffer)(struct pw_stream *stream,
                                 struct pw_buffer *buffer);
int (*fp_pw_stream_set_active)(struct pw_stream *stream, bool active);

int (*fp_pw_stream_connect)(
        struct pw_stream *stream,
        enum pw_direction direction,
        uint32_t target_id,
        enum pw_stream_flags flags,
        const struct spa_pod **params,
        uint32_t n_params);

struct pw_stream *(*fp_pw_stream_new)(
        struct pw_core *core,
        const char *name,
        struct pw_properties *props
);
void (*fp_pw_stream_add_listener)(struct pw_stream *stream,
                            struct spa_hook *listener,
                            const struct pw_stream_events *events,
                            void *data);
int (*fp_pw_stream_disconnect)(struct pw_stream *stream);
void (*fp_pw_stream_destroy)(struct pw_stream *stream);


void (*fp_pw_init)(int *argc, char **argv[]);
void (*fp_pw_deinit)(void);

struct pw_core *
(*fp_pw_context_connect_fd)(struct pw_context *context,
                      int fd,
                      struct pw_properties *properties,
                      size_t user_data_size);

int (*fp_pw_core_disconnect)(struct pw_core *core);

struct pw_context * (*fp_pw_context_new)(struct pw_loop *main_loop,
                                   struct pw_properties *props,
                                   size_t user_data_size);

struct pw_thread_loop *
(*fp_pw_thread_loop_new)(const char *name, const struct spa_dict *props);
struct pw_loop * (*fp_pw_thread_loop_get_loop)(struct pw_thread_loop *loop);
void (*fp_pw_thread_loop_signal)(struct pw_thread_loop *loop,
                                 bool wait_for_accept);
void (*fp_pw_thread_loop_wait)(struct pw_thread_loop *loop);
void (*fp_pw_thread_loop_accept)(struct pw_thread_loop *loop);
int (*fp_pw_thread_loop_start)(struct pw_thread_loop *loop);
void (*fp_pw_thread_loop_stop)(struct pw_thread_loop *loop);
void (*fp_pw_thread_loop_destroy)(struct pw_thread_loop *loop);
void (*fp_pw_thread_loop_lock)(struct pw_thread_loop *loop);
void (*fp_pw_thread_loop_unlock)(struct pw_thread_loop *loop);

struct pw_properties * (*fp_pw_properties_new)(const char *key, ...);


#endif //_FP_PIPEWIRE_H
