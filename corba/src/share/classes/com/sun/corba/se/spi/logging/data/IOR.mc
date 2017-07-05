;
; Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
; DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
;
; This code is free software; you can redistribute it and/or modify it
; under the terms of the GNU General Public License version 2 only, as
; published by the Free Software Foundation.  Oracle designates this
; particular file as subject to the "Classpath" exception as provided
; by Oracle in the LICENSE file that accompanied this code.
;
; This code is distributed in the hope that it will be useful, but WITHOUT
; ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
; FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
; version 2 for more details (a copy is included in the LICENSE file that
; accompanied this code).
;
; You should have received a copy of the GNU General Public License version
; 2 along with this work; if not, write to the Free Software Foundation,
; Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
;
; Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
; or visit www.oracle.com if you need additional information or have any
; questions.
;
("com.sun.corba.se.impl.logging" "IORSystemException" IOR
    (
	(INTERNAL 
	    (ORT_NOT_INITIALIZED 1 WARNING "ObjectReferenceTemplate is not initialized")
	    (NULL_POA 2 WARNING "Null POA")
	    (BAD_MAGIC 3 WARNING "Bad magic number {0} in ObjectKeyTemplate")
	    (STRINGIFY_WRITE_ERROR 4  WARNING "Error while stringifying an object reference")
	    (TAGGED_PROFILE_TEMPLATE_FACTORY_NOT_FOUND 5 WARNING "Could not find a TaggedProfileTemplateFactory for id {0}")
	    (INVALID_JDK1_3_1_PATCH_LEVEL 6 WARNING "Found a JDK 1.3.1 patch level indicator with value {0} less than JDK 1.3.1_01 value of 1")
	    (GET_LOCAL_SERVANT_FAILURE 7 FINE "Exception occurred while looking for ObjectAdapter {0} in IIOPProfileImpl.getServant"))
	(BAD_OPERATION
	    (ADAPTER_ID_NOT_AVAILABLE 1 WARNING "Adapter ID not available")
	    (SERVER_ID_NOT_AVAILABLE 2 WARNING "Server ID not available")
	    (ORB_ID_NOT_AVAILABLE 3 WARNING "ORB ID not available")
	    (OBJECT_ADAPTER_ID_NOT_AVAILABLE 4 WARNING "Object adapter ID not available"))
	(BAD_PARAM 
	    (BAD_OID_IN_IOR_TEMPLATE_LIST 1 WARNING "Profiles in IOR do not all have the same Object ID, so conversion to IORTemplateList is impossible")
	    (INVALID_TAGGED_PROFILE 2 WARNING "Error in reading IIOP TaggedProfile")
	    (BAD_IIOP_ADDRESS_PORT 3 WARNING "Attempt to create IIOPAdiress with port {0}, which is out of range"))
	(INV_OBJREF
	    (IOR_MUST_HAVE_IIOP_PROFILE 1 WARNING "IOR must have at least one IIOP profile"))))
