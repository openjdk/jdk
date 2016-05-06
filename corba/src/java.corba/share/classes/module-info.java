/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

module java.corba {
    requires public java.desktop;
    requires public java.rmi;
    requires java.logging;
    requires java.naming;
    requires java.transaction;

    exports javax.activity;
    exports javax.rmi;
    exports javax.rmi.CORBA;
    exports org.omg.CORBA;
    exports org.omg.CORBA.DynAnyPackage;
    exports org.omg.CORBA.ORBPackage;
    exports org.omg.CORBA.TypeCodePackage;
    exports org.omg.CORBA.portable;
    exports org.omg.CORBA_2_3;
    exports org.omg.CORBA_2_3.portable;
    exports org.omg.CosNaming;
    exports org.omg.CosNaming.NamingContextExtPackage;
    exports org.omg.CosNaming.NamingContextPackage;
    exports org.omg.Dynamic;
    exports org.omg.DynamicAny;
    exports org.omg.DynamicAny.DynAnyFactoryPackage;
    exports org.omg.DynamicAny.DynAnyPackage;
    exports org.omg.IOP;
    exports org.omg.IOP.CodecFactoryPackage;
    exports org.omg.IOP.CodecPackage;
    exports org.omg.Messaging;
    exports org.omg.PortableInterceptor;
    exports org.omg.PortableInterceptor.ORBInitInfoPackage;
    exports org.omg.PortableServer;
    exports org.omg.PortableServer.CurrentPackage;
    exports org.omg.PortableServer.POAManagerPackage;
    exports org.omg.PortableServer.POAPackage;
    exports org.omg.PortableServer.ServantLocatorPackage;
    exports org.omg.PortableServer.portable;
    exports org.omg.SendingContext;
    exports org.omg.stub.java.rmi;
    exports com.sun.corba.se.impl.util to
        jdk.rmic;
    exports com.sun.jndi.cosnaming to
        java.naming;
    exports com.sun.jndi.url.corbaname to
        java.naming;
    exports com.sun.jndi.url.iiop to
        java.naming;
    exports com.sun.jndi.url.iiopname to
        java.naming;

    provides javax.naming.spi.InitialContextFactory
        with com.sun.jndi.cosnaming.CNCtxFactory;
}
