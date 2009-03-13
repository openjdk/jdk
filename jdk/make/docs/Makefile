#
# Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# Definitions for $(DOCSDIR), $(MKDIR), $(BINDIR), etc.
#
BUILDDIR=..
PRODUCT=docs
include $(BUILDDIR)/common/Defs.gmk

# We override whatever the max VM memory setting is here.
#    NOTE: javadoc will not complete without these larger settings.
#    WARNING: This could cause thrashing on low memory machines.
ifeq ($(ARCH_DATA_MODEL),64)
  MAX_VM_MEMORY = 1024
else
  MAX_VM_MEMORY = 512
endif

#
# Variables used by docs target
#
DOCSTMPDIR          = $(TEMPDIR)/doctmp

COMMON_JAVADOCFLAGS =					\
		$(NO_PROPRIETARY_API_WARNINGS)		\
		-source 1.5				\
		-quiet					\
                -use					\
                -keywords				\
		$(JAVADOC_VM_MEMORY_FLAGS)		\
		$(ADDITIONAL_JAVADOCFLAGS)
ADDITIONAL_JAVADOCFLAGS =

CORE_JAVADOCFLAGS  = $(COMMON_JAVADOCFLAGS)		\
		$(TAGS)					\
		-encoding ISO-8859-1			\
		-splitIndex				\
		-doctitle $(DOCTITLE_SWITCH)		\
		-windowtitle $(WINDOWTITLE_SWITCH)	\
		-header $(HEADER_SWITCH)		\
		$(TOPOPTION) 				\
		-bottom $(JAVADOCBOTTOM_SWITCH)		\
		$(OVERVIEW_OPTION)

DRAFT = '<br><strong>DRAFT&nbsp;'$(MILESTONE)-$(BUILD_NUMBER)'</strong>'
THIS_YEAR := $(shell $(DATE) | $(SED) -e 's/  / /g' | $(CUT) -d' ' -f6)
TRADEMARK = &\#x2122;

IGNORED_TAGS =	beaninfo revised since.unbundled spec Note ToDo

JLS3_URL = http://java.sun.com/docs/books/jls/
JLS3_CITE = <a href="$(JLS3_URL)"> \
		The Java Language Specification, Third Edition</a>
TAG_JLS3 = -tag 'jls3:a:See <cite>$(JLS3_CITE)</cite>:'

TAGS = $(IGNORED_TAGS:%=-tag %:X) $(TAG_JLS3)

ifeq ($(MILESTONE), fcs)
    DOCTITLE_SWITCH = $(JAVADOCTITLE)
    WINDOWTITLE_SWITCH = $(JAVADOCWINDOWTITLE)
    HEADER_SWITCH = $(JAVADOCHEADER)
    TOPOPTION=
    JAVADOCBOTTOM_SWITCH= $(JAVADOCBOTTOM)
    OVERVIEW_OPTION = -overview $(JAVADOCOVERVIEW)
else
    DOCTITLE_SWITCH = $(JAVADOCTITLE_EARLYACCESS)$(DRAFT)
    WINDOWTITLE_SWITCH = $(JAVADOCWINDOWTITLE)" $(BUILD_NUMBER)"
    HEADER_SWITCH = $(JAVADOCHEADER)$(DRAFT)
    JAVADOCBOTTOM_SWITCH= $(JAVADOCBOTTOM_EARLYACCESS)
    TOPOPTION= -top $(JAVADOCTOP_EARLYACCESS)
    OVERVIEW_OPTION =
endif

JAVADOCTITLE  = 'Java$(TRADEMARK) Platform, Standard Edition $(JDK_MINOR_VERSION)<br>API Specification'
JAVADOCWINDOWTITLE  = 'Java Platform SE $(JDK_MINOR_VERSION)'
JAVADOCHEADER = '<strong>Java$(TRADEMARK)&nbsp;Platform<br>Standard&nbsp;Ed.&nbsp;$(JDK_MINOR_VERSION)</strong>'
JAVADOCBOTTOM = '<font size="-1"><a href="http://bugs.sun.com/services/bugreport/index.jsp">Submit a bug or feature</a><br>For further API reference and developer documentation, see <a href="{@docroot}/../../webnotes/devdocs-vs-specs.html">Java SE Developer Documentation</a>. That documentation contains more detailed, developer-targeted descriptions, with conceptual overviews, definitions of terms, workarounds, and working code examples. <p>Copyright $(THIS_YEAR) Sun Microsystems, Inc. All Rights Reserved. Use is subject to <a href="{@docroot}/../legal/license.html">license terms</a>. Also see the <a href="http://java.sun.com/docs/redist.html">documentation redistribution policy</a>.</font>'
JAVADOCOVERVIEW = $(SHARE_SRC)/classes/overview-core.html

#
# Early access top and bottom text (for snapshots, beta and rc)
#
JAVADOCTOP_EARLYACCESS = '<div style="background-color: \#EEEEEE"> <div style="padding: 6px; margin-top: 2px; margin-bottom: 6px; margin-left: 6px; margin-right: 6px; text-align: justify; font-size: 80%; font-family: Helvetica, Arial, sans-serif; font-weight: normal;"> Please note that this documentation is not final and is subject to change. </div> </div>'
JAVADOCBOTTOM_EARLYACCESS = '<font size="-1"><a href="http://bugs.sun.com/services/bugreport/index.jsp">Submit a bug or feature</a> <p>Copyright $(THIS_YEAR) Sun Microsystems, Inc. All Rights Reserved. Use is subject to <a href="{@docroot}/../legal/license.html">license terms</a>.</font>'
JAVADOCTITLE_EARLYACCESS = $(subst Specification,Documentation,$(JAVADOCTITLE))

#
# Variables used by domapidocs target
#

DOMAPI_JAVADOCFLAGS = $(COMMON_JAVADOCFLAGS)		      \
		    -encoding ascii			      \
                    -splitIndex                               \
                    -doctitle $(DOMAPI_JAVADOCTITLE)          \
                    -windowtitle $(DOMAPI_JAVADOCWINDOWTITLE) \
                    -header $(DOMAPI_JAVADOCHEADER)           \
                    -bottom $(DOMAPI_JAVADOCBOTTOM)           \
                    -group $(DOMAPI_GROUPNAME) $(DOMAPI_REGEXP)
DOMAPI_JAVADOCTITLE  = 'Common DOM API'
DOMAPI_JAVADOCWINDOWTITLE  = 'Common DOM API'
DOMAPI_JAVADOCHEADER = '<strong>Common DOM API</strong>'
DOMAPI_JAVADOCBOTTOM = '<font size="-1"><a href="http://bugs.sun.com/services/bugreport/index.jsp">Submit a bug or feature</a><br>Java is a trademark or registered trademark of Sun Microsystems, Inc. in the US and other countries.<br>Copyright $(THIS_YEAR) Sun Microsystems, Inc. 4150 Network Circle<br>Santa Clara, California, 95054, U.S.A.  All Rights Reserved.</font>'
DOMAPI_GROUPNAME = "Packages"
DOMAPI_REGEXP = "com.sun.java.browser.dom:org.w3c.dom*"
# DOMAPI_PKGS is located in NON_CORE_PKGS.gmk

#
# Variables used by mirrordocs target
#

MIRROR_JAVADOCFLAGS = $(COMMON_JAVADOCFLAGS)		        \
                    -encoding ascii			        \
                    -doctitle $(MIRROR_JAVADOCTITLE)            \
                    -windowtitle $(MIRROR_JAVADOCWINDOWTITLE)   \
                    -header $(MIRROR_JAVADOCHEADER)             \
                    -bottom $(MIRROR_JAVADOCBOTTOM)             \
                    -group $(MIRROR_GROUPNAME) $(MIRROR_REGEXP) \
                    -overview $(MIRROR_OVERVIEW)
MIRROR_JAVADOCTITLE = 'Mirror API'
MIRROR_JAVADOCWINDOWTITLE = 'Mirror API'
MIRROR_JAVADOCHEADER = '<strong>Mirror API</strong>'
MIRROR_JAVADOCBOTTOM = '<font size="-1"><a href="http://bugs.sun.com/services/bugreport/index.jsp">Report a bug or request a feature.</a><br>Copyright $(THIS_YEAR) Sun Microsystems, Inc. All Rights Reserved. Use is subject to license terms.</font>'
MIRROR_GROUPNAME = "Packages"
MIRROR_OVERVIEW = $(IMPORTSRCDIR)/com/sun/mirror/overview.html
MIRROR_REGEXP = "com.sun.mirror.*"
MIRROR_DESTDIR = $(DOCSDIR)/jdk/api/apt/mirror
MIRROR_LINKOPT = -linkoffline ../../../../api $(DOCSDIR)/api/
# MIRROR_PKGS is located in NON_CORE_PKGS.gmk

#
# Variables used by docletapidocs target
#

DOCLETAPI_JAVADOCFLAGS = $(COMMON_JAVADOCFLAGS)			 \
		    -breakiterator				 \
		    -encoding ascii			         \
                    -doctitle $(DOCLETAPI_JAVADOCTITLE)          \
                    -windowtitle $(DOCLETAPI_JAVADOCWINDOWTITLE) \
                    -header $(DOCLETAPI_JAVADOCHEADER)           \
                    -bottom $(DOCLETAPI_JAVADOCBOTTOM)           \
                    -group $(DOCLETAPI_GROUPNAME) $(DOCLETAPI_REGEXP)
DOCLETAPI_JAVADOCTITLE  = 'Doclet API'
DOCLETAPI_JAVADOCWINDOWTITLE  = 'Doclet API'
DOCLETAPI_JAVADOCHEADER = '<strong>Doclet API</strong>'
DOCLETAPI_JAVADOCBOTTOM = '<font size="-1"><a href="http://bugs.sun.com/services/bugreport/index.jsp">Submit a bug or feature</a><br>Java is a trademark or registered trademark of Sun Microsystems, Inc. in the US and other countries.<br>Copyright 1993-$(THIS_YEAR) Sun Microsystems, Inc. 4150 Network Circle<br>Santa Clara, California, 95054, U.S.A.  All Rights Reserved.</font>'
DOCLETAPI_GROUPNAME = "Packages"
DOCLETAPI_REGEXP = "com.sun.javadoc"
DOCLETAPI_LINKOPT = -linkoffline ../../../../api $(DOCSDIR)/api/
# DOCLETAPI_PKGS is located in NON_CORE_PKGS.gmk

#
# Variables used by tagletapidocs target
#

TAGLETAPI_JAVADOCFLAGS = $(COMMON_JAVADOCFLAGS)			\
		    -encoding ascii			        \
                    -nonavbar                                   \
                    -noindex                                    \
                    -bottom $(TAGLETAPI_JAVADOCBOTTOM)
TAGLETAPI_JAVADOCBOTTOM = '<font size="-1"><a href="http://bugs.sun.com/services/bugreport/index.jsp">Submit a bug or feature</a><br>Java is a trademark or registered trademark of Sun Microsystems, Inc. in the US and other countries.<br>Copyright 1993-$(THIS_YEAR) Sun Microsystems, Inc. 4150 Network Circle<br>Santa Clara, California, 95054, U.S.A.  All Rights Reserved.</font>'
# TAGLETAPI_FILE is located in NON_CORE_PKGS.gmk

#
# Variables used by jdi target
#

JPDA_SOURCEPATH  = $(TOPDIR)/src/share/classes

JDI_JAVADOCFLAGS = $(COMMON_JAVADOCFLAGS)			 \
		   -encoding ascii			         \
                   -nodeprecatedlist                             \
                   -d $(DOCSDIR)/jdk/api/jpda/jdi                  \
                   -sourcepath $(JPDA_SOURCEPATH)                \
                   -windowtitle $(JDI_WINDOWTITLE)               \
                   -doctitle $(JDI_DOCTITLE)                     \
                   -header $(JDI_HEADER)                         \
                   -linkoffline ../../../../api $(DOCSDIR)/api/     \
                   -overview $(JPDA_SOURCEPATH)/jdi-overview.html
JDI_WINDOWTITLE = "Java Debug Interface"
JDI_DOCTITLE    = "Java$(TRADEMARK) Debug Interface"
JDI_HEADER      = "Java Debug Interface"
# JDI_PKGS is located in NON_CORE_PKGS.gmk

# Variables used by security components
SECURITYAPI_JAVADOCBOTTOM = '<font size="-1"><a href="http://bugs.sun.com/services/bugreport/index.jsp">Report a bug or request a feature.</a><br>Copyright $(THIS_YEAR) Sun Microsystems, Inc. All Rights Reserved. Use is subject to license terms.</font>'

#
# Variables used by JAAS target
#
# NOTE: Quotes are required around sourcepath argument only on Windows.  Otherwise,
#       you get "No packages or classes specified." due to $(CLASSPATH_SEPARATOR)
#       being interpreted as an end of command (newline)

JAAS_SOURCEPATH  = "$(TOPDIR)/src/share/classes$(CLASSPATH_SEPARATOR)$(TOPDIR)/src/solaris/classes$(CLASSPATH_SEPARATOR)$(TOPDIR)/src/windows/classes$(CLASSPATH_SEPARATOR)$(TOPDIR)/src/linux/classes"
JAAS_DOCDIR	 = $(DOCSDIR)/jre/api/security/jaas/spec
JAAS_JAVADOCFLAGS = $(COMMON_JAVADOCFLAGS)			 \
		   -encoding ascii			         \
                   -nodeprecatedlist                             \
                   -d $(JAAS_DOCDIR)				 \
                   -sourcepath $(JAAS_SOURCEPATH)                \
                   -windowtitle $(JAAS_WINDOWTITLE)              \
                   -doctitle $(JAAS_DOCTITLE)                    \
                   -header $(JAAS_JAVADOCHEADER)                 \
                   -bottom $(SECURITYAPI_JAVADOCBOTTOM)                      \
                   -linkoffline ../../../../../api $(DOCSDIR)/api/	 \
                   -overview $(TOPDIR)/src/share/classes/com/sun/security/auth/jaas-overview.html
JAAS_WINDOWTITLE = "Java Authentication and Authorization Service "
JAAS_DOCTITLE    = "Java$(TRADEMARK) Authentication and Authorization Service"
JAAS_JAVADOCHEADER      = "JAAS"
# JAAS_PKGS is located in NON_CORE_PKGS.gmk

#
# Variables used by JGSS target
#

JGSS_SOURCEPATH  = $(TOPDIR)/src/share/classes
JGSS_DOCDIR	 = $(DOCSDIR)/jre/api/security/jgss/spec

JGSS_JAVADOCFLAGS = $(COMMON_JAVADOCFLAGS)			\
		   -encoding ascii			        \
                   -nodeprecatedlist                            \
                   -d $(JGSS_DOCDIR)				\
                   -sourcepath $(JGSS_SOURCEPATH)               \
                   -windowtitle $(JGSS_WINDOWTITLE)             \
                   -doctitle $(JGSS_DOCTITLE)                   \
                   -header $(JGSS_JAVADOCHEADER)                \
                   -bottom $(SECURITYAPI_JAVADOCBOTTOM)                      \
                   -linkoffline ../../../../../api $(DOCSDIR)/api/ \
                   -overview $(JGSS_SOURCEPATH)/com/sun/security/jgss/jgss-overview.html

JGSS_WINDOWTITLE = "Java GSS-API Utilities "
JGSS_DOCTITLE    = "Java$(TRADEMARK) GSS-API Utilities"
JGSS_JAVADOCHEADER      = "Java GSS-API Utilities"
# JGSS_PKGS is located in NON_CORE_PKGS.gmk

#
# Variables used by SMARTCARDIO target
#

SMARTCARDIO_SOURCEPATH  = $(TOPDIR)/src/share/classes
SMARTCARDIO_DOCDIR	 = $(DOCSDIR)/jre/api/security/smartcardio/spec

SMARTCARDIO_JAVADOCFLAGS = $(COMMON_JAVADOCFLAGS)			\
		   -encoding ascii			        \
                   -nodeprecatedlist                            \
                   -d $(SMARTCARDIO_DOCDIR)				\
                   -sourcepath $(SMARTCARDIO_SOURCEPATH)               \
                   -windowtitle $(SMARTCARDIO_WINDOWTITLE)             \
                   -doctitle $(SMARTCARDIO_DOCTITLE)                   \
                   -header $(SMARTCARDIO_JAVADOCHEADER)                \
                   -bottom $(SECURITYAPI_JAVADOCBOTTOM)                      \
                   -linkoffline ../../../../../api $(DOCSDIR)/api/

SMARTCARDIO_WINDOWTITLE = "Java Smart Card I/O"
SMARTCARDIO_DOCTITLE    = "Java$(TRADEMARK) Smart Card I/O"
SMARTCARDIO_JAVADOCHEADER      = "Java Smart Card I/O"
# SMARTCARDIO_PKGS is located in NON_CORE_PKGS.gmk

#
# Variables used by TRACING target
#

TRACING_SOURCEPATH  = $(TOPDIR)/src/share/classes
TRACING_DOCDIR	 = $(DOCSDIR)/jre/api/tracing

TRACING_JAVADOCFLAGS = $(COMMON_JAVADOCFLAGS)			    \
		   -encoding ascii			            \
                   -nodeprecatedlist                                \
                   -d $(TRACING_DOCDIR)				    \
                   -sourcepath $(TRACING_SOURCEPATH)                \
                   -windowtitle $(TRACING_WINDOWTITLE)              \
                   -doctitle $(TRACING_DOCTITLE)                    \
                   -header $(TRACING_JAVADOCHEADER)                 \
                   -linkoffline ../../../../../api $(DOCSDIR)/api/

TRACING_WINDOWTITLE = "Tracing"
TRACING_DOCTITLE    = "Java$(TRADEMARK) Platform Tracing"
TRACING_JAVADOCHEADER      = "Platform Tracing"
# TRACING_PKGS is located in NON_CORE_PKGS.gmk

#
# Variables used by HTTPSERVER target
#

HTTPSERVER_SOURCEPATH  = $(TOPDIR)/src/share/classes
HTTPSERVER_DOCDIR	 = $(DOCSDIR)/jre/api/net/httpserver/spec

HTTPSERVER_JAVADOCFLAGS = $(COMMON_JAVADOCFLAGS)			\
		   -encoding ascii			        \
                   -nodeprecatedlist                            \
                   -d $(HTTPSERVER_DOCDIR)				\
                   -sourcepath $(HTTPSERVER_SOURCEPATH)               \
                   -windowtitle $(HTTPSERVER_WINDOWTITLE)             \
                   -doctitle $(HTTPSERVER_DOCTITLE)                   \
                   -header $(HTTPSERVER_JAVADOCHEADER)                \
                   -linkoffline ../../../../../api $(DOCSDIR)/api/

HTTPSERVER_WINDOWTITLE = "Java HTTP Server"
HTTPSERVER_DOCTITLE    = "Java$(TRADEMARK) HTTP Server"
HTTPSERVER_JAVADOCHEADER      = "Java HTTP Server"
# HTTPSERVER_PKGS is located in NON_CORE_PKGS.gmk

#
# Variables used by jvmti target
#
JVMTI_DOCS_SUBDIR = platform/jvmti
JVMTI_HTML        = $(HOTSPOT_DOCS_IMPORT_PATH)/$(JVMTI_DOCS_SUBDIR)/jvmti.html

#
# Variables used by mgmt target
#
MGMT_DOCDIR	   = $(DOCSDIR)/jre/api/management/
MGMT_EXT_DIR	   = $(MGMT_DOCDIR)/extension
MGMT_SOURCEPATH    = $(TOPDIR)/src/share/classes
JVM_MIB_NAME       = JVM-MANAGEMENT-MIB.mib
JVM_MIB_SRC        = $(CLOSED_SRC)/share/classes/sun/management/snmp/$(JVM_MIB_NAME)

ifdef OPENJDK
   COPY-MIB-TARGET =
else
   COPY-MIB-TARGET = copy-mib
endif
MGMT_JAVADOCFLAGS  = $(COMMON_JAVADOCFLAGS)			   \
		     -encoding ascii			           \
                     -nodeprecatedlist                             \
                     -d $(MGMT_EXT_DIR) 		  	   \
                     -sourcepath $(MGMT_SOURCEPATH)                \
                     -windowtitle $(MGMT_WINDOWTITLE)              \
                     -doctitle $(MGMT_DOCTITLE)                    \
                     -header $(MGMT_HEADER)                        \
		     -linkoffline ../../../../api $(DOCSDIR)/api/  \
                     -overview $(MGMT_SOURCEPATH)/com/sun/management/mgmt-overview.html
MGMT_WINDOWTITLE = "Monitoring and Management Interface for the Java Platform"
MGMT_DOCTITLE    = "Monitoring and Management Interface for the Java$(TRADEMARK) Platform"
MGMT_HEADER      = "Monitoring and Management Interface for the Java Platform"
# MGMT_PKGS is located in NON_CORE_PKGS.gmk

#
# Variables used by attach target
#

ATTACH_SOURCEPATH  = $(TOPDIR)/src/share/classes
ATTACH_DOCDIR      = $(DOCSDIR)/jdk/api/attach/spec
ATTACH_JAVADOCFLAGS = $(COMMON_JAVADOCFLAGS)                        	\
		   -encoding ascii                               	\
                   -nodeprecatedlist                             	\
		   -d $(ATTACH_DOCDIR)					\
                   -sourcepath $(ATTACH_SOURCEPATH)                	\
                   -windowtitle $(ATTACH_WINDOWTITLE)               	\
                   -doctitle $(ATTACH_DOCTITLE)                     	\
                   -header $(ATTACH_HEADER)                         	\
		   -linkoffline ../../../../api $(DOCSDIR)/api/
ATTACH_WINDOWTITLE = "Attach API"
ATTACH_DOCTITLE    = "Attach API"
ATTACH_HEADER      = "Attach API"
# ATTACH_PKGS is located in NON_CORE_PKGS.gmk

#
# Variables used by jconsole target
#

JCONSOLE_SOURCEPATH  = $(TOPDIR)/src/share/classes
JCONSOLE_DOCDIR      = $(DOCSDIR)/jdk/api/jconsole/spec
JCONSOLE_JAVADOCFLAGS = $(COMMON_JAVADOCFLAGS)                        	\
		   -encoding ascii                               	\
                   -nodeprecatedlist                             	\
		   -d $(JCONSOLE_DOCDIR)				\
                   -sourcepath $(JCONSOLE_SOURCEPATH)                	\
                   -windowtitle $(JCONSOLE_WINDOWTITLE)               	\
                   -doctitle $(JCONSOLE_DOCTITLE)                     	\
                   -header $(JCONSOLE_HEADER)                         	\
		   -linkoffline ../../../../api $(DOCSDIR)/api/
JCONSOLE_WINDOWTITLE = "JConsole API"
JCONSOLE_DOCTITLE    = "JConsole API"
JCONSOLE_HEADER      = "JConsole API"
# JCONSOLE_PKGS is located in NON_CORE_PKGS.gmk

#
# Variables used by treeapidocs target
#

TREEAPI_JAVADOCFLAGS = $(COMMON_JAVADOCFLAGS)		        \
                    -encoding ascii			        \
                    -doctitle $(TREEAPI_JAVADOCTITLE)            \
                    -windowtitle $(TREEAPI_JAVADOCWINDOWTITLE)   \
                    -header $(TREEAPI_JAVADOCHEADER)             \
                    -bottom $(TREEAPI_JAVADOCBOTTOM)             \
                    -group $(TREEAPI_GROUPNAME) $(TREEAPI_REGEXP) 
#
#	            -overview $(TREEAPI_OVERVIEW)
#
TREEAPI_JAVADOCTITLE = 'Compiler Tree API'
TREEAPI_JAVADOCWINDOWTITLE = 'Compiler Tree API'
TREEAPI_JAVADOCHEADER = '<strong>Compiler Tree API</strong>'
TREEAPI_JAVADOCBOTTOM = '<font size="-1"><a href="http://bugs.sun.com/services/bugreport/index.jsp">Report a bug or request a feature.</a><br>Copyright $(THIS_YEAR) Sun Microsystems, Inc. All Rights Reserved. Use is subject to license terms.</font>'
TREEAPI_GROUPNAME = "Packages"
TREEAPI_OVERVIEW = $(SHARE_SRC)/classes/com/sun/source/overview.html
TREEAPI_REGEXP = "com.sun.source.*"
TREEAPI_DESTDIR = $(DOCSDIR)/jdk/api/javac/tree
TREEAPI_LINKOPT = -linkoffline ../../../../api $(DOCSDIR)/api/
# TREEAPI_PKGS is located in NON_CORE_PKGS.gmk

#
# Path where javadoc should find source files for release docs
#
RELEASEDOCS_SRCPATH = "$(SHARE_SRC)/classes$(CLASSPATH_SEPARATOR)$(PLATFORM_SRC)/classes$(CLASSPATH_SEPARATOR)$(GENSRCDIR)$(CLASSPATH_SEPARATOR)$(SHARE_SRC)/doc/stub$(CLASSPATH_SEPARATOR)$(CLOSED_SRC)/share/classes$(CLASSPATH_SEPARATOR)$(IMPORTSRCDIR)"

#
# CORE_PKGS environment variable has been moved to the following file
#
include CORE_PKGS.gmk

#
# Load environment variables for API package names that are not part of
# the Java SE platform
#
include NON_CORE_PKGS.gmk

# Targets for all APIs other than the core platform APIs
ALL_OTHER_TARGETS = \
    mirrordocs \
    docletapidocs \
    tagletapidocs \
    domapidocs \
    jpdadocs \
    jaasdocs \
    jgssdocs \
    smartcardiodocs \
    tracingdocs \
    httpserverdocs  \
    mgmtdocs \
    attachdocs \
    jconsoledocs \
    treeapidocs

.PHONY: all docs
all docs: coredocs otherdocs

.PHONY: otherdocs
otherdocs: ${ALL_OTHER_TARGETS}


#################################################################
# Production Targets -- USE THESE TARGETS WHEN:
#    a) You're generating docs outside of release engineering's
#       standard control build.
#    b) The docs will be pushed to the web and/or included in
#       the downloaded doc bundle.
#
# See:  Notes.html#releaseTargets
# Note: Spaces preceed ifdef/ifndef indents. Tabs preceed target commands (!)
#
.PHONY: sanitycheckcoredocs
sanitycheckcoredocs:
	@$(ECHO) ""
	@$(ECHO) "Building core api docs with these values:"
	@$(ECHO) "    BUILD_NUMBER = $(BUILD_NUMBER)"
	@$(ECHO) "    MILESTONE = $(MILESTONE)"
	@$(ECHO) ""
  ifeq ($(BUILD_NUMBER), b00)
	@$(ECHO) "ERROR: Build number must be defined"
	@$(ECHO) "MILESTONE is set to $(MILESTONE)"
	@$(ECHO) ""
	exit 1
  endif

# Maximize performance and ensure that build number & milestone are set.
.PHONY: rel-coredocs
rel-coredocs: sanitycheckcoredocs
	@# ######## release version of core packages ########
	$(MAKE) coredocs

.PHONY: rel-docs
rel-docs: rel-coredocs ${ALL_OTHER_TARGETS}
#
# end of production targets
#############################################################

.PHONY: coredocs
coredocs:
	@# ######## core packages #######################
	$(RM) -r $(DOCSDIR)/api
	$(MKDIR) -p $(DOCSDIR)/api
	$(JAVADOC_CMD) $(CORE_JAVADOCFLAGS)            \
                   -d $(DOCSDIR)/api                   \
                   -sourcepath $(RELEASEDOCS_SRCPATH)  \
                   $(CORE_PKGS)

.PHONY: mirrordocs
mirrordocs:
	@# ######## mirror api for apt ##################
	$(RM) -r $(MIRROR_DESTDIR)
	$(MKDIR) -p $(MIRROR_DESTDIR)
	$(JAVADOC_CMD) $(MIRROR_JAVADOCFLAGS)             \
		   -d $(MIRROR_DESTDIR)               \
		   -sourcepath $(RELEASEDOCS_SRCPATH) \
		   $(MIRROR_LINKOPT)                  \
		   $(MIRROR_PKGS)

.PHONY: docletapidocs
docletapidocs:
	@# ######## doclet api ############################
	$(RM) -r $(DOCSDIR)/jdk/api/javadoc/doclet
	$(MKDIR) -p $(DOCSDIR)/jdk/api/javadoc/doclet
	$(JAVADOC_CMD) $(DOCLETAPI_JAVADOCFLAGS)                 \
                   -d $(DOCSDIR)/jdk/api/javadoc/doclet      \
                   -sourcepath $(RELEASEDOCS_SRCPATH)        \
                   $(DOCLETAPI_LINKOPT)                      \
                   $(DOCLETAPI_PKGS)

.PHONY: tagletapidocs
tagletapidocs:
	@# ######## taglet api ############################
	$(RM) -r $(DOCSDIR)/jdk/api/javadoc/taglet
	$(MKDIR) -p $(DOCSDIR)/jdk/api/javadoc/taglet
	$(RM) -r $(DOCSTMPDIR)
	$(MKDIR) -p $(DOCSTMPDIR)
	$(JAVADOC_CMD) $(TAGLETAPI_JAVADOCFLAGS)                 \
                   -d $(DOCSTMPDIR)                          \
                   -linkoffline ../../../../api $(DOCSDIR)/api/ \
                   $(IMPORTSRCDIR)/$(TAGLETAPI_FILE)
	cp -r $(DOCSTMPDIR)/com $(DOCSDIR)/jdk/api/javadoc/taglet
	cp $(DOCSTMPDIR)/stylesheet.css $(DOCSDIR)/jdk/api/javadoc/taglet
	$(RM) -r $(DOCSTMPDIR)

.PHONY: domapidocs
domapidocs:
	@# ######## dom api ############################
	$(RM) -r $(DOCSDIR)/jre/api/plugin/dom
	$(MKDIR) -p $(DOCSDIR)/jre/api/plugin/dom
	$(JAVADOC_CMD) $(DOMAPI_JAVADOCFLAGS)		     \
                   -d $(DOCSDIR)/jre/api/plugin/dom            \
                   -sourcepath $(RELEASEDOCS_SRCPATH)        \
                   -linkoffline ../../../../api $(DOCSDIR)/api/ \
                   $(DOMAPI_PKGS)

.PHONY: jpdadocs
jpdadocs: jdidocs jdwpdocs jvmtidocs

.PHONY: jdidocs
jdidocs:
	@# ######## jdi #################################
	$(RM) -r $(DOCSDIR)/jdk/api/jpda/jdi
	$(MKDIR) -p $(DOCSDIR)/jdk/api/jpda/jdi
	$(JAVADOC_CMD) $(JDI_JAVADOCFLAGS)                       \
                   $(JDI_PKGS)

JDWP_SPEC       = $(BUILDDIR)/jpda/jdwp/jdwp.spec
JDWP_DOC        = $(DOCSDIR)/platform/jpda/jdwp/jdwp-protocol.html
JDWPGEN_JARFILE = $(BUILDTOOLJARDIR)/jdwpgen.jar

.PHONY: jdwpdocs
jdwpdocs: $(JDWP_DOC)

$(JDWP_DOC): $(JDWPGEN_JARFILE) $(JDWP_SPEC)
	$(prep-target)
	$(BOOT_JAVA_CMD) -jar $(JDWPGEN_JARFILE) $(JDWP_SPEC) -doc $(JDWP_DOC)

.PHONY: jvmtidocs
jvmtidocs:
	@# ######## jvmti #################################
	@if [ -f $(JVMTI_HTML) ] ; then \
	  $(RM) -r $(DOCSDIR)/$(JVMTI_DOCS_SUBDIR); \
	  $(MKDIR) -p $(DOCSDIR)/$(JVMTI_DOCS_SUBDIR); \
	  $(ECHO) $(CP) $(JVMTI_HTML) $(DOCSDIR)/$(JVMTI_DOCS_SUBDIR); \
	  $(CP) $(JVMTI_HTML) $(DOCSDIR)/$(JVMTI_DOCS_SUBDIR); \
	else \
	  $(ECHO) "WARNING: Generated jvmti file does not exist: $(JVMTI_HTML)"; \
	fi

.PHONY: jaasdocs
jaasdocs:
	@# ######## api-jaas ############################
	$(RM) -r $(JAAS_DOCDIR)
	$(MKDIR) -p $(JAAS_DOCDIR)
	$(JAVADOC_CMD) $(JAAS_JAVADOCFLAGS)                     \
		   $(JAAS_PKGS)

.PHONY: jgssdocs
jgssdocs:
	@# ######## api-jgss ############################
	$(RM) -r $(JGSS_DOCDIR)
	$(MKDIR) -p $(JGSS_DOCDIR)
	$(JAVADOC_CMD) $(JGSS_JAVADOCFLAGS)                     \
		   $(JGSS_PKGS)

.PHONY: smartcardiodocs
smartcardiodocs:
	@# ######## api-smartcardio ############################
	$(RM) -r $(SMARTCARDIO_DOCDIR)
	$(MKDIR) -p $(SMARTCARDIO_DOCDIR)
	$(JAVADOC_CMD) $(SMARTCARDIO_JAVADOCFLAGS)                     \
		   $(SMARTCARDIO_PKGS)

.PHONY: tracingdocs
tracingdocs:
	@# ######## api-tracing ############################
	$(RM) -r $(TRACING_DOCDIR)
	$(MKDIR) -p $(TRACING_DOCDIR)
	$(JAVADOC) $(TRACING_JAVADOCFLAGS)                     \
		   $(TRACING_PKGS)

.PHONY: httpserverdocs
httpserverdocs:
	@# ######## api-httpserver #######################
	$(RM) -r $(HTTPSERVER_DOCDIR)
	$(MKDIR) -p $(HTTPSERVER_DOCDIR)
	$(JAVADOC_CMD) $(HTTPSERVER_JAVADOCFLAGS)                     \
		   $(HTTPSERVER_PKGS)

.PHONY: mgmtdocs
mgmtdocs: $(COPY-MIB-TARGET)
	@# ######## api-management ############################
	$(RM) -r $(MGMT_EXT_DIR)
	$(MKDIR) -p $(MGMT_EXT_DIR)
	$(JAVADOC_CMD) $(MGMT_JAVADOCFLAGS)                     \
		   $(MGMT_PKGS)

copy-mib:
	@# ######## copy-snmp-mib ############################
	$(RM) $(MGMT_DOCDIR)/$(JVM_MIB_NAME)
	$(MKDIR) -p $(MGMT_DOCDIR)
	$(CP) $(JVM_MIB_SRC) $(MGMT_DOCDIR)

.PHONY: attachdocs
attachdocs:
	@# ######## api-attach ############################
	$(RM) -r $(ATTACH_DOCDIR)
	$(MKDIR) -p $(ATTACH_DOCDIR)
	$(JAVADOC_CMD) $(ATTACH_JAVADOCFLAGS)                     \
		   $(ATTACH_PKGS)

.PHONY: jconsoledocs
jconsoledocs:
	@# ######## api-jconsole ############################
	$(RM) -r $(JCONSOLE_DOCDIR)
	$(MKDIR) -p $(JCONSOLE_DOCDIR)
	$(JAVADOC_CMD) $(JCONSOLE_JAVADOCFLAGS)                     \
		   $(JCONSOLE_PKGS)

.PHONY: treeapidocs
treeapidocs:
	@# ######## tree api for javac ##################
	$(RM) -r $(TREEAPI_DESTDIR)
	$(MKDIR) -p $(TREEAPI_DESTDIR)
	$(JAVADOC_CMD) $(TREEAPI_JAVADOCFLAGS)             \
		   -d $(TREEAPI_DESTDIR)               \
		   -sourcepath $(RELEASEDOCS_SRCPATH) \
		   $(TREEAPI_LINKOPT)                  \
		   $(TREEAPI_PKGS)

# DEBUG TARGET
# List the values defined in the makefile hierarchy, to make sure everything
# is set properly, and to help identify values we can use instead of making new ones.
# (Most of them come from common/shared/Defs.gmk)
#
#  Notes:
#    * BUILD_NUMBER defaults to b00 if not set on command line with BUILD_NUMBER=<value>
#    * MILESTONE defaults to internal unless set to beta, rc, or fcs on command line
#
.PHONY: echovalues
echovalues:
	@$(ECHO) ""
	@$(ECHO) --------------Shared---------------------------
	@$(ECHO) BUILD_NUMBER = $(BUILD_NUMBER)
	@$(ECHO) FULL_VERSION = $(FULL_VERSION)
	@$(ECHO) JDK_VERSION = $(JDK_VERSION)
	@$(ECHO) JDK_MAJOR_VERSION = $(JDK_MAJOR_VERSION)
	@$(ECHO) JDK_MINOR_VERSION = $(JDK_MINOR_VERSION)
	@$(ECHO) JDK_MICRO_VERSION = $(JDK_MICRO_VERSION)
	@$(ECHO) JDK_UPDATE_VERSION = $(JDK_UPDATE_VERSION)
	@$(ECHO) JDK_MKTG_VERSION = $(JDK_MKTG_VERSION)
	@$(ECHO) JDK_UNDERSCORE_VERSION = $(JDK_UNDERSCORE_VERSION)
	@$(ECHO) JDK_MKTG_UNDERSCORE_VERSION = $(JDK_MKTG_UNDERSCORE_VERSION)
	@$(ECHO) MARKETING_NUMBER = $(MARKETING_NUMBER)
	@$(ECHO) MARKET_NAME = $(MARKET_NAME)
	@$(ECHO) MILESTONE = $(MILESTONE)
	@$(ECHO) RELEASE = $(RELEASE)
	@$(ECHO) USER_RELEASE_SUFFIX = $(USER_RELEASE_SUFFIX)
	@$(ECHO) --------------Shared---------------------------
	@$(ECHO) ""
	@$(ECHO) --------------common/Defs---------------------------
	@$(ECHO) "RELEASEDOCS_SRCPATH"
	@$(ECHO) "   SHARE_SRC/classes: $(SHARE_SRC)/classes"
	@$(ECHO) "   PLATFORM_SRC/classes: $(PLATFORM_SRC)/classes"
	@$(ECHO) "   GENSRCDIR: $(GENSRCDIR)"
	@$(ECHO) "   IMPORTSRCDIR: $(IMPORTSRCDIR)"
	@$(ECHO) "   SHARE_SRC/doc/stub: $(SHARE_SRC)/doc/stub"
	@$(ECHO) --------------common/Defs---------------------------
	@$(ECHO) ""
