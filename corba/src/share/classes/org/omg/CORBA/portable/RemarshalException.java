/*
 * Copyright (c) 1998, 1999, Oracle and/or its affiliates. All rights reserved.
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
package org.omg.CORBA.portable;

/**
This class is used for reporting locate forward exceptions and object forward
GIOP messages back to the ORB. In this case the ORB must remarshal the request
before trying again.
Stubs which use the stream-based model shall catch the <code>RemarshalException</code>
which is potentially thrown from the <code>_invoke()</code> method of <code>ObjectImpl</code>.
Upon catching the exception, the stub shall immediately remarshal the request by calling
<code>_request()</code>, marshalling the arguments (if any), and then calling
<code>_invoke()</code>. The stub shall repeat this process until <code>_invoke()</code>
returns normally or raises some exception other than <code>RemarshalException</code>.
*/

public final class RemarshalException extends Exception {
    /**
     * Constructs a RemarshalException.
     */
    public RemarshalException() {
        super();
    }
}
