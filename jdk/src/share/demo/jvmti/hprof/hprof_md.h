/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


#ifndef HPROF_MD_H
#define HPROF_MD_H

void    md_init(void);
int     md_getpid(void);
void    md_sleep(unsigned seconds);
int     md_connect(char *hostname, unsigned short port);
int     md_recv(int f, char *buf, int len, int option);
int     md_shutdown(int filedes, int option);
int     md_open(const char *filename);
int     md_open_binary(const char *filename);
int     md_creat(const char *filename);
int     md_creat_binary(const char *filename);
jlong   md_seek(int filedes, jlong cur);
void    md_close(int filedes);
int     md_send(int s, const char *msg, int len, int flags);
int     md_write(int filedes, const void *buf, int nbyte);
int     md_read(int filedes, void *buf, int nbyte);
jlong   md_get_microsecs(void);
jlong   md_get_timemillis(void);
jlong   md_get_thread_cpu_timemillis(void);
void    md_get_prelude_path(char *path, int path_len, char *filename);
int     md_snprintf(char *s, int n, const char *format, ...);
int     md_vsnprintf(char *s, int n, const char *format, va_list ap);
void    md_system_error(char *buf, int len);

unsigned md_htons(unsigned short s);
unsigned md_htonl(unsigned l);
unsigned md_ntohs(unsigned short s);
unsigned md_ntohl(unsigned l);

void   md_build_library_name(char *holder, int holderlen, const char *pname, const char *fname);
void * md_load_library(const char *name, char *err_buf, int err_buflen);
void   md_unload_library(void *handle);
void * md_find_library_entry(void *handle, const char *name);

#endif
