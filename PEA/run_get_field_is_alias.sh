#!/usr/bin/bash
java -XX:+UnlockExperimentalVMOptions -Xbatch -XX:CompileCommand=compileOnly,GetFieldIsAlias::main -XX:CompileCommand=quiet $* GetFieldIsAlias
