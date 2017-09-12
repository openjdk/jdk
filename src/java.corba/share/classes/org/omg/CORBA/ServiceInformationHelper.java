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

 /**
  * The Helper for {@code ServiceInformation}. For more information on
  * Helper files, see <a href="doc-files/generatedfiles.html#helper">
  * "Generated Files: Helper Files"</a>.<P>
  */

package org.omg.CORBA;


public abstract class ServiceInformationHelper {

    public static void write(org.omg.CORBA.portable.OutputStream out, org.omg.CORBA.ServiceInformation that)
    {
        out.write_long(that.service_options.length);
        out.write_ulong_array(that.service_options, 0, that.service_options.length);
        out.write_long(that.service_details.length);
        for (int i = 0 ; i < that.service_details.length ; i += 1) {
            org.omg.CORBA.ServiceDetailHelper.write(out, that.service_details[i]);
        }
    }

    public static org.omg.CORBA.ServiceInformation read(org.omg.CORBA.portable.InputStream in) {
        org.omg.CORBA.ServiceInformation that = new org.omg.CORBA.ServiceInformation();
        {
            int __length = in.read_long();
            that.service_options = new int[__length];
            in.read_ulong_array(that.service_options, 0, that.service_options.length);
        }
        {
            int __length = in.read_long();
            that.service_details = new org.omg.CORBA.ServiceDetail[__length];
            for (int __index = 0 ; __index < that.service_details.length ; __index += 1) {
                that.service_details[__index] = org.omg.CORBA.ServiceDetailHelper.read(in);
            }
        }
        return that;
    }
    public static org.omg.CORBA.ServiceInformation extract(org.omg.CORBA.Any a) {
        org.omg.CORBA.portable.InputStream in = a.create_input_stream();
        return read(in);
    }
    public static void insert(org.omg.CORBA.Any a, org.omg.CORBA.ServiceInformation that) {
        org.omg.CORBA.portable.OutputStream out = a.create_output_stream();
        write(out, that);
        a.read_value(out.create_input_stream(), type());
    }
    private static org.omg.CORBA.TypeCode _tc;
    synchronized public static org.omg.CORBA.TypeCode type() {
        int _memberCount = 2;
        org.omg.CORBA.StructMember[] _members = null;
        if (_tc == null) {
            _members= new org.omg.CORBA.StructMember[2];
            _members[0] = new org.omg.CORBA.StructMember(
                                                         "service_options",
                                                         org.omg.CORBA.ORB.init().create_sequence_tc(0, org.omg.CORBA.ORB.init().get_primitive_tc(org.omg.CORBA.TCKind.tk_ulong)),
                                                         null);

            _members[1] = new org.omg.CORBA.StructMember(
                                                         "service_details",
                                                         org.omg.CORBA.ORB.init().create_sequence_tc(0, org.omg.CORBA.ServiceDetailHelper.type()),
                                                         null);
            _tc = org.omg.CORBA.ORB.init().create_struct_tc(id(), "ServiceInformation", _members);
        }
        return _tc;
    }
    public static String id() {
        return "IDL:omg.org/CORBA/ServiceInformation:1.0";
    }
}
