#
# Copyright (c) 2000, 2005, Oracle and/or its affiliates. All rights reserved.
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

# This Makefile is intended to produce new reordering files for the
# reordering of jar files and shared libraries.  This is not part of the
# standard build.  The objects produced by this Makefile must be copied
# into their standard locations and checked in.

BUILDDIR = ../..
PACKAGE = tools.reorder
PRODUCT = tools
include $(BUILDDIR)/common/Defs.gmk

OUTDIR = $(OUTPUTDIR)/reorder
PRELOAD_DIR = $(ABS_OUTPUTDIR)/reorder/$(ARCH)

ifeq ($(PLATFORM), windows)
SEP = \\
else
SEP = /
endif

CLASSDIR = $(OUTDIR)/classes
OBJDIR = $(OUTDIR)/$(ARCH)
TESTS = Null Exit Hello Sleep IntToString \
	LoadToolkit LoadFrame LoadJFrame JHello
TEST_CLASSES = $(TESTS:%=$(CLASSDIR)/%.class)
SWINGSET2 = $(OUTPUTDIR)/demo/jfc/SwingSet2/SwingSet2.jar

# Run java from the JRE image because using rt.jar requires a
# larger list of classes, and these should appear in the list.
#   FIXUP: adding rt.jar into bootclasspath?
JRE_LIB = $(JRE_IMAGE_DIR)/lib
BOOTPATH = -Xbootclasspath:$(JRE_LIB)/rt.jar:$(JRE_LIB)/charsets.jar
JRE_JAVA = $(JRE_IMAGE_DIR)/bin/java

JAVA_BIN = $(BINDIR)/java

all : jars.reorder libs.reorder

tools : $(OBJDIR) $(OBJDIR)/libmcount.so $(OBJDIR)/remove_mcount

copy : jars.copy libs.copy

clean : 
	$(RM) $(REORDER_JAR)

clobber : clean
	$(RM) -r $(OUTDIR)


jars.reorder :
	$(MAKE) JARFILE=rt.jar   reorder.jar
	$(MAKE) JARFILE=charsets.jar reorder.jar

jars.copy:
	$(CP) $(OUTDIR)/reorder_rt_jar-$(PLATFORM)    reorder_rt_jar-$(PLATFORM)
	$(CP) $(OUTDIR)/reorder_charsets_jar-$(PLATFORM)  reorder_charsets_jar-$(PLATFORM)

REORDER_JAR = $(OUTDIR)/reorder_$(subst .,_,$(JARFILE))-$(PLATFORM)
reorder.jar : $(REORDER_JAR)

libs.reorder :
ifeq ($(PLATFORM), solaris)
	$(MAKE) LIBBLDDIR=java/zip        LIBTMPDIR=sun/java.util.zip/zip   reorder.lib
	$(MAKE) LIBBLDDIR=java/hpi/native LIBTMPDIR=java/hpi/native_threads reorder.lib
	$(MAKE) LIBBLDDIR=java/java       LIBTMPDIR=java/java.lang/java     reorder.lib
	$(MAKE) LIBBLDDIR=java/nio        LIBTMPDIR=java/java.nio/nio       reorder.lib
	$(MAKE) LIBBLDDIR=sun/font        LIBTMPDIR=sun/sun.awt.font/fontmanager reorder.lib
	$(MAKE) LIBBLDDIR=sun/jpeg        LIBTMPDIR=sun/sun.awt/jpeg        reorder.lib
	$(MAKE) LIBBLDDIR=java/verify     LIBTMPDIR=java/verify             reorder.lib
endif

libs.copy:
ifeq ($(PLATFORM), solaris)
	$(CP) $(OUTDIR)/reorder_java_zip-$(ARCH) ../../java/zip/reorder-$(ARCH)
	$(CP) $(OUTDIR)/reorder_java_hpi_native-$(ARCH) ../../java/hpi/native/reorder-$(ARCH)
	$(CP) $(OUTDIR)/reorder_java_java-$(ARCH) ../../java/java/reorder-$(ARCH)
	$(CP) $(OUTDIR)/reorder_sun_font-$(ARCH) ../../sun/font/reorder-$(ARCH)
	$(CP) $(OUTDIR)/reorder_sun_jpeg-$(ARCH) ../../sun/jpeg/reorder-$(ARCH)
	$(CP) $(OUTDIR)/reorder_java_verify-$(ARCH) ../../java/verify/reorder-$(ARCH)
endif

REORDER_LIB = $(OUTDIR)/reorder_$(subst /,_,$(LIBBLDDIR))-$(ARCH)
reorder.lib : $(OBJDIR) $(REORDER_LIB)


# This target 'test_classes' also used by hotspot reordering.
test_classes : $(TEST_CLASSES)

# This target 'tool_classes' also used by hotspot reordering.
tool_classes : $(CLASSDIR) $(CLASSDIR)/Combine.class $(CLASSDIR)/MaxTime.class

$(REORDER_JAR) : $(CLASSDIR) test_classes tool_classes
	$(RM) $(REORDER_JAR)

	@$(ECHO) "# Ordered list of files to include in $(JARFILE)." > $(REORDER_JAR)
	@$(ECHO) "# This is a generated file - do not edit."	    >> $(REORDER_JAR)
	@$(ECHO) "#"						    >> $(REORDER_JAR)

	@# Run each of a set of tests, extract the classes required and
	@# append the new classes to the ClassList.

	@for f in $(TESTS) ; do						  \
	    $(ECHO) Running test $$f.;					  \
	    $(RM) $(REORDER_JAR)_tmp1;					  \
	    echo "# Test $$f" >> $(REORDER_JAR);			  \
	    $(JRE_JAVA) -verbose -classpath $(CLASSDIR) $$f |		  \
	      $(SED) -n -e 's=\[Loaded \(.*\) from .*$(SEP)lib$(SEP)$(JARFILE)]=\1=p' | \
	      $(SED) -e 's=\.=/=g' -e 's=$$=.class='			  \
		 > $(REORDER_JAR)_tmp1;					  \
	    $(JRE_JAVA) -classpath $(CLASSDIR) Combine			  \
		$(REORDER_JAR) $(REORDER_JAR)_tmp1			  \
		 > $(REORDER_JAR)_tmp2;					  \
	    $(MV) $(REORDER_JAR)_tmp2 $(REORDER_JAR);			  \
	    $(RM) $(REORDER_JAR)_tmp1;					  \
	done

	@# Run SwingSet2, extract the classes required and
	@# append the new classes to the ClassList.

	@$(RM) $(REORDER_JAR)_tmp1
	@$(RM) $(REORDER_JAR)_tmp2
	@$(ECHO) "# SwingSet2" >> $(REORDER_JAR)

	@$(ECHO) ""
	@$(ECHO) ""
	@$(ECHO) "When SwingSet2 has finished drawing, " \
		 "you may terminate it (with your mouse)."
	@$(ECHO) "Otherwise, it should be automatically terminated in 2 minutes."
	@$(ECHO) ""
	@$(ECHO) ""

	$(JRE_JAVA) -verbose -classpath $(CLASSDIR) MaxTime $(SWINGSET2) 120 \
		 > $(REORDER_JAR)_tmp2 

	$(SED) -n -e 's=\[Loaded \(.*\) from .*$(SEP)lib$(SEP)$(JARFILE)]=\1=p'	   \
	    < $(REORDER_JAR)_tmp2 |					   \
	$(SED) -e 's=\.=/=g' -e 's=$$=.class=' > $(REORDER_JAR)_tmp1

	$(RM) $(REORDER_JAR)_tmp2
	$(JRE_JAVA) -classpath $(CLASSDIR) Combine			  \
	    $(REORDER_JAR) $(REORDER_JAR)_tmp1 > $(REORDER_JAR)_tmp2
	$(MV) $(REORDER_JAR)_tmp2 $(REORDER_JAR)
	$(RM) $(REORDER_JAR)_tmp1

$(CLASSDIR) :
	$(MKDIR) -p $(CLASSDIR)

$(CLASSDIR)/%.class : tools/%.java
	$(JAVAC_CMD) -d $(CLASSDIR) $<
$(CLASSDIR)/%.class : tests/%.java
	$(JAVAC_CMD) -d $(CLASSDIR) $<

$(OBJDIR) :
	$(MKDIR) -p $(OBJDIR)


MCOUNT_SRC = mcount.c
MCOUNT_OBJ = $(MCOUNT_SRC:%.c=$(OBJDIR)/%.o)
vpath %.c tools

$(OBJDIR)/%.o : %.c tools/util-$(ARCH).il
	$(COMPILE.c) $(CC_OBJECT_OUTPUT_FLAG)$@ $^

# This library 'libmcount.so' also used by hotspot reordering.
$(OBJDIR)/libmcount.so : $(MCOUNT_OBJ)
	$(CC) -G -mt -zdefs -o $@ $^ -ldl -lelf $(EXTRA_LIBS)

# This library 'libmcount.so' also used by hotspot reordering.
$(OBJDIR)/remove_mcount : remove_mcount.c
	$(CC) -g -o $@ $^ -ldl -lelf $(EXTRA_LIBS)


# Reorder libraries.
# + Delete all of the library's object files, and recompile them for profiling.
# + Run the test sequence using libmcount to produce the mapfile.
# + Delete all of the library's object files, and recompile them normally.

$(REORDER_LIB): $(OBJDIR)/libmcount.so $(OBJDIR)/remove_mcount \
		test_classes tool_classes

ifeq ($(ARCH), i586)
	@# On Solaris-i586 we need to remove the symbol _mcount from the command
	$(OBJDIR)/remove_mcount $(BINDIR)/java
endif

	$(RM) $(OUTPUTDIR)/tmp/$(LIBTMPDIR)/$(OBJDIRNAME)/*.o
	$(CD) $(BUILDDIR)/$(LIBBLDDIR) ; \
	  $(MAKE) PARTIAL_GPROF=true LDNOMAP=true NO_ROBOT=true all

	$(RM) $(REORDER_LIB)
	@$(ECHO) "data = R0x2000;" > $(REORDER_LIB)
	@$(ECHO) "text = LOAD ?RXO;" >> $(REORDER_LIB)

	@# Run each of a set of tests, extract the classes required and
	@# append the new classes to the ClassList.

	for f in $(TESTS) ; do						  \
	    $(ECHO) Running test $$f.;					  \
	    $(RM) $(REORDER_LIB)_tmp1;					  \
	    $(ECHO) "# Test $$f" >> $(REORDER_LIB);			  \
	    LD_PRELOAD=$(PRELOAD_DIR)/libmcount.so			  \
	      $(SETENV)							  \
	      $(JAVA_BIN) $(BOOTPATH) -classpath $(CLASSDIR) $$f 	  \
		 2> $(REORDER_LIB)_tmp1;				  \
	    $(SED) -n -e 's=[./]*$(OUTPUTDIR)=OUTPUTDIR=' -e '/^text:/p'  \
		 $(REORDER_LIB)_tmp1 > $(REORDER_LIB)_tmp2;		  \
	    $(SED) -e '/^text:/d' $(REORDER_LIB)_tmp1;	 		  \
	    $(JRE_JAVA) -classpath $(CLASSDIR) Combine			  \
		$(REORDER_LIB) $(REORDER_LIB)_tmp2			  \
		 > $(REORDER_LIB)_tmp3;					  \
	    $(MV) $(REORDER_LIB)_tmp3 $(REORDER_LIB);			  \
	    $(RM) $(REORDER_LIB)_tmp2;					  \
	    $(RM) $(REORDER_LIB)_tmp1;					  \
	done

	@# Run SwingSet2, extract the classes required and
	@# append the new classes to the ClassList.

	@$(RM) $(REORDER_LIB)_tmp1
	@$(RM) $(REORDER_JAR)_tmp2
	@$(ECHO) "# SwingSet2" >> $(REORDER_LIB)

	@$(ECHO) ""
	@$(ECHO) ""
	@$(ECHO) "When SwingSet2 has finished drawing, " \
		 "you may terminate it (with your mouse)."
	@$(ECHO) "Otherwise, it should be automatically terminated in 2 minutes."
	@$(ECHO) ""
	@$(ECHO) ""

	LD_PRELOAD=$(PRELOAD_DIR)/libmcount.so				   \
	  $(SETENV)							   \
	  $(JAVA_BIN) $(BOOTPATH) -classpath $(CLASSDIR) MaxTime	   \
		$(SWINGSET2) 120  2> $(REORDER_LIB)_tmp1

	$(SED) -n -e 's=[./]*$(OUTPUTDIR)=OUTPUTDIR=' -e '/^text:/p'	  \
	    $(REORDER_LIB)_tmp1 > $(REORDER_LIB)_tmp2
	$(SED) -e '/^text:/d' $(REORDER_LIB)_tmp1
	$(JRE_JAVA) -classpath $(CLASSDIR) Combine			  \
	    $(REORDER_LIB) $(REORDER_LIB)_tmp2				  \
	     > $(REORDER_LIB)_tmp3
	$(MV) $(REORDER_LIB)_tmp3 $(REORDER_LIB)
	$(RM) $(REORDER_LIB)_tmp2
	$(RM) $(REORDER_LIB)_tmp1

	$(RM) $(OUTPUTDIR)/tmp/$(LIBTMPDIR)/$(OBJDIRNAME)/*.o
	$(CD) $(BUILDDIR)/$(LIBBLDDIR) ; $(MAKE) all

.PHONY: test_classes tool_classes
