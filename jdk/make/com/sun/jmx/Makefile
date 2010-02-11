#
# Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

#
# Makefile for building RMI stubs of JMX Remote
#

BUILDDIR = ../../..
PACKAGE = javax.management
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Files to compile
#

# NO java files to compile: already compiled in javax/management/Makefile

# Note : some targets are double colon rules and some single colon rules
# within common included gmk files : that is why the following for loop
# has been duplicated.

# When building the openjdk, build snmp only if importing binary plugs,
ifdef OPENJDK
  ifeq ($(IMPORT_BINARY_PLUGS),true)
    SUBDIRS = snmp
  endif
else
  SUBDIRS = snmp
endif

include $(BUILDDIR)/common/Subdirs.gmk

all build:
	$(SUBDIRS-loop)

clean clobber::
	$(SUBDIRS-loop)

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

build: stubs

#
# Clean up internal-use-only package.
#
clean ::
	$(RM) -r $(CLASSDESTDIR)/com/sun/jmx \
                 $(CLASSDESTDIR)/com/sun/management/jmx \
                 $(CLASSDESTDIR)/org/omg/stub/javax/management/remote/rmi \


#
# The remainder of this makefile contains defs and rules
# for generating RMI stubs.
# With inspiration from com/sun/jndi/rmi/registry
#

#
# Full class names of implementations requiring stubs
#
REMOTE_impls = \
    javax.management.remote.rmi.RMIConnectionImpl \
    javax.management.remote.rmi.RMIServerImpl \


REMOTE_files = $(subst .,/,$(REMOTE_impls))
FILES_stubs  = $(REMOTE_files:%=$(CLASSDESTDIR)/%_Stub.class)
FILES_ties   = $(subst javax/management/remote/rmi/,javax/management/remote/rmi/_,$(REMOTE_files:%=$(CLASSDESTDIR)/%_Tie.class))


#
# Compile stubs and skeletons for remote implementations
#
# Keep generated RMI/JRMP Stub source files and copy them to GENSRCDIR
# so that javadoc can include them in the API (4997471)
#
# For RMI/IIOP call rmic a second time with -standardPackage option
# so that *_tie classes are generated in package without the prefix
# org.omg.stub (6375696)
# 
$(CLASSDESTDIR)/%_Stub.class: $(CLASSDESTDIR)/%.class
	$(prep-target)
	$(RMIC) -classpath "$(CLASSDESTDIR)"    \
                -d $(CLASSDESTDIR)              \
                -v1.2                           \
                -keepgenerated                  \
                $(subst /,.,$(<:$(CLASSDESTDIR)/%.class=%))
	$(MKDIR) -p $(@D:$(CLASSDESTDIR)/%=$(GENSRCDIR)/%)
	$(CP)       $(@:%.class=%.java)                                     \
                    $(@D:$(CLASSDESTDIR)/%=$(GENSRCDIR)/%)
	$(RMIC) -classpath "$(CLASSDESTDIR)"    \
                -d $(CLASSDESTDIR)              \
                -iiop -v1.2                     \
                $(subst /,.,$(<:$(CLASSDESTDIR)/%.class=%))
	$(RMIC) $(HOTSPOT_INTERPRETER_FLAG) -classpath "$(CLASSDESTDIR)"    \
                -d $(CLASSDESTDIR)              \
                -iiop -v1.2                     \
                -standardPackage                \
                $(subst /,.,$(<:$(CLASSDESTDIR)/%.class=%))
	@$(java-vm-cleanup)

stubs: $(FILES_stubs) 
