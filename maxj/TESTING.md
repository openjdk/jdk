# MaxJ Javadoc Testing Guide

## Quick Test

Run the simple test script:
```bash
./maxj/testing/test-simple.sh
```

## Comprehensive Test

Run the full test suite:
```bash
./maxj/testing/test-javadoc.sh
```

## Manual Testing Examples

### Test MaxJ Files (.maxj extension)
```bash
# Single MaxJ file
./build/macosx-aarch64-server-release/jdk/bin/javadoc \
  -Xdoclint:none -quiet -private \
  -d ./maxj/testing/output/single-maxj \
  maxj/testing/test-files/ComprehensiveMaxJSyntaxTest.maxj

# Multiple MaxJ files
./build/macosx-aarch64-server-release/jdk/bin/javadoc \
  -Xdoclint:none -quiet -private \
  -d ./maxj/testing/output/multi-maxj \
  maxj/testing/test-files/*.maxj

# Real MaxJ project (expect dependency errors but no syntax errors)
./build/macosx-aarch64-server-release/jdk/bin/javadoc \
  -Xdoclint:none -quiet -private \
  -d ./out/real-project \
  ../maxelercore/platforms/tests-it/src/tests/maxring/MaxRingNoCPULoopbackTest.maxj
```

### Test Java Files (.java extension)
```bash
# Single Java file
./build/macosx-aarch64-server-release/jdk/bin/javadoc \
  -Xdoclint:none -quiet -private \
  -d ./maxj/testing/output/single-java \
  maxj/testing/test-files/StandardJavaSyntaxTest.java

# Multiple Java files
./build/macosx-aarch64-server-release/jdk/bin/javadoc \
  -Xdoclint:none -quiet -private \
  -d ./maxj/testing/output/multi-java \
  maxj/testing/test-files/*.java
```

## MaxJ Syntax Elements Supported

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

## Expected Behavior

### ✅ Success Cases
- `.maxj` files with MaxJ syntax → Perfect parsing, docs generated
- `.java` files with Java syntax → Perfect parsing, docs generated
- Mixed usage → Both syntaxes work independently

### ⚠️ Expected Issues
- **Dependency errors** in real MaxJ projects (missing MaxJ compiler libraries)
- **HTML warnings** in javadoc comments containing `<==` (parsed as HTML)
- **Filename warnings** for public classes in `.maxj` files (expects `.java`)

### ❌ Should Not Happen
- Syntax errors on MaxJ keywords in `.maxj` files
- Syntax errors on Java keywords in `.java` files
- Keyword conflicts between MaxJ and Java syntax

## Verification Commands

Check that documentation was generated:
```bash
ls -la maxj/testing/output/
find maxj/testing/output/ -name "*.html" | wc -l
```

View generated documentation:
```bash
open maxj/testing/output/maxj/maxjtest/ComprehensiveMaxJSyntaxTest.html
open maxj/testing/output/java/javatest/StandardJavaSyntaxTest.html
```

## Test File Details

| File | Extension | Purpose | Key Features Tested |
|------|-----------|---------|-------------------|
| `ComprehensiveMaxJSyntaxTest.maxj` | `.maxj` | Complete MaxJ syntax | All keywords, operators, nesting |
| `MAXJSwitchEdgeCases.maxj` | `.maxj` | MaxJ edge cases | Complex switch scenarios |
| `StandardJavaSyntaxTest.java` | `.java` | Java compatibility | Modern Java features, operators |
| `KeywordConflictTest.java` | `.java` | Conflict testing | Case sensitivity, no collisions |

## Success Criteria

The MaxJ javadoc implementation is successful if:
1. All test scripts pass without syntax errors
2. Documentation is generated for both `.maxj` and `.java` files
3. MaxJ and Java syntax coexist without conflicts
4. Real MaxJ projects parse syntactically (dependencies may fail)
