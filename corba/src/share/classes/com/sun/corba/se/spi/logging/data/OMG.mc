;
; Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
("com.sun.corba.se.impl.logging" "OMGSystemException" OMG
    (
	(BAD_CONTEXT
	    (IDL_CONTEXT_NOT_FOUND	    1  WARNING "IDL context not found")
	    (NO_MATCHING_IDL_CONTEXT	    2  WARNING "No matching IDL context property"))
	(BAD_INV_ORDER
	    (DEP_PREVENT_DESTRUCTION	    1  WARNING "Dependency exists in IFR preventing destruction of this object")
	    (DESTROY_INDESTRUCTIBLE	    2  WARNING "Attempt to destroy indestructible objects in IFR")
	    (SHUTDOWN_WAIT_FOR_COMPLETION_DEADLOCK    3  WARNING "Operation would deadlock")
	    (BAD_OPERATION_AFTER_SHUTDOWN   4  WARNING "ORB has shutdown")
	    (BAD_INVOKE			    5  WARNING "Attempt to invoke send or invoke operation of the same Request object more than once ")
	    (BAD_SET_SERVANT_MANAGER	    6  WARNING "Attempt to set a servent manager after one has already been set")
	    (BAD_ARGUMENTS_CALL		    7  WARNING "ServerRequest::arguments called more than once or after a call to ServerRequest::set_exception")
	    (BAD_CTX_CALL		    8  WARNING "ServerRequest::ctx called more than once or before ServerRequest::arguments or after ServerRequest::ctx, ServerRequest::set_result or ServerRequest::set_exception")
	    (BAD_RESULT_CALL 		    9  WARNING "ServerRequest::set_result called more than once or before ServerRequest::arguments or after ServerRequest::set_result or ServerRequest::set_exception")
	    (BAD_SEND			    10 WARNING "Attempt to send a DII request after it was sent previously")
	    (BAD_POLL_BEFORE		    11 WARNING "Attempt to poll a DII request or to retrieve its result before the request was sent")
	    (BAD_POLL_AFTER		    12 WARNING "Attempt to poll a DII request or to retrieve its result after the result was retrieved previously")
	    (BAD_POLL_SYNC		    13 WARNING "Attempt to poll a synchronous DII request or to retrieve results from a synchronous DII request")
	    (INVALID_PI_CALL1	            14 FINE "Invalid call to forward_reference() when reply status is not LOCATION_FORWARD")
	    (INVALID_PI_CALL2	            14 FINE "Cannot access this attribute or method at this point")
	    (INVALID_PI_CALL3	            14 FINE "Cannot call set_slot from within an ORBInitializer")
	    (INVALID_PI_CALL4	            14 FINE "Cannot call get_slot from within an ORBInitializer")
	    (SERVICE_CONTEXT_ADD_FAILED	    15 FINE "Service context add failed in portable interceptor because a service context with id {0} already exists")
	    (POLICY_FACTORY_REG_FAILED	    16 WARNING "Registration of PolicyFactory failed because a factory already exists for the given PolicyType {0}")
	    (CREATE_POA_DESTROY		    17 WARNING "POA cannot create POAs while undergoing destruction")
	    (PRIORITY_REASSIGN		    18 WARNING "Attempt to reassign priority")
	    (XA_START_OUTSIZE		    19 WARNING "An OTS/XA integration xa_start() call returned XAER_OUTSIDE")
	    (XA_START_PROTO		    20 WARNING "An OTS/XA integration xa_ call returned XAER_PROTO"))
	(BAD_OPERATION
	    (BAD_SERVANT_MANAGER_TYPE	    1  WARNING "ServantManager returned wrong servant type")
	    (OPERATION_UNKNOWN_TO_TARGET    2  WARNING "Operation or attribute not known to target object "))
	(BAD_PARAM
	    (UNABLE_REGISTER_VALUE_FACTORY  1  WARNING "Failure to register, unregister or lookup value factory")
	    (RID_ALREADY_DEFINED	    2  WARNING "RID already defined in IFR")
	    (NAME_USED_IFR		    3  WARNING "Name already used in the context in IFR ")
	    (TARGET_NOT_CONTAINER	    4  WARNING "Target is not a valid container")
	    (NAME_CLASH			    5  WARNING "Name clash in inherited context")
	    (NOT_SERIALIZABLE		    6  WARNING "Class {0} is not Serializable")
	    (SO_BAD_SCHEME_NAME		    7  WARNING "string_to_object conversion failed due to bad scheme name")
	    (SO_BAD_ADDRESS		    8  WARNING "string_to_object conversion failed due to bad address")
	    (SO_BAD_SCHEMA_SPECIFIC	    9  WARNING "string_to_object conversion failed due to bad bad schema specific part")
	    (SO_NON_SPECIFIC		    10 WARNING "string_to_object conversion failed due to non specific reason")
	    (IR_DERIVE_ABS_INT_BASE	    11 WARNING "Attempt to derive abstract interface from non-abstract base interface in the Interface Repository")
	    (IR_VALUE_SUPPORT		    12 WARNING "Attempt to let a ValueDef support more than one non-abstract interface in the Interface Repository")
	    (INCOMPLETE_TYPECODE	    13 WARNING "Attempt to use an incomplete TypeCode as a parameter")
	    (INVALID_OBJECT_ID		    14 WARNING "Invalid object id passed to POA::create_reference_by_id ")
	    (TYPECODE_BAD_NAME		    15 WARNING "Bad name argument in TypeCode operation")
	    (TYPECODE_BAD_REPID		    16 WARNING "Bad RepositoryId argument in TypeCode operation")
	    (TYPECODE_INV_MEMBER	    17 WARNING "Invalid member name in TypeCode operation ")
	    (TC_UNION_DUP_LABEL		    18 WARNING "Duplicate label value in create_union_tc ")
	    (TC_UNION_INCOMPATIBLE	    19 WARNING "Incompatible TypeCode of label and discriminator in create_union_tc ")
	    (TC_UNION_BAD_DISC		    20 WARNING "Supplied discriminator type illegitimate in create_union_tc ")
	    (SET_EXCEPTION_BAD_ANY	    21 WARNING "Any passed to ServerRequest::set_exception does not contain an exception ")
	    (SET_EXCEPTION_UNLISTED	    22 WARNING "Unlisted user exception passed to ServerRequest::set_exception ")
	    (NO_CLIENT_WCHAR_CODESET_CTX    23 WARNING "wchar transmission code set not in service context")
	    (ILLEGAL_SERVICE_CONTEXT	    24 WARNING "Service context is not in OMG-defined range")
	    (ENUM_OUT_OF_RANGE		    25 WARNING "Enum value out of range")
	    (INVALID_SERVICE_CONTEXT_ID	    26 FINE "Invalid service context Id in portable interceptor")
	    (RIR_WITH_NULL_OBJECT	    27 WARNING "Attempt to call register_initial_reference with a null Object")
	    (INVALID_COMPONENT_ID	    28 FINE "Invalid component Id {0} in portable interceptor")
	    (INVALID_PROFILE_ID		    29 WARNING "Profile ID does not define a known profile or it is impossible to add components to that profile")
	    (POLICY_TYPE_DUPLICATE	    30 WARNING "Two or more Policy objects with the same PolicyType value supplied to Object::set_policy_overrides or PolicyManager::set_policy_overrides")
	    (BAD_ONEWAY_DEFINITION	    31 WARNING "Attempt to define a oneway operation with non-void result, out or inout parameters or user exceptions")
	    (DII_FOR_IMPLICIT_OPERATION	    32 WARNING "DII asked to create request for an implicit operation")
	    (XA_CALL_INVAL		    33 WARNING "An OTS/XA integration xa_ call returned XAER_INVAL")
	    (UNION_BAD_DISCRIMINATOR	    34 WARNING "Union branch modifier method called with bad case label discriminator")
	    (CTX_ILLEGAL_PROPERTY_NAME	    35 WARNING "Illegal IDL context property name")
	    (CTX_ILLEGAL_SEARCH_STRING	    36 WARNING "Illegal IDL property search string")
	    (CTX_ILLEGAL_NAME		    37 WARNING "Illegal IDL context name")
	    (CTX_NON_EMPTY		    38 WARNING "Non-empty IDL context")
	    (INVALID_STREAM_FORMAT_VERSION  39 WARNING "Unsupported RMI/IDL custom value type stream format {0}")
	    (NOT_A_VALUEOUTPUTSTREAM	    40 WARNING "ORB output stream does not support ValueOutputStream interface")
	    (NOT_A_VALUEINPUTSTREAM	    41 WARNING "ORB input stream does not support ValueInputStream interface"))
	(BAD_TYPECODE
	    (MARSHALL_INCOMPLETE_TYPECODE   1  WARNING "Attempt to marshal incomplete TypeCode")
	    (BAD_MEMBER_TYPECODE	    2  WARNING "Member type code illegitimate in TypeCode operation")
	    (ILLEGAL_PARAMETER		    3  WARNING "Illegal parameter type"))
	(DATA_CONVERSION
	    (CHAR_NOT_IN_CODESET	    1  WARNING "Character does not map to negotiated transmission code set")
	    (PRIORITY_MAP_FAILRE	    2  WARNING "Failure of PriorityMapping object"))
	(IMP_LIMIT
	    (NO_USABLE_PROFILE		    1  WARNING "Unable to use any profile in IOR"))
	(INITIALIZE
	    (PRIORITY_RANGE_RESTRICT	    1  WARNING "Priority range too restricted for ORB"))
	(INV_OBJREF
	    (NO_SERVER_WCHAR_CODESET_CMP    1  WARNING "wchar Code Set support not specified")
	    (CODESET_COMPONENT_REQUIRED	    2  WARNING "Codeset component required for type using wchar or wstring data"))
	(INV_POLICY
	    (IOR_POLICY_RECONCILE_ERROR	    1 WARNING "Unable to reconcile IOR specified policy with effective policy override")
	    (POLICY_UNKNOWN		    2 WARNING "Invalid PolicyType")
	    (NO_POLICY_FACTORY		    3 WARNING "No PolicyFactory has been registered for the given PolicyType"))
	(INTERNAL
	    (XA_RMERR			    1  WARNING "An OTS/XA integration xa_ call returned XAER_RMERR")
	    (XA_RMFAIL			    2  WARNING "An OTS/XA integration xa_ call returned XAER_RMFAIL"))
	(INTF_REPOS
	    (NO_IR			    1  WARNING "Interface Repository not available")
	    (NO_INTERFACE_IN_IR		    2  WARNING "No entry for requested interface in Interface Repository"))
	(MARSHAL
	    (UNABLE_LOCATE_VALUE_FACTORY    1  FINE "Unable to locate value factory")
	    (SET_RESULT_BEFORE_CTX	    2  WARNING "ServerRequest::set_result called before ServerRequest::ctx when the operation IDL contains a context clause ")
	    (BAD_NVLIST			    3  WARNING "NVList passed to ServerRequest::arguments does not describe all parameters passed by client")
	    (NOT_AN_OBJECT_IMPL		    4  WARNING "Attempt to marshal Local object")
	    (WCHAR_BAD_GIOP_VERSION_SENT    5  WARNING "wchar or wstring data erroneosly sent by client over GIOP 1.0 connection ")
	    (WCHAR_BAD_GIOP_VERSION_RETURNED 6 WARNING "wchar or wstring data erroneously returned by server over GIOP 1.0 connection ")
	    (UNSUPPORTED_FORMAT_VERSION	    7  WARNING "Unsupported RMI/IDL custom value type stream format")
	    (RMIIIOP_OPTIONAL_DATA_INCOMPATIBLE1 8 WARNING "No optional data available")
	    (RMIIIOP_OPTIONAL_DATA_INCOMPATIBLE2 8 WARNING "Not enough space left in current chunk")
	    (RMIIIOP_OPTIONAL_DATA_INCOMPATIBLE3 8 FINE "Not enough optional data available"))
	(NO_IMPLEMENT
	    (MISSING_LOCAL_VALUE_IMPL	    1  WARNING "Missing local value implementation")
	    (INCOMPATIBLE_VALUE_IMPL	    2  WARNING "Incompatible value implementation version")
	    (NO_USABLE_PROFILE_2	    3  WARNING "Unable to use any profile in IOR")
	    (DII_LOCAL_OBJECT		    4  WARNING "Attempt to use DII on Local object")
	    (BIO_RESET			    5  WARNING "Biomolecular Sequence Analysis iterator cannot be reset")
	    (BIO_META_NOT_AVAILABLE		    6  WARNING "Biomolecular Sequence Analysis metadata is not available as XML")
	    (BIO_GENOMIC_NO_ITERATOR	    7  WARNING "Genomic Maps iterator cannot be reset"))
	(NO_RESOURCES
	    (PI_OPERATION_NOT_SUPPORTED1    1  FINE "The portable Java bindings do not support arguments()")
	    (PI_OPERATION_NOT_SUPPORTED2    1  FINE "The portable Java bindings do not support exceptions()")
	    (PI_OPERATION_NOT_SUPPORTED3    1  FINE "The portable Java bindings do not support contexts()")
	    (PI_OPERATION_NOT_SUPPORTED4    1  FINE "The portable Java bindings do not support operation_context()")
	    (PI_OPERATION_NOT_SUPPORTED5    1  FINE "The portable Java bindings do not support result()")
	    (PI_OPERATION_NOT_SUPPORTED6    1  FINE "The object ID was never set")
	    (PI_OPERATION_NOT_SUPPORTED7    1  FINE "The ObjectKeyTemplate was never set")
	    (PI_OPERATION_NOT_SUPPORTED8    1  FINE "ServerRequest::arguments() was never called")
	    (NO_CONNECTION_PRIORITY	    2  WARNING "No connection for request's priority"))
	(TRANSACTION_ROLLEDBACK
	    (XA_RB			    1  WARNING "An OTS/XA integration xa_ call returned XAER_RB")
	    (XA_NOTA			    2  WARNING "An OTS/XA integration xa_ call returned XAER_NOTA")
	    (XA_END_TRUE_ROLLBACK_DEFERRED  3  WARNING "OTS/XA integration end() was called with success set to TRUE while transaction rollback was deferred"))
	(TRANSIENT
	    (POA_REQUEST_DISCARD	    1  WARNING "Request discarded because of resource exhaustion in POA or because POA is in DISCARDING state")
	    (NO_USABLE_PROFILE_3	    2  WARNING "No usable profile in IOR")
	    (REQUEST_CANCELLED		    3  WARNING "Request cancelled")
	    (POA_DESTROYED		    4  WARNING "POA destroyed"))
	(OBJECT_NOT_EXIST
	    (UNREGISTERED_VALUE_AS_OBJREF   1   WARNING "Attempt to pass an unactivated (unregistered) value as an object reference")
	    (NO_OBJECT_ADAPTOR		    2  FINE "Failed to create or locate Object Adaptor")
	    (BIO_NOT_AVAILABLE		    3  WARNING "Biomolecular Sequence Analysis Service is no longer available")
	    (OBJECT_ADAPTER_INACTIVE	    4  WARNING "Object Adapter Inactive"))
	(OBJ_ADAPTER
	    (ADAPTER_ACTIVATOR_EXCEPTION    1  WARNING "System exception in POA::unknown_adapter for POA {0} with parent POA {1}")
	    (BAD_SERVANT_TYPE		    2  WARNING "Incorrect servant type returned by servant manager ")
	    (NO_DEFAULT_SERVANT		    3  WARNING "No default servant available [POA policy]")
	    (NO_SERVANT_MANAGER		    4  WARNING "No servant manager available [POA Policy]")
	    (BAD_POLICY_INCARNATE	    5  WARNING "Violation of POA policy by ServantActivator::incarnate")
	    (PI_EXC_COMP_ESTABLISHED	    6  WARNING "Exception in PortableInterceptor::IORInterceptor.components_established")
	    (NULL_SERVANT_RETURNED	    7  FINE "Null servant returned by servant manager"))
	(UNKNOWN
	    (UNKNOWN_USER_EXCEPTION         1  FINE "Unlisted user exception received by client ")
	    (UNSUPPORTED_SYSTEM_EXCEPTION   2  WARNING "Non-standard System Exception not supported")
	    (PI_UNKNOWN_USER_EXCEPTION	    3  WARNING "An unknown user exception received by a portable interceptor"))))
