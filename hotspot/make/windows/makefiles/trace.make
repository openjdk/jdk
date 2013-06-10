#
# Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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
#

# This makefile (trace.make) is included from the trace.make in the
# build directories.
#
# It knows how to build and run the tools to generate trace files.

!include $(WorkSpace)/make/windows/makefiles/rules.make

# #########################################################################


TraceAltSrcDir = $(WorkSpace)/src/closed/share/vm/trace
TraceSrcDir = $(WorkSpace)/src/share/vm/trace

TraceGeneratedNames =     \
    traceEventClasses.hpp \
    traceEventIds.hpp     \
    traceTypes.hpp


!if "$(OPENJDK)" != "true"
TraceGeneratedNames = $(TraceGeneratedNames) \
    traceRequestables.hpp \
    traceEventControl.hpp \
    traceProducer.cpp
!endif


#Note: TraceGeneratedFiles must be kept in sync with TraceGeneratedNames by hand.
#Should be equivalent to "TraceGeneratedFiles = $(TraceGeneratedNames:%=$(TraceOutDir)/%)"
TraceGeneratedFiles = \
    $(TraceOutDir)/traceEventClasses.hpp \
	$(TraceOutDir)/traceEventIds.hpp     \
	$(TraceOutDir)/traceTypes.hpp

!if "$(OPENJDK)" != "true"
TraceGeneratedFiles = $(TraceGeneratedFiles) \
	$(TraceOutDir)/traceRequestables.hpp \
    $(TraceOutDir)/traceEventControl.hpp \
	$(TraceOutDir)/traceProducer.cpp
!endif

XSLT = $(QUIETLY) $(REMOTE) $(RUN_JAVA) -classpath $(JvmtiOutDir) jvmtiGen

XML_DEPS = $(TraceSrcDir)/trace.xml $(TraceSrcDir)/tracetypes.xml \
    $(TraceSrcDir)/trace.dtd $(TraceSrcDir)/xinclude.mod

!if "$(OPENJDK)" != "true"
XML_DEPS = $(XML_DEPS) $(TraceAltSrcDir)/traceevents.xml
!endif

.PHONY: all clean cleanall

# #########################################################################

default::
	@if not exist $(TraceOutDir) mkdir $(TraceOutDir)

$(TraceOutDir)/traceEventIds.hpp: $(TraceSrcDir)/trace.xml $(TraceSrcDir)/traceEventIds.xsl $(XML_DEPS)
	@echo Generating $@
	@$(XSLT) -IN $(TraceSrcDir)/trace.xml -XSL $(TraceSrcDir)/traceEventIds.xsl -OUT $(TraceOutDir)/traceEventIds.hpp

$(TraceOutDir)/traceTypes.hpp: $(TraceSrcDir)/trace.xml $(TraceSrcDir)/traceTypes.xsl $(XML_DEPS)
	@echo Generating $@
	@$(XSLT) -IN $(TraceSrcDir)/trace.xml -XSL $(TraceSrcDir)/traceTypes.xsl -OUT $(TraceOutDir)/traceTypes.hpp

!if "$(OPENJDK)" == "true"

$(TraceOutDir)/traceEventClasses.hpp: $(TraceSrcDir)/trace.xml $(TraceSrcDir)/traceEventClasses.xsl $(XML_DEPS)
	@echo Generating $@
	@$(XSLT) -IN $(TraceSrcDir)/trace.xml -XSL $(TraceSrcDir)/traceEventClasses.xsl -OUT $(TraceOutDir)/traceEventClasses.hpp

!else

$(TraceOutDir)/traceEventClasses.hpp: $(TraceSrcDir)/trace.xml $(TraceAltSrcDir)/traceEventClasses.xsl $(XML_DEPS)
	@echo Generating $@
	@$(XSLT) -IN $(TraceSrcDir)/trace.xml -XSL $(TraceAltSrcDir)/traceEventClasses.xsl -OUT $(TraceOutDir)/traceEventClasses.hpp

$(TraceOutDir)/traceProducer.cpp: $(TraceSrcDir)/trace.xml $(TraceAltSrcDir)/traceProducer.xsl $(XML_DEPS)
	@echo Generating $@
	@$(XSLT) -IN $(TraceSrcDir)/trace.xml -XSL $(TraceAltSrcDir)/traceProducer.xsl -OUT $(TraceOutDir)/traceProducer.cpp

$(TraceOutDir)/traceRequestables.hpp: $(TraceSrcDir)/trace.xml $(TraceAltSrcDir)/traceRequestables.xsl $(XML_DEPS)
	@echo Generating $@
	@$(XSLT) -IN $(TraceSrcDir)/trace.xml -XSL $(TraceAltSrcDir)/traceRequestables.xsl -OUT $(TraceOutDir)/traceRequestables.hpp

$(TraceOutDir)/traceEventControl.hpp: $(TraceSrcDir)/trace.xml $(TraceAltSrcDir)/traceEventControl.xsl $(XML_DEPS)
	@echo Generating $@
	@$(XSLT) -IN $(TraceSrcDir)/trace.xml -XSL $(TraceAltSrcDir)/traceEventControl.xsl -OUT $(TraceOutDir)/traceEventControl.hpp

!endif

# #########################################################################

cleanall :
	rm $(TraceGeneratedFiles)


