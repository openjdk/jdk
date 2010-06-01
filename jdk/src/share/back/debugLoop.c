/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

#include "util.h"
#include "transport.h"
#include "debugLoop.h"
#include "debugDispatch.h"
#include "standardHandlers.h"
#include "inStream.h"
#include "outStream.h"
#include "threadControl.h"


static void JNICALL reader(jvmtiEnv* jvmti_env, JNIEnv* jni_env, void* arg);
static void enqueue(jdwpPacket *p);
static jboolean dequeue(jdwpPacket *p);
static void notifyTransportError(void);

struct PacketList {
    jdwpPacket packet;
    struct PacketList *next;
};

static volatile struct PacketList *cmdQueue;
static jrawMonitorID cmdQueueLock;
static jrawMonitorID resumeLock;
static jboolean transportError;

static jboolean
lastCommand(jdwpCmdPacket *cmd)
{
    if ((cmd->cmdSet == JDWP_COMMAND_SET(VirtualMachine)) &&
        ((cmd->cmd == JDWP_COMMAND(VirtualMachine, Dispose)) ||
         (cmd->cmd == JDWP_COMMAND(VirtualMachine, Exit)))) {
        return JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
}

static jboolean
resumeCommand(jdwpCmdPacket *cmd)
{
    if ( (cmd->cmdSet == JDWP_COMMAND_SET(VirtualMachine)) &&
         (cmd->cmd == JDWP_COMMAND(VirtualMachine, Resume)) ) {
        return JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
}

void
debugLoop_initialize(void)
{
    resumeLock = debugMonitorCreate("JDWP Resume Lock");
}

void
debugLoop_sync(void)
{
    debugMonitorEnter(resumeLock);
    debugMonitorExit(resumeLock);
}

/*
 * This is where all the work gets done.
 */

void
debugLoop_run(void)
{
    jboolean shouldListen;
    jdwpPacket p;
    jvmtiStartFunction func;

    /* Initialize all statics */
    /* We may be starting a new connection after an error */
    cmdQueue = NULL;
    cmdQueueLock = debugMonitorCreate("JDWP Command Queue Lock");
    transportError = JNI_FALSE;

    shouldListen = JNI_TRUE;

    func = &reader;
    (void)spawnNewThread(func, NULL, "JDWP Command Reader");

    standardHandlers_onConnect();
    threadControl_onConnect();

    /* Okay, start reading cmds! */
    while (shouldListen) {
        if (!dequeue(&p)) {
            break;
        }

        if (p.type.cmd.flags & JDWPTRANSPORT_FLAGS_REPLY) {
            /*
             * Its a reply packet.
             */
           continue;
        } else {
            /*
             * Its a cmd packet.
             */
            jdwpCmdPacket *cmd = &p.type.cmd;
            PacketInputStream in;
            PacketOutputStream out;
            CommandHandler func;

            /* Should reply be sent to sender.
             * For error handling, assume yes, since
             * only VM/exit does not reply
             */
            jboolean replyToSender = JNI_TRUE;

            /*
             * For VirtualMachine.Resume commands we hold the resumeLock
             * while executing and replying to the command. This ensures
             * that a Resume after VM_DEATH will be allowed to complete
             * before the thread posting the VM_DEATH continues VM
             * termination.
             */
            if (resumeCommand(cmd)) {
                debugMonitorEnter(resumeLock);
            }

            /* Initialize the input and output streams */
            inStream_init(&in, p);
            outStream_initReply(&out, inStream_id(&in));

            LOG_MISC(("Command set %d, command %d", cmd->cmdSet, cmd->cmd));

            func = debugDispatch_getHandler(cmd->cmdSet,cmd->cmd);
            if (func == NULL) {
                /* we've never heard of this, so I guess we
                 * haven't implemented it.
                 * Handle gracefully for future expansion
                 * and platform / vendor expansion.
                 */
                outStream_setError(&out, JDWP_ERROR(NOT_IMPLEMENTED));
            } else if (gdata->vmDead &&
             ((cmd->cmdSet) != JDWP_COMMAND_SET(VirtualMachine))) {
                /* Protect the VM from calls while dead.
                 * VirtualMachine cmdSet quietly ignores some cmds
                 * after VM death, so, it sends it's own errors.
                 */
                outStream_setError(&out, JDWP_ERROR(VM_DEAD));
            } else {
                /* Call the command handler */
                replyToSender = func(&in, &out);
            }

            /* Reply to the sender */
            if (replyToSender) {
                if (inStream_error(&in)) {
                    outStream_setError(&out, inStream_error(&in));
                }
                outStream_sendReply(&out);
            }

            /*
             * Release the resumeLock as the reply has been posted.
             */
            if (resumeCommand(cmd)) {
                debugMonitorExit(resumeLock);
            }

            inStream_destroy(&in);
            outStream_destroy(&out);

            shouldListen = !lastCommand(cmd);
        }
    }
    threadControl_onDisconnect();
    standardHandlers_onDisconnect();

    /*
     * Cut off the transport immediately. This has the effect of
     * cutting off any events that the eventHelper thread might
     * be trying to send.
     */
    transport_close();
    debugMonitorDestroy(cmdQueueLock);

    /* Reset for a new connection to this VM if it's still alive */
    if ( ! gdata->vmDead ) {
        debugInit_reset(getEnv());
    }
}

/* Command reader */
static void JNICALL
reader(jvmtiEnv* jvmti_env, JNIEnv* jni_env, void* arg)
{
    jdwpPacket packet;
    jdwpCmdPacket *cmd;
    jboolean shouldListen = JNI_TRUE;

    LOG_MISC(("Begin reader thread"));

    while (shouldListen) {
        jint rc;

        rc = transport_receivePacket(&packet);

        /* I/O error or EOF */
        if (rc != 0 || (rc == 0 && packet.type.cmd.len == 0)) {
            shouldListen = JNI_FALSE;
            notifyTransportError();
        } else {
            cmd = &packet.type.cmd;

            LOG_MISC(("Command set %d, command %d", cmd->cmdSet, cmd->cmd));

            /*
             * FIXME! We need to deal with high priority
             * packets and queue flushes!
             */
            enqueue(&packet);

            shouldListen = !lastCommand(cmd);
        }
    }
    LOG_MISC(("End reader thread"));
}

/*
 * The current system for queueing packets is highly
 * inefficient, and should be rewritten! It'd be nice
 * to avoid any additional memory allocations.
 */

static void
enqueue(jdwpPacket *packet)
{
    struct PacketList *pL;
    struct PacketList *walker;

    pL = jvmtiAllocate((jint)sizeof(struct PacketList));
    if (pL == NULL) {
        EXIT_ERROR(AGENT_ERROR_OUT_OF_MEMORY,"packet list");
    }

    pL->packet = *packet;
    pL->next = NULL;

    debugMonitorEnter(cmdQueueLock);

    if (cmdQueue == NULL) {
        cmdQueue = pL;
        debugMonitorNotify(cmdQueueLock);
    } else {
        walker = (struct PacketList *)cmdQueue;
        while (walker->next != NULL)
            walker = walker->next;

        walker->next = pL;
    }

    debugMonitorExit(cmdQueueLock);
}

static jboolean
dequeue(jdwpPacket *packet) {
    struct PacketList *node = NULL;

    debugMonitorEnter(cmdQueueLock);

    while (!transportError && (cmdQueue == NULL)) {
        debugMonitorWait(cmdQueueLock);
    }

    if (cmdQueue != NULL) {
        node = (struct PacketList *)cmdQueue;
        cmdQueue = node->next;
    }
    debugMonitorExit(cmdQueueLock);

    if (node != NULL) {
        *packet = node->packet;
        jvmtiDeallocate(node);
    }
    return (node != NULL);
}

static void
notifyTransportError(void) {
    debugMonitorEnter(cmdQueueLock);
    transportError = JNI_TRUE;
    debugMonitorNotify(cmdQueueLock);
    debugMonitorExit(cmdQueueLock);
}
