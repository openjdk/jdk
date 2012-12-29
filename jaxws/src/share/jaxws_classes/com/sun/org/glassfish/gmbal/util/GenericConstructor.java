/*
 * Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
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



package com.sun.org.glassfish.gmbal.util;

import java.lang.reflect.Constructor ;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Class that allows any class to be instantiated via any accessible constructor.
 * Really a short hand to avoid writing a bunch of reflective code.
 */
public class GenericConstructor<T> {
    private final Object lock = new Object() ;

    private String typeName ;
    private Class<T> resultType ;
    private Class<?> type ;
    private Class<?>[] signature ;

    // Use the raw type of the constructor here, because
    // MethodInfo can only return a raw type for a constructor.
    // It is not possible to have MethodInfo return a
    // Constructor<T> because T may not be known at compile time.
    private Constructor constructor ;

    /** Create a generic of type T for the untyped class cls.
     * Generally cls is a class that has been generated and loaded, so
     * no compiled code can depend on the class directly.  However, the
     * generated class probably implements some interface T, represented
     * here by Class<T>.
     * @param type The expected type of a create call.
     * @param className The name of the class to use for a constructor.
     * @param signature The signature of the desired constructor.
     * @throws IllegalArgumentException if cls is not a subclass of type.
     */
    public GenericConstructor( final Class<T> type, final String className,
        final Class<?>... signature ) {
        this.resultType = type ;
        this.typeName = className ;
        this.signature = signature.clone() ;
    }

    @SuppressWarnings("unchecked")
    private void getConstructor() {
        synchronized( lock ) {
            if ((type == null) || (constructor == null)) {
                try {
                    type = (Class<T>)Class.forName( typeName ) ;
                    constructor = AccessController.doPrivileged(
                        new PrivilegedExceptionAction<Constructor>() {
                            public Constructor run() throws Exception {
                                synchronized( lock ) {
                                    return type.getDeclaredConstructor( signature ) ;
                                }
                            }
                        } ) ;
                } catch (Exception exc) {
                    // Catch all for several checked exceptions: ignore findbugs
                    Logger.getLogger( "com.sun.org.glassfish.gmbal.util" ).log( Level.FINE,
                        "Failure in getConstructor", exc ) ;
                }
            }
        }
    }

    /** Create an instance of type T using the constructor that
     * matches the given arguments if possible.  The constructor
     * is cached, so an instance of GenericClass should always be
     * used for the same types of arguments.  If a call fails,
     * a check is made to see if a different constructor could
     * be used.
     * @param args The constructor arguments.
     * @return A new instance of the object.
     */
    public synchronized T create( Object... args ) {
        synchronized(lock) {
            T result = null ;

            for (int ctr=0; ctr<=1; ctr++) {
                getConstructor() ;
                if (constructor == null) {
                    break ;
                }

                try {
                    result = resultType.cast( constructor.newInstance( args ) ) ;
                    break ;
                } catch (Exception exc) {
                    // There are 4 checked exceptions here with identical handling.
                    // Ignore FindBugs complaints.
                    constructor = null ;
                    Logger.getLogger("com.sun.org.glassfish.gmbal.util").
                        log(Level.WARNING, "Error invoking constructor", exc );
                }
            }

            return result ;
        }
    }
}
