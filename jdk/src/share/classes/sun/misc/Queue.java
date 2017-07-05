/*
 * Copyright 1996-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.misc;

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * Queue: implements a simple queue mechanism.  Allows for enumeration of the
 * elements.
 *
 * @author Herb Jellinek
 */

public class Queue {

    int length = 0;

    QueueElement head = null;
    QueueElement tail = null;

    public Queue() {
    }

    /**
     * Enqueue an object.
     */
    public synchronized void enqueue(Object obj) {

        QueueElement newElt = new QueueElement(obj);

        if (head == null) {
            head = newElt;
            tail = newElt;
            length = 1;
        } else {
            newElt.next = head;
            head.prev = newElt;
            head = newElt;
            length++;
        }
        notify();
    }

    /**
     * Dequeue the oldest object on the queue.  Will wait indefinitely.
     *
     * @return    the oldest object on the queue.
     * @exception java.lang.InterruptedException if any thread has
     *              interrupted this thread.
     */
    public Object dequeue() throws InterruptedException {
        return dequeue(0L);
    }

    /**
     * Dequeue the oldest object on the queue.
     * @param timeOut the number of milliseconds to wait for something
     * to arrive.
     *
     * @return    the oldest object on the queue.
     * @exception java.lang.InterruptedException if any thread has
     *              interrupted this thread.
     */
    public synchronized Object dequeue(long timeOut)
        throws InterruptedException {

        while (tail == null) {
            wait(timeOut);
        }
        QueueElement elt = tail;
        tail = elt.prev;
        if (tail == null) {
            head = null;
        } else {
            tail.next = null;
        }
        length--;
        return elt.obj;
    }

    /**
     * Is the queue empty?
     * @return true if the queue is empty.
     */
    public synchronized boolean isEmpty() {
        return (tail == null);
    }

    /**
     * Returns an enumeration of the elements in Last-In, First-Out
     * order. Use the Enumeration methods on the returned object to
     * fetch the elements sequentially.
     */
    public final synchronized Enumeration elements() {
        return new LIFOQueueEnumerator(this);
    }

    /**
     * Returns an enumeration of the elements in First-In, First-Out
     * order. Use the Enumeration methods on the returned object to
     * fetch the elements sequentially.
     */
    public final synchronized Enumeration reverseElements() {
        return new FIFOQueueEnumerator(this);
    }

    public synchronized void dump(String msg) {
        System.err.println(">> "+msg);
        System.err.println("["+length+" elt(s); head = "+
                           (head == null ? "null" : (head.obj)+"")+
                           " tail = "+(tail == null ? "null" : (tail.obj)+""));
        QueueElement cursor = head;
        QueueElement last = null;
        while (cursor != null) {
            System.err.println("  "+cursor);
            last = cursor;
            cursor = cursor.next;
        }
        if (last != tail) {
            System.err.println("  tail != last: "+tail+", "+last);
        }
        System.err.println("]");
    }
}

final class FIFOQueueEnumerator implements Enumeration {
    Queue queue;
    QueueElement cursor;

    FIFOQueueEnumerator(Queue q) {
        queue = q;
        cursor = q.tail;
    }

    public boolean hasMoreElements() {
        return (cursor != null);
    }

    public Object nextElement() {
        synchronized (queue) {
            if (cursor != null) {
                QueueElement result = cursor;
                cursor = cursor.prev;
                return result.obj;
            }
        }
        throw new NoSuchElementException("FIFOQueueEnumerator");
    }
}

final class LIFOQueueEnumerator implements Enumeration {
    Queue queue;
    QueueElement cursor;

    LIFOQueueEnumerator(Queue q) {
        queue = q;
        cursor = q.head;
    }

    public boolean hasMoreElements() {
        return (cursor != null);
    }

    public Object nextElement() {
        synchronized (queue) {
            if (cursor != null) {
                QueueElement result = cursor;
                cursor = cursor.next;
                return result.obj;
            }
        }
        throw new NoSuchElementException("LIFOQueueEnumerator");
    }
}

class QueueElement {
    QueueElement next = null;
    QueueElement prev = null;

    Object obj = null;

    QueueElement(Object obj) {
        this.obj = obj;
    }

    public String toString() {
        return "QueueElement[obj="+obj+(prev == null ? " null" : " prev")+
            (next == null ? " null" : " next")+"]";
    }
}
