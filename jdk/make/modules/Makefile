#
# Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

BUILDDIR = ..
include $(BUILDDIR)/common/Defs.gmk

#
# Modularizing the JDK
# - Post jdk build process until the source tree is restructured
#   for modules build
# - <outputdir>/modules/<module> will be created for each module.
#
# Steps:
# 0. During jdk build before this makefile is invoked, classes, 
#    resource files, and other non-class files such as native libraries,
#    properties file, images, etc are created.
#
#    Non-class files are copied to <outputdir>/tmp/modules/<MODULE>
#    directory in this step to prepare for the post-build modularization. 
#
#    The MODULE variable defined in other makefiles specifies 
#    the lowest-level module that the non-class files belong to.
#    The name might or might not be the same as the name of the modules 
#    in the resulting <outputdir>/modules directory.
#    
# 1. Unpack all jars in the <builddir>/lib directory to a temporary 
#    location (<outputdir>/tmp/modules/classes) to prepare for modules
#    creation.
#
# 2. Run ClassAnalyzer tool to analyze all jdk classes and generate
#    class list for all modules and also perform dependency analysis. 
#
#    Input configuration files :-
#
#    modules.config : defines the low-level modules and specifies 
#       what classes and resource files each module includes.
#    modules.group  : defines the module groups and its members.
#    jdk7.depconfig : lists the dynamic dependencies including
#       use of reflection Class.forName and JNI FindClass and
#       service provider.
#    optional.depconfig : lists the optional dependencies
#
# 3. Create one directory for each module (<outputdir>/modules/<module>)
#    based on the output files from (2).
#       
#    modules.list lists the modules to be created for the modules
#    build and its members. For each module (m) in modules.list,
#    a. create $m/lib/$m.jar with all classes and resource files
#       listed in $m.classlist and $m.resources respectively.
#    b. copy all non-class files from its members to 
#       <outputdir>/modules/$m.


MAINMANIFEST=$(JDK_TOPDIR)/make/tools/manifest.mf
MODULE_JAR_MANIFEST_FILE=$(ABS_TEMPDIR)/manifest.tmp

TMP = $(ABS_MODULES_TEMPDIR)
MODULE_CLASSLIST = $(ABS_MODULES_TEMPDIR)/classlist
MODULE_CLASSES = $(ABS_MODULES_TEMPDIR)/classes
MODULES_LIST = $(MODULE_CLASSLIST)/modules.list

all:: unpack-jars gen-classlist modularize

JAR_LIST := $(shell $(FIND) $(ABS_OUTPUTDIR)/lib -name \*.jar -print) 
unpack-jars:
	@$(ECHO) ">>>Making "$@" @ `$(DATE)` ..."
	$(RM) -rf $(MODULE_CLASSES)
	$(MKDIR) -p $(MODULE_CLASSES)
	$(CP) -rf $(CLASSBINDIR)/* $(MODULE_CLASSES)
	@for jf in  $(JAR_LIST) ; do \
	   $(CD) $(MODULE_CLASSES) && $(BOOT_JAR_CMD) xf $$jf $(BOOT_JAR_JFLAGS);\
	done
	@$(ECHO) ">>>Finished making "$@" @ `$(DATE)` ..."

gen-classlist:
	$(CD) tools && $(MAKE) all

modularize: $(MODULE_JAR_MANIFEST_FILE)
	@$(ECHO) ">>>Making "$@" @ `$(DATE)` ..."
	@$(RM) -rf $(MODULES_DIR)

	@# create jar file for modules and
	@# copy other files from all members of this module 
	for m in `$(NAWK) '{print $$1}' $(MODULES_LIST)` ; do \
   	   $(ECHO) "Creating module $$m" ; \
   	   $(SED) -e 's%\\%\/%g' < $(MODULE_CLASSLIST)/$$m.classlist > $(TMP)/tmp.cf ; \
	   if [ -f $(MODULE_CLASSLIST)/$$m.resources ] ; then \
   	       $(SED) -e 's%\\%\/%g' < $(MODULE_CLASSLIST)/$$m.resources >> $(TMP)/tmp.cf ; \
           fi ; \
           $(MKDIR) -p $(ABS_MODULES_DIR)/$$m/lib; \
	   $(CD) $(MODULE_CLASSES) && \
               $(BOOT_JAR_CMD) c0mf $(MODULE_JAR_MANIFEST_FILE) \
		   $(ABS_MODULES_DIR)/$$m/lib/$$m.jar \
	           @$(TMP)/tmp.cf \
                   $(BOOT_JAR_JFLAGS); \
	   for s in `$(GREP) "^$$m" $(MODULES_LIST)` ; do \
               if [ -d $(TMP)/$$s ] ; then \
               	   $(CP) -rf $(TMP)/$$s/*  $(ABS_MODULES_DIR)/$$m; \
		   $(RM) -rf $(ABS_MODULES_DIR)/$$m/classes; \
	       fi \
	   done \
        done
	@$(CD) $(MODULE_CLASSES) && $(java-vm-cleanup)
	@$(ECHO) ">>>Finished making "$@" @ `$(DATE)` ..."

$(MODULE_JAR_MANIFEST_FILE):
	$(SED) -e "s/@@RELEASE@@/$(RELEASE)/" $(MAINMANIFEST) > $@

clean clobber::
	$(RM) -rf $(MODULE_CLASSLIST)
	$(RM) -rf $(MODULES_DIR)
	$(RM) $(MODULE_JAR_MANIFEST_FILE)
