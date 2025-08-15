# MaxJ Javadoc Support

This directory contains all MaxJ-related files for the JDK21-based javadoc tool with MaxJ syntax support.

## Directory Structure

```
maxj/
├── README.md                 # This file
├── TESTING.md               # Comprehensive testing guide
└── testing/                 # All testing-related files
    ├── test-simple.sh       # Quick MaxJ syntax verification
    ├── test-javadoc.sh      # Comprehensive test suite
    ├── test-operators.sh    # MaxJ operator-specific tests
    ├── test-files/          # Test files for MaxJ syntax
    │   ├── README.md        # Detailed test file documentation
    │   ├── *.maxj           # MaxJ syntax test files
    │   └── *.java           # Java compatibility test files
    └── output/              # Generated test documentation output
```

## Quick Start

### Run Simple Test
```bash
./maxj/testing/test-simple.sh
```

### Run Comprehensive Test Suite
```bash
./maxj/testing/test-javadoc.sh
```

### Manual Testing
```bash
# Test a single MaxJ file
./build/macosx-aarch64-server-release/jdk/bin/javadoc \
  -Xdoclint:none -quiet -private \
  -d ./maxj/testing/output/manual-test \
  maxj/testing/test-files/ComprehensiveMaxJSyntaxTest.maxj
```

## MaxJ Syntax Support

This javadoc implementation supports:

### Keywords
- `SWITCH (expr) { ... }` - MaxJ switch statement
- `CASE (value) { ... }` - MaxJ case block
- `OTHERWISE { ... }` - MaxJ default case
- `IF (condition) { ... } ELSE { ... }` - MaxJ conditionals

### Operators
- `===` - MaxJ triple equality
- `#` - MaxJ concatenation
- `<==` - MaxJ connection/assignment

### Features
- ✅ Nested SWITCH/CASE structures
- ✅ Complex IF/ELSE chains
- ✅ Expression-based CASE values
- ✅ Mixed MaxJ and Java syntax
- ✅ Case-sensitive parsing (no conflicts with Java keywords)

## Documentation

- **[TESTING.md](TESTING.md)** - Complete testing guide with examples
- **[testing/test-files/README.md](testing/test-files/README.md)** - Detailed test file documentation

## File Extensions

- `.maxj` files - Use MaxJ syntax parsing
- `.java` files - Use standard Java syntax parsing

Both file types can be processed by the same javadoc tool without conflicts.