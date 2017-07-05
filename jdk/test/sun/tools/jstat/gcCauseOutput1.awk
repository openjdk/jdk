#
# matching the following output specified as a pattern that verifies
# that the numerical values conform to a specific pattern, rather than
# specific values.
#
#  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT    LGCC                 GCC                 
#  0.00   0.00   0.00   9.97  90.94  87.70      2    0.013     0    0.000    0.013 Allocation Failure   No GC


BEGIN	{
	    headerlines=0; datalines=0; totallines=0
	}

/^  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT    LGCC                 GCC                 $/	{
	    headerlines++;
	}

# The following pattern does not verify the validity of the gc cause
# string as the values can vary depending on conditions out of our
# control. To accomodate this variability, the pattern matcher simply
# detects that there are two strings that match a specific pattern
# where the first character is a letter followed by a sequence of zero
# or more letters and spaces. It also provides for the ".", "(", and ")"
# characters to allow for the string "System.gc()".
#
/^[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*([0-9]+\.[0-9]+)|-[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[a-zA-Z]+[a-zA-Z \.\(\)]*[ ]*[a-zA-Z]+[a-zA-Z \.\(\)]*$/	{
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
