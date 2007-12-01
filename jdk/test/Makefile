#
# Makefile to run jtreg
#

# Get OS/ARCH specifics
OSNAME = $(shell uname -s)
ifeq ($(OSNAME), SunOS)
  PLATFORM = solaris
  JCT_PLATFORM = solaris
  ARCH = $(shell uname -p)
  ifeq ($(ARCH), i386)
    ARCH=i586
  endif
endif
ifeq ($(OSNAME), Linux)
  PLATFORM = linux
  JCT_PLATFORM = linux
  ARCH = $(shell uname -m)
  ifeq ($(ARCH), i386)
    ARCH=i586
  endif
endif
ifeq ($(OSNAME), Windows_NT)
  PLATFORM = windows
  JCT_PLATFORM = win32
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
  JT_HOME = J:/svc/jct-tools3.2.2_02
  JTREG_KEY_OPTION=-k:!ignore
else
  JT_HOME = /java/svc/jct-tools3.2.2_02
  JTREG_KEY_OPTION=-k:\!ignore
endif

# Default JTREG to run
JTREG = $(JT_HOME)/$(JCT_PLATFORM)/bin/jtreg

# Default JDK to test
JAVA_HOME = $(TEST_ROOT)/../build/$(PLATFORM)-$(ARCH)

# The test directories to run
DEFAULT_TESTDIRS = demo/jvmti/gctest demo/jvmti/hprof
TESTDIRS = $(DEFAULT_TESTDIRS)

# Root of all test results
JTREG_OUTPUT_DIR = $(TEST_ROOT)/o_$(PLATFORM)-$(ARCH)

# Export this setting and pass it in.
#JAVA_TOOL_OPTIONS = -Djava.awt.headless=true
#export JAVA_TOOL_OPTIONS

# Default make rule
all: clean check tests $(JPRT_ARCHIVE_BUNDLE)
	@echo "Testing completed successfully"

# Chaeck to make sure these directories exist
check: $(JT_HOME) $(JAVA_HOME) $(JTREG)

# Run the tests
tests: FRC
	@echo "Using export JAVA_TOOL_OPTIONS=$(JAVA_TOOL_OPTIONS)"
	@rm -f -r $(JTREG_OUTPUT_DIR)
	@mkdir -p $(JTREG_OUTPUT_DIR)
	$(JTREG) -a -v:fail,error \
          $(JTREG_KEY_OPTION) \
          -r:$(JTREG_OUTPUT_DIR)/JTreport \
          -w:$(JTREG_OUTPUT_DIR)/JTwork \
          -jdk:$(JAVA_HOME) \
          $(JAVA_TOOL_OPTIONS:%=-vmoption:%) \
          $(JAVA_ARGS:%=-vmoption:%) \
          $(TESTDIRS)

# Bundle up the results
$(JPRT_ARCHIVE_BUNDLE): FRC
	@rm -f $@
	@mkdir -p $(@D)
	( cd $(JTREG_OUTPUT_DIR) && zip -q -r $@ . )

# Cleanup
clean:
	rm -f -r $(JTREG_OUTPUT_DIR)
	rm -f $(JPRT_ARCHIVE_BUNDLE)

# Used to force a target rules to run
FRC:

# Phony targets (e.g. these are not filenames)
.PHONY: all tests clean check

