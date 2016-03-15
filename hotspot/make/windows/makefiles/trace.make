#
# Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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


TraceAltSrcDir = $(WorkSpace)\src\closed\share\vm\trace
TraceSrcDir = $(WorkSpace)\src\share\vm\trace

!ifndef OPENJDK
!if EXISTS($(TraceAltSrcDir))
HAS_ALT_SRC = true
!endif
!endif

TraceGeneratedNames =     \
    traceEventClasses.hpp \
    traceEventIds.hpp     \
    traceTypes.hpp

!ifdef HAS_ALT_SRC
TraceGeneratedNames = $(TraceGeneratedNames) \
    traceRequestables.hpp \
    traceEventControl.hpp
!endif


#Note: TraceGeneratedFiles must be kept in sync with TraceGeneratedNames by hand.
#Should be equivalent to "TraceGeneratedFiles = $(TraceGeneratedNames:%=$(TraceOutDir)/%)"
TraceGeneratedFiles = \
    $(TraceOutDir)/traceEventClasses.hpp \
    $(TraceOutDir)/traceEventIds.hpp     \
    $(TraceOutDir)/traceTypes.hpp

!ifdef HAS_ALT_SRC
TraceGeneratedFiles = $(TraceGeneratedFiles) \
    $(TraceOutDir)/traceRequestables.hpp \
    $(TraceOutDir)/traceEventControl.hpp
!endif

XSLT = $(QUIETLY) $(REMOTE) $(RUN_JAVA) -classpath $(JvmtiOutDir) jvmtiGen

TraceXml = $(TraceSrcDir)/trace.xml

!ifdef HAS_ALT_SRC
TraceXml = $(TraceAltSrcDir)/trace.xml
!endif

XML_DEPS = $(TraceXml) $(TraceSrcDir)/tracetypes.xml \
    $(TraceSrcDir)/trace.dtd $(TraceSrcDir)/xinclude.mod \
    $(TraceSrcDir)/tracerelationdecls.xml $(TraceSrcDir)/traceevents.xml

!ifdef HAS_ALT_SRC
XML_DEPS = $(XML_DEPS) $(TraceAltSrcDir)/traceeventscustom.xml \
    $(TraceAltSrcDir)/traceeventtypes.xml
!endif

.PHONY: all clean cleanall

# #########################################################################

default::
	@if not exist $(TraceOutDir) mkdir $(TraceOutDir)

$(TraceOutDir)/traceEventIds.hpp: $(TraceSrcDir)/traceEventIds.xsl $(XML_DEPS)
	@echo Generating $@
	$(XSLT) -IN $(TraceXml) -XSL $(TraceSrcDir)/traceEventIds.xsl -OUT $(TraceOutDir)/traceEventIds.hpp

$(TraceOutDir)/traceTypes.hpp: $(TraceSrcDir)/traceTypes.xsl $(XML_DEPS)
	@echo Generating $@
	$(XSLT) -IN $(TraceXml) -XSL $(TraceSrcDir)/traceTypes.xsl -OUT $(TraceOutDir)/traceTypes.hpp

!ifndef HAS_ALT_SRC

$(TraceOutDir)/traceEventClasses.hpp: $(TraceSrcDir)/traceEventClasses.xsl $(XML_DEPS)
	@echo Generating OpenJDK $@
	$(XSLT) -IN $(TraceXml) -XSL $(TraceSrcDir)/traceEventClasses.xsl -OUT $(TraceOutDir)/traceEventClasses.hpp

!else

$(TraceOutDir)/traceEventClasses.hpp: $(TraceAltSrcDir)/traceEventClasses.xsl $(XML_DEPS)
	@echo Generating AltSrc $@
	$(XSLT) -IN $(TraceXml) -XSL $(TraceAltSrcDir)/traceEventClasses.xsl -OUT $(TraceOutDir)/traceEventClasses.hpp

$(TraceOutDir)/traceRequestables.hpp: $(TraceAltSrcDir)/traceRequestables.xsl $(XML_DEPS)
	@echo Generating AltSrc $@
	$(XSLT) -IN $(TraceXml) -XSL $(TraceAltSrcDir)/traceRequestables.xsl -OUT $(TraceOutDir)/traceRequestables.hpp

$(TraceOutDir)/traceEventControl.hpp: $(TraceAltSrcDir)/traceEventControl.xsl $(XML_DEPS)
	@echo Generating AltSrc $@
	$(XSLT) -IN $(TraceXml) -XSL $(TraceAltSrcDir)/traceEventControl.xsl -OUT $(TraceOutDir)/traceEventControl.hpp

!endif

# #########################################################################

cleanall :
	rm $(TraceGeneratedFiles)
