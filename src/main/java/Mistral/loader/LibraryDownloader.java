package Mistral.loader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.jar.JarFile;

public final class LibraryDownloader {
    private static final long MAX_BYTES = 32L * 1024 * 1024;
    private final int connectTimeout, readTimeout, attempts;
    public LibraryDownloader() { this(10000, 30000, 2); }
    public LibraryDownloader(int connectTimeout, int readTimeout, int attempts) {
        this.connectTimeout = connectTimeout; this.readTimeout = readTimeout; this.attempts = attempts;
    }

    public void ensure(Path directory, List<LibrarySpec> libraries, Consumer<String> log) throws IOException {
        Files.createDirectories(directory);
        for (LibrarySpec library : libraries) {
            Path target = directory.resolve(library.fileName);
            if (Files.isRegularFile(target) && library.sha256.equals(sha256(target))) continue;
            if (Files.exists(target) && !Files.isRegularFile(target)) throw new IOException("依赖路径不是文件：" + target);
            log.accept("正在准备依赖：" + library.fileName);
            IOException failure = null;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                Path temporary = Files.createTempFile(directory, "." + library.fileName + "-", ".part");
                try {
                    download(library.url, temporary);
                    if (!library.sha256.equals(sha256(temporary))) throw new IOException("SHA-256 校验失败");
                    try (JarFile jar = new JarFile(temporary.toFile())) {
                        if (!jar.entries().hasMoreElements()) throw new IOException("下载的 JAR 为空");
                    }
                    try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
                    failure = null;
                    break;
                } catch (IOException exception) {
                    failure = exception;
                    if (attempt < attempts) log.accept("依赖下载失败，正在重试：" + library.fileName);
                } finally { Files.deleteIfExists(temporary); }
            }
            if (failure != null) throw new IOException("依赖准备失败：" + library.fileName + "；可从 " + library.url + " 手动下载到 " + target, failure);
        }
    }

    private void download(String source, Path target) throws IOException {
        URI current = URI.create(source);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        for (int redirect = 0; redirect <= 5; redirect++) {
            if (!"https".equalsIgnoreCase(current.getScheme()) && !"http".equalsIgnoreCase(current.getScheme())) throw new IOException("不支持的依赖 URL");
            HttpURLConnection connection = (HttpURLConnection) current.toURL().openConnection();
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "AutoLoginAuthme-LibraryLoader/3.0.0");
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) throw new IOException("重定向缺少 Location");
                    URI next = current.resolve(location);
                    if ("https".equalsIgnoreCase(current.getScheme()) && !"https".equalsIgnoreCase(next.getScheme())) throw new IOException("拒绝降级依赖下载协议");
                    current = next;
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + status);
                long expected = connection.getContentLengthLong();
                if (expected > MAX_BYTES) throw new IOException("依赖超过大小限制");
                try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[16384];
                    int count;
                    long total = 0;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_BYTES || System.nanoTime() > deadline) throw new IOException("依赖下载超过大小或时间限制");
                        output.write(buffer, 0, count);
                    }
                    if (expected >= 0 && expected != total) throw new IOException("依赖下载不完整");
                }
                return;
            } finally { connection.disconnect(); }
        }
        throw new IOException("依赖重定向次数过多");
    }

    public static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
        return result.toString();
    }
}
