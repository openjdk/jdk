/*
 * Copyright (c) 1998, 2001, Oracle and/or its affiliates. All rights reserved.
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

package org.omg.CORBA;


/** An IDL struct in the CORBA module that
 *  stores information about a CORBA service available in the
 *  ORB implementation and is obtained from the <tt>ORB.get_service_information</tt>
 *  method.
 */
public final class ServiceInformation implements org.omg.CORBA.portable.IDLEntity
{
    /** Array of ints representing service options.
    */
    public int[] service_options;

    /** Array of ServiceDetails giving more details about the service.
    */
    public org.omg.CORBA.ServiceDetail[] service_details;

    /** Constructs a ServiceInformation object with empty service_options
    * and service_details.
    */
    public ServiceInformation() { }

    /** Constructs a ServiceInformation object with the given service_options
    * and service_details.
    * @param __service_options An array of ints describing the service options.
    * @param __service_details An array of ServiceDetails describing the service
    * details.
    */
    public ServiceInformation(int[] __service_options,
                              org.omg.CORBA.ServiceDetail[] __service_details)
    {
        service_options = __service_options;
        service_details = __service_details;
    }
}
