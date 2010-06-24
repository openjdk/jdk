#
# Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

PACKAGE=com/sun/kenc/util

CLASSES=LogWrapperBase IndentingPrintWriter StringUtil
SOURCE=$(CLASSES:%=$(PACKAGE)/%.java)
OBJECT=$(CLASSES:%=$(PACKAGE)/%.class)

MCROOTS= Activation Interceptors IOR Naming ORBUtil POA Util OMG
MCFILES=$(MCROOTS:%=data/%.mc)
MCSOURCE=$(MCROOTS:%=$(PACKAGE)/%SystemException.java)
MCCLASS=$(MCROOTS:%=$(PACKAGE)/%SystemException.class)
MCRESOURCE=$(MCROOTS:%=resources/%SystemException.resource)

JARDIR=lib
UTILJAR=$(JARDIR)/util.jar
JSCHEMEJAR=$(JARDIR)/jscheme.jar
JARS=$(UTILJAR):$(JSCHEMEJAR)

all: mkdir jar resources classes 

mkdir : 
	@-mkdir resources

clean:
	@-rm $(OBJECT) $(MCSOURCE) $(MCOBJECT) $(MCCLASS) $(MCRESOURCE) $(UTILJAR)

test:
	@echo "PACKAGE	    :" $(PACKAGE)
	@echo "CLASSES	    :" $(CLASSES)
	@echo "SOURCE	    :" $(SOURCE)
	@echo "OBJECT	    :" $(OBJECT)
	@echo "MCROOTS	    :" $(MCROOTS)
	@echo "MCFILES	    :" $(MCFILES)
	@echo "MCCLASS	    :" $(MCCLASS)
	@echo "MCRESOURCE   :" $(MCRESOURCE)
	
jar: $(UTILJAR)

$(UTILJAR) : $(OBJECT)
	jar cvf $(JARDIR)/util.jar $(OBJECT)

$(OBJECT) : $(SOURCE)

classes: $(MCCLASS)

$(MCCLASS) : $(MCSOURCE)

$(MCSOURCE) : $(MCFILES)

resources: $(MCRESOURCE)

$(MCRESOURCE) : $(MCFILES)

# This target does not compile, because the generated source code requires
# com.sun.corba.se.impl.util.SUNVMCID, which would normally be built in the
# ee package.  This should not be a problem once everything is merged into the
# workspace.
genclasses: $(MCCLASS)

$(PACKAGE)/%SystemException.java : data/%.mc
	./mc make-class $<
	mv *SystemException.java $(PACKAGE)

resources/%SystemException.resource : data/%.mc
	./mc make-resource $<
	mv *.resource resources

$(PACKAGE)/%.class : $(PACKAGE)/%.java
	javac -classpath $(JARS) $<
