package info.danbecker.ha;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class StreamTest {
    @Test
    public void testFlatMapDelete() {
        var stream = Stream.of("one","two","three","four" );
        List<String> list = stream
          .flatMap(s -> {
              if (s.length() <= 4) {
                  return Stream.of(s);
              } else {
                  return Stream.of();
              }
          })
         .toList();
        assertIterableEquals( List.of("one", "two", "four"), list );
    }

    @Test
    public void testFlatMapAppend() {
        var stream = Stream.of("one","two","three","four" );
        List<String> list = stream
            .flatMap(s -> {
                if (s.equals("two")) {
                    return Stream.of("A", s);
                } else if (s.equals("three")) {
                    return Stream.of(s, "B");
                } else {
                    return Stream.of(s);
                }
            })
            .toList();
        assertIterableEquals( List.of("one", "A", "two", "three", "B", "four"), list );
    }

    @Test
    public void testJDK22GatherersWindowFixed() {
        var stream = Stream.of("a","b","c","d","e","f","g","h","i","j" );
        String output = stream
            .gather(Gatherers.windowFixed(3)) // JDK 22+
            .map(window -> String.join("", window))
            .collect( Collectors.joining("\n---\n"));
        assertEquals( "abc\n---\ndef\n---\nghi\n---\nj", output );
    }

    @Test
    public void testJDK22GatherersScan() {
        var list =
        Stream.of(1,2,3,4,5,6,7,8,9)
            .gather(Gatherers.scan(
                () -> 0, // initial state Supplier
                (current, next) -> current + next)) // combiner BiFunction
            .toList();
        assertIterableEquals( List.of(1,3,6,10,15,21,28,36,45), list );
    }

    @Test
    public void testJDK22GatherersFold() {
        var semicolonSeparated =
        Stream.of(1,2,3,4,5,6,7,8,9)
            .gather(Gatherers.fold(
                () -> "", // initial state Supplier
                (result, element) -> { // combiner BiFunction
                    if (result.isEmpty())
                        return element.toString();
                    return result + ";" + element;
                    }
                ))
           .findFirst()
           .get();
        assertEquals( "1;2;3;4;5;6;7;8;9", semicolonSeparated );
    }
}