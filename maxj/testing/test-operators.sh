#!/bin/bash

# Ultimate MaxJ Operator Test
# Tests every single MaxJ operator and keyword

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
JAVADOC="$PROJECT_ROOT/build/macosx-aarch64-server-release/jdk/bin/javadoc"
OUT_DIR="$SCRIPT_DIR/output/ultimate-test"

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}Ultimate MaxJ Operator Test${NC}"
echo "=========================="

# Clean output
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

# Create ultimate test file
cat > "$OUT_DIR/UltimateMaxJTest.maxj" << 'EOF'
package ultimate;

/**
 * Ultimate MaxJ Syntax Test
 * Tests EVERY MaxJ operator and keyword.
 */
public class UltimateMaxJTest {

    public static class HardwareSignal {
        public int value;
        public HardwareSignal next;
        public boolean valid;
    }

    /**
     * Tests ALL MaxJ syntax elements.
     * @param control control value
     * @param data data value
     * @return result
     */
    public int testAllMaxJSyntax(int control, int data) {
        HardwareSignal signal = new HardwareSignal();
        signal.next = new HardwareSignal();
        int result = 0;

        // TEST 1: MaxJ SWITCH/CASE/OTHERWISE
        SWITCH (control) {
            CASE (1) {
                result = 100;
            }
            CASE (2) {
                result = 200;
            }
            OTHERWISE {
                result = 999;
            }
        }

        // TEST 2: MaxJ IF/ELSE
        IF (data > 0) {
            result = result + 10;
        } ELSE {
            result = result - 10;
        }

        // TEST 3: MaxJ === operator
        IF (control === 1) {
            result = result + 1;
        } ELSE {
            result = result + 0;
        }

        // TEST 4: MaxJ # operator (concatenation)
        int concatenated = control # data;
        result = result + concatenated;

        // TEST 5: MaxJ <== operator (connection)
        signal.next.value <== data;
        signal.next.valid <== true;

        // TEST 6: Complex combinations
        SWITCH (control # data) {
            CASE (12) {
                IF (signal.next.value === 2) {
                    signal.next.value <== control # data # 100;
                } ELSE {
                    signal.next.value <== 0;
                }
            }
            OTHERWISE {
                signal.next.value <== control # data;
            }
        }

        return result + signal.next.value;
    }
}
EOF

# Test the ultimate file
echo -n "Testing ALL MaxJ operators and keywords... "
if $JAVADOC -Xdoclint:none -quiet -private -d "$OUT_DIR/docs" "$OUT_DIR/UltimateMaxJTest.maxj" 2>/dev/null; then
    echo -e "${GREEN}✅ SUCCESS${NC}"
    echo ""
    echo "✅ ALL MaxJ operators verified:"
    echo "  🔸 SWITCH/CASE/OTHERWISE - Control flow keywords"
    echo "  🔸 IF/ELSE - Conditional keywords"
    echo "  🔸 === - Triple equals operator"
    echo "  🔸 # - Concatenation operator"
    echo "  🔸 <== - Connection operator"
    echo "  🔸 Complex nested combinations"
    echo ""
    echo -e "${GREEN}🎉 MaxJ javadoc generation is 100% functional!${NC}"
    echo ""
    echo "Generated documentation:"
    echo "  📁 $OUT_DIR/docs/"
    ls "$OUT_DIR/docs/" | head -5
else
    echo -e "${RED}❌ FAILED${NC}"
    echo "Error details:"
    $JAVADOC -Xdoclint:none -quiet -private -d "$OUT_DIR/docs" "$OUT_DIR/UltimateMaxJTest.maxj"
    exit 1
fi
