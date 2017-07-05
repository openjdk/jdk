/*
 * Copyright (c) 2001, 2005, Oracle and/or its affiliates. All rights reserved.
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
/*
 * This module tracks classes that have been prepared, so as to
 * be able to compute which have been unloaded.  On VM start-up
 * all prepared classes are put in a table.  As class prepare
 * events come in they are added to the table.  After an unload
 * event or series of them, the VM can be asked for the list
 * of classes; this list is compared against the table keep by
 * this module, any classes no longer present are known to
 * have been unloaded.
 *
 * For efficient access, classes are keep in a hash table.
 * Each slot in the hash table has a linked list of KlassNode.
 *
 * Comparing current set of classes is compared with previous
 * set by transferring all classes in the current set into
 * a new table, any that remain in the old table have been
 * unloaded.
 */

#include "util.h"
#include "bag.h"
#include "classTrack.h"

/* ClassTrack hash table slot count */
#define CT_HASH_SLOT_COUNT 263    /* Prime which eauals 4k+3 for some k */

typedef struct KlassNode {
    jclass klass;            /* weak global reference */
    char *signature;         /* class signature */
    struct KlassNode *next;  /* next node in this slot */
} KlassNode;

/*
 * Hash table of prepared classes.  Each entry is a pointer
 * to a linked list of KlassNode.
 */
static KlassNode **table;

/*
 * Return slot in hash table to use for this class.
 */
static jint
hashKlass(jclass klass)
{
    jint hashCode = objectHashCode(klass);
    return abs(hashCode) % CT_HASH_SLOT_COUNT;
}

/*
 * Transfer a node (which represents klass) from the current
 * table to the new table.
 */
static void
transferClass(JNIEnv *env, jclass klass, KlassNode **newTable) {
    jint slot = hashKlass(klass);
    KlassNode **head = &table[slot];
    KlassNode **newHead = &newTable[slot];
    KlassNode **nodePtr;
    KlassNode *node;

    /* Search the node list of the current table for klass */
    for (nodePtr = head; node = *nodePtr, node != NULL; nodePtr = &(node->next)) {
        if (isSameObject(env, klass, node->klass)) {
            /* Match found transfer node */

            /* unlink from old list */
            *nodePtr = node->next;

            /* insert in new list */
            node->next = *newHead;
            *newHead = node;

            return;
        }
    }

    /* we haven't found the class, only unloads should have happenned,
     * so the only reason a class should not have been found is
     * that it is not prepared yet, in which case we don't want it.
     * Asset that the above is true.
     */
/**** the HotSpot VM doesn't create prepare events for some internal classes ***
    JDI_ASSERT_MSG((classStatus(klass) &
                (JVMTI_CLASS_STATUS_PREPARED|JVMTI_CLASS_STATUS_ARRAY))==0,
               classSignature(klass));
***/
}

/*
 * Delete a hash table of classes.
 * The signatures of classes in the table are returned.
 */
static struct bag *
deleteTable(JNIEnv *env, KlassNode *oldTable[])
{
    struct bag *signatures = bagCreateBag(sizeof(char*), 10);
    jint slot;

    if (signatures == NULL) {
        EXIT_ERROR(AGENT_ERROR_OUT_OF_MEMORY,"signatures");
    }

    for (slot = 0; slot < CT_HASH_SLOT_COUNT; slot++) {
        KlassNode *node = oldTable[slot];

        while (node != NULL) {
            KlassNode *next;
            char **sigSpot;

            /* Add signature to the signature bag */
            sigSpot = bagAdd(signatures);
            if (sigSpot == NULL) {
                EXIT_ERROR(AGENT_ERROR_OUT_OF_MEMORY,"signature bag");
            }
            *sigSpot = node->signature;

            /* Free weak ref and the node itself */
            JNI_FUNC_PTR(env,DeleteWeakGlobalRef)(env, node->klass);
            next = node->next;
            jvmtiDeallocate(node);

            node = next;
        }
    }
    jvmtiDeallocate(oldTable);

    return signatures;
}

/*
 * Called after class unloads have occurred.  Creates a new hash table
 * of currently loaded prepared classes.
 * The signatures of classes which were unloaded (not present in the
 * new table) are returned.
 */
struct bag *
classTrack_processUnloads(JNIEnv *env)
{
    KlassNode **newTable;
    struct bag *unloadedSignatures;

    unloadedSignatures = NULL;
    newTable = jvmtiAllocate(CT_HASH_SLOT_COUNT * sizeof(KlassNode *));
    if (newTable == NULL) {
        EXIT_ERROR(AGENT_ERROR_OUT_OF_MEMORY, "classTrack table");
    } else {

        (void)memset(newTable, 0, CT_HASH_SLOT_COUNT * sizeof(KlassNode *));

        WITH_LOCAL_REFS(env, 1) {

            jint classCount;
            jclass *classes;
            jvmtiError error;
            int i;

            error = allLoadedClasses(&classes, &classCount);
            if ( error != JVMTI_ERROR_NONE ) {
                jvmtiDeallocate(newTable);
                EXIT_ERROR(error,"loaded classes");
            } else {

                /* Transfer each current class into the new table */
                for (i=0; i<classCount; i++) {
                    jclass klass = classes[i];
                    transferClass(env, klass, newTable);
                }
                jvmtiDeallocate(classes);

                /* Delete old table, install new one */
                unloadedSignatures = deleteTable(env, table);
                table = newTable;
            }

        } END_WITH_LOCAL_REFS(env)

    }

    return unloadedSignatures;
}

/*
 * Add a class to the prepared class hash table.
 * Assumes no duplicates.
 */
void
classTrack_addPreparedClass(JNIEnv *env, jclass klass)
{
    jint slot = hashKlass(klass);
    KlassNode **head = &table[slot];
    KlassNode *node;
    jvmtiError error;

    if (gdata->assertOn) {
        /* Check this is not a duplicate */
        for (node = *head; node != NULL; node = node->next) {
            if (isSameObject(env, klass, node->klass)) {
                JDI_ASSERT_FAILED("Attempting to insert duplicate class");
                break;
            }
        }
    }

    node = jvmtiAllocate(sizeof(KlassNode));
    if (node == NULL) {
        EXIT_ERROR(AGENT_ERROR_OUT_OF_MEMORY,"KlassNode");
    }
    error = classSignature(klass, &(node->signature), NULL);
    if (error != JVMTI_ERROR_NONE) {
        jvmtiDeallocate(node);
        EXIT_ERROR(error,"signature");
    }
    if ((node->klass = JNI_FUNC_PTR(env,NewWeakGlobalRef)(env, klass)) == NULL) {
        jvmtiDeallocate(node->signature);
        jvmtiDeallocate(node);
        EXIT_ERROR(AGENT_ERROR_NULL_POINTER,"NewWeakGlobalRef");
    }

    /* Insert the new node */
    node->next = *head;
    *head = node;
}

/*
 * Called once to build the initial prepared class hash table.
 */
void
classTrack_initialize(JNIEnv *env)
{
    WITH_LOCAL_REFS(env, 1) {

        jint classCount;
        jclass *classes;
        jvmtiError error;
        jint i;

        error = allLoadedClasses(&classes, &classCount);
        if ( error == JVMTI_ERROR_NONE ) {
            table = jvmtiAllocate(CT_HASH_SLOT_COUNT * sizeof(KlassNode *));
            if (table != NULL) {
                (void)memset(table, 0, CT_HASH_SLOT_COUNT * sizeof(KlassNode *));
                for (i=0; i<classCount; i++) {
                    jclass klass = classes[i];
                    jint status;
                    jint wanted =
                        (JVMTI_CLASS_STATUS_PREPARED|JVMTI_CLASS_STATUS_ARRAY);

                    /* We only want prepared classes and arrays */
                    status = classStatus(klass);
                    if ( (status & wanted) != 0 ) {
                        classTrack_addPreparedClass(env, klass);
                    }
                }
            } else {
                jvmtiDeallocate(classes);
                EXIT_ERROR(AGENT_ERROR_OUT_OF_MEMORY,"KlassNode");
            }
            jvmtiDeallocate(classes);
        } else {
            EXIT_ERROR(error,"loaded classes array");
        }

    } END_WITH_LOCAL_REFS(env)

}

void
classTrack_reset(void)
{
}
