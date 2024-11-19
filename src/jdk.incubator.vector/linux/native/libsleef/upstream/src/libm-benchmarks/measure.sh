#!/bin/sh
echo
read -p "Enter label of measurement(e.g. My desktop PC) : " label

if [ -f counter.txt ]
then
    counter=`cat counter.txt`
else
    counter=0
fi

echo Measurement in progress. This may take several minutes.
for i in $*; do
    $i "$label" $counter
done
counter=$((counter+1))
echo $counter > counter.txt
