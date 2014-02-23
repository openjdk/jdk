#
# matching the following output specified as a pattern that verifies
# that the numerical values conform to a specific pattern, rather than
# specific values.
#
#  S0     S1     E      O      P     YGC     YGCT    FGC    FGCT     GCT   
#  0.00 100.00  68.87   1.24  27.75      1    0.044     0    0.000    0.044

BEGIN	{
	    headerlines=0; datalines=0; totallines=0
	}

/^  S0     S1     E      O      P     YGC     YGCT    FGC    FGCT     GCT   $/	{
	    headerlines++;
	}

/^[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+$/	{
	    datalines++;
	}

	{ totallines++; print $0 }

END	{
	    if ((headerlines == 1) && (datalines == 1)) {
	        exit 0
	    }
	    else {
	        exit 1
	    }
	}
