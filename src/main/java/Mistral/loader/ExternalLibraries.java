package Mistral.loader;

import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ExternalLibraries {
    public static final List<LibrarySpec> REQUIRED = Collections.unmodifiableList(Arrays.asList(
        library("com/zaxxer/HikariCP/4.0.3/HikariCP-4.0.3.jar", "7c024aeff1c1063576d74453513f9de6447d8e624d17f8e27f30a2e97688c6c9"),
        library("org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar", "d3ef575e3e4979678dc01bf1dcce51021493b4d11fb7f1be8ad982877c16a1c0"),
        library("org/slf4j/slf4j-jdk14/1.7.36/slf4j-jdk14-1.7.36.jar", "5bf64690af4e59876b8902bb0db3dd39c686a40abfed97d3837eeeec7a2922ac"),
        library("com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar", "d77962877d010777cff997015da90ee689f0f4bb76848340e1488f2b83332af5"),
        library("com/google/protobuf/protobuf-java/3.25.1/protobuf-java-3.25.1.jar", "48a8e58a1a8f82eff141a7a388d38dfe77d7a48d5e57c9066ee37f19147e20df")
    ));
    private ExternalLibraries() { }
    private static LibrarySpec library(String path, String hash) {
        return new LibrarySpec(path.substring(path.lastIndexOf('/') + 1), "https://repo.maven.apache.org/maven2/" + path, hash);
    }
    public static URL[] urls(URL ownJar, Path directory) throws java.net.MalformedURLException {
        URL[] urls = new URL[REQUIRED.size() + 1];
        urls[0] = ownJar;
        for (int index = 0; index < REQUIRED.size(); index++) urls[index + 1] = directory.resolve(REQUIRED.get(index).fileName).toUri().toURL();
        return urls;
    }
}
