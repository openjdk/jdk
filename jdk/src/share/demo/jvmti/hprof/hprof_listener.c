/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

/* The hprof listener loop thread. net=hostname:port option */

/*
 * The option net=hostname:port causes all hprof output to be sent down
 *   a socket connection, and also allows for commands to come in over the
 *   socket. The commands are documented below.
 *
 * This thread can cause havoc when started prematurely or not terminated
 *   properly, see listener_init() and listener_term(), and their calls
 *   in hprof_init.c.
 *
 * The listener loop (hprof_listener.c) can dynamically turn on or off the
 *  sampling of all or selected threads.
 *
 * The specification of this command protocol is only here, in the comments
 *  below.  The HAT tools uses this interface.
 *  It is also unknown how well these options work given the limited
 *  testing of this interface.
 *
 */

#include "hprof.h"

/* When the hprof Agent in the VM is connected via a socket to the
 * profiling client, the client may send the hprof Agent a set of commands.
 * The commands have the following format:
 *
 * u1           a TAG denoting the type of the record
 * u4           a serial number
 * u4           number of bytes *remaining* in the record. Note that
 *              this number excludes the tag and the length field itself.
 * [u1]*        BODY of the record (a sequence of bytes)
 */

/* The following commands are presently supported:
 *
 * TAG           BODY       notes
 * ----------------------------------------------------------
 * HPROF_CMD_GC             force a GC.
 *
 * HPROF_CMD_DUMP_HEAP      obtain a heap dump
 *
 * HPROF_CMD_ALLOC_SITES    obtain allocation sites
 *
 *               u2         flags 0x0001: incremental vs. complete
 *                                0x0002: sorted by allocation vs. live
 *                                0x0004: whether to force a GC
 *               u4         cutoff ratio (0.0 ~ 1.0)
 *
 * HPROF_CMD_HEAP_SUMMARY   obtain heap summary
 *
 * HPROF_CMD_DUMP_TRACES    obtain all newly created traces
 *
 * HPROF_CMD_CPU_SAMPLES    obtain a HPROF_CPU_SAMPLES record
 *
 *               u2         ignored for now
 *               u4         cutoff ratio (0.0 ~ 1.0)
 *
 * HPROF_CMD_CONTROL        changing settings
 *
 *               u2         0x0001: alloc traces on
 *                          0x0002: alloc traces off
 *
 *                          0x0003: CPU sampling on
 *
 *                                  id:   thread object id (NULL for all)
 *
 *                          0x0004: CPU sampling off
 *
 *                                  id:   thread object id (NULL for all)
 *
 *                          0x0005: CPU sampling clear
 *
 *                          0x0006: clear alloc sites info
 *
 *                          0x0007: set max stack depth in CPU samples
 *                                  and alloc traces
 *
 *                                  u2:   new depth
 */

typedef enum HprofCmd {
    HPROF_CMD_GC                = 0x01,
    HPROF_CMD_DUMP_HEAP         = 0x02,
    HPROF_CMD_ALLOC_SITES       = 0x03,
    HPROF_CMD_HEAP_SUMMARY      = 0x04,
    HPROF_CMD_EXIT              = 0x05,
    HPROF_CMD_DUMP_TRACES       = 0x06,
    HPROF_CMD_CPU_SAMPLES       = 0x07,
    HPROF_CMD_CONTROL           = 0x08,
    HPROF_CMD_EOF               = 0xFF
} HprofCmd;

static jint
recv_fully(int f, char *buf, int len)
{
    jint nbytes;

    nbytes = 0;
    if ( f < 0 ) {
        return nbytes;
    }
    while (nbytes < len) {
        int res;

        res = md_recv(f, buf + nbytes, (len - nbytes), 0);
        if (res < 0) {
            /*
             * hprof was disabled before we returned from recv() above.
             * This means the command socket is closed so we let that
             * trickle back up the command processing stack.
             */
            LOG("recv() returned < 0");
            break;
        }
        nbytes += res;
    }
    return nbytes;
}

static unsigned char
recv_u1(void)
{
    unsigned char c;
    jint nbytes;

    nbytes = recv_fully(gdata->fd, (char *)&c, (int)sizeof(unsigned char));
    if (nbytes == 0) {
        c = HPROF_CMD_EOF;
    }
    return c;
}

static unsigned short
recv_u2(void)
{
    unsigned short s;
    jint nbytes;

    nbytes = recv_fully(gdata->fd, (char *)&s, (int)sizeof(unsigned short));
    if (nbytes == 0) {
        s = (unsigned short)-1;
    }
    return md_ntohs(s);
}

static unsigned
recv_u4(void)
{
    unsigned i;
    jint nbytes;

    nbytes = recv_fully(gdata->fd, (char *)&i, (int)sizeof(unsigned));
    if (nbytes == 0) {
        i = (unsigned)-1;
    }
    return md_ntohl(i);
}

static ObjectIndex
recv_id(void)
{
    ObjectIndex result;
    jint        nbytes;

    nbytes = recv_fully(gdata->fd, (char *)&result, (int)sizeof(ObjectIndex));
    if (nbytes == 0) {
        result = (ObjectIndex)0;
    }
    return result;
}

static void JNICALL
listener_loop_function(jvmtiEnv *jvmti, JNIEnv *env, void *p)
{
    jboolean keep_processing;
    unsigned char tag;
    jboolean kill_the_whole_process;

    kill_the_whole_process = JNI_FALSE;
    tag = 0;

    rawMonitorEnter(gdata->listener_loop_lock); {
        gdata->listener_loop_running = JNI_TRUE;
        keep_processing = gdata->listener_loop_running;
        /* Tell listener_init() that we have started */
        rawMonitorNotifyAll(gdata->listener_loop_lock);
    } rawMonitorExit(gdata->listener_loop_lock);

    while ( keep_processing ) {

        LOG("listener loop iteration");

        tag = recv_u1();  /* This blocks here on the socket read, a close()
                           *   on this fd will wake this up. And if recv_u1()
                           *   can't read anything, it returns HPROF_CMD_EOF.
                           */

        LOG3("listener_loop", "command = ", tag);

        if (tag == HPROF_CMD_EOF) {
            /* The cmd socket has closed so the listener thread is done
             *   just fall out of loop and let the thread die.
             */
            keep_processing = JNI_FALSE;
            break;
        }

        /* seq_num not used */
        (void)recv_u4();
        /* length not used */
        (void)recv_u4();

        switch (tag) {
            case HPROF_CMD_GC:
                runGC();
                break;
            case HPROF_CMD_DUMP_HEAP: {
                site_heapdump(env);
                break;
            }
            case HPROF_CMD_ALLOC_SITES: {
                unsigned short flags;
                unsigned i_tmp;
                float ratio;

                flags = recv_u2();
                i_tmp = recv_u4();
                ratio = *(float *)(&i_tmp);
                site_write(env, flags, ratio);
                break;
            }
            case HPROF_CMD_HEAP_SUMMARY: {
                rawMonitorEnter(gdata->data_access_lock); {
                    io_write_heap_summary(  gdata->total_live_bytes,
                                            gdata->total_live_instances,
                                            gdata->total_alloced_bytes,
                                            gdata->total_alloced_instances);
                } rawMonitorExit(gdata->data_access_lock);
                break;
            }
            case HPROF_CMD_EXIT:
                keep_processing = JNI_FALSE;
                kill_the_whole_process = JNI_TRUE;
                verbose_message("HPROF: received exit event, exiting ...\n");
                break;
            case HPROF_CMD_DUMP_TRACES:
                rawMonitorEnter(gdata->data_access_lock); {
                    trace_output_unmarked(env);
                } rawMonitorExit(gdata->data_access_lock);
                break;
            case HPROF_CMD_CPU_SAMPLES: {
                unsigned i_tmp;
                float ratio;

                /* flags not used */
                (void)recv_u2();
                i_tmp = recv_u4();
                ratio = *(float *)(&i_tmp);
                trace_output_cost(env, ratio);
                break;
            }
            case HPROF_CMD_CONTROL: {
                unsigned short cmd = recv_u2();
                if (cmd == 0x0001) {
                    setEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_OBJECT_FREE, NULL);
                    tracker_engage(env);
                } else if (cmd == 0x0002) {
                    setEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_OBJECT_FREE, NULL);
                    tracker_disengage(env);
                } else if (cmd == 0x0003) {
                    ObjectIndex thread_object_index;
                    thread_object_index = recv_id();
                    cpu_sample_on(env, thread_object_index);
                } else if (cmd == 0x0004) {
                    ObjectIndex thread_object_index;
                    thread_object_index = recv_id();
                    cpu_sample_off(env, thread_object_index);
                } else if (cmd == 0x0005) {
                    rawMonitorEnter(gdata->data_access_lock); {
                        trace_clear_cost();
                    } rawMonitorExit(gdata->data_access_lock);
                } else if (cmd == 0x0006) {
                    rawMonitorEnter(gdata->data_access_lock); {
                        site_cleanup();
                        site_init();
                    } rawMonitorExit(gdata->data_access_lock);
                } else if (cmd == 0x0007) {
                    gdata->max_trace_depth = recv_u2();
                }
                break;
            }
            default:{
                char buf[80];

                keep_processing = JNI_FALSE;
                kill_the_whole_process = JNI_TRUE;
                (void)md_snprintf(buf, sizeof(buf),
                        "failed to recognize cmd %d, exiting..", (int)tag);
                buf[sizeof(buf)-1] = 0;
                HPROF_ERROR(JNI_FALSE, buf);
                break;
            }
        }

        rawMonitorEnter(gdata->data_access_lock); {
            io_flush();
        } rawMonitorExit(gdata->data_access_lock);

        rawMonitorEnter(gdata->listener_loop_lock); {
            if ( !gdata->listener_loop_running ) {
                keep_processing         = JNI_FALSE;
            }
        } rawMonitorExit(gdata->listener_loop_lock);

    }

    /* If listener_term() is causing this loop to terminate, then
     *   you will block here until listener_term wants you to proceed.
     */
    rawMonitorEnter(gdata->listener_loop_lock); {
        if ( gdata->listener_loop_running ) {
            /* We are terminating for our own reasons, maybe because of
             *   EOF (socket closed?), or EXIT request, or invalid command.
             *   Not from listener_term().
             *   We set gdata->listener_loop_running=FALSE so that any
             *   future call to listener_term() will do nothing.
             */
            gdata->listener_loop_running = JNI_FALSE;
        } else {
            /* We assume that listener_term() is stopping us,
             *    now we need to tell it we understood.
             */
            rawMonitorNotifyAll(gdata->listener_loop_lock);
        }
    } rawMonitorExit(gdata->listener_loop_lock);

    LOG3("listener_loop", "finished command = ", tag);

    /* If we got an explicit command request to die, die here */
    if ( kill_the_whole_process ) {
        error_exit_process(0);
    }

}

/* External functions */

void
listener_init(JNIEnv *env)
{
    /* Create the raw monitor */
    gdata->listener_loop_lock = createRawMonitor("HPROF listener lock");

    rawMonitorEnter(gdata->listener_loop_lock); {
        createAgentThread(env, "HPROF listener thread",
                                &listener_loop_function);
        /* Wait for listener_loop_function() to tell us it started. */
        rawMonitorWait(gdata->listener_loop_lock, 0);
    } rawMonitorExit(gdata->listener_loop_lock);
}

void
listener_term(JNIEnv *env)
{
    rawMonitorEnter(gdata->listener_loop_lock); {

        /* If we are in the middle of sending bytes down the socket, this
         *   at least keeps us blocked until that processing is done.
         */
        rawMonitorEnter(gdata->data_access_lock); {

            /* Make sure the socket gets everything */
            io_flush();

            /*
             * Graceful shutdown of the socket will assure that all data
             * sent is received before the socket close completes.
             */
            (void)md_shutdown(gdata->fd, 2 /* disallow sends and receives */);

            /* This close will cause the listener loop to possibly wake up
             *    from the recv_u1(), this is critical to get thread running again.
             */
            md_close(gdata->fd);
        } rawMonitorExit(gdata->data_access_lock);

        /* It could have shut itself down, so we check the global flag */
        if ( gdata->listener_loop_running ) {
            /* It stopped because of something listener_term() did. */
            gdata->listener_loop_running = JNI_FALSE;
            /* Wait for listener_loop_function() to tell us it finished. */
            rawMonitorWait(gdata->listener_loop_lock, 0);
        }
    } rawMonitorExit(gdata->listener_loop_lock);
}
