#!/bin/bash


for i in `seq 1 100` ; do
	zed=""
	for j in `seq 1 $i` ; do
		zed="${zed}Z"
	done
	cp ArrayCreationTree.java ${zed}ArrayCreationTree.java
	sed -i '' -e "s/class ArrayCreationTree/class ${zed}ArrayCreationTree/" ${zed}ArrayCreationTree.java
done
