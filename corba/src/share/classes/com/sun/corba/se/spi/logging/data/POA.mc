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
("com.sun.corba.se.impl.logging" "POASystemException" POA
    (
	(BAD_INV_ORDER 
	    (SERVANT_MANAGER_ALREADY_SET 1 WARNING "Servant Manager already set")
	    (DESTROY_DEADLOCK 2 WARNING "Request to wait for POA destruction while servicing request would deadlock"))
	(BAD_OPERATION
	    (SERVANT_ORB  1 WARNING "Bad operation on servant ORB???")
	    (BAD_SERVANT  2 WARNING "Bad Servant???")
	    (ILLEGAL_FORWARD_REQUEST  3 WARNING "Illegal Forward Request???"))
	(BAD_PARAM
	    (BAD_TRANSACTION_CONTEXT  1 WARNING "Bad transaction context")
	    (BAD_REPOSITORY_ID  2 WARNING "Bad repository id"))
	(INTERNAL
	    (INVOKESETUP  1 WARNING "invoke setup???")
	    (BAD_LOCALREPLYSTATUS  2 WARNING "bad local reply status???")
	    (PERSISTENT_SERVERPORT_ERROR  3 WARNING "persistent serverport error???")
	    (SERVANT_DISPATCH  4 WARNING "servant dispatch???")
	    (WRONG_CLIENTSC  5 WARNING "wrong client request dispatcher???")
	    (CANT_CLONE_TEMPLATE  6 WARNING "can't clone template???")
	    (POACURRENT_UNBALANCED_STACK 7 WARNING "POACurrent stack is unbalanced")
	    (POACURRENT_NULL_FIELD  8 WARNING "Null field in POACurrent")
	    (POA_INTERNAL_GET_SERVANT_ERROR 9 WARNING "POA internalGetServant error")
	    (MAKE_FACTORY_NOT_POA  10  WARNING "First Object Adapter name is {0}, should be RootPOA")
	    (DUPLICATE_ORB_VERSION_SC  11  WARNING "Duplicate ORB version service context")
	    (PREINVOKE_CLONE_ERROR  12  WARNING "preinvoke clone error")
	    (PREINVOKE_POA_DESTROYED  13  WARNING "preinvoke POA destroyed")
	    (PMF_CREATE_RETAIN  14  WARNING "Bad dispatch policy for RETAIN policy in POAPolicyMediatorFactory")
	    (PMF_CREATE_NON_RETAIN  15  WARNING "Bad dispatch policy for NON_RETAIN policy in POAPolicyMediatorFactory")
	    (POLICY_MEDIATOR_BAD_POLICY_IN_FACTORY  16  WARNING "Inconsistent policy in PolicyMediator")
	    (SERVANT_TO_ID_OAA  17  WARNING "ObjectAlreadyActive in servantToId")
	    (SERVANT_TO_ID_SAA  18  WARNING "ServantAlreadyActive in servantToId")
	    (SERVANT_TO_ID_WP  19  WARNING "WrongPolicy in servantToId")
	    (CANT_RESOLVE_ROOT_POA  20  WARNING "Can't resolve root POA")
	    (SERVANT_MUST_BE_LOCAL 21 WARNING "Call made to local client request dispatcher with non-local servant")
	    (NO_PROFILES_IN_IOR 22 WARNING "IOR does not have any profiles")
	    (AOM_ENTRY_DEC_ZERO 23 WARNING "Tried to decrement AOMEntry counter that is already 0")
	    (ADD_POA_INACTIVE 24 WARNING "Tried to add a POA to an inactive POAManager")
	    (ILLEGAL_POA_STATE_TRANS 25 WARNING "POA tried to make an illegal state transition")
	    (UNEXPECTED_EXCEPTION 26 WARNING "Unexpected exception in POA {0}"))
	(NO_IMPLEMENT 
	    (SINGLE_THREAD_NOT_SUPPORTED  1  WARNING "Single thread policy is not supported")
	    (METHOD_NOT_IMPLEMENTED 2 WARNING "This method is not implemented"))
	(OBJ_ADAPTER 
	    (POA_LOOKUP_ERROR  1 WARNING "Error in find_POA")
	    (POA_INACTIVE  2 FINE "POA is inactive")
	    (POA_NO_SERVANT_MANAGER  3 WARNING "POA has no servant manager")
	    (POA_NO_DEFAULT_SERVANT  4 WARNING "POA has no default servant")
	    (POA_SERVANT_NOT_UNIQUE  5 WARNING "POA servant is not unique")
	    (POA_WRONG_POLICY  6 WARNING "Bad policy in POA")
	    (FINDPOA_ERROR  7 WARNING "Another error in find_POA")
	    (POA_SERVANT_ACTIVATOR_LOOKUP_FAILED  9 WARNING "POA ServantActivator lookup failed")
	    (POA_BAD_SERVANT_MANAGER  10 WARNING "POA has bad servant manager")
	    (POA_SERVANT_LOCATOR_LOOKUP_FAILED  11 WARNING "POA ServantLocator lookup failed")
	    (POA_UNKNOWN_POLICY  12 WARNING "Unknown policy passed to POA")
	    (POA_NOT_FOUND  13 WARNING "POA not found")
	    (SERVANT_LOOKUP  14 WARNING "Error in servant lookup")
	    (LOCAL_SERVANT_LOOKUP  15 WARNING "Error in local servant lookup")
	    (SERVANT_MANAGER_BAD_TYPE  16 WARNING "Bad type for servant manager")
	    (DEFAULT_POA_NOT_POAIMPL 17 WARNING "Servant's _default_POA must be an instance of POAImpl")
	    (WRONG_POLICIES_FOR_THIS_OBJECT 18 WARNING "Wrong POA policies for _this_object called outside of an invocation context")
	    (THIS_OBJECT_SERVANT_NOT_ACTIVE 19 WARNING "ServantNotActive exception in _this_object")
	    (THIS_OBJECT_WRONG_POLICY 20 WARNING "WrongPolicy exception in _this_object")
	    (NO_CONTEXT 21 FINE "Operation called outside of invocation context")
	    (INCARNATE_RETURNED_NULL 22 WARNING "ServantActivator.incarnate() returned a null Servant"))
	(INITIALIZE 
	    (JTS_INIT_ERROR  1 WARNING "JTS initialization error")
	    (PERSISTENT_SERVERID_NOT_SET  2 WARNING "Persistent server ID is not set")
	    (PERSISTENT_SERVERPORT_NOT_SET  3 WARNING "Persistent server port is not set")
	    (ORBD_ERROR  4 WARNING "Error in ORBD")
	    (BOOTSTRAP_ERROR  5 WARNING "Error in bootstrap"))
	(TRANSIENT 
	    (POA_DISCARDING  1 FINE "POA is in discarding state"))
	(UNKNOWN 
	    (OTSHOOKEXCEPTION  1 WARNING "Error in OTS hook")
	    (UNKNOWN_SERVER_EXCEPTION  2 WARNING "Unknown server exception")
	    (UNKNOWN_SERVERAPP_EXCEPTION  3 WARNING "Unknown server application exception")
	    (UNKNOWN_LOCALINVOCATION_ERROR  4 WARNING "Unknon local invocation error"))
	(OBJECT_NOT_EXIST 
	    (ADAPTER_ACTIVATOR_NONEXISTENT  1 WARNING "AdapterActivator does not exist")
	    (ADAPTER_ACTIVATOR_FAILED  2 WARNING "AdapterActivator failed")
	    (BAD_SKELETON  3 WARNING "Bad skeleton")
	    (NULL_SERVANT  4 FINE "Null servant")
	    (ADAPTER_DESTROYED  5 WARNING "POA has been destroyed"))))
