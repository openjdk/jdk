#
# Copyright (c) 2000, 2006, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

BUILDDIR = ../../..

PACKAGE = org.omg
PRODUCT = org

include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
CORBA_JMK_DIRECTORY=$(TOPDIR)/make/com/sun/corba/minclude/
include $(CORBA_JMK_DIRECTORY)org_omg_PortableServer.jmk
include $(CORBA_JMK_DIRECTORY)org_omg_DynamicAny.jmk
include $(CORBA_JMK_DIRECTORY)org_omg_CosNaming.jmk
include $(CORBA_JMK_DIRECTORY)org_omg_IOP.jmk
include $(CORBA_JMK_DIRECTORY)org_omg_Messaging.jmk
include $(CORBA_JMK_DIRECTORY)org_omg_CORBAX.jmk
include $(CORBA_JMK_DIRECTORY)org_omg_PortableInterceptor.jmk

ifdef STANDALONE_CORBA_WS
include $(CORBA_JMK_DIRECTORY)org_omg_CosTransactions.jmk
endif

#
# Rules
#

ifdef STANDALONE_CORBA_WS
build: poa.compile iop.compile corbax.compile messaging.compile pi.compile dynany.compile ns.compile tr.compile
clean clobber:: pi.clean iop.clean corbax.clean messaging.clean poa.clean dynany.clean ns.clean tr.clean
else
build: poa.compile iop.compile corbax.compile messaging.compile pi.compile ns.compile dynany.compile
clean clobber:: iop.clean corbax.clean messaging.clean pi.clean poa.clean ns.clean dynany.clean
endif


#
# These rules are for compiling/cleaning the POA generated stuff
#

SRC_DIR = $(SHARE_SRC)/classes
CORBA_IDL_DIR = $(SRC_DIR)/org/omg/PortableServer

# IDL Files

POA.IDL = $(SRC_DIR)/org/omg/PortableServer/poa.idl
DYNAMIC_ANY.IDL = $(SRC_DIR)/org/omg/DynamicAny/DynamicAny.idl
COSTRANSACTIONS.IDL = $(SRC_DIR)/org/omg/CosTransactions/CosTransactions.idl
COSTSINTEROP.IDL = $(SRC_DIR)/org/omg/CosTransactions/CosTSInteroperation.idl
NAMESERVICE.IDL = $(SRC_DIR)/org/omg/CosNaming/nameservice.idl
# The following 4 files are for Portable Interceptors
IOP.IDL = $(SRC_DIR)/org/omg/PortableInterceptor/IOP.idl
CORBAX.IDL = $(SRC_DIR)/org/omg/PortableInterceptor/CORBAX.idl
MESSAGING.IDL = $(SRC_DIR)/org/omg/PortableInterceptor/Messaging.idl
INTERCEPTORS.IDL = $(SRC_DIR)/org/omg/PortableInterceptor/Interceptors.idl

# IDL Directories

POAFILEDIR = $(GENSRCDIR)/org/omg/PortableServer
POASRCDIR = $(SRC_DIR)/org/omg/PortableServer
DYNANY_DIR = $(GENSRCDIR)/org/omg/DynamicAny
NAMESERVICEDIR = $(GENSRCDIR)/org/omg/CosNaming
INTERCEPTORSDIR = $(GENSRCDIR)/org/omg/PortableInterceptor

# POA files and Rules

POAGENERATEDFILES = $(POA_GENERATED_java:%=$(GENSRCDIR)/%)
POAHELHOLFILES =    $(POA_GENERATED_HELP_HOL_java:%=$(GENSRCDIR)/%)

$(POAGENERATEDFILES):: $(POA.IDL)
	$(IDLJ) -td "$(GENSRCDIR)" -i "$(SRC_DIR)/org/omg/PortableServer" -i "$(SRC_DIR)/org/omg/PortableInterceptor" -corba 3.0 -fall -pkgPrefix PortableServer org.omg $(POA.IDL)
	$(RM) -r $(POAHELHOLFILES)

poa.compile: $(POAGENERATEDFILES)

poa.clean: 
	$(RM) -r $(POAFILEDIR)

# DynAny Files and Rules

DYNANY_IDLJ_FLAGS=-i "$(SRC_DIR)/org/omg/CORBA" -corba 3.0 -fall -pkgPrefix DynamicAny org.omg

DYNANYFILES = $(DYNANY_GENERATED_java:%=$(GENSRCDIR)/%)
DYNANYDELETEFILES = $(DYNANY_TO_DELETE_FILES:%=$(GENSRCDIR)/%)

# Generating and deleting unused files to reduce images size
$(DYNANYFILES): $(DYNAMIC_ANY.IDL)
	$(IDLJ) -td "$(GENSRCDIR)" $(DYNANY_IDLJ_FLAGS) $(DYNAMIC_ANY.IDL)
	$(RM) $(DYNANY_DIR)/*POA*
	$(RM) $(DYNANY_DIR)/*Holder*
	$(RM) $(DYNANYDELETEFILES)

dynany.compile: $(DYNANYFILES)

dynany.clean:
	$(RM) -r $(DYNANY_DIR)

# CosTransactions Files
TRGENERATEDFILES = $(TR_GENERATED_java:%=$(GENSRCDIR)/%)
TR_IDLJ_FLAGS =-i "$(SRC_DIR)/org/omg/PortableInterceptor" -i "$(SRC_DIR)/org/omg/PortableServer" -i "$(SRC_DIR)/org/omg/CosTransactions"

$(TRGENERATEDFILES):: $(COSTRANSACTIONS.IDL) $(COSTSINTEROP.IDL)
	$(IDLJ) -td "$(GENSRCDIR)" $(TR_IDLJ_FLAGS) -D CORBA3 -corba 3.0 -fall -pkgPrefix CORBA org.omg -pkgPrefix CosTransactions org.omg $(COSTRANSACTIONS.IDL)
	$(IDLJ) -td "$(GENSRCDIR)" $(TR_IDLJ_FLAGS) -D CORBA3 -corba 3.0 -fall -pkgPrefix CORBA org.omg -pkgPrefix CosTSInteroperation org.omg $(COSTSINTEROP.IDL)
#	$(IDLJ) -td "$(GENSRCDIR)" $(TR_IDLJ_FLAGS) -D CORBA3 -corba 3.0 -fall -pkgPrefix CORBA org.omg $(TRANSACTIONS.IDL)
#	$(IDLJ) -td "$(GENSRCDIR)" $(TR_IDLJ_FLAGS) -D CORBA3 -corba 3.0 -fallTIE -pkgPrefix CORBA org.omg $(TRANSACTIONS.IDL)
#	$(IDLJ) -td "$(GENSRCDIR)" $(TR_IDLJ_FLAGS) -D CORBA3 -corba 3.0 -fall -pkgPrefix CORBA org.omg $(TRANSACTIONS.IDL)
#	$(IDLJ) -td "$(GENSRCDIR)" $(TR_IDLJ_FLAGS) -D CORBA3 -corba 3.0 -fallTIE -pkgPrefix CORBA org.omg $(TRANSACTIONS.IDL)

tr.compile: $(TRGENERATEDFILES)

tr.clean:
	$(RM) -f $(TRGENERATEDFILES)

#CosNaming Files
NSGENERATEDFILES = $(NS_GENERATED_java:%=$(GENSRCDIR)/%)

# Compile NameService IDL file and then copy
# _NamingContextImplBase.java and _BindingIteratorImplBase.java files
# to GENSRC directory. Note that these two classes are deprecated.
$(NSGENERATEDFILES): $(NAMESERVICE.IDL)
	$(IDLJ) -td "$(GENSRCDIR)" -i $(NAMESERVICEDIR) -fall -pkgPrefix CosNaming org.omg $(NAMESERVICE.IDL)

ns.compile: $(NSGENERATEDFILES)

ns.clean:
	$(RM) -f $(NSGENERATEDFILES)

#IOP compile 
IOPGENERATEDFILES = $(IOP_java:%=$(GENSRCDIR)/%)
IOPDELETEFILES =    $(IOP_TO_DELETE_FILES:%=$(GENSRCDIR)/%)

$(IOPGENERATEDFILES):: $(IOP.IDL)
	$(IDLJ) -td "$(GENSRCDIR)" -i "$(SRC_DIR)/org/omg/PortableInterceptor" -i $(CORBA_IDL_DIR) -fall -corba 3.0 -D CORBA3 -pkgPrefix IOP org.omg $(IOP.IDL)
	$(RM) -r $(IOPDELETEFILES)

iop.compile: $(IOPGENERATEDFILES)

iop.clean: 
	$(RM) $(IOPGENERATEDFILES)

#CORBAX compile 
CORBAXGENERATEDFILES = $(CORBA_java:%=$(GENSRCDIR)/%)
CORBAXDUPLICATEFILES = $(CORBA_DUPLICATE_java:%=$(GENSRCDIR)/%)

$(CORBAXGENERATEDFILES):: $(CORBAX.IDL)
	$(IDLJ) -td "$(GENSRCDIR)" -i "$(SRC_DIR)/org/omg/PortableInterceptor" -i $(CORBA_IDL_DIR) -fall -corba 3.0 $(CORBAX.IDL)
	$(RM) $(CORBAXDUPLICATEFILES)

corbax.compile: $(CORBAXGENERATEDFILES)

corbax.clean: 
	$(RM) $(CORBAXGENERATEDFILES)

#Messaging compile 
MESSAGINGGENERATEDFILES = $(Messaging_java:%=$(GENSRCDIR)/%)

$(MESSAGINGGENERATEDFILES):: $(MESSAGING.IDL)
	$(IDLJ) -td "$(GENSRCDIR)" -i "$(SRC_DIR)/org/omg/PortableInterceptor" -i $(CORBA_IDL_DIR) -fall -corba 3.0 -pkgPrefix Messaging org.omg $(MESSAGING.IDL)

messaging.compile: $(MESSAGINGGENERATEDFILES)

messaging.clean: 
	$(RM) $(MESSAGINGGENERATEDFILES)



#PortableInterceptor Files
PIGENERATEDFILES = $(PI_GENERATED_java:%=$(GENSRCDIR)/%)
PIHELHOLFILES = $(PI_GENERATED_HELP_HOL_java:%=$(GENSRCDIR)/%)

$(PIGENERATEDFILES):: $(INTERCEPTORS.IDL)
	$(IDLJ) -td "$(GENSRCDIR)" -i "$(SRC_DIR)/org/omg/PortableInterceptor" -i $(CORBA_IDL_DIR) -fall -pkgPrefix PortableInterceptor org.omg -pkgPrefix Dynamic org.omg -pkgPrefix IOP org.omg -corba 3.0 -D CORBA3 $(INTERCEPTORS.IDL)
	$(RM) $(PIHELHOLFILES)

pi.compile: $(PIGENERATEDFILES)

pi.clean:
	$(RM) -f $(PIGENERATEDFILES)


ifdef STANDALONE_CORBA_WS
.PHONY : poa.compile poa.clean iop.compile iop.clean messaging.compile messaging.clean corbax.compile corbax.clean pi.compile pi.clean dynany.clean dynany.compile ns.compile ns.clean tr.compile tr.clean
else
.PHONY : poa.compile poa.clean iop.compile iop.clean messaging.compile messaging.clean corbax.compile corbax.clean pi.compile pi.clean ns.compile ns.clean dynany.clean dynany.compile
endif

include $(BUILDDIR)/common/Rules.gmk
