#
# Makefile to run jtreg and any other tests
#

# Get OS/ARCH specifics
OSNAME = $(shell uname -s)
ifeq ($(OSNAME), SunOS)
  PLATFORM = solaris
  JT_PLATFORM = solaris
  ARCH = $(shell uname -p)
  ifeq ($(ARCH), i386)
    ARCH=i586
  endif
endif
ifeq ($(OSNAME), Linux)
  PLATFORM = linux
  JT_PLATFORM = linux
  ARCH = $(shell uname -m)
  ifeq ($(ARCH), i386)
    ARCH=i586
  endif
endif
ifeq ($(OSNAME), Windows_NT)
  PLATFORM = windows
  JT_PLATFORM = win32
  ifeq ($(word 1, $(PROCESSOR_IDENTIFIER)),ia64)
    ARCH=ia64
  else
    ifeq ($(word 1, $(PROCESSOR_IDENTIFIER)),AMD64)
      ARCH=x64
    else
      ifeq ($(word 1, $(PROCESSOR_IDENTIFIER)),EM64T)
        ARCH=x64
      else
        ARCH=i586
      endif
    endif
  endif
endif

# Root of this test area (important to use full paths in some places)
TEST_ROOT := $(shell pwd)

# Default bundle of all test results (passed or not)
JPRT_ARCHIVE_BUNDLE=$(TEST_ROOT)/JPRT_ARCHIVE_BUNDLE.zip

# Default home for JTREG
ifeq ($(PLATFORM), windows)
  SLASH_JAVA = J:
else
  SLASH_JAVA = /java
endif

# Default JTREG to run
JT_HOME = $(SLASH_JAVA)/svc/jct-tools3.2.2_02
JTREG = $(JT_HOME)/$(JT_PLATFORM)/bin/jtreg

# Default JDK for JTREG
ifdef JPRT_JAVA_HOME
  JT_JAVA = $(JPRT_JAVA_HOME)
else
  JT_JAVA = $(SLASH_JAVA)/re/jdk/1.6.0/archive/fcs/binaries/$(PLATFORM)-$(ARCH)
endif

# Default JDK to test
TESTJAVA = $(SLASH_JAVA)/re/jdk/1.7.0/promoted/latest/binaries/$(PLATFORM)-$(ARCH)
TESTBOOTCLASSPATH = $(PRODUCT_HOME)/dist/lib/classes.jar

# The test directories to run
DEFAULT_TESTDIRS = .
TESTDIRS = $(DEFAULT_TESTDIRS)

# Root of all test results
TEST_OUTPUT_DIR = $(TEST_ROOT)/o_$(PLATFORM)-$(ARCH)

# Export this setting and pass it in.
JAVA_TOOL_OPTIONS = -Djava.awt.headless=true
export JAVA_TOOL_OPTIONS

# Default make rule
all javac javadoc javah javap apt: clean check jtreg-tests $(JPRT_ARCHIVE_BUNDLE)
	@echo "Testing completed successfully"

# for use with JPRT -testrule
all:		TESTDIRS = .
javac fastjavac: TESTDIRS = tools/javac
javadoc:	TESTDIRS = tools/javadoc com/sun/javadoc
javah:		TESTDIRS = tools/javah
javap:		TESTDIRS = tools/javap
apt:		TESTDIRS = tools/apt

fastjavac:	SAMEVM = -samevm

# Check to make sure these directories exist
check: $(JT_HOME) $(PRODUCT_HOME) $(JTREG)

# Run the tests
jtreg-tests: FRC
	ls /opt/jprt /opt/jprt/jdk*
	@echo "Using export JAVA_TOOL_OPTIONS=$(JAVA_TOOL_OPTIONS)"
	@rm -f -r $(TEST_OUTPUT_DIR)/JTwork $(TEST_OUTPUT_DIR)/JTreport
	@mkdir -p $(TEST_OUTPUT_DIR)
	JT_JAVA=$(JT_JAVA) $(JTREG) -k:\!ignore -a -v:fail,error $(SAMEVM) \
          -r:$(TEST_OUTPUT_DIR)/JTreport \
          -w:$(TEST_OUTPUT_DIR)/JTwork \
          -jdk:$(TESTJAVA) \
	  -Xbootclasspath/p:$(TESTBOOTCLASSPATH) \
	  -exclude:bootclasspath-exclude.jtx \
          $(JAVA_TOOL_OPTIONS:%=-vmoption:%) \
          $(JAVA_ARGS:%=-vmoption:%) \
          $(TESTDIRS)

# Bundle up the results
$(JPRT_ARCHIVE_BUNDLE): FRC
	@rm -f $@
	@mkdir -p $(@D)
	( cd $(TEST_OUTPUT_DIR) && zip -q -r $@ . )

# Cleanup
clean:
	rm -f -r $(TEST_OUTPUT_DIR)
	rm -f $(JPRT_ARCHIVE_BUNDLE)

# Used to force a target rules to run
FRC:

# Phony targets (e.g. these are not filenames)
.PHONY: all tests clean check

