/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.interceptors;

import org.omg.PortableInterceptor.Interceptor;
import org.omg.PortableInterceptor.ORBInitInfo;
import org.omg.PortableInterceptor.ORBInitInfoPackage.DuplicateName;

import org.omg.CORBA.INTERNAL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.lang.reflect.Array;

import com.sun.corba.se.impl.logging.InterceptorsSystemException ;

/**
 * Provides a repository of registered Portable Interceptors, organized
 * by type.  This list is designed to be accessed as efficiently as
 * possible during runtime, with the expense of added complexity during
 * initialization and interceptor registration.  The class is designed
 * to easily allow for the addition of new interceptor types.
 */
public class InterceptorList {

    // Interceptor type list.  If additional interceptors are needed,
    // add additional types in numerical order (do not skip numbers),
    // and update NUM_INTERCEPTOR_TYPES and classTypes accordingly.
    // NUM_INTERCEPTOR_TYPES represents the number of interceptor
    // types, so we know how many lists to maintain.
    static final int INTERCEPTOR_TYPE_CLIENT            = 0;
    static final int INTERCEPTOR_TYPE_SERVER            = 1;
    static final int INTERCEPTOR_TYPE_IOR               = 2;

    static final int NUM_INTERCEPTOR_TYPES              = 3;

    // Array of class types for interceptors.  This is used to create the
    // appropriate array type for each interceptor type.  These must
    // match the indices of the constants declared above.
    static final Class[] classTypes = {
        org.omg.PortableInterceptor.ClientRequestInterceptor.class,
        org.omg.PortableInterceptor.ServerRequestInterceptor.class,
        org.omg.PortableInterceptor.IORInterceptor.class
    };

    // True if no further interceptors may be registered with this list.
    private boolean locked = false;
    private InterceptorsSystemException wrapper ;

    // List of interceptors currently registered.  There are
    // NUM_INTERCEPTOR_TYPES lists of registered interceptors.
    // For example, interceptors[INTERCEPTOR_TYPE_CLIENT] contains an array
    // of objects of type ClientRequestInterceptor.
    private Interceptor[][] interceptors =
        new Interceptor[NUM_INTERCEPTOR_TYPES][];

    /**
     * Creates a new Interceptor List.  Constructor is package scope so
     * only the ORB can create it.
     */
    InterceptorList( InterceptorsSystemException wrapper ) {
        this.wrapper = wrapper ;
        // Create empty interceptors arrays for each type:
        initInterceptorArrays();
    }

    /**
     * Registers an interceptor of the given type into the interceptor list.
     * The type is one of:
     * <ul>
     *   <li>INTERCEPTOR_TYPE_CLIENT - ClientRequestInterceptor
     *   <li>INTERCEPTOR_TYPE_SERVER - ServerRequestInterceptor
     *   <li>INTERCEPTOR_TYPE_IOR - IORInterceptor
     * </ul>
     *
     * @exception DuplicateName Thrown if an interceptor of the given
     *     name already exists for the given type.
     */
    void register_interceptor( Interceptor interceptor, int type )
        throws DuplicateName
    {
        // If locked, deny any further addition of interceptors.
        if( locked ) {
            throw wrapper.interceptorListLocked() ;
        }

        // Cache interceptor name:
        String interceptorName = interceptor.name();
        boolean anonymous = interceptorName.equals( "" );
        boolean foundDuplicate = false;
        Interceptor[] interceptorList = interceptors[type];

        // If this is not an anonymous interceptor,
        // search for an interceptor of the same name in this category:
        if( !anonymous ) {
            int size = interceptorList.length;

            // An O(n) search will suffice because register_interceptor is not
            // likely to be called often.
            for( int i = 0; i < size; i++ ) {
                Interceptor in = (Interceptor)interceptorList[i];
                if( in.name().equals( interceptorName ) ) {
                    foundDuplicate = true;
                    break;
                }
            }
        }

        if( !foundDuplicate ) {
            growInterceptorArray( type );
            interceptors[type][interceptors[type].length-1] = interceptor;
        }
        else {
            throw new DuplicateName( interceptorName );
        }
    }

    /**
     * Locks this interceptor list so that no more interceptors may be
     * registered.  This method is called after all interceptors are
     * registered for security reasons.
     */
    void lock() {
        locked = true;
    }

    /**
     * Retrieves an array of interceptors of the given type.  For efficiency,
     * the type parameter is assumed to be valid.
     */
    Interceptor[] getInterceptors( int type ) {
        return interceptors[type];
    }

    /**
     * Returns true if there is at least one interceptor of the given type,
     * or false if not.
     */
    boolean hasInterceptorsOfType( int type ) {
        return interceptors[type].length > 0;
    }

    /**
     * Initializes all interceptors arrays to zero-length arrays of the
     * correct type, based on the classTypes list.
     */
    private void initInterceptorArrays() {
        for( int type = 0; type < NUM_INTERCEPTOR_TYPES; type++ ) {
            Class classType = classTypes[type];

            // Create a zero-length array for each type:
            interceptors[type] =
                (Interceptor[])Array.newInstance( classType, 0 );
        }
    }

    /**
     * Grows the given interceptor array by one:
     */
    private void growInterceptorArray( int type ) {
        Class classType = classTypes[type];
        int currentLength = interceptors[type].length;
        Interceptor[] replacementArray;

        // Create new array to replace the old one.  The new array will be
        // one element larger but have the same type as the old one.
        replacementArray = (Interceptor[])
            Array.newInstance( classType, currentLength + 1 );
        System.arraycopy( interceptors[type], 0,
                          replacementArray, 0, currentLength );
        interceptors[type] = replacementArray;
    }

    /**
     * Destroys all interceptors in this list by invoking their destroy()
     * method.
     */
    void destroyAll() {
        int numTypes = interceptors.length;

        for( int i = 0; i < numTypes; i++ ) {
            int numInterceptors = interceptors[i].length;
            for( int j = 0; j < numInterceptors; j++ ) {
                interceptors[i][j].destroy();
            }
        }
    }

    /**
     * Sort interceptors.
     */
    void sortInterceptors() {
        List sorted = null;
        List unsorted = null;

        int numTypes = interceptors.length;

        for( int i = 0; i < numTypes; i++ ) {
            int numInterceptors = interceptors[i].length;
            if (numInterceptors > 0) {
                // Get fresh sorting bins for each non empty type.
                sorted = new ArrayList(); // not synchronized like we want.
                unsorted = new ArrayList();
            }
            for( int j = 0; j < numInterceptors; j++ ) {
                Interceptor interceptor = interceptors[i][j];
                if (interceptor instanceof Comparable) {
                    sorted.add(interceptor);
                } else {
                    unsorted.add(interceptor);
                }
            }
            if (numInterceptors > 0 && sorted.size() > 0) {
                // Let the RuntimeExceptions thrown by sort
                // (i.e., ClassCastException and UnsupportedOperationException)
                // flow back to the user.
                Collections.sort(sorted);
                Iterator sortedIterator = sorted.iterator();
                Iterator unsortedIterator = unsorted.iterator();
                for( int j = 0; j < numInterceptors; j++ ) {
                    if (sortedIterator.hasNext()) {
                        interceptors[i][j] =
                            (Interceptor) sortedIterator.next();
                    } else if (unsortedIterator.hasNext()) {
                        interceptors[i][j] =
                            (Interceptor) unsortedIterator.next();
                    } else {
                        throw wrapper.sortSizeMismatch() ;
                    }
                }
            }
        }
    }
}
