#
# Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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

PACKAGE = com.sun.corba.se
PRODUCT = org

include $(BUILDDIR)/common/Defs.gmk

#
# Rules
#
build: logwrapper.generate logresource.generate activation.idl.compile portableactivation.idl.compile 

clean clobber:: activation.idl.clean portableactivation.idl.clean

#
# Files
#
CORBA_JMK_DIRECTORY=$(TOPDIR)/make/com/sun/corba/minclude/
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_spi_activation.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_logging.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_PortableActivationIDL.jmk

#
# Dirs
#

SRC_DIR = $(SHARE_SRC)/classes
CORBA_IDL_DIR = $(SRC_DIR)/org/omg/PortableServer
PI_IDL_DIR = $(SRC_DIR)/org/omg/PortableInterceptor

ACTIVATIONFILEDIR = $(GENSRCDIR)/com/sun/corba/se/spi/activation
LOG_GENDIRECTORY  = $(GENSRCDIR)/com/sun/corba/se/impl/logging
LOG_CLASS_OUTPUTDIRECTORY = $(CLASSBINDIR)/com/sun/corba/se/impl/logging
LOGSTRINGS_PROPERTIES     = LogStrings.properties
PORTABLEACTIVATIONFILEDIR = $(GENSRCDIR)/com/sun/corba/se/PortableActivationIDL

#
# IDLs
#

ACTIVATION.IDL = $(SRC_DIR)/com/sun/corba/se/spi/activation/activation.idl
PORTABLEACTIVATION.IDL = $(SRC_DIR)/com/sun/corba/se/PortableActivationIDL/activation.idl

ACTIVATION.MC   = $(SRC_DIR)/com/sun/corba/se/spi/logging/data/Activation.mc
IOR.MC          = $(SRC_DIR)/com/sun/corba/se/spi/logging/data/IOR.mc
INTERCEPTORS.MC = $(SRC_DIR)/com/sun/corba/se/spi/logging/data/Interceptors.mc
NAMING.MC       = $(SRC_DIR)/com/sun/corba/se/spi/logging/data/Naming.mc
OMG.MC          = $(SRC_DIR)/com/sun/corba/se/spi/logging/data/OMG.mc
ORBUTIL.MC      = $(SRC_DIR)/com/sun/corba/se/spi/logging/data/ORBUtil.mc
POA.MC          = $(SRC_DIR)/com/sun/corba/se/spi/logging/data/POA.mc
UTIL.MC         = $(SRC_DIR)/com/sun/corba/se/spi/logging/data/Util.mc

MC_GENERATE_CLASS = make-class
MC_GENERATE_LOG_RB = make-resource 

MC_CLASSPATH=$(BUILDTOOLJARDIR)/MC.jar
MCJ_GENERATE_CLASS = $(BOOT_JAVA_CMD) \
    -cp "$(MC_CLASSPATH)" com.sun.tools.corba.se.logutil.MC $(MC_GENERATE_CLASS) 
MCJ_GENERATE_LOG_RB = $(BOOT_JAVA_CMD) \
    -cp "$(MC_CLASSPATH)" com.sun.tools.corba.se.logutil.MC $(MC_GENERATE_LOG_RB) 


#
# Generate LogWrapper classes
#
logwrapper.generate: \
	$(LOG_GENDIRECTORY) \
	$(LOG_GENDIRECTORY)/ActivationSystemException.java \
	$(LOG_GENDIRECTORY)/IORSystemException.java \
	$(LOG_GENDIRECTORY)/InterceptorsSystemException.java \
	$(LOG_GENDIRECTORY)/NamingSystemException.java \
	$(LOG_GENDIRECTORY)/OMGSystemException.java \
	$(LOG_GENDIRECTORY)/ORBUtilSystemException.java \
	$(LOG_GENDIRECTORY)/POASystemException.java \
	$(LOG_GENDIRECTORY)/UtilSystemException.java

$(LOG_GENDIRECTORY):
	$(MKDIR) -p $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/ActivationSystemException.java : $(ACTIVATION.MC)
	$(MCJ_GENERATE_CLASS) $(ACTIVATION.MC) $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/IORSystemException.java : $(IOR.MC)
	$(MCJ_GENERATE_CLASS) $(IOR.MC) $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/InterceptorsSystemException.java : $(INTERCEPTORS.MC)
	$(MCJ_GENERATE_CLASS) $(INTERCEPTORS.MC) $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/NamingSystemException.java : $(NAMING.MC)
	$(MCJ_GENERATE_CLASS) $(NAMING.MC) $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/OMGSystemException.java : $(OMG.MC)
	$(MCJ_GENERATE_CLASS) $(OMG.MC) $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/ORBUtilSystemException.java : $(ORBUTIL.MC)
	$(MCJ_GENERATE_CLASS) $(ORBUTIL.MC) $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/POASystemException.java : $(POA.MC)
	$(MCJ_GENERATE_CLASS) $(POA.MC) $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/UtilSystemException.java : $(UTIL.MC)
	$(MCJ_GENERATE_CLASS) $(UTIL.MC) $(LOG_GENDIRECTORY)

logresource.generate: $(LOG_GENDIRECTORY)/LogStrings.properties

$(LOG_GENDIRECTORY)/LogStrings.properties: \
		$(LOG_GENDIRECTORY) \
		$(LOG_GENDIRECTORY)/ActivationSystemException.resource \
		$(LOG_GENDIRECTORY)/IORSystemException.resource \
		$(LOG_GENDIRECTORY)/InterceptorsSystemException.resource \
		$(LOG_GENDIRECTORY)/NamingSystemException.resource \
		$(LOG_GENDIRECTORY)/OMGSystemException.resource \
		$(LOG_GENDIRECTORY)/ORBUtilSystemException.resource \
		$(LOG_GENDIRECTORY)/POASystemException.resource \
		$(LOG_GENDIRECTORY)/UtilSystemException.resource
	$(CAT) $(LOG_GENDIRECTORY)/*.resource > $(LOG_GENDIRECTORY)/LogStrings.properties

$(LOG_GENDIRECTORY)/ActivationSystemException.resource : $(ACTIVATION.MC)
	$(MCJ_GENERATE_LOG_RB) $(ACTIVATION.MC) $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/IORSystemException.resource : $(IOR.MC)
	$(MCJ_GENERATE_LOG_RB) $(IOR.MC) $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/InterceptorsSystemException.resource : $(INTERCEPTORS.MC)
	$(MCJ_GENERATE_LOG_RB) $(INTERCEPTORS.MC) $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/NamingSystemException.resource : $(NAMING.MC)
	$(MCJ_GENERATE_LOG_RB) $(NAMING.MC) $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/OMGSystemException.resource : $(OMG.MC)
	$(MCJ_GENERATE_LOG_RB) $(OMG.MC) $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/ORBUtilSystemException.resource : $(ORBUTIL.MC)
	$(MCJ_GENERATE_LOG_RB) $(ORBUTIL.MC) $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/POASystemException.resource : $(POA.MC)
	$(MCJ_GENERATE_LOG_RB) $(POA.MC) $(LOG_GENDIRECTORY)

$(LOG_GENDIRECTORY)/UtilSystemException.resource : $(UTIL.MC)
	$(MCJ_GENERATE_LOG_RB) $(UTIL.MC) $(LOG_GENDIRECTORY)


#
# Activation
#

ACTIVATIONFILES = $(com_sun_corba_se_spi_activation_java:%=$(GENSRCDIR)/%)

$(ACTIVATIONFILES): $(ACTIVATION.IDL)
	$(IDLJ) -td $(GENSRCDIR) -i $(ACTIVATIONFILEDIR) -fall -oldImplBase -pkgPrefix activation com.sun.corba.se.spi $(ACTIVATION.IDL)

activation.idl.compile: $(ACTIVATIONFILES)

activation.idl.clean: 
	$(RM) -f $(ACTIVATIONFILES)


#
# PortableActivationIDL
#

PORTABLEACTIVATIONFILES = $(com_sun_corba_se_PortableActivationIDL_java:%=$(GENSRCDIR)/%)

PORTABLEACTIVATION_INCLUDES = -i $(CORBA_IDL_DIR) -i $(PI_IDL_DIR)

PORTABLEACTIVATION_PKG_PREFIXES = -pkgPrefix PortableInterceptor org.omg \
    -pkgPrefix PortableActivationIDL com.sun.corba.se \
    -pkgPrefix Dynamic org.omg \
    -pkgPrefix IOP org.omg

# REVISIT: remove -oldImplBase
$(PORTABLEACTIVATIONFILES): $(PORTABLEACTIVATION.IDL)
	$(IDLJ) -td $(GENSRCDIR) $(PORTABLEACTIVATION_INCLUDES) $(PORTABLEACTIVATION_PKG_PREFIXES) -fall -oldImplBase -corba 3.0 -D CORBA3 $(PORTABLEACTIVATION.IDL)

portableactivation.idl.compile: $(PORTABLEACTIVATIONFILES)

portableactivation.idl.clean:
	$(RM) -f $(PORTABLEACTIVATIONFILES)

#
# Include
#
include $(BUILDDIR)/common/Rules.gmk
