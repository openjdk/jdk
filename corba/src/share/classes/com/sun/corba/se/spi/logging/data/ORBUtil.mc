;

; Copyright 2003-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
("com.sun.corba.se.impl.logging" "ORBUtilSystemException" ORBUTIL
    (
	(BAD_OPERATION 
	    (ADAPTER_ID_NOT_AVAILABLE 1 WARNING "Adapter ID not available")
	    (SERVER_ID_NOT_AVAILABLE 2 WARNING "Server ID not available")
	    (ORB_ID_NOT_AVAILABLE 3 WARNING "ORB ID not available")
	    (OBJECT_ADAPTER_ID_NOT_AVAILABLE 4 WARNING "Object adapter ID not available")
	    (CONNECTING_SERVANT 5 WARNING "Error connecting servant")
	    (EXTRACT_WRONG_TYPE 6 FINE "Expected typecode kind {0} but got typecode kind {1}")
	    (EXTRACT_WRONG_TYPE_LIST 7 WARNING "Expected typecode kind to be one of {0} but got typecode kind {1}")
	    (BAD_STRING_BOUNDS 8 WARNING "String length of {0} exceeds bounded string length of {1}")
	    (INSERT_OBJECT_INCOMPATIBLE 10 WARNING "Tried to insert an object of an incompatible type into an Any for an object reference")
	    (INSERT_OBJECT_FAILED 11 WARNING "insert_Object call failed on an Any")
	    (EXTRACT_OBJECT_INCOMPATIBLE 12 WARNING "extract_Object call failed on an Any")
	    (FIXED_NOT_MATCH 13 WARNING "Fixed type does not match typecode")
	    (FIXED_BAD_TYPECODE 14 WARNING "Tried to insert Fixed type for non-Fixed typecode")
	    (SET_EXCEPTION_CALLED_NULL_ARGS 23 WARNING "set_exception(Any) called with null args for DSI ServerRequest")
	    (SET_EXCEPTION_CALLED_BAD_TYPE 24 WARNING "set_exception(Any) called with a bad (non-exception) type")
	    (CONTEXT_CALLED_OUT_OF_ORDER 25 WARNING "ctx() called out of order for DSI ServerRequest")
	    (BAD_ORB_CONFIGURATOR 26 WARNING "ORB configurator class {0} could not be instantiated")
	    (ORB_CONFIGURATOR_ERROR 27 WARNING "Error in running ORB configurator")
	    (ORB_DESTROYED 28 WARNING "This ORB instance has been destroyed, so no operations can be performed on it")
	    (NEGATIVE_BOUNDS 29 WARNING "Negative bound for string TypeCode is illegal")
	    (EXTRACT_NOT_INITIALIZED 30 WARNING "Called typecode extract on an uninitialized typecode")
	    (EXTRACT_OBJECT_FAILED 31 WARNING "extract_Object failed on an uninitialized Any")
	    (METHOD_NOT_FOUND_IN_TIE 32 FINE "Could not find method named {0} in class {1} in reflective Tie")
	    (CLASS_NOT_FOUND1 33 FINE "ClassNotFoundException while attempting to load preferred stub named {0}")
	    (CLASS_NOT_FOUND2 34 FINE "ClassNotFoundException while attempting to load alternate stub named {0}")
	    (CLASS_NOT_FOUND3 35 FINE "ClassNotFoundException while attempting to load interface {0}")
	    (GET_DELEGATE_SERVANT_NOT_ACTIVE 36 WARNING "POA ServantNotActive exception while trying get an org.omg.CORBA.Portable.Delegate for an org.omg.PortableServer.Servant")
	    (GET_DELEGATE_WRONG_POLICY 37 WARNING "POA WrongPolicy exception while trying get an org.omg.CORBA.Portable.Delegate for an org.omg.PortableServer.Servant")
	    (SET_DELEGATE_REQUIRES_STUB 38 FINE "Call to StubAdapter.setDelegate did not pass a stub")
	    (GET_DELEGATE_REQUIRES_STUB 39 WARNING "Call to StubAdapter.getDelegate did not pass a stub")
	    (GET_TYPE_IDS_REQUIRES_STUB 40 WARNING "Call to StubAdapter.getTypeIds did not pass a stub")
	    (GET_ORB_REQUIRES_STUB 41 WARNING "Call to StubAdapter.getORB did not pass a stub")
	    (CONNECT_REQUIRES_STUB 42 WARNING "Call to StubAdapter.connect did not pass a stub")
	    (IS_LOCAL_REQUIRES_STUB 43 WARNING "Call to StubAdapter.isLocal did not pass a stub")
	    (REQUEST_REQUIRES_STUB 44 WARNING "Call to StubAdapter.request did not pass a stub")
	    (BAD_ACTIVATE_TIE_CALL 45 WARNING "Call to StubAdapter.activateTie did not pass a valid Tie")
	    )
	(BAD_PARAM
	    (NULL_PARAM 1 WARNING "Null parameter")
	    (UNABLE_FIND_VALUE_FACTORY 2 FINE "Unable to find value factory")
	    (ABSTRACT_FROM_NON_ABSTRACT 3 WARNING "Abstract interface derived from non-abstract interface")
	    (INVALID_TAGGED_PROFILE 4 WARNING "Error in reading IIOP TaggedProfile")
	    (OBJREF_FROM_FOREIGN_ORB 5 WARNING "Object reference came from foreign ORB")
	    (LOCAL_OBJECT_NOT_ALLOWED 6 FINE "Local object not allowed")
	    (NULL_OBJECT_REFERENCE 7 WARNING "null object reference")
	    (COULD_NOT_LOAD_CLASS 8 WARNING "Could not load class {0}")
	    (BAD_URL 9 WARNING "Malformed URL {0}")
	    (FIELD_NOT_FOUND 10 WARNING "Field {0} not found in parser data object")
	    (ERROR_SETTING_FIELD 11 WARNING "Error in setting field {0} to value {1} in parser data object")
	    (BOUNDS_ERROR_IN_DII_REQUEST 12 WARNING "Bounds error occurred in DII request")
	    (PERSISTENT_SERVER_INIT_ERROR 13 WARNING "Initialization error for persistent server")
	    (COULD_NOT_CREATE_ARRAY 14 WARNING "Could not create array for field {0} with component type {1} and size {2}")
	    (COULD_NOT_SET_ARRAY 15 WARNING "Could not set array for field {0} at index {1} with component type {2} and size {3} to value {4}")
	    (ILLEGAL_BOOTSTRAP_OPERATION 16 WARNING "Illegal bootstrap operation {0}")
	    (BOOTSTRAP_RUNTIME_EXCEPTION 17 WARNING "Runtime Exception during bootstrap operation")
	    (BOOTSTRAP_EXCEPTION 18 WARNING "Exception during bootstrap operation")
	    (STRING_EXPECTED 19 WARNING "Expected a string, but argument was not of String type")
	    (INVALID_TYPECODE_KIND 20 WARNING "{0} does not represent a valid kind of typecode")
	    (SOCKET_FACTORY_AND_CONTACT_INFO_LIST_AT_SAME_TIME
	     21 WARNING "cannot have a SocketFactory and a ContactInfoList at the same time")
	    (ACCEPTORS_AND_LEGACY_SOCKET_FACTORY_AT_SAME_TIME
	     22 WARNING "cannot have Acceptors and a legacy SocketFactory at the same time")
	    (BAD_ORB_FOR_SERVANT 
	     23 WARNING "Reflective POA Servant requires an instance of org.omg.CORBA_2_3.ORB")
	    (INVALID_REQUEST_PARTITIONING_POLICY_VALUE 24 WARNING 
	     "Request partitioning value specified, {0}, is outside supported range, {1} - {2}")
	    (INVALID_REQUEST_PARTITIONING_COMPONENT_VALUE 25 WARNING 
	     "Could not set request partitioning component value to {0}, valid values are {1} - {2}")
	    (INVALID_REQUEST_PARTITIONING_ID
	     26 WARNING "Invalid request partitioning id {0}, valid values are {1} - {2}")
	    (ERROR_IN_SETTING_DYNAMIC_STUB_FACTORY_FACTORY 
	     27 FINE "ORBDynamicStubFactoryFactoryClass property had value {0}, which could not be loaded by the ORB ClassLoader" )
	    )
	(BAD_INV_ORDER 
	    (DSIMETHOD_NOTCALLED 1 WARNING "DSI method not called")
	    (ARGUMENTS_CALLED_MULTIPLE 2 WARNING "arguments(NVList) called more than once for DSI ServerRequest")
	    (ARGUMENTS_CALLED_AFTER_EXCEPTION 3 WARNING "arguments(NVList) called after exceptions set for DSI ServerRequest")
	    (ARGUMENTS_CALLED_NULL_ARGS 4 WARNING "arguments(NVList) called with null args for DSI ServerRequest")
	    (ARGUMENTS_NOT_CALLED 5 FINE "arguments(NVList) not called for DSI ServerRequest")
	    (SET_RESULT_CALLED_MULTIPLE 6 WARNING "set_result(Any) called more than once for DSI ServerRequest")
	    (SET_RESULT_AFTER_EXCEPTION 7 FINE "set_result(Any) called exception was set for DSI ServerRequest")
	    (SET_RESULT_CALLED_NULL_ARGS 8 WARNING "set_result(Any) called with null args for DSI ServerRequest"))
	(BAD_TYPECODE
	    (BAD_REMOTE_TYPECODE 1 WARNING "Foreign to native typecode conversion constructor should not be called with native typecode")
	    (UNRESOLVED_RECURSIVE_TYPECODE 2 WARNING "Invoked operation on unresolved recursive TypeCode"))
	(COMM_FAILURE 
	    (CONNECT_FAILURE
	     1  WARNING "Connection failure: socketType: {0}; hostname: {1}; port: {2}")
	    (CONNECTION_CLOSE_REBIND
	     2  WARNING "Connection close: rebind")
	    (WRITE_ERROR_SEND
	     3  FINE "Write error sent")
	    (GET_PROPERTIES_ERROR
	     4  WARNING "Get properties error")
	    (BOOTSTRAP_SERVER_NOT_AVAIL
	     5  WARNING "Bootstrap server is not available")
	    (INVOKE_ERROR
	     6  WARNING "Invocation error")
	    (DEFAULT_CREATE_SERVER_SOCKET_GIVEN_NON_IIOP_CLEAR_TEXT
	     7 WARNING "DefaultSocketFactory.createServerSocket only handles IIOP_CLEAR_TEXT, given {0}")
	    (CONNECTION_ABORT
	     8 FINE "Connection abort")
	    (CONNECTION_REBIND
	     9 FINE "Connection rebind")
	    (RECV_MSG_ERROR
	     10 WARNING "Received a GIOP MessageError, indicating header corruption or version mismatch")
	    (IOEXCEPTION_WHEN_READING_CONNECTION
	     11 FINE "IOException when reading connection")
	    (SELECTION_KEY_INVALID
	     12 FINE "SelectionKey invalid on channel, {0}")
    	    (EXCEPTION_IN_ACCEPT
	     13 FINE "Unexpected {0} in accept")
   	    (SECURITY_EXCEPTION_IN_ACCEPT
	     14 FINE "Unexpected {0}, has permissions {1}")
	    (TRANSPORT_READ_TIMEOUT_EXCEEDED
	     15 WARNING "Read of full message failed : bytes requested = {0} bytes read = {1} max wait time = {2} total time spent waiting = {3}")
	    (CREATE_LISTENER_FAILED 
	     16 SEVERE "Unable to create listener thread on the specified port: {0}")
	    (BUFFER_READ_MANAGER_TIMEOUT
	     17 WARNING "Timeout while reading data in buffer manager")
	    )
	(DATA_CONVERSION
	    (BAD_STRINGIFIED_IOR_LEN 1  WARNING "A character did not map to the transmission code set")
	    (BAD_STRINGIFIED_IOR 2  WARNING "Bad stringified IOR")
	    (BAD_MODIFIER 3 WARNING "Unable to perform resolve_initial_references due to bad host or port configuration")
	    (CODESET_INCOMPATIBLE 4 WARNING "Codesets incompatible")
	    (BAD_HEX_DIGIT 5  WARNING "Illegal hexadecimal digit")
	    (BAD_UNICODE_PAIR 6 WARNING "Invalid unicode pair detected during code set conversion")
	    (BTC_RESULT_MORE_THAN_ONE_CHAR 7 WARNING "Tried to convert bytes to a single java char, but conversion yielded more than one Java char (Surrogate pair?)")
	    (BAD_CODESETS_FROM_CLIENT 8 WARNING "Client sent code set service context that we do not support")
	    (INVALID_SINGLE_CHAR_CTB 9 WARNING "Char to byte conversion for a CORBA char resulted in more than one byte")
	    (BAD_GIOP_1_1_CTB 10 WARNING "Character to byte conversion did not exactly double number of chars (GIOP 1.1 only)")
	    (BAD_SEQUENCE_BOUNDS 12 WARNING "Tried to insert a sequence of length {0} into a bounded sequence of maximum length {1} in an Any")
	    (ILLEGAL_SOCKET_FACTORY_TYPE 13 WARNING "Class {0} is not a subtype of ORBSocketFactory")
	    (BAD_CUSTOM_SOCKET_FACTORY 14 WARNING "{0} is not a valid custom socket factory")
	    (FRAGMENT_SIZE_MINIMUM 15 WARNING "Fragment size {0} is too small: it must be at least {1}")
	    (FRAGMENT_SIZE_DIV 16 WARNING "Illegal valiue for fragment size ({0}): must be divisible by {1}")
	    (ORB_INITIALIZER_FAILURE
	     17 WARNING "Could not instantiate ORBInitializer {0}")
	    (ORB_INITIALIZER_TYPE
	     18 WARNING "orb initializer class {0} is not a subtype of ORBInitializer")
	    (ORB_INITIALREFERENCE_SYNTAX
	     19 WARNING "Bad syntax for ORBInitialReference")
	    (ACCEPTOR_INSTANTIATION_FAILURE 
	     20 WARNING "Could not instantiate Acceptor {0}")
	    (ACCEPTOR_INSTANTIATION_TYPE_FAILURE 
	     21 WARNING "Acceptor class {0} is not a subtype of Acceptor")
	    (ILLEGAL_CONTACT_INFO_LIST_FACTORY_TYPE
	     22 WARNING "Class {0} is not a subtype of CorbaContactInfoListFactory")
	    (BAD_CONTACT_INFO_LIST_FACTORY
	     23 WARNING "{0} is not a valid CorbaContactInfoListFactory")
	    (ILLEGAL_IOR_TO_SOCKET_INFO_TYPE
	     24 WARNING "Class {0} is not a subtype of IORToSocketInfo")
	    (BAD_CUSTOM_IOR_TO_SOCKET_INFO
	     25 WARNING "{0} is not a valid custom IORToSocketInfo")
	    (ILLEGAL_IIOP_PRIMARY_TO_CONTACT_INFO_TYPE
	     26 WARNING "Class {0} is not a subtype of IIOPPrimaryToContactInfo")
	    (BAD_CUSTOM_IIOP_PRIMARY_TO_CONTACT_INFO
	     27 WARNING "{0} is not a valid custom IIOPPrimaryToContactInfo")

	    )
	(INV_OBJREF 
	    (BAD_CORBALOC_STRING 1  WARNING "Bad corbaloc: URL")
	    (NO_PROFILE_PRESENT 2  WARNING "No profile in IOR"))
	(INITIALIZE 
	    (CANNOT_CREATE_ORBID_DB  1 WARNING "Cannot create ORB ID datastore")
	    (CANNOT_READ_ORBID_DB    2 WARNING "Cannot read ORB ID datastore")
	    (CANNOT_WRITE_ORBID_DB   3 WARNING "Cannot write ORB ID datastore")
	    (GET_SERVER_PORT_CALLED_BEFORE_ENDPOINTS_INITIALIZED 4 WARNING "legacyGetServerPort called before endpoints initialized")
	    (PERSISTENT_SERVERPORT_NOT_SET  5 WARNING "Persistent server port is not set")
	    (PERSISTENT_SERVERID_NOT_SET  6 WARNING "Persistent server ID is not set"))
	(INTERNAL 
	    (NON_EXISTENT_ORBID 1 WARNING "Non-existent ORB ID")
	    (NO_SERVER_SUBCONTRACT 2  WARNING "No server request dispatcher")
	    (SERVER_SC_TEMP_SIZE 3  WARNING "server request dispatcher template size error")	
	    (NO_CLIENT_SC_CLASS 4  WARNING "No client request dispatcher class")	
	    (SERVER_SC_NO_IIOP_PROFILE 5  WARNING "No IIOP profile in server request dispatcher")	
	    (GET_SYSTEM_EX_RETURNED_NULL 6 WARNING "getSystemException returned null")
	    (PEEKSTRING_FAILED 7  WARNING "The repository ID of a user exception had a bad length")
	    (GET_LOCAL_HOST_FAILED 8  WARNING "Unable to determine local hostname from InetAddress.getLocalHost().getHostName()")
	    ;; 9 is not used at this time - it is available for reuse.
	    (BAD_LOCATE_REQUEST_STATUS 10  WARNING "Bad locate request status in IIOP locate reply")
	    (STRINGIFY_WRITE_ERROR 11  WARNING "Error while stringifying an object reference")
	    (BAD_GIOP_REQUEST_TYPE 12  WARNING "IIOP message with bad GIOP 1.0 message type")
	    (ERROR_UNMARSHALING_USEREXC 13  WARNING "Error in unmarshalling user exception")
	    (RequestDispatcherRegistry_ERROR 14  WARNING "Overflow in RequestDispatcherRegistry")
	    (LOCATIONFORWARD_ERROR 15  WARNING "Error in processing a LocationForward")
	    (WRONG_CLIENTSC 16  WARNING "Wrong client request dispatcher")
	    (BAD_SERVANT_READ_OBJECT 17  WARNING "Bad servant in read_Object")
	    (MULT_IIOP_PROF_NOT_SUPPORTED 18  WARNING "multiple IIOP profiles not supported")
	    (GIOP_MAGIC_ERROR 20 WARNING "Error in GIOP magic")
	    (GIOP_VERSION_ERROR 21 WARNING "Error in GIOP version")
	    (ILLEGAL_REPLY_STATUS 22 WARNING "Illegal reply status in GIOP reply message")
	    (ILLEGAL_GIOP_MSG_TYPE 23 WARNING "Illegal GIOP message type")
	    (FRAGMENTATION_DISALLOWED 24 WARNING "Fragmentation not allowed for this message type")
	    (BAD_REPLYSTATUS 25  WARNING "Bad status in the IIOP reply message")
	    (CTB_CONVERTER_FAILURE 26 WARNING "character to byte converter failure")
	    (BTC_CONVERTER_FAILURE 27 WARNING "byte to character converter failure")
	    (WCHAR_ARRAY_UNSUPPORTED_ENCODING 28 WARNING "Unsupported wchar encoding: ORB only supports fixed width UTF-16 encoding")
	    (ILLEGAL_TARGET_ADDRESS_DISPOSITION 29 WARNING "Illegal target address disposition value")    
	    (NULL_REPLY_IN_GET_ADDR_DISPOSITION 30 WARNING "No reply while attempting to get addressing disposition")
	    (ORB_TARGET_ADDR_PREFERENCE_IN_EXTRACT_OBJECTKEY_INVALID 31 WARNING "Invalid GIOP target addressing preference")
	    (INVALID_ISSTREAMED_TCKIND 32 WARNING "Invalid isStreamed TCKind {0}")
	    (INVALID_JDK1_3_1_PATCH_LEVEL 33 WARNING "Found a JDK 1.3.1 patch level indicator with value less than JDK 1.3.1_01 value of 1")
	    (SVCCTX_UNMARSHAL_ERROR 34 WARNING "Error unmarshalling service context data")
	    (NULL_IOR 35  WARNING "null IOR")
	    (UNSUPPORTED_GIOP_VERSION 36 WARNING "Unsupported GIOP version {0}")
	    (APPLICATION_EXCEPTION_IN_SPECIAL_METHOD 37 WARNING "Application exception in special method: should not happen")
	    (STATEMENT_NOT_REACHABLE1 38 WARNING "Assertion failed: statement not reachable (1)")
	    (STATEMENT_NOT_REACHABLE2 39 WARNING "Assertion failed: statement not reachable (2)")
	    (STATEMENT_NOT_REACHABLE3 40 WARNING "Assertion failed: statement not reachable (3)")
	    (STATEMENT_NOT_REACHABLE4 41 FINE "Assertion failed: statement not reachable (4)")
	    (STATEMENT_NOT_REACHABLE5 42 WARNING "Assertion failed: statement not reachable (5)")
	    (STATEMENT_NOT_REACHABLE6 43 WARNING "Assertion failed: statement not reachable (6)")
	    (UNEXPECTED_DII_EXCEPTION 44 WARNING "Unexpected exception while unmarshalling DII user exception")
	    (METHOD_SHOULD_NOT_BE_CALLED 45 WARNING "This method should never be called")
	    (CANCEL_NOT_SUPPORTED 46 WARNING "We do not support cancel request for GIOP 1.1")
	    (EMPTY_STACK_RUN_SERVANT_POST_INVOKE 47 WARNING "Empty stack exception while calling runServantPostInvoke")
	    (PROBLEM_WITH_EXCEPTION_TYPECODE 48 WARNING "Bad exception typecode")
	    (ILLEGAL_SUBCONTRACT_ID 49 WARNING "Illegal Subcontract id {0}")
	    (BAD_SYSTEM_EXCEPTION_IN_LOCATE_REPLY 50 WARNING "Bad system exception in locate reply")
	    (BAD_SYSTEM_EXCEPTION_IN_REPLY 51 WARNING "Bad system exception in reply")
	    (BAD_COMPLETION_STATUS_IN_LOCATE_REPLY 52 WARNING "Bad CompletionStatus {0} in locate reply")
	    (BAD_COMPLETION_STATUS_IN_REPLY 53 WARNING "Bad CompletionStatus {0} in reply")
	    (BADKIND_CANNOT_OCCUR 54 WARNING "The BadKind exception should never occur here")
	    (ERROR_RESOLVING_ALIAS 55 WARNING "Could not resolve alias typecode")
	    (TK_LONG_DOUBLE_NOT_SUPPORTED 56 WARNING "The long double type is not supported in Java")
	    (TYPECODE_NOT_SUPPORTED 57 WARNING "Illegal typecode kind")
	    (BOUNDS_CANNOT_OCCUR 59 WARNING "Bounds exception cannot occur in this context")
	    (NUM_INVOCATIONS_ALREADY_ZERO 61 WARNING "Number of invocations is already zero, but another invocation has completed")
	    (ERROR_INIT_BADSERVERIDHANDLER 62 WARNING "Error in constructing instance of bad server ID handler")
	    (NO_TOA 63 WARNING "No TOAFactory is availble")
	    (NO_POA 64 WARNING "No POAFactory is availble")
	    (INVOCATION_INFO_STACK_EMPTY 65 WARNING "Invocation info stack is unexpectedly empty")
	    (BAD_CODE_SET_STRING 66 WARNING "Empty or null code set string")
	    (UNKNOWN_NATIVE_CODESET 67 WARNING "Unknown native codeset: {0}")
	    (UNKNOWN_CONVERSION_CODE_SET 68 WARNING "Unknown conversion codset: {0}")
	    (INVALID_CODE_SET_NUMBER 69 WARNING "Invalid codeset number")
	    (INVALID_CODE_SET_STRING 70 WARNING "Invalid codeset string {0}")
	    (INVALID_CTB_CONVERTER_NAME 71 WARNING "Invalid CTB converter {0}")
	    (INVALID_BTC_CONVERTER_NAME 72 WARNING "Invalid BTC converter {0}")
	    (COULD_NOT_DUPLICATE_CDR_INPUT_STREAM 73 WARNING "Could not duplicate CDRInputStream")
	    (BOOTSTRAP_APPLICATION_EXCEPTION 74 WARNING "BootstrapResolver caught an unexpected ApplicationException")
	    (DUPLICATE_INDIRECTION_OFFSET 75 WARNING "Old entry in serialization indirection table has a different value than the value being added with the same key")
	    (BAD_MESSAGE_TYPE_FOR_CANCEL 76 WARNING "GIOP Cancel request contained a bad request ID: the request ID did not match the request that was to be cancelled")
	    (DUPLICATE_EXCEPTION_DETAIL_MESSAGE 77 WARNING "Duplicate ExceptionDetailMessage")
	    (BAD_EXCEPTION_DETAIL_MESSAGE_SERVICE_CONTEXT_TYPE 78 WARNING "Bad ExceptionDetailMessage ServiceContext type")
	    (UNEXPECTED_DIRECT_BYTE_BUFFER_WITH_NON_CHANNEL_SOCKET 79 WARNING "unexpected direct ByteBuffer with non-channel socket")
	    (UNEXPECTED_NON_DIRECT_BYTE_BUFFER_WITH_CHANNEL_SOCKET 80 WARNING "unexpected non-direct ByteBuffer with channel socket")
	    (INVALID_CONTACT_INFO_LIST_ITERATOR_FAILURE_EXCEPTION 82 WARNING "There should be at least one CorbaContactInfo to try (and fail) so this error should not be seen.")
	    (REMARSHAL_WITH_NOWHERE_TO_GO 83 WARNING "Remarshal with nowhere to go")
	    (EXCEPTION_WHEN_SENDING_CLOSE_CONNECTION 84 WARNING "Exception when sending close connection")
	    (INVOCATION_ERROR_IN_REFLECTIVE_TIE 85 WARNING "A reflective tie got an error while invoking method {0} on class {1}")
	    (BAD_HELPER_WRITE_METHOD 86 WARNING "Could not find or invoke write method on exception Helper class {0}")
	    (BAD_HELPER_READ_METHOD 87 WARNING "Could not find or invoke read method on exception Helper class {0}")
	    (BAD_HELPER_ID_METHOD 88 WARNING "Could not find or invoke id method on exception Helper class {0}")
	    (WRITE_UNDECLARED_EXCEPTION 89 WARNING "Tried to write exception of type {0} that was not declared on method")
	    (READ_UNDECLARED_EXCEPTION 90 WARNING "Tried to read undeclared exception with ID {0}")
	    (UNABLE_TO_SET_SOCKET_FACTORY_ORB 91 WARNING "Unable to setSocketFactoryORB")
	    (UNEXPECTED_EXCEPTION 92 WARNING "Unexpected exception occurred where no exception should occur")
	    (NO_INVOCATION_HANDLER 93 WARNING "No invocation handler available for {0}")
	    (INVALID_BUFF_MGR_STRATEGY 94 WARNING "{0}: invalid buffer manager strategy for Java serialization")
	    (JAVA_STREAM_INIT_FAILED 95 WARNING "Java stream initialization failed")
	    (DUPLICATE_ORB_VERSION_SERVICE_CONTEXT 96 WARNING "An ORBVersionServiceContext was already in the service context list")
	    (DUPLICATE_SENDING_CONTEXT_SERVICE_CONTEXT 97 WARNING "A SendingContextServiceContext was already in the service context list")
	    )
	(MARSHAL 
	    (CHUNK_OVERFLOW 1 WARNING "Data read past end of chunk without closing the chunk")
	    (UNEXPECTED_EOF 2 WARNING "Grow buffer strategy called underflow handler")
	    (READ_OBJECT_EXCEPTION 3  WARNING "Error in reading marshalled object")
	    (CHARACTER_OUTOFRANGE 4  WARNING "Character not IOS Latin-1 compliant in marshalling")
	    (DSI_RESULT_EXCEPTION 5  WARNING "Exception thrown during result() on ServerRequest")
	    (IIOPINPUTSTREAM_GROW 6  WARNING "grow() called on IIOPInputStream")
	    (END_OF_STREAM 7 FINE "Underflow in BufferManagerReadStream after last fragment in message")
	    (INVALID_OBJECT_KEY 8 WARNING "Invalid ObjectKey in request header")
	    (MALFORMED_URL 9 WARNING "Unable to locate value class for repository ID {0} because codebase URL {1] is malformed")
	    (VALUEHANDLER_READ_ERROR 10 WARNING "Error from readValue on ValueHandler in CDRInputStream")
	    (VALUEHANDLER_READ_EXCEPTION 11 WARNING "Exception from readValue on ValueHandler in CDRInputStream")
	    (BAD_KIND 12 WARNING "Bad kind in isCustomType in CDRInputStream")
	    (CNFE_READ_CLASS 13 WARNING "Could not find class {0} in CDRInputStream.readClass")
	    (BAD_REP_ID_INDIRECTION 14 WARNING "Bad repository ID indirection at index {0}")
	    (BAD_CODEBASE_INDIRECTION 15 WARNING "Bad codebase string indirection at index {0}")
	    (UNKNOWN_CODESET 16 WARNING "Unknown code set {0} specified by client ORB as a negotiated code set")
	    (WCHAR_DATA_IN_GIOP_1_0 17 WARNING "Attempt to marshal wide character or string data in GIOP 1.0")
	    (NEGATIVE_STRING_LENGTH 18 WARNING "String or wstring with a negative length {0}")
	    (EXPECTED_TYPE_NULL_AND_NO_REP_ID 19 WARNING "CDRInputStream.read_value(null) called, but no repository ID information on the wire")
	    (READ_VALUE_AND_NO_REP_ID 20 WARNING "CDRInputStream.read_value() called, but no repository ID information on the wire")
	    (UNEXPECTED_ENCLOSING_VALUETYPE 22 WARNING "Received end tag {0}, which is less than the expected value {1}")
	    (POSITIVE_END_TAG 23 WARNING "Read non-negative end tag {0} at offset {1} (end tags should always be negative)")
	    (NULL_OUT_CALL 24 WARNING "Out call descriptor is missing")
	    (WRITE_LOCAL_OBJECT 25 WARNING "write_Object called with a local object")
	    (BAD_INSERTOBJ_PARAM 26 WARNING "Tried to insert non-ObjectImpl {0} into an Any via insert_Object")
	    (CUSTOM_WRAPPER_WITH_CODEBASE 27 WARNING "Codebase present in RMI-IIOP stream format version 1 optional data valuetype header")
	    (CUSTOM_WRAPPER_INDIRECTION 28 WARNING "Indirection preseint in RMI-IIOP stream format version 2 optional data valuetype header")
	    (CUSTOM_WRAPPER_NOT_SINGLE_REPID 29 WARNING "0 or more than one repository ID found reading the optional data valuetype header")
	    (BAD_VALUE_TAG 30 WARNING "Bad valuetag {0} found while reading repository IDs")
	    (BAD_TYPECODE_FOR_CUSTOM_VALUE 31 WARNING "Bad typecode found for custom valuetype")
	    (ERROR_INVOKING_HELPER_WRITE 32 WARNING "An error occurred using reflection to invoke IDL Helper write method")
	    (BAD_DIGIT_IN_FIXED 33 WARNING "A bad digit was found while marshalling an IDL fixed type")
	    (REF_TYPE_INDIR_TYPE 34 WARNING "Referenced type of indirect type not marshaled")
	    (BAD_RESERVED_LENGTH 35 WARNING "Request message reserved bytes has invalid length")
	    (NULL_NOT_ALLOWED 36 WARNING "A null object is not allowed here")
	    (UNION_DISCRIMINATOR_ERROR 38 WARNING "Error in typecode union discriminator")
	    (CANNOT_MARSHAL_NATIVE 39 WARNING "Cannot marshal a native TypeCode")
	    (CANNOT_MARSHAL_BAD_TCKIND 40 WARNING "Cannot marshal an invalid TypeCode kind")
	    (INVALID_INDIRECTION 41 WARNING "Invalid indirection value {0} (>-4): probable stream corruption")
	    (INDIRECTION_NOT_FOUND 42 FINE "No type found at indirection {0}: probably stream corruption")
	    (RECURSIVE_TYPECODE_ERROR 43 WARNING "Recursive TypeCode not supported by InputStream subtype")
	    (INVALID_SIMPLE_TYPECODE 44 WARNING "TypeCode is of wrong kind to be simple")
	    (INVALID_COMPLEX_TYPECODE 45 WARNING "TypeCode is of wrong kind to be complex")
	    (INVALID_TYPECODE_KIND_MARSHAL 46 WARNING "Cannot marshal typecode of invalid kind")
	    (UNEXPECTED_UNION_DEFAULT 47 WARNING "Default union branch not expected") 
	    (ILLEGAL_UNION_DISCRIMINATOR_TYPE 48 WARNING "Illegal discriminator type in union")
	    (COULD_NOT_SKIP_BYTES 49 WARNING "Could not skip over {0} bytes at offset {1}")
	    (BAD_CHUNK_LENGTH 50 WARNING "Incorrect chunk length {0} at offset {1}")
	    (UNABLE_TO_LOCATE_REP_ID_ARRAY 51 WARNING "Unable to locate array of repository IDs from indirection {0}")
	    (BAD_FIXED 52 WARNING "Fixed of length {0} in buffer of length {1}")
	    (READ_OBJECT_LOAD_CLASS_FAILURE 53 WARNING "Failed to load stub for {0} with class {1}")
	    (COULD_NOT_INSTANTIATE_HELPER 54 WARNING "Could not instantiate Helper class {0}")
	    (BAD_TOA_OAID 55 WARNING "Bad ObjectAdapterId for TOA")
	    (COULD_NOT_INVOKE_HELPER_READ_METHOD 56 WARNING "Could not invoke helper read method for helper {0}")
	    (COULD_NOT_FIND_CLASS 57 WARNING "Could not find class")            
	    (BAD_ARGUMENTS_NVLIST 58 FINE "Error in arguments(NVList) for DSI ServerRequest")
	    (STUB_CREATE_ERROR 59 FINE "Could not create stub")
	    (JAVA_SERIALIZATION_EXCEPTION 60 WARNING "Java serialization exception during {0} operation"))
	(NO_IMPLEMENT
	    (GENERIC_NO_IMPL 1  FINE "feature not implemented")
	    (CONTEXT_NOT_IMPLEMENTED 2  FINE "IDL request context is not implemented") 
	    (GETINTERFACE_NOT_IMPLEMENTED 3  FINE "getInterface() is not implemented")
	    (SEND_DEFERRED_NOTIMPLEMENTED 4  FINE "send deferred is not implemented")
	    (LONG_DOUBLE_NOT_IMPLEMENTED 5 FINE "IDL type long double is not supported in Java"))
	(OBJ_ADAPTER 
	    (NO_SERVER_SC_IN_DISPATCH 1  WARNING "No server request dispatcher found when dispatching request to object adapter")
	    (ORB_CONNECT_ERROR 2  WARNING "Error in connecting servant to ORB")
	    (ADAPTER_INACTIVE_IN_ACTIVATION 3 FINE "StubAdapter.getDelegate failed to activate a Servant"))
	(OBJECT_NOT_EXIST
	    (LOCATE_UNKNOWN_OBJECT 1  WARNING "Locate response indicated that the object was unknown") 
	    (BAD_SERVER_ID 2  FINE "The server ID in the target object key does not match the server key expected by the server") 
	    (BAD_SKELETON 3  WARNING "No skeleton found in the server that matches the target object key") 
	    (SERVANT_NOT_FOUND 4  WARNING "Servant not found") 
	    (NO_OBJECT_ADAPTER_FACTORY 5 WARNING "No object adapter factory")
	    (BAD_ADAPTER_ID 6  WARNING "Bad adapter ID")
	    (DYN_ANY_DESTROYED 7 WARNING "Dynamic Any was destroyed: all operations are invalid"))
	(TRANSIENT
	    (REQUEST_CANCELED 1 WARNING "Request cancelled by exception"))
	(UNKNOWN 
	    (UNKNOWN_CORBA_EXC 1  WARNING "Unknown user exception while unmarshalling")
	    (RUNTIMEEXCEPTION 2  WARNING "Unknown user exception thrown by the server")
	    (UNKNOWN_SERVER_ERROR 3  WARNING "Unknown exception or error thrown by the ORB or application")
	    (UNKNOWN_DSI_SYSEX 4 WARNING "Error while marshalling SystemException after DSI-based invocation")
	    (UNKNOWN_SYSEX 5 WARNING "Error while unmarshalling SystemException")
	    (WRONG_INTERFACE_DEF 6 WARNING "InterfaceDef object of wrong type returned by server")
	    (NO_INTERFACE_DEF_STUB 7 WARNING "org.omg.CORBA._InterfaceDefStub class not available")
	    (UNKNOWN_EXCEPTION_IN_DISPATCH 9 FINE "UnknownException in dispatch"))))
