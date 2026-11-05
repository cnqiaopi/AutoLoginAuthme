package Mistral.loader;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LibraryDownloaderTest {
    @TempDir Path directory;
    HttpServer server;
    byte[] payload;
    int status = 200;
    AtomicInteger requests = new AtomicInteger();
    LibrarySpec spec;
    LibraryDownloader downloader = new LibraryDownloader(1000, 1000, 2);

    @BeforeEach void start() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(output)) { jar.putNextEntry(new JarEntry("test.txt")); jar.write(new byte[]{1, 2, 3}); jar.closeEntry(); }
        payload = output.toByteArray();
        Path reference = directory.resolve("reference.jar"); Files.write(reference, payload);
        String hash = LibraryDownloader.sha256(reference); Files.delete(reference);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/test.jar", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(status, payload.length);
            exchange.getResponseBody().write(payload); exchange.close();
        });
        server.createContext("/redirect", exchange -> { exchange.getResponseHeaders().add("Location", "/test.jar"); exchange.sendResponseHeaders(302, -1); exchange.close(); });
        server.start();
        spec = new LibrarySpec("test.jar", "http://127.0.0.1:" + server.getAddress().getPort() + "/test.jar", hash);
    }
    @AfterEach void stop() { server.stop(0); }
    void ensure() throws Exception { downloader.ensure(directory, Collections.singletonList(spec), ignored -> { }); }
    void noPartial() throws Exception { try (java.util.stream.Stream<Path> files = Files.list(directory)) { assertFalse(files.anyMatch(path -> path.toString().endsWith(".part"))); } }

    @Test void firstRunDownloadsAndChecksLibrary() throws Exception { ensure(); assertArrayEquals(payload, Files.readAllBytes(directory.resolve("test.jar"))); assertEquals(1, requests.get()); noPartial(); }
    @Test void validCacheNeedsNoNetwork() throws Exception { Files.write(directory.resolve("test.jar"), payload); server.stop(0); ensure(); assertEquals(0, requests.get()); }
    @Test void corruptCacheIsReplacedOnlyAfterValidDownload() throws Exception { Files.write(directory.resolve("test.jar"), new byte[]{0}); ensure(); assertArrayEquals(payload, Files.readAllBytes(directory.resolve("test.jar"))); noPartial(); }
    @Test void hashFailureRetriesAndPreservesExistingFile() throws Exception {
        Files.write(directory.resolve("test.jar"), new byte[]{42}); payload = new byte[]{1, 2};
        assertThrows(java.io.IOException.class, this::ensure);
        assertEquals(2, requests.get()); assertArrayEquals(new byte[]{42}, Files.readAllBytes(directory.resolve("test.jar"))); noPartial();
    }
    @Test void httpFailureRetriesAndCleansTemporaryFiles() throws Exception { status = 404; assertThrows(java.io.IOException.class, this::ensure); assertEquals(2, requests.get()); noPartial(); assertFalse(Files.exists(directory.resolve("test.jar"))); }
    @Test void relativeRedirectIsSupported() throws Exception { spec = new LibrarySpec(spec.fileName, spec.url.replace("/test.jar", "/redirect"), spec.sha256); ensure(); assertEquals(1, requests.get()); }
    @Test void directoryCannotBeOverwrittenAsJar() throws Exception { Files.createDirectories(directory.resolve("test.jar")); assertThrows(java.io.IOException.class, this::ensure); assertEquals(0, requests.get()); }
    @Test void filenamesCannotEscapeLibraryDirectory() { assertThrows(IllegalArgumentException.class, () -> new LibrarySpec("../other.jar", spec.url, spec.sha256)); }
}
