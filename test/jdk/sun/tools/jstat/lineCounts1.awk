#
# matching the following output specified as a pattern that verifies
# that the numerical values conform to a specific pattern, rather than
# specific values.
#
#  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT    CGC    CGCT     GCT
#  0.00  93.76  28.80   1.82  77.74  68.02      1    0.005     0    0.000     0    0.000    0.005
#  0.00  93.76  73.04   1.82  77.74  68.02      1    0.005     0    0.000     0    0.000    0.005
#  0.00  93.76  73.04   1.82  77.74  68.02      1    0.005     0    0.000     0    0.000    0.005
#  0.00  93.76  73.04   1.82  77.74  68.02      1    0.005     0    0.000     0    0.000    0.005
#  0.00  93.76  75.00   1.82  77.74  68.02      1    0.005     0    0.000     0    0.000    0.005

BEGIN	{
	    headerlines=0; datalines=0; totallines=0
	}

/^  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT    CGC    CGCT     GCT   $/	{
	    headerlines++;
	}

/^[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*([0-9]+\.[0-9]+)|-[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+$/	{
	    datalines++;
	}

	{ totallines++; print $0 }

END	{
	    if ((headerlines == 1) && (datalines == 5)) {
	        exit 0
            }
            else {
	        exit 1
            }
	}
