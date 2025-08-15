# MaxJ Javadoc Test Suite

This directory contains comprehensive tests for MaxJ syntax support in the JDK21-based javadoc tool.

## Test Files

### MaxJ Syntax Tests (.maxj files)
- **`ComprehensiveMaxJSyntaxTest.maxj`** - Complete MaxJ syntax test including:
  - SWITCH/CASE/OTHERWISE control flow
  - IF/ELSE conditional statements
  - MaxJ operators: `===`, `#`, `<==`
  - Nested structures and complex expressions
  - Mixed MaxJ and standard Java syntax

- **`MAXJSwitchEdgeCases.maxj`** - Edge cases for MaxJ switch statements:
  - Nested switches
  - Expression-based case values
  - String switches
  - Empty cases

### Java Compatibility Tests (.java files)
- **`StandardJavaSyntaxTest.java`** - Verifies standard Java functionality:
  - Modern Java features (records, sealed classes, pattern matching)
  - All Java operators and control flow
  - Generics, lambdas, streams
  - Switch expressions (JDK 12+)

- **`KeywordConflictTest.java`** - Tests for keyword conflicts:
  - Case sensitivity (java `case` vs MaxJ `CASE`)
  - Operator conflicts (java `==` vs MaxJ `===`)
  - Variables and methods named with MaxJ keywords
  - Mixed usage scenarios

## Running Tests

### Quick Test
```bash
./maxj/testing/test-javadoc.sh
```

### Manual Testing

**Test MaxJ files:**
```bash
./build/macosx-aarch64-server-release/jdk/bin/javadoc \
  -Xdoclint:none -quiet -private \
  -d ./maxj/testing/output/maxj-test \
  maxj/testing/test-files/ComprehensiveMaxJSyntaxTest.maxj
```

**Test Java files:**
```bash
./build/macosx-aarch64-server-release/jdk/bin/javadoc \
  -Xdoclint:none -quiet -private \
  -d ./maxj/testing/output/java-test \
  maxj/testing/test-files/StandardJavaSyntaxTest.java
```

**Test real MaxJ project files:**
```bash
./build/macosx-aarch64-server-release/jdk/bin/javadoc \
  -Xdoclint:none -quiet -private \
  -d ./out/real-maxj \
  ../maxelercore/**/*.maxj
```

## MaxJ Syntax Elements Tested

### Control Flow Keywords
- `SWITCH (expression) { ... }` - MaxJ switch statement
- `CASE (value) { ... }` - MaxJ case block  
- `OTHERWISE { ... }` - MaxJ default case
- `IF (condition) { ... } ELSE { ... }` - MaxJ conditional

### Operators
- `===` - MaxJ triple equality operator
- `#` - MaxJ concatenation operator
- `<==` - MaxJ connection/assignment operator

### Advanced Features
- Nested SWITCH/CASE structures
- Complex IF/ELSE chains
- Expression-based CASE values
- Empty CASE blocks
- Mixed MaxJ and standard Java syntax

## Expected Results

### Successful Tests
- ✅ All MaxJ syntax should parse without syntax errors
- ✅ Standard Java syntax should remain fully functional
- ✅ No keyword conflicts between MaxJ and Java
- ✅ Documentation generated for all test cases

### Expected Issues
- Dependency errors for real MaxJ project files (normal - missing MaxJ libraries)
- HTML warnings in javadoc comments containing `<==` (treated as HTML tags)

## Troubleshooting

If tests fail:

1. **Build the project first:**
   ```bash
   make clean && make
   ```

2. **Check javadoc path:**
   ```bash
   ls -la build/macosx-aarch64-server-release/jdk/bin/javadoc
   ```

3. **Run individual tests manually** to see detailed error messages

4. **Check generated documentation:**
   ```bash
   open maxj/testing/output/comprehensive-maxj/maxjtest/ComprehensiveMaxJSyntaxTest.html
   ```

## File Structure

```
maxjdoc/
├── maxj/                         # MaxJ support directory
│   ├── README.md                 # Main MaxJ documentation
│   ├── TESTING.md               # Testing guide
│   └── testing/                 # All testing-related files
│       ├── test-simple.sh       # Quick test
│       ├── test-javadoc.sh      # Comprehensive test
│       ├── test-operators.sh    # Operator test
│       ├── test-files/          # Test files
│       │   ├── README.md        # This file
│       │   ├── ComprehensiveMaxJSyntaxTest.maxj
│       │   ├── MAXJSwitchEdgeCases.maxj
│       │   ├── StandardJavaSyntaxTest.java
│       │   └── KeywordConflictTest.java
│       └── output/              # Generated documentation
│           ├── comprehensive-maxj/
│           ├── edge-cases-maxj/
│           ├── standard-java/
│           ├── keyword-conflicts/
│           ├── multiple-java/
│           └── multiple-maxj/
```
