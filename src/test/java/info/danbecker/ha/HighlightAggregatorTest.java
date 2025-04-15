package info.danbecker.ha;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


import static org.junit.jupiter.api.Assertions.*;

public class HighlightAggregatorTest {
    public enum TestType { Equals, StartsWith, Contains }

    @Test
    public void testKindle1() throws IOException {
        String[] args = { "-inPath", "src/test/resources", "-inFile", "testNotebook1.html" };
        Path inPath = Paths.get( args[1] );
        assertTrue( Files.isReadable( inPath ));
        assertTrue( Files.isDirectory( inPath ));
        assertTrue( Files.isReadable( Paths.get( args[1] + "/" + args[ 3 ] )));
        HighlightAggregator.main( args );

        String resultFileName = args[ 1 ] + "/testNotebook1Aggregated.html";
        Path resultPath = Paths.get( resultFileName  );
        assertTrue( Files.exists( resultPath ));
        assertTrue( Files.isReadable( resultPath ));

        // Read document DOM
        Document doc = Jsoup.parse( resultPath );  // not autoclose
        // Test various doc elements
        // Equals sectionHeading text
        String[] expected = { "Section Heading 1", "Section Heading 2", "Section Heading 3", "Index - Section Heading" };
        assertElements( doc, "h2.sectionHeading", TestType.Equals, expected );
        // Equals chapterHeading text
        expected = new String[] { "Chapter 1. Num and Text", "Chapter 2. Second Chapter", "Chapter 3. Third Chapter",
            "Chapter 4. Fourth Chapter", "Chapter 5. Last Chapter" };
        assertElements( doc, "h3.chapterHeading", TestType.Equals, expected );
        // StartsWith aggr text
        expected = new String[] {"c0-a1 ", "c0-a2 ", "c0-a3 ", "c0-a4 ", "c1-a5 ", "c1-a6 ",
                "c2-a7 ", "c2-a8 ", "c3-a9 ", "c4-a10 ", "c5-a11 ", "c?-a12 " };
        assertElements( doc, "div.noteText", TestType.StartsWith, expected );
        // Contains aggr done, event start text
        String defEnd = "done. (e";
        expected = new String[] { " done.) (e", defEnd, defEnd, defEnd, defEnd, defEnd,
                " done.” (e", defEnd, defEnd, defEnd, defEnd, defEnd, };
        assertElements( doc, "div.noteText", TestType.Contains, expected );
    }
    @Test
    public void testKindle2() throws IOException {
        String[] args = { "-inPath", "src/test/resources", "-inFile", "testNotebook2.html" };
        Path inPath = Paths.get( args[1] );
        assertTrue( Files.isReadable( inPath ));
        assertTrue( Files.isDirectory( inPath ));
        assertTrue( Files.isReadable( Paths.get( args[1] + "/" + args[ 3 ] )));
        HighlightAggregator.main( args );

        String resultFileName = args[ 1 ] + "/testNotebook2Aggregated.html";
        Path resultPath = Paths.get( resultFileName  );
        assertTrue( Files.exists( resultPath ));
        assertTrue( Files.isReadable( resultPath ));

        // Read document DOM
        Document doc = Jsoup.parse( resultPath );  // not autoclose
        // Test various doc elements
        // Equals chapterHeading text
        String[] expected = { "Chapter I", "Chapter II" };
        assertElements( doc, "h3.chapterHeading", TestType.Equals, expected );
        // StartsWith aggr text
        expected = new String[] { "“Who is he?” ", "an order had come from Shamil " };
        assertElements( doc, "div.noteText", TestType.StartsWith, expected );
        // Contains aggr done, event start text
        expected = new String[] { "Shamil in Vedén. (e0-4,cI", "Hadji Murád, (e5-6,cII" };
        assertElements( doc, "div.noteText", TestType.Contains, expected );
    }

    /** A method for testing lots of css element text. */
    public static void assertElements( Document doc, String cssSelector, TestType testType, String [] expected ) {
        assertNotNull( doc );
        assertNotNull( cssSelector );
        Elements elements = doc.select( cssSelector );
        assertEquals( expected.length, elements.size(),
            String.format( "Doc \"%s\" test %d assert lengths failed. Tests length %d != elements length %d.%n",
                cssSelector, 0, expected.length, elements.size()));
        int testi = 1;
        for (Element sectionHeading : elements) {
            String message = String.format( "Doc \"%s\" test %d assert %s \"%s\" failed.%n",
                cssSelector, testi, testType.toString(), expected[ testi-1 ]);
            switch ( testType ) {
                case Equals -> assertEquals( expected[ testi-1 ], sectionHeading.text(), message);
                case StartsWith -> assertTrue( sectionHeading.text().startsWith( expected[ testi-1 ] ), message);
                case Contains -> assertTrue( sectionHeading.text().contains( expected[ testi-1 ] ), message);
            }
            testi++;
        }
    }
}