#
# Copyright 1998-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

BUILDDIR  = ../../../..
PACKAGE   = javax.swing.plaf.nimbus
PRODUCT   = com
SWING_SRC = $(SHARE_SRC)/classes/javax/swing
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
NIMBUS_PKG = javax/swing/plaf/nimbus
NIMBUS_COMPAT_PKG = com/sun/java/swing/plaf/nimbus
NIMBUS_GENSRC_DIR = $(GENSRCDIR)/$(NIMBUS_PKG)
NIMBUS_SKIN_FILE = $(SHARE_SRC)/classes/$(NIMBUS_PKG)/skin.laf
NIMBUS_GENERATOR_JAR = $(BUILDTOOLJARDIR)/nimbus_generator.jar

AUTO_FILES_JAVA_DIRS = $(NIMBUS_PKG) $(NIMBUS_COMPAT_PKG)


#
# Rules
#
CLASSES_INIT = $(NIMBUS_GENSRC_DIR)

include $(BUILDDIR)/common/Classes.gmk

$(NIMBUS_GENSRC_DIR): $(NIMBUS_SKIN_FILE) $(NIMBUS_GENERATOR_JAR)
	@$(ECHO) "Generating Nimbus source files:"
	$(BOOT_JAVA_CMD) -jar $(NIMBUS_GENERATOR_JAR) \
	    -skinFile $(NIMBUS_SKIN_FILE) \
	    -buildDir $(GENSRCDIR) -srcDir $(GENSRCDIR) \
	    -packagePrefix $(PACKAGE) -lafName Nimbus
	@$(ECHO) "Finished generating Nimbus source files"

clean clobber::
	$(RM) -r $(NIMBUS_GENSRC_DIR)
