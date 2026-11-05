package Mistral.loader;

public final class LibrarySpec {
    public final String fileName, url, sha256;
    public LibrarySpec(String fileName, String url, String sha256) {
        if (!fileName.matches("[A-Za-z0-9._-]+\\.jar") || !sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("无效依赖定义");
        this.fileName = fileName;
        this.url = url;
        this.sha256 = sha256;
    }
}
