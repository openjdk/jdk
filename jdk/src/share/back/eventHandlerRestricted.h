/*
 * Copyright 2001-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

#ifndef JDWP_EVENTHANDLERRESTRICTED_H
#define JDWP_EVENTHANDLERRESTRICTED_H

/**
 * eventHandler functionality restricted to use only by it's
 * component - eventFilter.
 */

typedef jboolean (*IteratorFunction)(JNIEnv *env,
                                     HandlerNode *node,
                                     void *arg);
jboolean eventHandlerRestricted_iterator(EventIndex ei,
                              IteratorFunction func, void *arg);

/* HandlerNode data has three components:
 *    public info                (HandlerNode)  as declared in eventHandler.h
 *    eventHandler private data  (EventHandlerPrivate_Data) as declared below
 *    eventFilter private data   declared privately in eventFilter.c
 *
 * These three components are stored sequentially within the node.
 */

/* this is HandlerNode PRIVATE data  --
 * present in this .h file only for defining EventHandlerRestricted_HandlerNode
 */
typedef struct EventHandlerPrivate_Data_ {
    struct HandlerNode_      *private_next;
    struct HandlerNode_      *private_prev;
    struct HandlerChain_     *private_chain;
    HandlerFunction private_handlerFunction;
} EventHandlerPrivate_Data;

/* this structure should only be used outside of eventHandler
 * for proper address computation
 */
typedef struct EventHandlerRestricted_HandlerNode_ {
    HandlerNode                 hn;
    EventHandlerPrivate_Data    private_ehpd;
} EventHandlerRestricted_HandlerNode;

#endif
