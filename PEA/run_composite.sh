#!/bin/bash
java -XX:CompileCommand=compileonly,CompositeObjects::main -XX:CompileCommand='dontinline,*::blackhole' -XX:-TieredCompilation -Xbatch -XX:+UnlockExperimentalVMOptions -XX:+PEAParanoid $* CompositeObjects
