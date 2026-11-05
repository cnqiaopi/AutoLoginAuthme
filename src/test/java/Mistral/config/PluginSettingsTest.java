package Mistral.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PluginSettingsTest {
    @Test void keepsLegacyDefaults() { PluginSettings settings = PluginSettings.load(new YamlConfiguration()); assertEquals(60, settings.maxMinutes); assertEquals(1000, settings.cacheSize); assertFalse(settings.allowOp); assertTrue(settings.database.jdbcUrl.contains("characterEncoding=UTF-8")); }
    @Test void rejectsNegativeCacheAndInvalidIntervals() { YamlConfiguration yaml = new YamlConfiguration(); yaml.set("performance.cache_max_size", -1); assertThrows(IllegalArgumentException.class, () -> PluginSettings.load(yaml)); yaml.set("performance.cache_max_size", 100); yaml.set("monitoring.monitor_interval", 0); assertThrows(IllegalArgumentException.class, () -> PluginSettings.load(yaml)); }
    @Test void validatesTimezoneAndMessageType() { YamlConfiguration yaml = new YamlConfiguration(); yaml.set("timezone", "Invalid/Zone"); assertThrows(Exception.class, () -> PluginSettings.load(yaml)); yaml.set("timezone", "Asia/Shanghai"); yaml.set("message_type", "bad"); assertThrows(IllegalArgumentException.class, () -> PluginSettings.load(yaml)); }
    @Test void doesNotReplaceUnrelatedAmpersands() { assertEquals("A&Z §a绿", PluginSettings.color("A&Z &a绿")); }
}
