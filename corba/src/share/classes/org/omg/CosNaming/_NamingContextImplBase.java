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
 * File: ./org/omg/CosNaming/_NamingContextImplBase.java
 * From: nameservice.idl
 * Date: Tue Aug 11 03:12:09 1998
 *   By: idltojava Java IDL 1.2 Aug 11 1998 02:00:18
 * @deprecated Deprecated in JDK 1.4.
 */

package org.omg.CosNaming;
public abstract class _NamingContextImplBase extends org.omg.CORBA.DynamicImplementation implements org.omg.CosNaming.NamingContext {
    // Constructor
    public _NamingContextImplBase() {
        super();
    }
    // Type strings for this class and its superclases
    private static final String _type_ids[] = {
        "IDL:omg.org/CosNaming/NamingContext:1.0"
    };

    public String[] _ids() { return (String[]) _type_ids.clone(); }

    private static java.util.Dictionary _methods = new java.util.Hashtable();
    static {
        _methods.put("bind", new java.lang.Integer(0));
        _methods.put("bind_context", new java.lang.Integer(1));
        _methods.put("rebind", new java.lang.Integer(2));
        _methods.put("rebind_context", new java.lang.Integer(3));
        _methods.put("resolve", new java.lang.Integer(4));
        _methods.put("unbind", new java.lang.Integer(5));
        _methods.put("list", new java.lang.Integer(6));
        _methods.put("new_context", new java.lang.Integer(7));
        _methods.put("bind_new_context", new java.lang.Integer(8));
        _methods.put("destroy", new java.lang.Integer(9));
    }
    // DSI Dispatch call
    public void invoke(org.omg.CORBA.ServerRequest r) {
        switch (((java.lang.Integer) _methods.get(r.op_name())).intValue()) {
        case 0: // org.omg.CosNaming.NamingContext.bind
            {
                org.omg.CORBA.NVList _list = _orb().create_list(0);
                org.omg.CORBA.Any _n = _orb().create_any();
                _n.type(org.omg.CosNaming.NameHelper.type());
                _list.add_value("n", _n, org.omg.CORBA.ARG_IN.value);
                org.omg.CORBA.Any _obj = _orb().create_any();
                _obj.type(org.omg.CORBA.ORB.init().get_primitive_tc(org.omg.CORBA.TCKind.tk_objref));
                _list.add_value("obj", _obj, org.omg.CORBA.ARG_IN.value);
                r.params(_list);
                org.omg.CosNaming.NameComponent[] n;
                n = org.omg.CosNaming.NameHelper.extract(_n);
                org.omg.CORBA.Object obj;
                obj = _obj.extract_Object();
                try {
                    this.bind(n, obj);
                }
                catch (org.omg.CosNaming.NamingContextPackage.NotFound e0) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.NotFoundHelper.insert(_except, e0);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.CannotProceed e1) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.CannotProceedHelper.insert(_except, e1);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.InvalidName e2) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.InvalidNameHelper.insert(_except, e2);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.AlreadyBound e3) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.AlreadyBoundHelper.insert(_except, e3);
                    r.except(_except);
                    return;
                }
                org.omg.CORBA.Any __return = _orb().create_any();
                __return.type(_orb().get_primitive_tc(org.omg.CORBA.TCKind.tk_void));
                r.result(__return);
            }
            break;
        case 1: // org.omg.CosNaming.NamingContext.bind_context
            {
                org.omg.CORBA.NVList _list = _orb().create_list(0);
                org.omg.CORBA.Any _n = _orb().create_any();
                _n.type(org.omg.CosNaming.NameHelper.type());
                _list.add_value("n", _n, org.omg.CORBA.ARG_IN.value);
                org.omg.CORBA.Any _nc = _orb().create_any();
                _nc.type(org.omg.CosNaming.NamingContextHelper.type());
                _list.add_value("nc", _nc, org.omg.CORBA.ARG_IN.value);
                r.params(_list);
                org.omg.CosNaming.NameComponent[] n;
                n = org.omg.CosNaming.NameHelper.extract(_n);
                org.omg.CosNaming.NamingContext nc;
                nc = org.omg.CosNaming.NamingContextHelper.extract(_nc);
                try {
                    this.bind_context(n, nc);
                }
                catch (org.omg.CosNaming.NamingContextPackage.NotFound e0) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.NotFoundHelper.insert(_except, e0);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.CannotProceed e1) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.CannotProceedHelper.insert(_except, e1);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.InvalidName e2) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.InvalidNameHelper.insert(_except, e2);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.AlreadyBound e3) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.AlreadyBoundHelper.insert(_except, e3);
                    r.except(_except);
                    return;
                }
                org.omg.CORBA.Any __return = _orb().create_any();
                __return.type(_orb().get_primitive_tc(org.omg.CORBA.TCKind.tk_void));
                r.result(__return);
            }
            break;
        case 2: // org.omg.CosNaming.NamingContext.rebind
            {
                org.omg.CORBA.NVList _list = _orb().create_list(0);
                org.omg.CORBA.Any _n = _orb().create_any();
                _n.type(org.omg.CosNaming.NameHelper.type());
                _list.add_value("n", _n, org.omg.CORBA.ARG_IN.value);
                org.omg.CORBA.Any _obj = _orb().create_any();
                _obj.type(org.omg.CORBA.ORB.init().get_primitive_tc(org.omg.CORBA.TCKind.tk_objref));
                _list.add_value("obj", _obj, org.omg.CORBA.ARG_IN.value);
                r.params(_list);
                org.omg.CosNaming.NameComponent[] n;
                n = org.omg.CosNaming.NameHelper.extract(_n);
                org.omg.CORBA.Object obj;
                obj = _obj.extract_Object();
                try {
                    this.rebind(n, obj);
                }
                catch (org.omg.CosNaming.NamingContextPackage.NotFound e0) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.NotFoundHelper.insert(_except, e0);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.CannotProceed e1) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.CannotProceedHelper.insert(_except, e1);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.InvalidName e2) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.InvalidNameHelper.insert(_except, e2);
                    r.except(_except);
                    return;
                }
                org.omg.CORBA.Any __return = _orb().create_any();
                __return.type(_orb().get_primitive_tc(org.omg.CORBA.TCKind.tk_void));
                r.result(__return);
            }
            break;
        case 3: // org.omg.CosNaming.NamingContext.rebind_context
            {
                org.omg.CORBA.NVList _list = _orb().create_list(0);
                org.omg.CORBA.Any _n = _orb().create_any();
                _n.type(org.omg.CosNaming.NameHelper.type());
                _list.add_value("n", _n, org.omg.CORBA.ARG_IN.value);
                org.omg.CORBA.Any _nc = _orb().create_any();
                _nc.type(org.omg.CosNaming.NamingContextHelper.type());
                _list.add_value("nc", _nc, org.omg.CORBA.ARG_IN.value);
                r.params(_list);
                org.omg.CosNaming.NameComponent[] n;
                n = org.omg.CosNaming.NameHelper.extract(_n);
                org.omg.CosNaming.NamingContext nc;
                nc = org.omg.CosNaming.NamingContextHelper.extract(_nc);
                try {
                    this.rebind_context(n, nc);
                }
                catch (org.omg.CosNaming.NamingContextPackage.NotFound e0) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.NotFoundHelper.insert(_except, e0);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.CannotProceed e1) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.CannotProceedHelper.insert(_except, e1);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.InvalidName e2) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.InvalidNameHelper.insert(_except, e2);
                    r.except(_except);
                    return;
                }
                org.omg.CORBA.Any __return = _orb().create_any();
                __return.type(_orb().get_primitive_tc(org.omg.CORBA.TCKind.tk_void));
                r.result(__return);
            }
            break;
        case 4: // org.omg.CosNaming.NamingContext.resolve
            {
                org.omg.CORBA.NVList _list = _orb().create_list(0);
                org.omg.CORBA.Any _n = _orb().create_any();
                _n.type(org.omg.CosNaming.NameHelper.type());
                _list.add_value("n", _n, org.omg.CORBA.ARG_IN.value);
                r.params(_list);
                org.omg.CosNaming.NameComponent[] n;
                n = org.omg.CosNaming.NameHelper.extract(_n);
                org.omg.CORBA.Object ___result;
                try {
                    ___result = this.resolve(n);
                }
                catch (org.omg.CosNaming.NamingContextPackage.NotFound e0) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.NotFoundHelper.insert(_except, e0);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.CannotProceed e1) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.CannotProceedHelper.insert(_except, e1);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.InvalidName e2) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.InvalidNameHelper.insert(_except, e2);
                    r.except(_except);
                    return;
                }
                org.omg.CORBA.Any __result = _orb().create_any();
                __result.insert_Object(___result);
                r.result(__result);
            }
            break;
        case 5: // org.omg.CosNaming.NamingContext.unbind
            {
                org.omg.CORBA.NVList _list = _orb().create_list(0);
                org.omg.CORBA.Any _n = _orb().create_any();
                _n.type(org.omg.CosNaming.NameHelper.type());
                _list.add_value("n", _n, org.omg.CORBA.ARG_IN.value);
                r.params(_list);
                org.omg.CosNaming.NameComponent[] n;
                n = org.omg.CosNaming.NameHelper.extract(_n);
                try {
                    this.unbind(n);
                }
                catch (org.omg.CosNaming.NamingContextPackage.NotFound e0) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.NotFoundHelper.insert(_except, e0);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.CannotProceed e1) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.CannotProceedHelper.insert(_except, e1);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.InvalidName e2) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.InvalidNameHelper.insert(_except, e2);
                    r.except(_except);
                    return;
                }
                org.omg.CORBA.Any __return = _orb().create_any();
                __return.type(_orb().get_primitive_tc(org.omg.CORBA.TCKind.tk_void));
                r.result(__return);
            }
            break;
        case 6: // org.omg.CosNaming.NamingContext.list
            {
                org.omg.CORBA.NVList _list = _orb().create_list(0);
                org.omg.CORBA.Any _how_many = _orb().create_any();
                _how_many.type(org.omg.CORBA.ORB.init().get_primitive_tc(org.omg.CORBA.TCKind.tk_ulong));
                _list.add_value("how_many", _how_many, org.omg.CORBA.ARG_IN.value);
                org.omg.CORBA.Any _bl = _orb().create_any();
                _bl.type(org.omg.CosNaming.BindingListHelper.type());
                _list.add_value("bl", _bl, org.omg.CORBA.ARG_OUT.value);
                org.omg.CORBA.Any _bi = _orb().create_any();
                _bi.type(org.omg.CosNaming.BindingIteratorHelper.type());
                _list.add_value("bi", _bi, org.omg.CORBA.ARG_OUT.value);
                r.params(_list);
                int how_many;
                how_many = _how_many.extract_ulong();
                org.omg.CosNaming.BindingListHolder bl;
                bl = new org.omg.CosNaming.BindingListHolder();
                org.omg.CosNaming.BindingIteratorHolder bi;
                bi = new org.omg.CosNaming.BindingIteratorHolder();
                this.list(how_many, bl, bi);
                org.omg.CosNaming.BindingListHelper.insert(_bl, bl.value);
                org.omg.CosNaming.BindingIteratorHelper.insert(_bi, bi.value);
                org.omg.CORBA.Any __return = _orb().create_any();
                __return.type(_orb().get_primitive_tc(org.omg.CORBA.TCKind.tk_void));
                r.result(__return);
            }
            break;
        case 7: // org.omg.CosNaming.NamingContext.new_context
            {
                org.omg.CORBA.NVList _list = _orb().create_list(0);
                r.params(_list);
                org.omg.CosNaming.NamingContext ___result;
                ___result = this.new_context();
                org.omg.CORBA.Any __result = _orb().create_any();
                org.omg.CosNaming.NamingContextHelper.insert(__result, ___result);
                r.result(__result);
            }
            break;
        case 8: // org.omg.CosNaming.NamingContext.bind_new_context
            {
                org.omg.CORBA.NVList _list = _orb().create_list(0);
                org.omg.CORBA.Any _n = _orb().create_any();
                _n.type(org.omg.CosNaming.NameHelper.type());
                _list.add_value("n", _n, org.omg.CORBA.ARG_IN.value);
                r.params(_list);
                org.omg.CosNaming.NameComponent[] n;
                n = org.omg.CosNaming.NameHelper.extract(_n);
                org.omg.CosNaming.NamingContext ___result;
                try {
                    ___result = this.bind_new_context(n);
                }
                catch (org.omg.CosNaming.NamingContextPackage.NotFound e0) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.NotFoundHelper.insert(_except, e0);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.AlreadyBound e1) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.AlreadyBoundHelper.insert(_except, e1);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.CannotProceed e2) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.CannotProceedHelper.insert(_except, e2);
                    r.except(_except);
                    return;
                }
                catch (org.omg.CosNaming.NamingContextPackage.InvalidName e3) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.InvalidNameHelper.insert(_except, e3);
                    r.except(_except);
                    return;
                }
                org.omg.CORBA.Any __result = _orb().create_any();
                org.omg.CosNaming.NamingContextHelper.insert(__result, ___result);
                r.result(__result);
            }
            break;
        case 9: // org.omg.CosNaming.NamingContext.destroy
            {
                org.omg.CORBA.NVList _list = _orb().create_list(0);
                r.params(_list);
                try {
                    this.destroy();
                }
                catch (org.omg.CosNaming.NamingContextPackage.NotEmpty e0) {
                    org.omg.CORBA.Any _except = _orb().create_any();
                    org.omg.CosNaming.NamingContextPackage.NotEmptyHelper.insert(_except, e0);
                    r.except(_except);
                    return;
                }
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
