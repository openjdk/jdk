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

// java import
import java.io.Serializable;


/**
 * <p>Represents relational constraints similar to database query "where
 * clauses". Instances of QueryExp are returned by the static methods of the
 * {@link Query} class.</p>
 *
 * <p>It is possible, but not
 * recommended, to create custom queries by implementing this
 * interface.  In that case, it is better to extend the {@link
 * QueryEval} class than to implement the interface directly, so that
 * the {@link #setMBeanServer} method works correctly.
 *
 * @see MBeanServer#queryNames MBeanServer.queryNames
 * @since 1.5
 */
public interface QueryExp extends Serializable {


     /**
      * Applies the QueryExp on an MBean.
      *
      * @param name The name of the MBean on which the QueryExp will be applied.
      *
      * @return  True if the query was successfully applied to the MBean, false otherwise
      *
      * @exception BadStringOperationException
      * @exception BadBinaryOpValueExpException
      * @exception BadAttributeValueExpException
      * @exception InvalidApplicationException
      */
     public boolean apply(ObjectName name) throws BadStringOperationException, BadBinaryOpValueExpException,
         BadAttributeValueExpException, InvalidApplicationException ;

     /**
      * Sets the MBean server on which the query is to be performed.
      *
      * @param s The MBean server on which the query is to be performed.
      */
     public void setMBeanServer(MBeanServer s) ;

 }
