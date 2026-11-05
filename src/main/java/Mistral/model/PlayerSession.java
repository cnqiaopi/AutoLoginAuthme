package Mistral.model;

/** 数据库快照；时间单位为 Unix 秒，与原版表结构一致。 */
public final class PlayerSession {
    private final String ip;
    private final String brand;
    private final long authenticatedAt;

    public PlayerSession(String ip, String brand, long authenticatedAt) {
        this.ip = ip == null ? "" : ip;
        this.brand = brand == null ? "" : brand;
        this.authenticatedAt = authenticatedAt;
    }

    public String getIp() { return ip; }
    public String getBrand() { return brand; }
    public long getAuthenticatedAt() { return authenticatedAt; }
}
