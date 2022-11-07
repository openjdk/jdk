for f in *.c *.h;
do
  # replace tabs with spaces
  expand ${f} > ${f}.tmp;
  mv ${f}.tmp $f;

  # fix line endings to LF
  sed -i -e 's/\r$//g' ${f};

  # remove trailing spaces
  sed -i -e 's/[ ]* $//g' ${f};
done