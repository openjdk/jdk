#!/bin/bash

# Simple MaxJ Javadoc Test Script
# Quick verification that MaxJ syntax support works

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
JAVADOC="$PROJECT_ROOT/build/macosx-aarch64-server-release/jdk/bin/javadoc"
TEST_DIR="$SCRIPT_DIR/test-files"
OUT_DIR="$SCRIPT_DIR/output"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}MaxJ Javadoc Quick Test${NC}"
echo "======================"

# Clean output
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

# Test MaxJ syntax
echo -n "Testing MaxJ syntax (.maxj)... "
if $JAVADOC -Xdoclint:none -quiet -private -d "$OUT_DIR/maxj" "$TEST_DIR/ComprehensiveMaxJSyntaxTest.maxj" 2>/dev/null; then
    echo -e "${GREEN}✅ SUCCESS${NC}"
else
    echo -e "${RED}❌ FAILED${NC}"
    exit 1
fi

# Test MaxJ operators specifically
echo -n "Testing MaxJ operators (===, #, <==)... "
if $JAVADOC -Xdoclint:none -quiet -private -d "$OUT_DIR/operators" "$TEST_DIR/MaxJConnectionOperatorTest.maxj" "$TEST_DIR/AllMaxJOperatorsTest.maxj" 2>/dev/null; then
    echo -e "${GREEN}✅ SUCCESS${NC}"
else
    echo -e "${RED}❌ FAILED${NC}"
    exit 1
fi

# Test Java syntax
echo -n "Testing Java syntax (.java)... "
if $JAVADOC -Xdoclint:none -quiet -private -d "$OUT_DIR/java" "$TEST_DIR/StandardJavaSyntaxTest.java" 2>/dev/null; then
    echo -e "${GREEN}✅ SUCCESS${NC}"
else
    echo -e "${RED}❌ FAILED${NC}"
    exit 1
fi

# Test keyword conflicts
echo -n "Testing keyword conflicts... "
if $JAVADOC -Xdoclint:none -quiet -private -d "$OUT_DIR/conflicts" "$TEST_DIR/KeywordConflictTest.java" 2>/dev/null; then
    echo -e "${GREEN}✅ SUCCESS${NC}"
else
    echo -e "${RED}❌ FAILED${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}🎉 All tests passed!${NC}"
echo "Generated docs in: $OUT_DIR/"
echo ""
echo "MaxJ syntax elements tested:"
echo "  ✅ SWITCH/CASE/OTHERWISE"
echo "  ✅ IF/ELSE"
echo "  ✅ === (triple equals)"
echo "  ✅ # (concatenation)"
echo "  ✅ <== (connection operator)"
echo "  ✅ Nested structures"
echo "  ✅ Complex operator combinations"
echo "  ✅ No conflicts with Java syntax"
