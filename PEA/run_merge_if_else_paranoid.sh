#!/bin/bash
java -XX:CompileCommand=compileonly,MergeIfElseParanoid::test -XX:-TieredCompilation -Xbatch -XX:+UnlockExperimentalVMOptions -XX:+PEAParanoid $* MergeIfElseParanoid
