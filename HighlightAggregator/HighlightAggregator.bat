@ECHO OFF
REM Associate with file to run from Windows Explorer
echo HighlightAggregator with file path %1
pause
REM Create a Java JAR with all dependencies using maven-assembly-plugin and assembly:single target.
java -jar e:/computer/git/beckerdo/HighlightAggregator/target/HighlightAggregator-1.0-SNAPSHOT-jar-with-dependencies.jar -inFile %1
pause