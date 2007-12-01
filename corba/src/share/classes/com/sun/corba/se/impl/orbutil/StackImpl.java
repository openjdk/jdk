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

package com.sun.corba.se.impl.orbutil ;

import java.util.EmptyStackException ;

// We implement a Stack here instead of using java.util.Stack because
// java.util.Stack is thread-safe, negatively impacting performance.
// We use an ArrayList instead since it is not thread-safe.
// RequestInfoStack is used quite frequently.
public class StackImpl {
    // The stack for RequestInfo objects.
    private Object[] data = new Object[3] ;
    private int top = -1 ;

    // Tests if this stack is empty.
    public final boolean empty() {
        return top == -1;
    }

    // Looks at the object at the top of this stack without removing it
    // from the stack.
    public final Object peek() {
        if (empty())
            throw new EmptyStackException();

        return data[ top ];
    }

    // Removes the object at the top of this stack and returns that
    // object as the value of this function.
    public final Object pop() {
        Object obj = peek() ;
        data[top] = null ;
        top-- ;
        return obj;
    }

    private void ensure()
    {
        if (top == (data.length-1)) {
            int newSize = 2*data.length ;
            Object[] newData = new Object[ newSize ] ;
            System.arraycopy( data, 0, newData, 0, data.length ) ;
            data = newData ;
        }
    }

    // Pushes an item onto the top of the stack
    public final Object push( Object item ) {
        ensure() ;
        top++ ;
        data[top] = item;
        return item;
    }
}
