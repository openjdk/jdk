/*
 * Copyright (c) 1997, 2000, Oracle and/or its affiliates. All rights reserved.
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
 * File: ./org/omg/CosNaming/_BindingIteratorImplBase.java
 * From: nameservice.idl
 * Date: Tue Aug 11 03:12:09 1998
 *   By: idltojava Java IDL 1.2 Aug 11 1998 02:00:18
 * @deprecated Deprecated in JDK 1.4.
 */

package org.omg.CosNaming;
public abstract class _BindingIteratorImplBase extends org.omg.CORBA.DynamicImplementation implements org.omg.CosNaming.BindingIterator {
    // Constructor
    public _BindingIteratorImplBase() {
        super();
    }
    // Type strings for this class and its superclases
    private static final String _type_ids[] = {
        "IDL:omg.org/CosNaming/BindingIterator:1.0"
    };

    public String[] _ids() { return (String[]) _type_ids.clone(); }

    private static java.util.Dictionary _methods = new java.util.Hashtable();
    static {
        _methods.put("next_one", new java.lang.Integer(0));
        _methods.put("next_n", new java.lang.Integer(1));
        _methods.put("destroy", new java.lang.Integer(2));
    }
    // DSI Dispatch call
    public void invoke(org.omg.CORBA.ServerRequest r) {
        switch (((java.lang.Integer) _methods.get(r.op_name())).intValue()) {
        case 0: // org.omg.CosNaming.BindingIterator.next_one
            {
                org.omg.CORBA.NVList _list = _orb().create_list(0);
                org.omg.CORBA.Any _b = _orb().create_any();
                _b.type(org.omg.CosNaming.BindingHelper.type());
                _list.add_value("b", _b, org.omg.CORBA.ARG_OUT.value);
                r.params(_list);
                org.omg.CosNaming.BindingHolder b;
                b = new org.omg.CosNaming.BindingHolder();
                boolean ___result;
                ___result = this.next_one(b);
                org.omg.CosNaming.BindingHelper.insert(_b, b.value);
                org.omg.CORBA.Any __result = _orb().create_any();
                __result.insert_boolean(___result);
                r.result(__result);
            }
            break;
        case 1: // org.omg.CosNaming.BindingIterator.next_n
            {
                org.omg.CORBA.NVList _list = _orb().create_list(0);
                org.omg.CORBA.Any _how_many = _orb().create_any();
                _how_many.type(org.omg.CORBA.ORB.init().get_primitive_tc(org.omg.CORBA.TCKind.tk_ulong));
                _list.add_value("how_many", _how_many, org.omg.CORBA.ARG_IN.value);
                org.omg.CORBA.Any _bl = _orb().create_any();
                _bl.type(org.omg.CosNaming.BindingListHelper.type());
                _list.add_value("bl", _bl, org.omg.CORBA.ARG_OUT.value);
                r.params(_list);
                int how_many;
                how_many = _how_many.extract_ulong();
                org.omg.CosNaming.BindingListHolder bl;
                bl = new org.omg.CosNaming.BindingListHolder();
                boolean ___result;
                ___result = this.next_n(how_many, bl);
                org.omg.CosNaming.BindingListHelper.insert(_bl, bl.value);
                org.omg.CORBA.Any __result = _orb().create_any();
                __result.insert_boolean(___result);
                r.result(__result);
            }
            break;
        case 2: // org.omg.CosNaming.BindingIterator.destroy
            {
                org.omg.CORBA.NVList _list = _orb().create_list(0);
                r.params(_list);
                this.destroy();
                org.omg.CORBA.Any __return = _orb().create_any();
                __return.type(_orb().get_primitive_tc(org.omg.CORBA.TCKind.tk_void));
                r.result(__return);
            }
            break;
        default:
            throw new org.omg.CORBA.BAD_OPERATION(0, org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);
        }
    }
}
