shopt -s nullglob
for f in *.c *.h *.cc *.hh;
do
# replace tabs with spaces
expand ${f} > ${f}.tmp;
mv ${f}.tmp $f;

# fix line endings to LF
sed -e 's/\r$//g' ${f} > ${f}.tmp;
mv ${f}.tmp $f;

# remove trailing spaces
sed -e 's/[ ]* $//g' ${f} > ${f}.tmp;
mv ${f}.tmp $f;
done