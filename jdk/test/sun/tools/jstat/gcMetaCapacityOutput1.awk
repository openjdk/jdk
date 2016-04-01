#
# matching the following output specified as a pattern that verifies
# that the numerical values conform to a specific pattern, rather than
# specific values.
#
#   MCMN       MCMX        MC       CCSMN      CCSMX       CCSC     YGC   FGC    FGCT     GCT   
#     512.0   132096.0     5120.0      512.0   131072.0      512.0     1     0    0.000    0.004

BEGIN	{
	    headerlines=0; datalines=0; totallines=0
	}

/^   MCMN       MCMX        MC       CCSMN      CCSMX       CCSC     YGC   FGC    FGCT     GCT   $/	{
	    headerlines++;
	}

/^[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+$/	{
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
