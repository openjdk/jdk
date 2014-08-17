;
; Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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
("com.sun.corba.se.impl.logging" "UtilSystemException" UTIL
    (
	(BAD_OPERATION
	    (STUB_FACTORY_COULD_NOT_MAKE_STUB 1 FINE "StubFactory failed on makeStub call")
	    (ERROR_IN_MAKE_STUB_FROM_REPOSITORY_ID 2 FINE "Error in making stub given RepositoryId") 
	    (CLASS_CAST_EXCEPTION_IN_LOAD_STUB 3 FINE "ClassCastException in loadStub")
	    (EXCEPTION_IN_LOAD_STUB 4 FINE "Exception in loadStub")
	    )
	(BAD_PARAM
	    (NO_POA 2 WARNING "Error in loadStubAndUpdateCache caused by _this_object")
	    (CONNECT_WRONG_ORB 3 FINE "Tried to connect already connected Stub Delegate to a different ORB")
	    (CONNECT_NO_TIE    4 WARNING "Tried to connect unconnected Stub Delegate but no Tie was found")
	    (CONNECT_TIE_WRONG_ORB 5 WARNING "Tried to connect unconnected stub with Tie in a different ORB")
	    (CONNECT_TIE_NO_SERVANT 6 WARNING "Tried to connect unconnected stub to unconnected Tie")
	    (LOAD_TIE_FAILED 7 FINE "Failed to load Tie of class {0}")
	    )
	(DATA_CONVERSION 
	    (BAD_HEX_DIGIT 1 WARNING "Bad hex digit in string_to_object"))
	(MARSHAL
	    (UNABLE_LOCATE_VALUE_HELPER 2 WARNING "Could not locate value helper")
	    (INVALID_INDIRECTION 3 WARNING "Invalid indirection {0}"))
	(INV_OBJREF 
	    (OBJECT_NOT_CONNECTED 1 WARNING "{0} did not originate from a connected object")
	    (COULD_NOT_LOAD_STUB 2 WARNING "Could not load stub for class {0}")
	    (OBJECT_NOT_EXPORTED 3 WARNING "Class {0} not exported, or else is actually a JRMP stub"))
	(INTERNAL
	    (ERROR_SET_OBJECT_FIELD 1 WARNING "Error in setting object field {0} in {1} to {2}")
	    (ERROR_SET_BOOLEAN_FIELD 2 WARNING "Error in setting boolean field {0} in {1} to {2}")
	    (ERROR_SET_BYTE_FIELD 3 WARNING "Error in setting byte field {0} in {1} to {2}")
	    (ERROR_SET_CHAR_FIELD 4 WARNING "Error in setting char field {0} in {1} to {2}")
	    (ERROR_SET_SHORT_FIELD 5 WARNING "Error in setting short field {0} in {1} to {2}")
	    (ERROR_SET_INT_FIELD 6 WARNING "Error in setting int field {0} in {1} to {2}")
	    (ERROR_SET_LONG_FIELD 7 WARNING "Error in setting long field {0} in {1} to {2}")
	    (ERROR_SET_FLOAT_FIELD 8 WARNING "Error in setting float field {0} in {1} to {2}")
	    (ERROR_SET_DOUBLE_FIELD 9 WARNING "Error in setting double field {0} in {1} to {2}")
	    (ILLEGAL_FIELD_ACCESS 10 WARNING "IllegalAccessException while trying to write to field {0}")
	    (BAD_BEGIN_UNMARSHAL_CUSTOM_VALUE 11 WARNING "State should be saved and reset first")
	    (CLASS_NOT_FOUND 12 WARNING "Failure while loading specific Java remote exception class: {0}"))
	(UNKNOWN
	    (UNKNOWN_SYSEX 1 WARNING "Unknown System Exception"))))
