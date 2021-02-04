#
# Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

RENDERPERF_CLASSES = $(CLASSES)/renderperf/RenderPerfTest.class
RENDERPERFLCD_CLASSES = $(CLASSES)/renderperf/RenderPerfLCDTest.class
RENDERPERF_SOURCES = $(SOURCEPATH)/renderperf/RenderPerfTest.java
RENDERPERFLCD_SOURCES = $(SOURCEPATH)/renderperf/RenderPerfLCDTest.java

RENDERPERF_RESOURCES = $(CLASSES)/renderperf/images/duke.png

all: mkdirs $(DIST)/RenderPerfTest.jar $(DIST)/RenderPerfLCDTest.jar

run: mkdirs $(DIST)/RenderPerfTest.jar
	java -jar $(DIST)/RenderPerfTest.jar

$(DIST)/RenderPerfTest.jar: \
	$(RENDERPERF_CLASSES) $(RENDERPERF_RESOURCES) \
	$(CLASSES)/renderperf.manifest
	jar cvmf $(CLASSES)/renderperf.manifest $(DIST)/RenderPerfTest.jar -C $(CLASSES) .

$(DIST)/RenderPerfLCDTest.jar: \
	$(RENDERPERFLCD_CLASSES) $(RENDERPERFLCD_RESOURCES) \
	$(CLASSES)/renderperflcd.manifest
	jar cvmf $(CLASSES)/renderperflcd.manifest $(DIST)/RenderPerfLCDTest.jar -C $(CLASSES) .

$(CLASSES)/renderperf/images/%: $(RESOURCES)/renderperf/images/%
	cp -r $< $@


$(CLASSES)/renderperf.manifest:
	echo "Main-Class: renderperf.RenderPerfTest" > $@

$(CLASSES)/renderperflcd.manifest:
	echo "Main-Class: renderperf.RenderPerfLCDTest" > $@

$(DIST):
	mkdir  $(DIST)

$(CLASSES):
	mkdir $(CLASSES)
	mkdir -p $(CLASSES)/renderperf/images

mkdirs: $(DIST) $(CLASSES)

$(RENDERPERF_CLASSES): $(RENDERPERF_SOURCES)
	javac -g:none -d $(CLASSES) -sourcepath $(SOURCEPATH) $<

$(RENDERPERFLCD_CLASSES): $(RENDERPERFLCD_SOURCES)
	javac -g:none -d $(CLASSES) -sourcepath $(SOURCEPATH) $<

clean:
	rm -rf $(CLASSES)
	rm -rf $(DIST)
