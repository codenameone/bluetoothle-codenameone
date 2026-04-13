package com.codename1.bluetoothle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;

public class SharedSourceDeduplicationTest {

    @Test
    public void commonAndAndroidDoNotContainDuplicateJavaSources() throws IOException {
        Path repoRoot = Paths.get("..").toAbsolutePath().normalize();
        Path commonJavaRoot = repoRoot.resolve("common/src/main/java");
        Path androidJavaRoot = repoRoot.resolve("android/src/main/java");

        Assert.assertTrue("Expected common java sources", Files.isDirectory(commonJavaRoot));
        Assert.assertTrue("Expected android java sources", Files.isDirectory(androidJavaRoot));

        Set<String> commonSources = listRelativeJavaSources(commonJavaRoot);
        Set<String> androidSources = listRelativeJavaSources(androidJavaRoot);

        Set<String> duplicates = new TreeSet<String>(commonSources);
        duplicates.retainAll(androidSources);

        Assert.assertTrue("Duplicate Java sources found in common and android: " + duplicates, duplicates.isEmpty());
    }

    private static Set<String> listRelativeJavaSources(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .collect(Collectors.toSet());
        }
    }
}
