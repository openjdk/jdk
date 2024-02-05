## Task 1 : run an existing test suite

- take a look at `jdk/test/jdk/TEST.groups`, it defines the different test tiers
- you can run a specific test suite, for example

```bash
time make test TEST="test/jdk/:jdk_collections_core"
time make test TEST="test/jdk/:jdk_concurrent"
```

- you can just the one test case using the following pattern `make test TEST="test/jdk/<file-path>"`

```bash
time make test TEST="test/jdk/java/util/Map/EntrySetIterator.java"
```

## Task 2 : simple syntax sugar support

- [ ] empty map
- [ ] singleton map

## Task 3 : running tests inside the IDE

- [ ] **optional** the rabbit hole of jtreg IntelliJ IDEA plugin

## Task 4 : run the compiler in debug mode

- [ ] set some breakpoints
- [ ] configure a remote debugger
- [ ] use the `compile-debug.sh` script

## Task 5 : explore more tests of the enhanced map syntax

- [ ] full map with String to any key value pairs 