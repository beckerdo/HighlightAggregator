package info.danbecker.ha;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Location describes the type and chapter/page/location of the document.
 * The location elements are described in the parameter list below.
 * <p>
 * Several parameters are saved as Strings and ints. This is to
 * preserve the original style of the text (for example Roman numerals), but
 * allow quick integer comparison and equality testing.
 *
 * @author <a href="mailto://dan@danbecker.info>Dan Becker</a>
 *
 * @param type - Note (user text) or Highlight (author text)
 * @param chapterStr - chapter String (number or Roman numeral, may contain non-numerals)
 * @param pageStr -  page String (number or Roman numeral)
 * @param location - int location
 */
public record Location(NoteType type, String chapterStr, int chapter, String pageStr, int page, int location) implements Comparable<Location> {
    public enum NoteType {Highlight, Note}

    public Location {
        if (null == chapterStr)
            throw new IllegalArgumentException("chapterStr must not be null");
        if (0 > chapter)
            throw new IllegalArgumentException("chapter must be non-negative, value=" + chapter);
        if (null == pageStr)
            throw new IllegalArgumentException("pageStr must not be null");
        if (0 > page)
            throw new IllegalArgumentException("page must be non-negative, value=" + page );
        if (0 > location)
            throw new IllegalArgumentException("location must be non-negative, value=" + location);
    }

    public static Location fromStr( NoteType noteType, String chapterStr, String pageStr, int loc ) {
        int chapter = chapterStr.isEmpty() ? 0 : fromIntOrRoman( chapterStr ); // -1 will be no digits
        chapter = -1 == chapter ? 0 : chapter; // Location does not like negatives
        return new Location( noteType,
                chapterStr, chapter, pageStr, fromIntOrRoman( pageStr ), loc );
    }

    public static Location fromInt( NoteType noteType, int chapter, int page, int loc ) {
        return new Location( noteType,
            Integer.toString( chapter ), chapter, Integer.toString( page ), page, loc);
    }

    public static int fromIntOrRoman(String str) {
        int cVal = -1;
        if (null == str || str.isEmpty()) return cVal;
        try {
            cVal = intGroup(str);
            if ( -1 == cVal )
                cVal = romanGroup( str );
        } catch (NumberFormatException e) {
            cVal = romanGroup( str );
        }
        return cVal;
    }

    // public static String INT_RE = "[^-\\d]*(-?\\d+)\\D*";
    public static String INT_RE = "\\D*(-?\\d+)\\D*";
    public final static Pattern intPattern = Pattern.compile( INT_RE );
    public static int intGroup( String str ) throws NumberFormatException {
        int val = -1;
        Matcher matcher = intPattern.matcher(str);
        if ( matcher.find() ) {
            String group1 = matcher.group(1);
            val = Integer.parseInt(group1);
        }
        return val;
    }
    public static boolean intMatch( String str ) {
        Matcher matcher = intPattern.matcher(str);
        return matcher.find();
    }


    public static String ROMAN_RE = "((?=[MDCLXVI])M*(C[MD]|D?C{0,3})(X[CL]|L?X{0,3})(I[XV]|V?I{0,3}))";
    // public static String ROMAN_RE = ".*([IVXLCDM]+).*";
    public final static Pattern romanPattern = Pattern.compile(ROMAN_RE, Pattern.CASE_INSENSITIVE);
    public static int romanGroup(String romanStr) {
        int val = -1;
        Matcher matcher = romanPattern.matcher(romanStr);
        if (matcher.find()) {
            String group1 = matcher.group(1);
            val = romanInt(group1);
        }
        return val;
    }
    public static boolean romanMatch( String str ) {
        Matcher matcher = romanPattern.matcher(str);
        return matcher.find();
    }

    /**
     * Given a Roman numeral String, return decimal value
     *
     * @param str a Roman numeral string
     * @return a decimal int
     */
    public static int romanInt(String str) {
        int result = 0;
        for (int i = 0; i < str.length(); i++) {
            // Getting value of symbol s[i]
            int s1 = romanInt(str.charAt(i));

            // Getting value of symbol s[i+1]
            if (i + 1 < str.length()) {
                int s2 = romanInt(str.charAt(i + 1));

                // Comparing both values
                if (s1 >= s2) {
                    result += s1;
                } else {
                    result += s2 - s1;
                    i++;
                }
            } else {
                result += s1;
            }
        }
        return result;
    }

    /**
     * Given a Roman character, return in value.
     *
     * @param r a Roman char
     * @return int value of r
     */
    public static int romanInt(char r) {
        return switch (r) {
            case 'I', 'i' -> 1;
            case 'V', 'v' -> 5;
            case 'X', 'x' -> 10;
            case 'L', 'l' -> 50;
            case 'C', 'c' -> 100;
            case 'D', 'd' -> 500;
            case 'M', 'm' -> 1000;
            default -> 0;
        };
    }

    /**
     * Parse Kindle Notes and Highlights into data
     * "Note - I > Page 11 · Location 116"
     * "Highlight (<span class="highlight_yellow">yellow</span>) - I &gt; Page 13 · Location 144"
     * Beware chapter string with dashes - "Highlight (yellow) - The Days of Empire, 1870–1918 > Page 2 · Location 238"
     *
     * @param s input string as defined in comments above
     * @return a new Location from the Kindle noteHeading text
     */
    public static Location fromKindle(String s) {
        NoteType noteType = NoteType.Highlight;
        if (s.startsWith("Note")) {
            noteType = NoteType.Note;
        }

        String chapterStr = "";
        int dashPos = s.indexOf("- ");
        if (-1 != dashPos) {
            String delim = ">";
            int gtPos = s.indexOf(delim);
            if (-1 != gtPos) {
                chapterStr = s.substring(dashPos + 1, gtPos).trim();
            }
            // else chapter is empty
        }
        String pageStr = "";
        int pagePos = s.indexOf("Page");
        if (-1 != pagePos) {
            int dotPos = s.indexOf("·");
            if (-1 != dotPos) {
                pageStr = s.substring(pagePos + 4, dotPos).trim();
            }
        }

        String locStr = "";
        int locPos = s.indexOf("Location");
        if (-1 != locPos) {
            locStr = s.substring(locPos + 8).trim();
        }
        return Location.fromStr( noteType, chapterStr, pageStr, Integer.parseInt(locStr));
    }

    @Override
    public boolean equals( Object that ) {
        if ( !(that instanceof Location )) return false;
        return 0 == compareTo( (Location) that );
    }

    @Override
    public int compareTo(Location that) {
        // Type > Chapter > Page > Location
        if (null == that) return 1;
        if ( this.type != that.type) return this.type.ordinal() - that.type.ordinal();
        if ( this.chapter != that.chapter ) return this.chapter - that.chapter;
        if ( this.page != that.page ) return this.page - that.page;
        return this.location - that.location;
    }

    public static int compare( Location thisLoc, Location thatLoc ) {
        if (null == thisLoc) {
            if (null == thatLoc) return 0;
            return -1;
        }
        return thisLoc.compareTo( thatLoc );
    }

    @Override
    public String toString() {
        if ( !chapterStr.isEmpty() )
            return  "c" + chapterStr + ",p" + pageStr + ",l" + location;
        return  "p" + pageStr + ",l" + location;
    }
    /**
     * Make a Location with just the fields specified in the order specified
     * For example "lp" produces a string such as "l141,p15"
     *
     * @param fields a string with chars c (chapter), p (page), l (location)
     * @return a String with the requested fields
     */
    public String toString(String fields) {
        return fields.chars()
                .mapToObj(c -> String.valueOf((char) c))
                .map(s -> switch (s) {
                    case "t" -> type.toString();
                    case "c" -> s + chapterStr;
                    case "p" -> s + pageStr;
                    case "l" -> s + location;
                    default -> s;
                })
                .collect(Collectors.joining(","));
    }

    /**
     * Given two locations, returns a String
     * with ranges for non-identical fields.
     * For example Location cI,p12, l150 and Location cII, p24, l200 produces
     * cI-II,pxii-xxiv,l150-200
     *
     * @param l1 begining of the range
     * @param l2 end of the range
     * @return a String with intervals
     */
    public static String range(Location l1, Location l2) {
        StringBuilder sb = new StringBuilder();
        if (!l1.chapterStr.isEmpty() && !l2.chapterStr.isEmpty()) {
            sb.append("c").append(l1.chapterStr);
            if (!l1.chapterStr.equals(l2.chapterStr)) {
                sb.append("-").append(l2.chapterStr());
            }
            sb.append(",");
        }
        sb.append("p").append(l1.pageStr);
        if (!l1.pageStr.equals(l2.pageStr)) {
            sb.append("-").append(l2.pageStr);
        }
        sb.append(",l").append(l1.location);
        if (l1.location != (l2.location)) {
            sb.append("-").append(l2.location);
        }
        return sb.toString();
    }
}