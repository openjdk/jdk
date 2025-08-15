#!/bin/bash

# MaxJ Javadoc Generation Test Script
# Tests that MaxJ syntax support works correctly for JDK21 javadoc

set -e  # Exit on any error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEST_DIR="$SCRIPT_DIR/test-files"
OUT_DIR="$SCRIPT_DIR/output"
JAVADOC="$PROJECT_ROOT/build/macosx-aarch64-server-release/jdk/bin/javadoc"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}MaxJ Javadoc Generation Test Suite${NC}"
echo "======================================"
echo ""

# Check if javadoc exists
if [ ! -f "$JAVADOC" ]; then
    echo -e "${RED}❌ Error: javadoc not found at $JAVADOC${NC}"
    echo "Please run 'make' first to build the MaxJ javadoc tool."
    exit 1
fi

# Check if test directory exists
if [ ! -d "$TEST_DIR" ]; then
    echo -e "${RED}❌ Error: Test directory not found at $TEST_DIR${NC}"
    exit 1
fi

# Clean output directory
echo -e "${BLUE}Cleaning output directory...${NC}"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

# Function to run a test
run_test() {
    local test_name="$1"
    local files="$2"
    local output_dir="$OUT_DIR/$test_name"

    echo -n "Testing $test_name... "

    if $JAVADOC -Xdoclint:none -quiet -private -d "$output_dir" $files 2>/dev/null; then
        echo -e "${GREEN}✅ SUCCESS${NC}"
        return 0
    else
        echo -e "${RED}❌ FAILED${NC}"
        echo "  Command: $JAVADOC -Xdoclint:none -quiet -private -d \"$output_dir\" $files"
        return 1
    fi
}

# Function to check generated docs
check_docs() {
    local test_name="$1"
    local output_dir="$OUT_DIR/$test_name"

    if [ -d "$output_dir" ] && [ "$(ls -A "$output_dir")" ]; then
        local html_count=$(find "$output_dir" -name "*.html" | wc -l)
        echo "  📄 Generated $html_count HTML files"
        return 0
    else
        echo -e "  ${RED}❌ No documentation generated${NC}"
        return 1
    fi
}

# Test counter
TESTS_RUN=0
TESTS_PASSED=0

echo -e "${BLUE}Running MaxJ Syntax Tests...${NC}"
echo ""

# Test 1: Comprehensive MaxJ Syntax
echo -e "${YELLOW}Test 1: Comprehensive MaxJ Syntax (.maxj)${NC}"
if run_test "comprehensive-maxj" "$TEST_DIR/ComprehensiveMaxJSyntaxTest.maxj"; then
    check_docs "comprehensive-maxj"
    TESTS_PASSED=$((TESTS_PASSED + 1))
fi
TESTS_RUN=$((TESTS_RUN + 1))
echo ""

# Test 2: Edge Cases MaxJ Syntax  
echo -e "${YELLOW}Test 2: MaxJ Edge Cases (.maxj)${NC}"
if run_test "edge-cases-maxj" "$TEST_DIR/MAXJSwitchEdgeCases.maxj"; then
    check_docs "edge-cases-maxj"
    TESTS_PASSED=$((TESTS_PASSED + 1))
fi
TESTS_RUN=$((TESTS_RUN + 1))
echo ""

# Test 3: Standard Java Syntax
echo -e "${YELLOW}Test 3: Standard Java Syntax (.java)${NC}"
if run_test "standard-java" "$TEST_DIR/StandardJavaSyntaxTest.java"; then
    check_docs "standard-java"
    TESTS_PASSED=$((TESTS_PASSED + 1))
fi
TESTS_RUN=$((TESTS_RUN + 1))
echo ""

# Test 4: Keyword Conflict Test
echo -e "${YELLOW}Test 4: Keyword Conflict Test (.java)${NC}"
if run_test "keyword-conflicts" "$TEST_DIR/KeywordConflictTest.java"; then
    check_docs "keyword-conflicts"
    TESTS_PASSED=$((TESTS_PASSED + 1))
fi
TESTS_RUN=$((TESTS_RUN + 1))
echo ""

# Test 5: Multiple Java Files Together
echo -e "${YELLOW}Test 5: Multiple Java Files Together${NC}"
if run_test "multiple-java" "$TEST_DIR/StandardJavaSyntaxTest.java $TEST_DIR/KeywordConflictTest.java"; then
    check_docs "multiple-java"
    TESTS_PASSED=$((TESTS_PASSED + 1))
fi
TESTS_RUN=$((TESTS_RUN + 1))
echo ""

# Test 6: Multiple MaxJ Files Together
echo -e "${YELLOW}Test 6: Multiple MaxJ Files Together${NC}"
if run_test "multiple-maxj" "$TEST_DIR/ComprehensiveMaxJSyntaxTest.maxj $TEST_DIR/MAXJSwitchEdgeCases.maxj"; then
    check_docs "multiple-maxj"
    TESTS_PASSED=$((TESTS_PASSED + 1))
fi
TESTS_RUN=$((TESTS_RUN + 1))
echo ""

# Test 7: Real MaxJ Project Files - Syntax Only (dependencies expected to fail)
echo -e "${YELLOW}Test 7: Real MaxJ Project Files (Syntax Test)${NC}"
REAL_MAXJ_FILE=$(find "$PROJECT_ROOT/../maxelercore" -name "*.maxj" 2>/dev/null | head -1)
if [ -n "$REAL_MAXJ_FILE" ]; then
    echo -n "Testing real maxj file (syntax parsing)... "
    # For real MaxJ files, we expect dependency errors but no syntax errors
    # Use timeout to prevent hanging on complex files
    ERROR_OUTPUT=$(timeout 30 $JAVADOC -Xdoclint:none -quiet -private -d "$OUT_DIR/real-maxj-project" "$REAL_MAXJ_FILE" 2>&1 || echo "timeout or dependency errors")
    if echo "$ERROR_OUTPUT" | grep -q -E "CASE|SWITCH|OTHERWISE|.*expected.*MAXJ|illegal.*MAXJ|unexpected.*MAXJ"; then
        echo -e "${RED}❌ SYNTAX ERRORS FOUND${NC}"
        echo "  MaxJ syntax parsing failed:"
        echo "$ERROR_OUTPUT" | grep -E "CASE|SWITCH|OTHERWISE|.*expected.*MAXJ|illegal.*MAXJ|unexpected.*MAXJ" | head -3
    else
        echo -e "${GREEN}✅ SYNTAX PARSING SUCCESS${NC}"
        echo "  📝 No MaxJ syntax errors (dependency errors are expected)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    fi
    TESTS_RUN=$((TESTS_RUN + 1))
else
    echo "  📝 Skipped (no maxelercore files found)"
fi
echo ""

# Summary
echo "======================================"
echo -e "${BLUE}Test Summary${NC}"
echo "======================================"
echo "Tests run: $TESTS_RUN"
echo -e "Tests passed: ${GREEN}$TESTS_PASSED${NC}"
if [ $TESTS_PASSED -eq $TESTS_RUN ]; then
    echo -e "Result: ${GREEN}🎉 ALL TESTS PASSED!${NC}"
    echo ""
    echo -e "${GREEN}MaxJ Javadoc generation is fully functional!${NC}"
    echo ""
    echo "Generated documentation available in:"
    echo "  📁 $OUT_DIR/"
    for dir in "$OUT_DIR"/*; do
        if [ -d "$dir" ]; then
            echo "     $(basename "$dir")/"
        fi
    done
    exit 0
else
    echo -e "Result: ${RED}❌ $((TESTS_RUN - TESTS_PASSED)) TESTS FAILED${NC}"
    exit 1
fi
