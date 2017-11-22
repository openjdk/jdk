/*
 * Copyright (c) 2006, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

#include <door.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <poll.h>
#include <signal.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <thread.h>
#include <unistd.h>
#include "jvm_dtrace.h"

// NOTE: These constants are used in JVM code as well.
// KEEP JVM CODE IN SYNC if you are going to change these...

#define DTRACE_ALLOC_PROBES   0x1
#define DTRACE_METHOD_PROBES  0x2
#define DTRACE_MONITOR_PROBES 0x4
#define DTRACE_ALL_PROBES     -1

// generic error messages
#define JVM_ERR_OUT_OF_MEMORY            "out of memory (native heap)"
#define JVM_ERR_INVALID_PARAM            "invalid input parameter(s)"
#define JVM_ERR_NULL_PARAM               "input paramater is NULL"

// error messages for attach
#define JVM_ERR_CANT_OPEN_DOOR           "cannot open door file"
#define JVM_ERR_CANT_CREATE_ATTACH_FILE  "cannot create attach file"
#define JVM_ERR_DOOR_FILE_PERMISSION     "door file is not secure"
#define JVM_ERR_CANT_SIGNAL              "cannot send SIGQUIT to target"

// error messages for enable probe
#define JVM_ERR_DOOR_CMD_SEND            "door command send failed"
#define JVM_ERR_DOOR_CANT_READ_STATUS    "cannot read door command status"
#define JVM_ERR_DOOR_CMD_STATUS          "door command error status"

// error message for detach
#define JVM_ERR_CANT_CLOSE_DOOR          "cannot close door file"

#define RESTARTABLE(_cmd, _result) do { \
    do { \
        _result = _cmd; \
    } while((_result == -1) && (errno == EINTR)); \
} while(0)

struct _jvm_t {
    pid_t pid;
    int door_fd;
};

static int libjvm_dtrace_debug;
static void print_debug(const char* fmt,...) {
    if (libjvm_dtrace_debug) {
        va_list alist;
        va_start(alist, fmt);
        fputs("libjvm_dtrace DEBUG: ", stderr);
        vfprintf(stderr, fmt, alist);
        va_end(alist);
    }
}

/* Key for thread local error message */
static thread_key_t jvm_error_key;

/* init function for this library */
static void init_jvm_dtrace() {
    /* check for env. var for debug mode */
    libjvm_dtrace_debug = getenv("LIBJVM_DTRACE_DEBUG") != NULL;
    /* create key for thread local error message */
    if (thr_keycreate(&jvm_error_key, NULL) != 0) {
        print_debug("can't create thread_key_t for jvm error key\n");
        // exit(1); ?
    }
}

#pragma init(init_jvm_dtrace)

/* set thread local error message */
static void set_jvm_error(const char* msg) {
    thr_setspecific(jvm_error_key, (void*)msg);
}

/* clear thread local error message */
static void clear_jvm_error() {
    thr_setspecific(jvm_error_key, NULL);
}

/* file handling functions that can handle interrupt */

static int file_open(const char* path, int flag) {
    int ret;
    RESTARTABLE(open(path, flag), ret);
    return ret;
}

static int file_close(int fd) {
    return close(fd);
}

static int file_read(int fd, char* buf, int len) {
    int ret;
    RESTARTABLE(read(fd, buf, len), ret);
    return ret;
}

/* send SIGQUIT signal to given process */
static int send_sigquit(pid_t pid) {
    int ret;
    RESTARTABLE(kill(pid, SIGQUIT), ret);
    return ret;
}

/* called to check permissions on attach file */
static int check_permission(const char* path) {
    struct stat64 sb;
    uid_t uid, gid;
    int res;

    /*
     * Check that the path is owned by the effective uid/gid of this
     * process. Also check that group/other access is not allowed.
     */
    uid = geteuid();
    gid = getegid();

    res = stat64(path, &sb);
    if (res != 0) {
        print_debug("stat failed for %s\n", path);
        return -1;
    }

    if ((sb.st_uid != uid) || (sb.st_gid != gid) ||
        ((sb.st_mode & (S_IRGRP|S_IWGRP|S_IROTH|S_IWOTH)) != 0)) {
        print_debug("well-known file %s is not secure\n", path);
        return -1;
    }
    return 0;
}

#define ATTACH_FILE_PATTERN "/tmp/.attach_pid%d"

/* fill-in the name of attach file name in given buffer */
static void fill_attach_file_name(char* path, int len, pid_t pid) {
    memset(path, 0, len);
    sprintf(path, ATTACH_FILE_PATTERN, pid);
}

#define DOOR_FILE_PATTERN "/tmp/.java_pid%d"

/* open door file for the given JVM */
static int open_door(pid_t pid) {
    char path[PATH_MAX + 1];
    int fd;

    sprintf(path, DOOR_FILE_PATTERN, pid);
    fd = file_open(path, O_RDONLY);
    if (fd < 0) {
        set_jvm_error(JVM_ERR_CANT_OPEN_DOOR);
        print_debug("cannot open door file %s\n", path);
        return -1;
    }
    print_debug("opened door file %s\n", path);
    if (check_permission(path) != 0) {
        set_jvm_error(JVM_ERR_DOOR_FILE_PERMISSION);
        print_debug("check permission failed for %s\n", path);
        file_close(fd);
        fd = -1;
    }
    return fd;
}

/* create attach file for given process */
static int create_attach_file(pid_t pid) {
    char path[PATH_MAX + 1];
    int fd;
    fill_attach_file_name(path, sizeof(path), pid);
    fd = file_open(path, O_CREAT | O_RDWR);
    if (fd < 0) {
        set_jvm_error(JVM_ERR_CANT_CREATE_ATTACH_FILE);
        print_debug("cannot create file %s\n", path);
    } else {
        print_debug("created attach file %s\n", path);
    }
    return fd;
}

/* delete attach file for given process */
static void delete_attach_file(pid_t pid) {
    char path[PATH_MAX + 1];
    fill_attach_file_name(path, sizeof(path), pid);
    int res = unlink(path);
    if (res) {
        print_debug("cannot delete attach file %s\n", path);
    } else {
        print_debug("deleted attach file %s\n", path);
    }
}

/* attach to given JVM */
jvm_t* jvm_attach(pid_t pid) {
    jvm_t* jvm;
    int door_fd, attach_fd, i = 0;

    jvm = (jvm_t*) calloc(1, sizeof(jvm_t));
    if (jvm == NULL) {
        set_jvm_error(JVM_ERR_OUT_OF_MEMORY);
        print_debug("calloc failed in %s at %d\n", __FILE__, __LINE__);
        return NULL;
    }
    jvm->pid = pid;
    attach_fd = -1;

    door_fd = open_door(pid);
    if (door_fd < 0) {
        print_debug("trying to create attach file\n");
        if ((attach_fd = create_attach_file(pid)) < 0) {
            goto quit;
        }

        /* send QUIT signal to the target so that it will
         * check for the attach file.
         */
        if (send_sigquit(pid) != 0) {
            set_jvm_error(JVM_ERR_CANT_SIGNAL);
            print_debug("sending SIGQUIT failed\n");
            goto quit;
        }

        /* give the target VM time to start the attach mechanism */
        do {
            int res;
            RESTARTABLE(poll(0, 0, 200), res);
            door_fd = open_door(pid);
            i++;
        } while (i <= 50 && door_fd == -1);
        if (door_fd < 0) {
            print_debug("Unable to open door to process %d\n", pid);
            goto quit;
        }
    }

quit:
    if (attach_fd >= 0) {
        file_close(attach_fd);
        delete_attach_file(jvm->pid);
    }
    if (door_fd >= 0) {
        jvm->door_fd = door_fd;
        clear_jvm_error();
    } else {
        free(jvm);
        jvm = NULL;
    }
    return jvm;
}

/* return the last thread local error message */
const char* jvm_get_last_error() {
    const char* res = NULL;
    thr_getspecific(jvm_error_key, (void**)&res);
    return res;
}

/* detach the givenb JVM */
int jvm_detach(jvm_t* jvm) {
    if (jvm) {
        int res = 0;
        if (jvm->door_fd != -1) {
            if (file_close(jvm->door_fd) != 0) {
                set_jvm_error(JVM_ERR_CANT_CLOSE_DOOR);
                res = -1;
            } else {
                clear_jvm_error();
            }
        }
        free(jvm);
        return res;
    } else {
        set_jvm_error(JVM_ERR_NULL_PARAM);
        print_debug("jvm_t* is NULL\n");
        return -1;
    }
}

/*
 * A simple table to translate some known errors into reasonable
 * error messages
 */
static struct {
    int err;
    const char* msg;
} const error_messages[] = {
    { 100,      "Bad request" },
    { 101,      "Protocol mismatch" },
    { 102,      "Resource failure" },
    { 103,      "Internal error" },
    { 104,      "Permission denied" },
};

/*
 * Lookup the given error code and return the appropriate
 * message. If not found return NULL.
 */
static const char* translate_error(int err) {
    int table_size = sizeof(error_messages) / sizeof(error_messages[0]);
    int i;

    for (i=0; i<table_size; i++) {
        if (err == error_messages[i].err) {
            return error_messages[i].msg;
        }
    }
    return NULL;
}

/*
 * Current protocol version
 */
static const char* PROTOCOL_VERSION = "1";

#define RES_BUF_SIZE 128

/*
 * Enqueue attach-on-demand command to the given JVM
 */
static
int enqueue_command(jvm_t* jvm, const char* cstr, int arg_count, const char** args) {
    size_t size;
    door_arg_t door_args;
    char res_buffer[RES_BUF_SIZE];
    int rc, i;
    char* buf = NULL;
    int result = -1;

    /*
     * First we get the command string and create the start of the
     * argument string to send to the target VM:
     * <ver>\0<cmd>\0
     */
    if (cstr == NULL) {
        print_debug("command name is NULL\n");
        goto quit;
    }
    size = strlen(PROTOCOL_VERSION) + strlen(cstr) + 2;
    buf = (char*)malloc(size);
    if (buf != NULL) {
        char* pos = buf;
        strcpy(buf, PROTOCOL_VERSION);
        pos += strlen(PROTOCOL_VERSION)+1;
        strcpy(pos, cstr);
    } else {
        set_jvm_error(JVM_ERR_OUT_OF_MEMORY);
        print_debug("malloc failed at %d in %s\n", __LINE__, __FILE__);
        goto quit;
    }

    /*
     * Next we iterate over the arguments and extend the buffer
     * to include them.
     */
    for (i=0; i<arg_count; i++) {
        cstr = args[i];
        if (cstr != NULL) {
            size_t len = strlen(cstr);
            char* newbuf = (char*)realloc(buf, size+len+1);
            if (newbuf == NULL) {
                set_jvm_error(JVM_ERR_OUT_OF_MEMORY);
                print_debug("realloc failed in %s at %d\n", __FILE__, __LINE__);
                goto quit;
            }
            buf = newbuf;
            strcpy(buf+size, cstr);
            size += len+1;
        }
    }

    /*
     * The arguments to the door function are in 'buf' so we now
     * do the door call
     */
    door_args.data_ptr = buf;
    door_args.data_size = size;
    door_args.desc_ptr = NULL;
    door_args.desc_num = 0;
    door_args.rbuf = (char*)&res_buffer;
    door_args.rsize = sizeof(res_buffer);

    RESTARTABLE(door_call(jvm->door_fd, &door_args), rc);

    /*
     * door_call failed
     */
    if (rc == -1) {
        print_debug("door_call failed\n");
    } else {
        /*
         * door_call succeeded but the call didn't return the expected jint.
         */
        if (door_args.data_size < sizeof(int)) {
            print_debug("Enqueue error - reason unknown as result is truncated!");
        } else {
            int* res = (int*)(door_args.data_ptr);
            if (*res != 0) {
                const char* msg = translate_error(*res);
                if (msg == NULL) {
                    print_debug("Unable to enqueue command to target VM: %d\n", *res);
                } else {
                    print_debug("Unable to enqueue command to target VM: %s\n", msg);
                }
            } else {
                /*
                 * The door call should return a file descriptor to one end of
                 * a socket pair
                 */
                if ((door_args.desc_ptr != NULL) &&
                    (door_args.desc_num == 1) &&
                    (door_args.desc_ptr->d_attributes & DOOR_DESCRIPTOR)) {
                    result = door_args.desc_ptr->d_data.d_desc.d_descriptor;
                } else {
                    print_debug("Reply from enqueue missing descriptor!\n");
                }
            }
        }
    }

quit:
    if (buf) free(buf);
    return result;
}

/* read status code for a door command */
static int read_status(int fd) {
    char ch, buf[16];
    int index = 0;

    while (1) {
        if (file_read(fd, &ch, sizeof(ch)) != sizeof(ch)) {
            set_jvm_error(JVM_ERR_DOOR_CANT_READ_STATUS);
            print_debug("door cmd status: read status failed\n");
            return -1;
        }
        buf[index++] = ch;
        if (ch == '\n') {
            buf[index - 1] = '\0';
            return atoi(buf);
        }
        if (index == sizeof(buf)) {
            set_jvm_error(JVM_ERR_DOOR_CANT_READ_STATUS);
            print_debug("door cmd status: read status overflow\n");
            return -1;
        }
    }
}

static const char* ENABLE_DPROBES_CMD = "enabledprobes";

/* enable one or more DTrace probes for a given JVM */
int jvm_enable_dtprobes(jvm_t* jvm, int num_probe_types, const char** probe_types) {
    int fd, status = 0;
    char ch;
    const char* args[1];
    char buf[16];
    int probe_type = 0, index;
    int count = 0;

    if (jvm == NULL) {
        set_jvm_error(JVM_ERR_NULL_PARAM);
        print_debug("jvm_t* is NULL\n");
        return -1;
    }

    if (num_probe_types == 0 || probe_types == NULL ||
        probe_types[0] == NULL) {
        set_jvm_error(JVM_ERR_INVALID_PARAM);
        print_debug("invalid probe type argument(s)\n");
        return -1;
    }

    for (index = 0; index < num_probe_types; index++) {
        const char* p = probe_types[index];
        if (strcmp(p, JVM_DTPROBE_OBJECT_ALLOC) == 0) {
            probe_type |= DTRACE_ALLOC_PROBES;
            count++;
        } else if (strcmp(p, JVM_DTPROBE_METHOD_ENTRY) == 0 ||
                   strcmp(p, JVM_DTPROBE_METHOD_RETURN) == 0) {
            probe_type |= DTRACE_METHOD_PROBES;
            count++;
        } else if (strcmp(p, JVM_DTPROBE_MONITOR_ENTER) == 0   ||
                   strcmp(p, JVM_DTPROBE_MONITOR_ENTERED) == 0 ||
                   strcmp(p, JVM_DTPROBE_MONITOR_EXIT) == 0    ||
                   strcmp(p, JVM_DTPROBE_MONITOR_WAIT) == 0    ||
                   strcmp(p, JVM_DTPROBE_MONITOR_WAITED) == 0  ||
                   strcmp(p, JVM_DTPROBE_MONITOR_NOTIFY) == 0  ||
                   strcmp(p, JVM_DTPROBE_MONITOR_NOTIFYALL) == 0) {
            probe_type |= DTRACE_MONITOR_PROBES;
            count++;
        } else if (strcmp(p, JVM_DTPROBE_ALL) == 0) {
            probe_type |= DTRACE_ALL_PROBES;
            count++;
        }
    }

    if (count == 0) {
        return count;
    }
    sprintf(buf, "%d", probe_type);
    args[0] = buf;

    fd = enqueue_command(jvm, ENABLE_DPROBES_CMD, 1, args);
    if (fd < 0) {
        set_jvm_error(JVM_ERR_DOOR_CMD_SEND);
        return -1;
    }

    status = read_status(fd);
    // non-zero status is error
    if (status) {
        set_jvm_error(JVM_ERR_DOOR_CMD_STATUS);
        print_debug("%s command failed (status: %d) in target JVM\n",
                    ENABLE_DPROBES_CMD, status);
        file_close(fd);
        return -1;
    }
    // read from stream until EOF
    while (file_read(fd, &ch, sizeof(ch)) == sizeof(ch)) {
        if (libjvm_dtrace_debug) {
            printf("%c", ch);
        }
    }

    file_close(fd);
    clear_jvm_error();
    return count;
}
