/*
 * Copyright 1999-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.management;



/**
 * Represents runtime exceptions thrown in the agent when performing operations on MBeans.
 * It wraps the actual <CODE>java.lang.RuntimeException</CODE> thrown.
 *
 * @since 1.5
 */
public class RuntimeOperationsException extends JMRuntimeException   {

    /* Serial version */
    private static final long serialVersionUID = -8408923047489133588L;

    /**
     * @serial The encapsulated {@link RuntimeException}
     */
    private java.lang.RuntimeException runtimeException ;


    /**
     * Creates a <CODE>RuntimeOperationsException</CODE> that wraps the actual <CODE>java.lang.RuntimeException</CODE>.
     *
     * @param e the wrapped exception.
     */
    public RuntimeOperationsException(java.lang.RuntimeException e) {
        super() ;
        runtimeException = e ;
    }

    /**
     * Creates a <CODE>RuntimeOperationsException</CODE> that wraps the actual <CODE>java.lang.RuntimeException</CODE>
     * with a detailed message.
     *
     * @param e the wrapped exception.
     * @param message the detail message.
     */
    public RuntimeOperationsException(java.lang.RuntimeException e, String message) {
        super(message);
        runtimeException = e ;
    }

    /**
     * Returns the actual {@link RuntimeException} thrown.
     *
     * @return the wrapped {@link RuntimeException}.
     */
    public java.lang.RuntimeException getTargetException()  {
        return runtimeException ;
    }

    /**
     * Returns the actual {@link RuntimeException} thrown.
     *
     * @return the wrapped {@link RuntimeException}.
     */
    public Throwable getCause() {
        return runtimeException;
    }
}
