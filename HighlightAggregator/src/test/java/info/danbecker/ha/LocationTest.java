package info.danbecker.ha;

import static info.danbecker.ha.Location.NoteType.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class LocationTest {
    @Test
    public void testBasic() {
        // Some books do not have chapters.
        assertThrows( IllegalArgumentException.class,
            () -> Location.fromStr( Highlight, "I", "-1", 2 ));
        assertThrows( IllegalArgumentException.class,
            () -> Location.fromStr( Highlight, "I", "-1", 2 ));
        assertThrows( IllegalArgumentException.class,
            () -> Location.fromStr(  Note, "I", "1", -2));

        Location test = Location.fromStr( Highlight, "I", "1", 2);
        assertEquals( Highlight, test.type() );
        assertEquals( "I", test.chapterStr() );
        assertEquals( 1, test.page() );
        assertEquals( 2, test.location() );

        assertEquals( "cI,p1,l2", test.toString());
        assertEquals( "cI", test.toString( "c" ));
        assertEquals( "p1", test.toString( "p" ));
        assertEquals( "l2,p1", test.toString( "lp" ));

        assertEquals( "cI-II,p1-3,l2-40", Location.range(test, Location.fromStr( Highlight, "II", "3", 40) ));
        assertEquals( "cI-II,p1-3,l2-40", Location.range(test, Location.fromStr( Highlight, "II", "3", 40) ));

        // Test strings without chapters.
        assertEquals( "p1,l2", Location.fromStr( Highlight, "", "1", 2).toString());
        assertEquals( "p1-3,l2-40", Location.range(test, Location.fromStr( Highlight, "", "3", 40) ));
    }
    @Test
    public void testParseInt() {
        assertTrue( Location.intMatch( "123"));
        assertTrue( Location.intMatch( "-123"));
        assertFalse( Location.intMatch( "abc"));
        assertTrue( Location.intMatch( "ab 123"));
        assertTrue( Location.intMatch( " 123 ab "));
        assertTrue( Location.intMatch( " Chapter 12 ") );

        // assertEquals( 123, Location.intGroup( "123"));
        assertEquals( -123, Location.intGroup( "-123"));
        assertEquals( -1, Location.intGroup( "abc"));
        assertEquals( 123, Location.intGroup( " ab 123 "));
    }

    @Test
    public void testRoman() {
        assertEquals( 0, Location.romanInt('Y'));
        assertEquals( 0, Location.romanInt('y'));
        assertEquals( 1, Location.romanInt('I'));
        assertEquals( 1, Location.romanInt('i'));
        assertEquals( 5, Location.romanInt('V'));
        assertEquals( 10, Location.romanInt('x'));
        assertEquals( 100, Location.romanInt('C'));
        assertEquals( 1000, Location.romanInt('m'));

        assertTrue( Location.romanMatch( " MmXxVi ") );
        assertTrue( Location.romanMatch( "abc MmXxVi 123") );

        // romanGroup and romanInt essentially the same.
        assertEquals( 2026, Location.romanGroup( " MmXxVi ") );
        assertEquals( 2028, Location.romanGroup( "ab MmXxViii 123") );
        assertEquals( 1904, Location.romanInt( "MCMIV" ));
        assertEquals( 2026, Location.romanInt( "mmxxvi" ));
        assertEquals( 2026, Location.romanInt( " MmXxVi " ));
        assertEquals( 13, Location.romanInt( " xiiI " ));
        assertEquals( 2026, Location.romanInt( "ab MmXxVi 123" ));
        assertEquals( 2126, Location.romanInt( "abc MmXxVi 123" )); // Because of the c
        assertEquals( 14, Location.romanInt( "ab XIV" ));

        // mixed ints and Romans
        assertEquals( 4, Location.fromIntOrRoman( "  4. Arabs" ));
        assertEquals( 12, Location.fromIntOrRoman( "  Chapter 12 " ));
        assertEquals( 13, Location.fromIntOrRoman( " xiiI " ));
        assertEquals( 14, Location.fromIntOrRoman( "ab XIV" ));
    }

    @Test
    public void testParseKindle() {
        // Test the parse of Kindle location from different books
        assertEquals( Location.fromStr( Note, "I", "11", 116 ),
                Location.fromKindle( "Note - I > Page 11 · Location 116" )); // Tolstoy - Hadji Murad
        assertEquals( Location.fromInt( Note, 23, 13, 144 ),
                Location.fromKindle( "Note - 23 > Page xiii · Location 144" )); // Jones "Power and Thrones"
        assertEquals( Location.fromInt( Highlight, 19, 13, 144 ),
                Location.fromKindle( "Highlight - XIX > Page xiii · Location 144" )); // Jones "Power and Thrones"
        assertEquals( Location.fromInt( Highlight, 0, 13, 144 ),
                Location.fromKindle( "Highlight - Page xiii · Location 144" )); // Jones "Power and Thrones"
        assertEquals( Location.fromStr( Highlight, "1. Romans", "3", 210 ),
                Location.fromKindle( "Highlight - 1. Romans > Page 3 · Location 210" )); // Jones "Power and Thrones"
        assertEquals( Location.fromStr( Note, "IX. Romans", "3", 210 ),
                Location.fromKindle( "Note - IX. Romans > Page 3 · Location 210" )); // Jones "Power and Thrones"
    }

        @Test
    public void testCompare() {
        // Compare locations
        assertEquals( 0, Location.compare( null, null ));
        assertEquals( -1, Location.compare( null, Location.fromInt( Note, 1,1, 2 )));
        assertEquals( 1, Location.compare( Location.fromInt( Note, 1,1, 2 ), null));
        Location test = Location.fromStr( Highlight, "X", "20", 30);
        assertEquals( 0, test.compareTo( test ));
        assertEquals( 0, test.compareTo( Location.fromStr( Highlight, "X", "20", 30) ));
        assertEquals( -1, test.compareTo( Location.fromStr( Note, "X", "20", 30) ));
        assertEquals( 1, test.compareTo( Location.fromStr( Highlight, "IX", "20", 30) ));
        assertEquals( -1, test.compareTo( Location.fromStr( Highlight, "X", "21", 30) ));
        assertEquals( 1, test.compareTo( Location.fromInt( Highlight, 10, 20, 29) ));
    }
}