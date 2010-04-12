#
# Copyright 1996-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
# Makefile for JDBC-ODBC Bridge Driver 
#
# Note - the native library for the bridge may be linked with the 
# shared library for the ODBC driver manager.  Or dummy libraries
# may be used to just create libJdbcOdbc.so with a dependency on
# libodbc.so and libodbcinst.so.
#

BUILDDIR = ../..
MODULE  = jdbc-odbc
PACKAGE = sun.jdbc.odbc
LIBRARY = JdbcOdbc
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#

AUTO_FILES_JAVA_DIRS = sun/jdbc/odbc

FILES_c = \
	JdbcOdbc.c

FILES_export = \
    sun/jdbc/odbc/JdbcOdbc.java 

# Use fake libraries on Solaris and Linux just so the library we create has
#   a dependency on these two library names. (which are not part of the jdk)
ifneq ($(PLATFORM), windows)
  # In jdk5 and jdk6, and on Solaris 32bit, we would have required that
  #    these two libraries exist at: $(ALT_ODBCDIR)/ISLIodbc/2.11/lib
  #    In jdk7, we just fake them out like we did on Linux in jdk5 and jdk6.
  #
  # If you wanted to use the real odbc libraries, change the value of
  #    ODBC_LIBRARY_LOCATION, and delete the variable assignments below.
  #
  # Tell linker to ignore missing externals when building this shared library.
  LDFLAGS_DEFS_OPTION = -z nodefs
  # Define a place to create the fake libraries and their names.
  ODBC_LIBRARY_LOCATION = $(TEMPDIR)
  ODBC_FAKE_LIBRARIES = $(ODBC_LIBRARY_LOCATION)/libodbcinst.so $(ODBC_LIBRARY_LOCATION)/libodbc.so
  # Make sure they get created early.
  INIT += $(ODBC_FAKE_LIBRARIES)
endif

#
# Rules
#
include $(BUILDDIR)/common/Library.gmk

#
# The UNIX define specifies conditional compilation for UNIX
#
ifeq ($(PLATFORM), windows)
  LDLIBS += odbc32.lib odbccp32.lib
else
  CFLAGS += -DUNIX
  LDFLAGS += -L$(ODBC_LIBRARY_LOCATION) -lodbcinst -lodbc
endif

#
# The native code for the bridge uses conditional compilation to 
# support Solaris, Win95 and Mac PPC. This is the path to the shared C files
# (which unfortunately are in the same directory as shared Java files).
#
vpath %.c $(CLOSED_SHARE_SRC)/classes/sun/jdbc/odbc

#
# Rules to generate fake libraries
#

ifdef ODBC_FAKE_LIBRARIES
$(TEMPDIR)/dummyodbc.c: 
	@$(prep-target)
	$(ECHO) "void dummyOdbc(void){}" >> $@
$(TEMPDIR)/dummyodbc.o: $(TEMPDIR)/dummyodbc.c
	@$(prep-target)
	$(COMPILE.c) $(CC_OBJECT_OUTPUT_FLAG)$@ $(CFLAGS_GPROF) $<
$(ODBC_FAKE_LIBRARIES): $(TEMPDIR)/dummyodbc.o
	@$(prep-target)
	$(CC) $(SHARED_LIBRARY_FLAG) $(LDFLAGS_COMMON) -o $@ $< $(EXTRA_LIBS)
clean::
	$(RM) -f $(ODBC_FAKE_LIBRARIES)
	$(RM) -f $(TEMPDIR)/dummyodbc.c
	$(RM) -f $(TEMPDIR)/dummyodbc.o
endif

#
# Files that need to be copied
#
SERVICEDIR = $(CLASSBINDIR)/META-INF/services

FILES_copy = \
	$(SERVICEDIR)/java.sql.Driver


FILES_mkdirs = \
	$(CLASSBINDIR)/META-INF \
	$(CLASSBINDIR)/META-INF/services

FILES_copydirs = \
	$(CLASSBINDIR) \
	$(FILES_mkdirs)

build: copy-files

copy-files: $(FILES_copy)

$(SERVICEDIR)/%: $(CLOSED_SHARE_SRC)/classes/sun/jdbc/odbc/META-INF/services/%
	$(install-file)

clean clobber::
	$(RM) $(FILES_copy)

.PHONY: copy-files

