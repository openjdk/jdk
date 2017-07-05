#
# matching the following output specified as a pattern that verifies
# that the numerical values conform to a specific pattern, rather than
# specific values.
#
#  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT   
#  0.00  93.76  28.40   1.82  77.74  68.02      1    0.005     0    0.000    0.005

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
	    if ((headerlines == 1) && (datalines == 1) && (totallines == 2)) {
	        exit 0
	    }
	    else {
	        exit 1
	    }
	}
