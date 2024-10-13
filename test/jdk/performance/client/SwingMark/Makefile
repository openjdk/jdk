#
# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
#
#   - Redistributions of source code must retain the above copyright
#     notice, this list of conditions and the following disclaimer.
#
#   - Redistributions in binary form must reproduce the above copyright
#     notice, this list of conditions and the following disclaimer in the
#     documentation and/or other materials provided with the distribution.
#
#   - Neither the name of Oracle nor the names of its
#     contributors may be used to endorse or promote products derived
#     from this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
# IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
# THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
# PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
# CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
# PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
# PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
# LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#

SOURCEPATH=src
CLASSES=build
DIST=dist
RESOURCES=resources

SWINGMARK_CLASSES = \
	$(CLASSES)/AbstractSwingTest.class \
	$(CLASSES)/AdvancedTextAreaTest.class \
	$(CLASSES)/InternalFrameTest.class \
	$(CLASSES)/JMTest_01.class \
	$(CLASSES)/JMTest_02.class \
	$(CLASSES)/JMTest_03.class \
	$(CLASSES)/JMTest_04.class \
	$(CLASSES)/JMTest_05.class \
	$(CLASSES)/LabelTest.class \
	$(CLASSES)/ListTest.class \
	$(CLASSES)/MenuTest.class \
	$(CLASSES)/NullRunnable.class \
	$(CLASSES)/SliderTest.class \
	$(CLASSES)/SwingMark.class \
	$(CLASSES)/SwingMarkPanel.class \
	$(CLASSES)/TableColMoveTest.class \
	$(CLASSES)/TableColTest.class \
	$(CLASSES)/TableRowTest.class \
	$(CLASSES)/TableScrollTest.class \
	$(CLASSES)/TextAreaTest.class \
	$(CLASSES)/TextPaneTest.class \
	$(CLASSES)/TreeTest.class \
	$(CLASSES)/TypingTest.class \
        $(CLASSES)/resources/TableRowTest.class \
        $(CLASSES)/resources/TableRowTest_ja.class

SWINGMARK_RESOURCES = \
	$(CLASSES)/resources/TestList.txt \
	$(CLASSES)/resources/JMTest_04.properties \
	$(CLASSES)/resources/JMTest_04_ja.properties \
	$(CLASSES)/resources/ListTest.properties \
	$(CLASSES)/resources/ListTest_ja.properties \
	$(CLASSES)/resources/TextAreaTest.properties \
	$(CLASSES)/resources/TextAreaTest_ja.properties

all: mkdirs SwingMark.jar

run: mkdirs SwingMark.jar
	cd $(DIST) && java -jar SwingMark.jar -q

SwingMark.jar: \
	$(SWINGMARK_CLASSES) \
	$(SWINGMARK_RESOURCES) \
	$(CLASSES)/swingmark.manifest
	jar cvmf $(CLASSES)/swingmark.manifest $(DIST)/SwingMark.jar -C $(CLASSES) .

$(CLASSES)/swingmark.manifest:
	echo "Main-Class: SwingMark" > $@

$(DIST):
	mkdir $(DIST)

$(CLASSES):
	mkdir $(CLASSES)

mkdirs: $(DIST) $(CLASSES)

$(CLASSES)/%.class: $(SOURCEPATH)/%.java
	javac -d $(CLASSES) -sourcepath $(SOURCEPATH) $<

$(CLASSES)/resources/%.properties: $(SOURCEPATH)/resources/%.properties
	cp -r $< $@

$(CLASSES)/resources/TestList.txt: $(SOURCEPATH)/resources/TestList.txt
	cp -r $< $@
	
clean:
	rm -rf $(CLASSES)
	rm -rf $(DIST)
