#
# matching the following output specified as a pattern that verifies
# that the numerical values conform to a specific pattern, rather than
# specific values.
#
#  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT   
#  0.00 100.00  56.99   7.81  95.03  87.56      1    0.009     0    0.000    0.009
#  0.00 100.00  63.64   7.81  95.03  87.56      1    0.009     0    0.000    0.009
#  0.00 100.00  64.68   7.81  95.03  87.56      1    0.009     0    0.000    0.009
#  0.00 100.00  65.73   7.81  95.03  87.56      1    0.009     0    0.000    0.009
#  0.00 100.00  67.22   7.81  95.03  87.56      1    0.009     0    0.000    0.009

BEGIN	{
	    headerlines=0; datalines=0; totallines=0
	}

/^  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT   $/	{
	    headerlines++;
	}

/^[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*([0-9]+\.[0-9]+)|-[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+$/	{
	    datalines++;
	}

	{ totallines++; print $0 }

END	{
	    if ((headerlines == 1) && (datalines == 5) && (totallines == 6)) {
	        exit 0
            }
            else {
	        exit 1
            }
	}
