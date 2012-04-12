#
# Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

include $(SPEC)
include MakeBase.gmk
include JavaCompilation.gmk

default: all

# The BOOT_JAVAC setup uses the bootdir compiler to compile the tools
# and the bootstrap javac, to be run by the bootdir jvm.
$(eval $(call SetupJavaCompiler,BOOT_JAVAC,\
	JAVAC:=$(JAVAC),\
        FLAGS:=-Xprefer:source -XDignore.symbol.file=true -g,\
        MODE:=SINGLE_THREADED_BATCH))

# Now setup the compilation of the properties compilation tool. You can depend
# upon $(BUILD_TOOLS) to trigger a compilation of the tools. Note that we
# add src/share/classes to the sourcepath. This is necessary since the GenStubs
# program needs to be linked and run towards the new javac sources.
$(eval $(call SetupJavaCompilation,BUILD_TOOLS,\
		SETUP:=BOOT_JAVAC,\
		SRC:=$(LANGTOOLS_TOPDIR)/make/tools $(LANGTOOLS_TOPDIR)/src/share/classes,\
		INCLUDES:=compileproperties genstubs,\
		BIN:=$(LANGTOOLS_OUTPUTDIR)/btclasses))

# The compileprops tools compiles a properties file into a resource bundle.
TOOL_COMPILEPROPS_CMD:=$(JAVA) -cp $(LANGTOOLS_OUTPUTDIR)/btclasses compileproperties.CompileProperties -quiet
# Lookup the properties that need to be compiled into resource bundles.
PROPSOURCES:=$(shell find $(LANGTOOLS_TOPDIR)/src/share/classes -name "*.properties")
# Strip away prefix and suffix, leaving for example only: "com/sun/tools/javac/resources/javac_zh_CN"
PROPPATHS:=$(patsubst $(LANGTOOLS_TOPDIR)/src/share/classes/%.properties,%,$(PROPSOURCES))
# Generate the list of java files to be created.
PROPJAVAS:=$(patsubst %,$(LANGTOOLS_OUTPUTDIR)/gensrc/%.java,$(PROPPATHS))
# Generate the package dirs for the tobe generated java files.
PROPDIRS:=$(dir $(PROPJAVAS))
# Now generate a sequence of "-compile ...javac_zh_CN.properties ...javac_zh_CN.java java.util.ListResourceBundle"
# suitable to be fed into the CompileProperties command.
PROPCMDLINE:=$(subst _SPACE_,$(SPACE),$(join $(addprefix -compile_SPACE_,$(PROPSOURCES)), \
		$(addsuffix _SPACE_java.util.ListResourceBundle,$(addprefix _SPACE_$(LANGTOOLS_OUTPUTDIR)/gensrc/,$(addsuffix .java,$(PROPPATHS))))))

# Now setup the rule for the generation of the resource bundles.
$(LANGTOOLS_OUTPUTDIR)/gensrc/_the_props.d : $(PROPSOURCES) $(BUILD_TOOLS)
	rm -rf $(@D)/*
	mkdir -p $(@D) $(PROPDIRS)
	printf "jdk=$(JDK_VERSION)\nfull=$(FULL_VERSION)\nrelease=$(RELEASE)\n" > $(LANGTOOLS_OUTPUTDIR)/gensrc/com/sun/tools/javah/resources/version.properties
	printf "jdk=$(JDK_VERSION)\nfull=$(FULL_VERSION)\nrelease=$(RELEASE)\n" > $(LANGTOOLS_OUTPUTDIR)/gensrc/com/sun/tools/javap/resources/version.properties
	printf "jdk=$(JDK_VERSION)\nfull=$(FULL_VERSION)\nrelease=$(RELEASE)\n" > $(LANGTOOLS_OUTPUTDIR)/gensrc/com/sun/tools/javac/resources/version.properties
	echo Compiling $(words $(PROPSOURCES) v1 v2 v3) properties into resource bundles
	$(TOOL_COMPILEPROPS_CMD) $(PROPCMDLINE) \
		-compile 	$(LANGTOOLS_OUTPUTDIR)/gensrc/com/sun/tools/javah/resources/version.properties \
				$(LANGTOOLS_OUTPUTDIR)/gensrc/com/sun/tools/javah/resources/version.java \
				java.util.ListResourceBundle \
		-compile 	$(LANGTOOLS_OUTPUTDIR)/gensrc/com/sun/tools/javap/resources/version.properties \
				$(LANGTOOLS_OUTPUTDIR)/gensrc/com/sun/tools/javap/resources/version.java \
				java.util.ListResourceBundle \
		-compile	$(LANGTOOLS_OUTPUTDIR)/gensrc/com/sun/tools/javac/resources/version.properties \
				$(LANGTOOLS_OUTPUTDIR)/gensrc/com/sun/tools/javac/resources/version.java \
				java.util.ListResourceBundle
	echo PROPS_ARE_CREATED=yes > $@

# Trigger the generation of the resource bundles. After the resource bundles have
# been compiled, then the makefile will restart and the newly created java files
# will become part of the build further along in the makefile.
-include $(LANGTOOLS_OUTPUTDIR)/gensrc/_the_props.d

ifeq ($(PROPS_ARE_CREATED),yes)
        # Setup the rules to build a dist/bootstrap/lib/javac.jar, ie a smaller intermediate javac
        # that can be compiled with an old javac. The intermediate javac is then used
        # to compile javac again and to build the complete new jdk.
        $(eval $(call SetupJavaCompilation,BUILD_BOOTSTRAP_LANGTOOLS,\
		SETUP:=BOOT_JAVAC,\
		SRC:=$(LANGTOOLS_TOPDIR)/src/share/classes $(LANGTOOLS_OUTPUTDIR)/gensrc,\
		EXCLUDES:=com/sun/tools/javac/nio,\
		BIN:=$(LANGTOOLS_OUTPUTDIR)/btclasses/bootstrap))

        $(eval $(call SetupArchive,ARCHIVE_BOOTSTRAP_JAVAC,$(BUILD_BOOTSTRAP_LANGTOOLS),\
		SRCS:=$(LANGTOOLS_OUTPUTDIR)/btclasses/bootstrap,\
		JAR:=$(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javac.jar,\
		JARMAIN:=com.sun.tools.javac.Main))

        $(eval $(call SetupArchive,ARCHIVE_BOOTSTRAP_JAVAH,$(BUILD_BOOTSTRAP_LANGTOOLS),\
		SRCS:=$(LANGTOOLS_OUTPUTDIR)/btclasses/bootstrap,\
		JAR:=$(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javah.jar,\
		JARMAIN:=com.sun.tools.javah.Main))

        $(eval $(call SetupArchive,ARCHIVE_BOOTSTRAP_JAVAP,$(BUILD_BOOTSTRAP_LANGTOOLS),\
		SRCS:=$(LANGTOOLS_OUTPUTDIR)/btclasses/bootstrap,\
		JAR:=$(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javap.jar,\
		JARMAIN:=com.sun.tools.javap.Main))

        $(eval $(call SetupArchive,ARCHIVE_BOOTSTRAP_JAVADOC,$(BUILD_BOOTSTRAP_LANGTOOLS),\
		SRCS:=$(LANGTOOLS_OUTPUTDIR)/btclasses/bootstrap,\
		JAR:=$(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javadoc.jar,\
		JARMAIN:=com.sun.tools.javadoc.Main))

        # GenStubs is used to bootstrap any dependencies from javac to the new JDK that is not 
        # yet built. It is currently not needed but might be again in the future. The following
        # exercises the functionality to verify that it works.
        TOOL_GENSTUBS_CMD=$(JAVA) \
             "-Xbootclasspath/p:$(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javac.jar" \
             -cp "$(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javac.jar$(PATH_SEP)$(LANGTOOLS_OUTPUTDIR)/btclasses" \
             genstubs.GenStubs
        # We fetch source from the JDK...
        JDKS=$(JDK_TOPDIR)/src/share/classes
        # Build the list of classes to generate stubs from. java/util/Objects.java isn't
        # currently needed, but is used as a demo for now.
	 STUBSOURCES:=$(shell $(FIND) $(JDKS) -name "*.java" | $(GREP) \
		    -e "$(JDKS)/java/util/Objects.java")
        # Rewrite the file names into class names because the GenStubs tool require this.
        STUBCLASSES:=$(subst /,.,$(patsubst $(JDKS)/%.java,%,$(STUBSOURCES)))

        # Now setup the build recipe for genstubs.
        $(LANGTOOLS_OUTPUTDIR)/genstubs/_the_stubs.d : $(STUBSOURCES) $(BUILD_TOOLS) \
				$(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javac.jar \
				$(LANGTOOLS_OUTPUTDIR)/gensrc/_the_props.d
	       	mkdir -p $(@D)
		mkdir -p $(LANGTOOLS_OUTPUTDIR)/tmpstubs
	       	echo Generating stubs from JDK sources.
	       	($(TOOL_GENSTUBS_CMD) -s $(LANGTOOLS_OUTPUTDIR)/tmpstubs -sourcepath $(JDKS) $(STUBCLASSES) && echo STUBS_ARE_CREATED=yes > $@)
		if $(DIFF) -x "_the*" -rq $(LANGTOOLS_OUTPUTDIR)/tmpstubs $(LANGTOOLS_OUTPUTDIR)/genstubs > /dev/null 2>&1; then \
			echo No changes in the stubs!; \
			rm -rf $(LANGTOOLS_OUTPUTDIR)/tmpstubs; \
		else \
			echo Changes in stubs detected!; \
			rm -rf $(@D); \
			mv $(LANGTOOLS_OUTPUTDIR)/tmpstubs $(@D); \
		fi
		echo STUBS_ARE_CREATED=yes > $@

        # Trigger a generation of the genstubs java source code and a restart
        # of the makefile to make sure that the following build setup use the 
        # newly created java files.
        -include $(LANGTOOLS_OUTPUTDIR)/genstubs/_the_stubs.d

	ifeq ($(STUBS_ARE_CREATED),yes)
                # Setup a compiler configuration using the intermediate javac in dist/bootstrap/lib/javac.jar
                # that generates code for the new jdk that is being built.
                # The code compiled by this compiler setup, cannot necessarily be run with the bootstrap jvm.
                $(eval $(call SetupJavaCompiler,GENERATE_NEWBYTECODE,\
                  JVM:=$(JAVA),\
                  JAVAC:="-Xbootclasspath/p:$(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javac.jar" \
			 -jar $(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javac.jar,\
                  FLAGS:=-Xprefer:source -XDignore.symbol.file=true,\
                  SERVER_DIR:=$(JAVAC_SERVERS),\
                  SERVER_JVM:=$(SERVER_JAVA),\
                  MODE:=$(JAVAC_USE_MODE),\
		  USE_DEPS:=$(JAVAC_USE_DEPS)))

                # javax.tools.JavaCompilerTool isn't really a suffix but this gets the file copied.
		RESOURCE_SUFFIXES:=.gif .xml .css javax.tools.JavaCompilerTool

                $(eval $(call SetupJavaCompilation,BUILD_FULL_JAVAC,\
			SETUP:=GENERATE_NEWBYTECODE,\
			SRC:=$(LANGTOOLS_TOPDIR)/src/share/classes $(LANGTOOLS_OUTPUTDIR)/gensrc \
			     $(LANGTOOLS_OUTPUTDIR)/genstubs,\
			EXCLUDES:=java/util java/io java/nio,\
			COPY:=$(RESOURCE_SUFFIXES),\
			BIN:=$(LANGTOOLS_OUTPUTDIR)/classes))

                $(eval $(call SetupArchive,ARCHIVE_FULL_JAVAC,$(BUILD_FULL_JAVAC),\
			SETUP:=GENERATE_NEWBYTECODE,\
			SRCS:=$(LANGTOOLS_OUTPUTDIR)/classes,\
			SUFFIXES:=.class $(RESOURCE_SUFFIXES),\
			JAR:=$(LANGTOOLS_OUTPUTDIR)/dist/lib/classes.jar,\
			HEADERS:=$(LANGTOOLS_OUTPUTDIR)/gensrc_headers))

                $(eval $(call SetupZipArchive,ZIP_FULL_JAVAC_SOURCE,\
			SRC:=$(LANGTOOLS_TOPDIR)/src/share/classes $(LANGTOOLS_OUTPUTDIR)/gensrc,\
			ZIP:=$(LANGTOOLS_OUTPUTDIR)/dist/lib/src.zip))

                all: 		$(LANGTOOLS_OUTPUTDIR)/dist/lib/classes.jar \
				$(LANGTOOLS_OUTPUTDIR)/dist/lib/src.zip \
	                 	$(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javac.jar \
				$(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javah.jar \
				$(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javap.jar \
				$(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javadoc.jar


        endif
endif
