package info.danbecker.ha;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static info.danbecker.ha.Location.NoteType.*;
import static java.lang.String.format;

/**
 * Highlight Aggregator
 * <p>
 * A utility to take exported highlights from an electronic book
 * and format the highlights in a much more readable and succinct manner.
 * <p><
 * Start with exported highlights from an electronic book reader, Kindle, Calibre, Libby, HTML, PDF
 *    - editing profile actions based on each reader, document type
 *    - user preferences such as fonts, styles
 *    - KindleAggregate, CalibreSimplify
 *    - Add Remove Images
 * Perform basic actions on the document object model (DOM).
 *    - fix up bad HTML such as weird XML artifacts or mismatched tags
 *    - fix up line edits such as bad spacing, weird punctuation or characters
 *    - add, remove, edit HTML text such as reader notes and highlights
 *    - add, remove, edit CSS class styles
 *       - remove text-align
 *       - change font-family
 *    - aggregate proximal highlights
 *       - specify merge distance via chapters, pages or locations or all
 *          - 0 means if chapter/page/location changes, end aggregation
 *          - number means distance (from start or end of previous item)
 *          - large number or special marker means no end with this chapter/page/location change
 *       - specify aggregation locations (page 14, locations 143-176) or (chapter 1, pages 1-22, locations 1-240)
 *       - aggregation emit - container options, lists/bullets, paragraphs, tables/rows, Moustacne templates, others
 *
 *  Output results to HTML, PDF, text
 *
 * @author <a href="mailto://dan@danbecker.info>Dan Becker</a>
 */
public class HighlightAggregator {
    public enum ProximityType { AggrStartLoc, HighStartLoc, HighEndLoc, Never }
    static Logger LOGGER = Logger.getLogger(HighlightAggregator.class.getName());
    public static final String TEMP_PREFIX = "temp";

    // TO DO
    // Minor Consider getting chapter numbers from sectionHeading as in "Anglo-Saxons" book
    // Minor Update ProximityType for relative to beginning or to last or don't care
    // Major Aggregation container options, lists/bullets, paragraphs, tables/rows, others
    // Major Use Moustache templates to output section/chapter/page/location texts.
    // Major insert named images into Notebook location - "Fig01-0725AD-MapOfBritain-C05-P278.png"
    // Minor line + N deletes (delete this line and the next N)
    // Major Move hard coded line edits, deletes, swaps to configuration file.
    // Major Software only works with English hard coded text sentinels ("Chapter", "Location", etc.)
    // Major PDF annotation sources
    // Minor deal with different color highlights

    // Some configuration parameters via JCommander.org
    public static class Options {
        @Parameter(names = "-chapProx", description = "Proximity to previous. Greater than this causes new aggregation")
        public int chapProx = 0;
        @Parameter(names = "-pageProx", description = "Proximity to previous. Greater than this causes new aggregation")
        public int pageProx = 0;
        @Parameter(names = "-locProx", description = "Proximity to previous. Greater than this causes new aggregation")
        public int locProx = 5;
        @Parameter(names = "-inPath", description = "Input path. A directory containing books.")
        public String inPath = "";
        @Parameter(names = "-inFile", description = "Input file. A file with notes and highlights.")
        public String inFile = "";
        @Parameter(names = "-nameContains", description = "Contains pattern for file names. A file with notes and highlights.")
        public String nameContains = "Notebook";
        @Parameter(names = "-nameEndsWith", description = "Ends with pattern for file names. A file with notes and highlights.")
        public String nameEndsWith = ".html";
        @Parameter(names = "-outPath", description = "Explicit text for output file name.")
        public String outPath = "";
        @Parameter(names = "-outFile", description = "Explicit text for output file name.")
        public String outFile = "";
        @Parameter(names = "-outputMarker", description = "Appended name text for output file names.")
        public String outputMarker = "Aggregated";
    }

    /** Run aggregation on the given configuration.
     * This method manages config options, filenames, etc.
     * @param args commandline args
     * @throws IOException when file does not exist or is unreadable
     */
    public static void main(String[] args) throws IOException {
        LOGGER.setLevel( Level.ALL );
        System.setProperty("java.util.logging.SimpleFormatter.format","%1$tF %1$tT %5$s%6$s");
        LOGGER.info( "Highlight Aggregator 1.0.0 by Dan Becker\n" );

        // Process configuration args
        Options opt = new Options();
        List<String> inputFiles = new ArrayList<>();
        if (0 < args.length) {
            processCommandOptions( args, opt, inputFiles );
        }

        // Work on a list of paths/files.
        for ( String inputFile : inputFiles ) {
            // Add,remove, edit lines
            String action = "lineEdits";
            File tempFile = File.createTempFile(TEMP_PREFIX, null);
            System.out.format("%s from \"%s\" to \"%s\"\n", action, inputFile, tempFile.getPath());
            lineEdits(inputFile, tempFile.getPath());

            // Aggregate noteHeading texts by proximity.
            action = "aggregateNoteText";
            String cssSelector = "h3.noteHeading";
            // String cssSelector = "[class*=Heading]";  // any class containing Heading
            String outputPathStr = opt.outFile;
            if (null == opt.outFile || opt.outFile.isEmpty() ) {
                // Create and output name if not provided.
                outputPathStr = inputFile.replace(".html", "");
                outputPathStr = outputPathStr + opt.outputMarker + ".html";
            }
            System.out.format("%s from \"%s\" to \"%s\", with selector %s%n", action, tempFile.getPath(), outputPathStr, cssSelector);
            aggregateNoteText(tempFile.getPath(), outputPathStr, cssSelector, opt.chapProx,  opt.pageProx, opt.locProx);
        }
    }

    /**
     * Process command line options from the String arguments.
     * Return list of options and list of input file paths.
     * <p>
     * Note that Windows file names passed from Explorer, to a batch runner, to this
     * throw java.nio.file.InvalidPathException: Illegal char <:> at index 2: /E:\books\
     * So there is some code to remove the leading / before the drive letter and colon.
     */
    public static void processCommandOptions( String[] args, Options opt, List<String> inputFiles ) throws IOException {
        JCommander.newBuilder()
                .addObject(opt)
                .build()
                .parse(args);

        String pathDelim = "/";
        boolean IS_WINDOWS = System.getProperty( "os.name" ).contains( "indow" );
        if ( IS_WINDOWS && opt.inPath.startsWith( pathDelim ))
            opt.inPath = opt.inPath.substring(1);
        boolean isInPathReadable = Files.isDirectory( Paths.get( opt.inPath ));
        System.out.printf( "Input path \"%s\" %s readable.%n", opt.inPath, isIsNot( isInPathReadable ));
        boolean isInPathDir = Files.isDirectory( Paths.get( opt.inPath ));
        System.out.printf( "Input path \"%s\" %s a directory.%n", opt.inPath, isIsNot( isInPathDir ));
        boolean dirReadable = isInPathReadable && isInPathDir;

        if ( IS_WINDOWS && opt.inFile.startsWith( pathDelim ))
            opt.inFile = opt.inFile.substring(1);
        boolean fileReadable = Files.isReadable( Paths.get( opt.inFile )) &&
                Files.isRegularFile( Paths.get( opt.inFile ));
        System.out.printf( "Input file \"%s\" %s readable.%n", opt.inFile, isIsNot( fileReadable ));

        String comboPathStr = opt.inFile;
        if ( isInPathDir ) {
            if ( opt.inPath.endsWith( pathDelim ) || opt.inFile.startsWith( pathDelim ))
                comboPathStr = opt.inPath + opt.inFile;
            else
                comboPathStr = opt.inPath + pathDelim + opt.inFile;
        }
        if ( IS_WINDOWS && comboPathStr.startsWith( pathDelim ))
            comboPathStr = comboPathStr.substring(1);
        Path comboPath = Paths.get( comboPathStr );
        boolean comboReadable = Files.isReadable( comboPath ) && Files.isRegularFile( comboPath );
        System.out.printf( "Input file \"%s\" %s readable.%n", comboPathStr, isIsNot( comboReadable ));

        if (comboReadable) {
            // Use comboPath
            inputFiles.add( comboPathStr );
        }
        else if (fileReadable) {
            // Use inputPath
            inputFiles.add( opt.inFile );
        }
        else if (dirReadable) {
            // dirReadable - list files there
            File[] files = new File(opt.inPath).listFiles();
            if ( null != files ) {
                for (File file : files) {
                    if (file.isFile() && file.canRead()) {
                        if (file.getName().contains(opt.nameContains) &&
                                file.getName().endsWith(opt.nameEndsWith) &&
                                // skip over outputs we generated here.
                                !file.getName().contains(opt.outputMarker)) {
                            LOGGER.info( format( "File \"%s\" is readable.%n", file.getPath() ));
                            inputFiles.add(file.getPath());
                        }
                    }
                }
            }
        }
        // otherwise - message
        // LOGGER.info( format( "Input path \"%s\", file \"%s\", contains \"%s\", endsWith \"%s\" found %d items.%n",
        //        opt.inPath, opt.inFile, opt.nameContains, opt.nameEndsWith, inputFiles.size()));
        LOGGER.info( format( "Proximities chap %d, page %d, loc %d%n",
                opt.chapProx, opt.pageProx, opt.locProx ));

        // Remove non Kindle files.
        List<String> removes = new ArrayList<>();
        for ( String fileName : inputFiles ) {
            if ( !fileCheckKindle( fileName  )) {
                LOGGER.info( format( "File \"%s\" does not appear to be a Kindle notebook. Skipping.%n", fileName ));
                removes.add( fileName ); // avoid inputFiles remove (concurrency exception)
            }
        }
        for ( String fileName : removes ) {
            inputFiles.remove( fileName );
        }
    }

    /** Change boolean to "is" or "is not" String. */
    public static String isIsNot( boolean is ) {
        return is ? "is" : "is not";
    }

    /**
     * Checks if the given file is a Kindle notebook.
     * @return true for Kindle notebook, false otherwise
     */
    public static boolean fileCheckKindle( String fileName  ) throws IOException {
        Document doc = Jsoup.parse(Paths.get(fileName));  // not autoclose
        Elements cssStyle = doc.select("style");
        if (cssStyle.isEmpty()) return false;
        DataNode cssNode = cssStyle.dataNodes().getFirst();
        String cssText = cssNode.getWholeData();
        return cssText.contains("bookTitle") && cssText.contains("bodyContainer");
    }

    /**
     * Perform fix ups to make DOM more compliant and aid DOM edits.
     * <p>>
     * Kindle book notes have mismatched h3 and div endings as in this line
     * <h3 class='noteHeading'>Note - I &gt; Page 11 &middot; Location 116</div><div class='noteText'>This is a note</h3>
     *
     * @param inputPath Files lines path for input lines
     * @param outputPath output path
     * @throws IOException for reading writing issues
     */
    public static void lineEdits(String inputPath, String outputPath) throws IOException {
        try (Stream<String> input = Files.lines( Paths.get( inputPath ));
             PrintWriter output = new PrintWriter( outputPath, StandardCharsets.UTF_8)) {
            input
                .filter(Predicate.not(HighlightAggregator::deleteLines)) // perform deletes
                .map(HighlightAggregator::updateLines) // perform line updates
                .map(s -> swap( s, "</h3>", "</div>" ) ) // perform line swaps
                .forEachOrdered(output::println);
        }
    }

    /** List of deletes to perform on the line. */
    static final List<String> deleteLines = List.of(
            "<?xml version="
    );
    /** Deletes a line which matches any "contains" test.
     * @param line the line which might be deleted
     * @return true if line should be deleted, false to keep line
     */
    public static boolean deleteLines( String line ) {
        for ( String substr : deleteLines ) {
            if ( line.contains( substr ) ) return true;
        }
        return false;
    }

    /** List of updates to perform on a line. */
    static final Map<String, String> updateLines = Map.ofEntries(
            new AbstractMap.SimpleEntry<>("<!DOCTYPE html PUBLIC ",
                    "<!DOCTYPE html>"),
            new AbstractMap.SimpleEntry<>("<html xmlns=",
                    "<html lang=\"en\">"),
            new AbstractMap.SimpleEntry<>("<meta http-equiv=",
                    "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">")
    );

    /** Updates a line that startsWith the given key, and
     * replaces it with the given entry String.
     * @param line the String which might be replaced.
     * @return if no updates return the original String, otherwise an updated String
     */
    public static String updateLines( String line ) {
        for (Map.Entry<String,String> entry : updateLines.entrySet() ) {
            if ( line.startsWith( entry.getKey() ) ) {
                return entry.getValue();
            }
        }
        return line;
    }

    /** Swaps to substrings in a given String and returns the new String
     * @param str target for swap
     * @param substr1 the first item to swap
     * @param substr2 the second item to swap
     * @return new String with ubstr swaps
     */
    public static String swap( String str, String substr1, String substr2 ) {
        if ( str.contains( substr1 ) && str.contains( substr2 ) ) {
            // Swap locations of mismatch end tags.
            str = str.replaceAll( substr1, "</temp>" );
            str = str.replaceAll( substr2, substr1 );
            str = str.replaceAll("</temp>", substr2 );
        }
        return str;
    }

    /** Perform a number of spacing and text improvements.
     * @param str String with spaced punctuation
     * @return sanitized text
     */
    public static String editKindleText( String str  ) {
        // remove pre spaces
        String puncs = ",;:!’”";
        for ( int i = 0; i < puncs.length(); i++ ) {
            String punc = puncs.substring( i, i+1);
            str = str.replaceAll(" " + punc, punc);
        }
        // Special reg-ex meaning
        str = str.replaceAll(" \\.", ".");
        str = str.replaceAll(" \\?", "?");
        str = str.replaceAll(" \\)", ")");
        // remove post spaces
        puncs = "“";
        for ( int i = 0; i < puncs.length(); i++ ) {
            String punc = puncs.substring( i, i+1);
            str = str.replaceAll(punc + " ", punc);
        }
        // Special reg-ex meaning
        str = str.replaceAll(" \\(.", "(");
        return str;
    }

    /**
     * Use the cssSelector to locate elements and aggregate them.
     * <p>
     * The values for chapter, page, and location proximities
     * determine when an aggregation is output and stored in the first
     * inputText element in the DOM. The next aggregation begins.
     *
     * @param inputPath the file from which the document DOM is read
     * @param outputPath the location for which the aggregations are saved
     * @param cssSelector the CSS selector for "element.class" containing items (typically noteText)
     * @param chapProx the change in chapter that will trigger saving the aggregation
     * @param pageProx the change in pages that will trigger saving the aggregation
     * @param locProx the change in location that will trigger saving the aggregation
     * @throws IOException when the input or output files do not exist or have read/write problems
     */
    public static void aggregateNoteText(String inputPath, String outputPath, String cssSelector,
                                         int chapProx, int pageProx, int locProx ) throws IOException {
        // Init variables for aggregation
        int elCount = 0;
        int aggrStartIndex = 0;
        StringBuilder aggrSB = new StringBuilder();
        Element elAggrStart = null; // noteText element for aggrSB
        Location aggrStartLoc = null;

        Location prevLoc = null;
        Location currLoc;
        Element elCurrNoteText;

        // Read document DOM
        Document doc = Jsoup.parse(Paths.get(inputPath));  // not autoclose
        Elements elements = doc.select(cssSelector);

        // For each element found
        for (Element element : elements) {
            // Get noteHeading location and noteText text.
            String headingText = element.text();
            currLoc = Location.fromKindle(headingText);

            elCurrNoteText = element.nextElementSibling();
            // If next DOM sibling is class "noteText", process it, else ignore.
            if ( null != elCurrNoteText ) {
                String attrNoteTextClass = elCurrNoteText.attr("class"); // Get attr by name
                if ("noteText".equals(elCurrNoteText.attr("class"))) {
                    // Fix up noteText text
                    String noteText = editKindleText(elCurrNoteText.text());
                    if (Note == currLoc.type())
                        noteText = "(Note: " + noteText + ")";

                    if (null == prevLoc) {
                        // We have some info. First loop initialization.
                        // Trigger a chapter change to get a chapter change.
                        int smallChap = currLoc.chapter() > 0 ? currLoc.chapter() - 1 : 0;
                        prevLoc = Location.fromInt(currLoc.type(), smallChap, currLoc.page(), currLoc.location());
                        aggrSB = new StringBuilder();
                        aggrStartLoc = currLoc;
                        elAggrStart = elCurrNoteText;
                    }

                    // Determine chapter, page, and location triggers.
                    boolean chapChange = prevLoc.chapter() + chapProx < currLoc.chapter();
                    boolean pageChange = prevLoc.page() + pageProx < currLoc.page();
                    boolean locChange = prevLoc.location() + locProx < currLoc.location();

                    // Flush aggregation if needed.
                    if (chapChange || pageChange || locChange) {
                        // Save aggregation text to first noteTextElement. Append location ranges.
                        if (null != elAggrStart)
                            flushAggregation(elAggrStart, aggrSB, aggrStartLoc, aggrStartIndex, prevLoc, elCount - 1);

                        // Start a new aggregation
                        aggrSB = new StringBuilder(noteText);
                        elAggrStart = elCurrNoteText;
                        aggrStartLoc = currLoc;
                        aggrStartIndex = elCount;

                        // Some books do not have chapters.
                        if (chapChange && !currLoc.chapterStr().isEmpty()) {
                            String chapStr = currLoc.chapterStr();
                            if (!chapStr.startsWith("Chapter"))
                                chapStr = "Chapter " + chapStr;
                            System.out.println(chapStr);
                            element.before(format("<h3 class='chapterHeading'>%s</h3>", chapStr));
                        }
                    } else {
                        // Append to current aggregation
                        if (!aggrSB.isEmpty())
                            aggrSB.append(" "); // delimiter
                        aggrSB.append(noteText);
                        // Remove div.noteText if not the aggr start.
                        // Warning. Sometimes multiple highlights on same line have the same location.
                        // if (!elAggrStart.equals(elCurrNoteText))
                        if (!elCurrNoteText.equals(elAggrStart))
                            elCurrNoteText.remove();
                    }
                } else {
                    System.out.format("Warning, expected \"noteText\" class  at loc %s, found class %s%n", currLoc, attrNoteTextClass);
                }
            } else {
                System.out.format("Warning, heading \"%s\" at loc %s has no following note text (null next element).%n", headingText, currLoc);
            }
            // Done with noteHeading node, remove
            element.remove();

            // Ready for next element.
            elCount++;
            prevLoc = currLoc;
        } // element iteration

        // If no more notes or highlights, flush remaining aggregation text and locations.
        flushAggregation( elAggrStart, aggrSB, aggrStartLoc, aggrStartIndex, prevLoc, elCount);
        System.out.format("EditDOM element count: %d%n", elCount-1);

        // Update CSS style
        Element cssStyle = doc.select( "style" ).first();
        DataNode cssNode = Objects.requireNonNull(cssStyle).dataNodes().getFirst();
        if ( null != cssNode ) {
            String cssText = cssNode.getWholeData();
            String newCSS = editCSSText(cssText);
            cssNode.setWholeData(newCSS);
        }

        // Write edited document DOM
        try (PrintWriter output = new PrintWriter(outputPath, StandardCharsets.UTF_8)) {
            output.write(doc.outerHtml());
        }
    }

    /**
     * Flush the current aggregation text and location range.
     * Place the text in the given noteText element.
     * Append the given location range to the end.
     *
     * @param elNoteText the noteText element to place aggregation in
     * @param aggrSB the current aggregation
     * @param aggrStartLoc start location of aggregation
     * @param aggrStartEle start index of aggregation
     * @param prevLoc previous item location
     * @param prevEle previous item index
     */
    public static void flushAggregation(Element elNoteText, StringBuilder aggrSB, Location aggrStartLoc, int aggrStartEle, Location prevLoc, int prevEle ) {
        if ( null == elNoteText)
            throw new IllegalArgumentException( "No DOM element for aggregation note text provided.");

        // Place aggregated text in the target element.
        elNoteText.text(aggrSB.toString());

        // Create location range string with element indexes.
        String locRange = Location.range(aggrStartLoc, prevLoc);
        String eleRange = " (e" + aggrStartEle;
        if (aggrStartEle != prevEle) eleRange += "-" + prevEle;
        eleRange = eleRange + "," + locRange + ")";

        // Style the location string.
        String styledRange = "<span class=\"locationStyle\">" + eleRange + "</span>";
        elNoteText.append( styledRange );
        System.out.format("%s %s%n", aggrSB, eleRange );
    }

    /**
     * Perform a number of editing tasks on
     * the CSS style text
     * @param cssStr String with spaced punctuation
     */
    public static String editCSSText( String cssStr  ) {
        // remove bodyContainer styles
        // add chapterHeading style
        // add location style
        String newStr = cssStr.replace("    font-family: Arial, Helvetica, sans-serif;\r\n    text-align: center;\r\n    padding-left: 32px;\r\n    padding-right: 32px;","");

        newStr = newStr + "\n.chapterHeading {\n    padding: 0px;\n}\n";
        newStr = newStr + "\n.locationStyle {\n    color: #999999;\n    font-size: 18px;\n}\n";
        return newStr;
    }
}