package Mistral.compat;

import Mistral.loader.ExternalLibraries;
import Mistral.loader.LibrarySpec;
import Mistral.loader.LibraryDownloader;
import Mistral.loader.LibraryClassLoader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.sql.Driver;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import static org.junit.jupiter.api.Assertions.*;

class PackagedPluginIT {
    final java.io.File jar = Paths.get("target", "AutoLoginAuthme-3.0.0.jar").toFile();

    @Test void descriptorAndAllClassesUseExpectedNamesAndJava8Bytecode() throws Exception {
        int classes = 0;
        try (JarFile archive = new JarFile(jar)) {
            YamlConfiguration descriptor = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(archive.getInputStream(archive.getJarEntry("plugin.yml")), java.nio.charset.StandardCharsets.UTF_8));
            assertEquals("AutoLoginAuthme", descriptor.getString("name"));
            assertEquals("Mistral.AutoLoginAuthme", descriptor.getString("main"));
            assertNull(descriptor.get("api-version"));
            Enumeration<JarEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                classes++;
                assertTrue(entry.getName().startsWith("Mistral/"), entry.getName());
                try (DataInputStream input = new DataInputStream(archive.getInputStream(entry))) {
                    assertEquals(0xcafebabe, input.readInt()); input.readUnsignedShort();
                    assertTrue(input.readUnsignedShort() <= 52, "非 Java 8 类：" + entry.getName());
                }
            }
        }
        assertTrue(classes >= 30, "必须包含正式插件业务类");
        assertTrue(jar.length() < 500000, "第三方库必须放在外置 libs");
    }

    @Test void isolatedJarLoadsMysqlDriverAndLoggingWithoutServerLibraries() throws Exception {
        try (LibraryClassLoader loader = new LibraryClassLoader(ExternalLibraries.urls(jar.toURI().toURL(), Paths.get("target/distribution/AutoLoginAuthme/libs")), ClassLoader.getSystemClassLoader().getParent())) {
            Class<?> driverType = Class.forName("com.mysql.cj.jdbc.Driver", true, loader);
            Driver driver = (Driver) driverType.getDeclaredConstructor().newInstance();
            assertTrue(driver.acceptsURL("jdbc:mysql://127.0.0.1:3306/minecraft"));
            Class.forName("com.zaxxer.hikari.HikariConfig", true, loader).getDeclaredConstructor().newInstance();
            Class.forName("org.slf4j.impl.StaticLoggerBinder", true, loader);
            Class<?> loggerFactory = Class.forName("org.slf4j.LoggerFactory", true, loader);
            assertEquals(loader, loggerFactory.getClassLoader());
            assertNotNull(loggerFactory.getMethod("getLogger", String.class).invoke(null, "AutoLoginAuthme-isolation-test"));
            assertEquals(loader, Class.forName("com.google.protobuf.Message", true, loader).getClassLoader());
            java.util.ResourceBundle messages = java.util.ResourceBundle.getBundle("com.mysql.cj.LocalizedErrorMessages", java.util.Locale.ENGLISH, loader);
            assertTrue(messages.getKeys().hasMoreElements());
            assertNotNull(loader.getResource("META-INF/services/java.sql.Driver"));
            assertSame(loader, driverType.getClassLoader());
            Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread", true, loader).getMethod("uncheckedShutdown").invoke(null);
        }
    }

    @Test void distributionLibrariesMatchPinnedHashesAndPluginContainsNoThirdPartyClasses() throws Exception {
        try (JarFile archive = new JarFile(jar)) {
            assertNull(archive.getJarEntry("com/zaxxer/hikari/HikariConfig.class"));
            assertNull(archive.getJarEntry("com/mysql/cj/jdbc/Driver.class"));
            assertNull(archive.getJarEntry("org/slf4j/LoggerFactory.class"));
        }
        for (LibrarySpec library : ExternalLibraries.REQUIRED) {
            assertEquals(library.sha256, LibraryDownloader.sha256(Paths.get("target/distribution/AutoLoginAuthme/libs").resolve(library.fileName)));
            assertEquals(library.sha256, LibraryDownloader.sha256(Paths.get("libs").resolve(library.fileName)));
        }
    }

    @Test void businessClassesAndLifecycleAreLoadedFromCorrectLoader() throws Exception {
        try (LibraryClassLoader loader = new LibraryClassLoader(ExternalLibraries.urls(jar.toURI().toURL(), Paths.get("target/distribution/AutoLoginAuthme/libs")), getClass().getClassLoader())) {
            Class<?> application = loader.loadClass("Mistral.bootstrap.PluginApplication");
            assertSame(loader, application.getClassLoader());
            assertTrue(Mistral.loader.PluginLifecycle.class.isAssignableFrom(application));
            assertNotNull(application.getConstructor(org.bukkit.plugin.java.JavaPlugin.class));
            assertSame(loader, loader.loadClass("Mistral.listener.AuthMeListener").getClassLoader());
            assertNotNull(loader.loadClass("Mistral.listener.AuthMeListener").getDeclaredMethods());
            assertSame(org.bukkit.entity.Player.class, loader.loadClass("org.bukkit.entity.Player"));
            Mistral.loader.PluginLifecycle lifecycle = (Mistral.loader.PluginLifecycle) application
                .getConstructor(org.bukkit.plugin.java.JavaPlugin.class).newInstance(new Object[]{null});
            lifecycle.disable().get(3, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test void actualPoolKeepsOwnMysqlWhenThreadContextAndDriverManagerHaveForeignDriver() throws Exception {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Driver foreign = new com.mysql.cj.jdbc.Driver();
        java.sql.DriverManager.registerDriver(foreign);
        try (LibraryClassLoader loader = new LibraryClassLoader(ExternalLibraries.urls(jar.toURI().toURL(), Paths.get("target/distribution/AutoLoginAuthme/libs")), getClass().getClassLoader())) {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("database.username", "test-account");
            yaml.set("database.password", "test-p@ss:#'");
            Object settings = loader.loadClass("Mistral.config.PluginSettings")
                .getMethod("load", org.bukkit.configuration.file.FileConfiguration.class).invoke(null, yaml);
            Object database = settings.getClass().getField("database").get(settings);
            Method factory = loader.loadClass("Mistral.storage.JdbcSessionRepository").getDeclaredMethod("poolConfiguration", database.getClass());
            factory.setAccessible(true);
            Object config = factory.invoke(null, database);
            Class<?> configType = loader.loadClass("com.zaxxer.hikari.HikariConfig");
            Object source = configType.getMethod("getDataSource").invoke(config);
            assertSame(loader, source.getClass().getClassLoader());
            assertEquals("test-account", source.getClass().getMethod("getUser").invoke(source));
            assertEquals("test-p@ss:#'", source.getClass().getMethod("getPassword").invoke(source));
            Field driverField = source.getClass().getDeclaredField("mysqlDriver");
            driverField.setAccessible(true);
            assertSame(loader, driverField.get(null).getClass().getClassLoader());
            configType.getMethod("setMinimumIdle", int.class).invoke(config, 0);
            configType.getMethod("setInitializationFailTimeout", long.class).invoke(config, -1L);
            Object pool = loader.loadClass("com.zaxxer.hikari.HikariDataSource").getConstructor(configType).newInstance(config);
            try { assertSame(source, pool.getClass().getMethod("unwrap", Class.class).invoke(pool, source.getClass())); }
            finally { pool.getClass().getMethod("close").invoke(pool); }
            Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread", true, loader).getMethod("uncheckedShutdown").invoke(null);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            java.sql.DriverManager.deregisterDriver(foreign);
        }
    }

    /** 对最终 JAR 的每个 Bukkit/AuthMe 方法调用逐一核对当前 profile 的真实 API。 */
    @Test void everyPluginApiReferenceResolvesAgainstSelectedServerApi() throws Exception {
        try (JarFile archive = new JarFile(jar)) {
            Enumeration<JarEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("Mistral/") || entry.getName().startsWith("Mistral/lib/") || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = archive.getInputStream(entry)) {
                    new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                    if (!owner.startsWith("org/bukkit/") && !owner.startsWith("fr/xephi/authme/")) return;
                                    checkMethod(owner, name, descriptor);
                                }
                                @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                                    if (!owner.startsWith("org/bukkit/")) return;
                                    try { assertEquals(descriptor, Type.getDescriptor(Class.forName(owner.replace('/', '.')).getField(name).getType())); }
                                    catch (ReflectiveOperationException exception) { fail(owner + "." + name, exception); }
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        }
    }

    private static void checkMethod(String owner, String name, String descriptor) {
        try {
            Class<?> type = Class.forName(owner.replace('/', '.'), false, PackagedPluginIT.class.getClassLoader());
            if ("<init>".equals(name)) {
                for (Constructor<?> constructor : type.getDeclaredConstructors()) if (Type.getConstructorDescriptor(constructor).equals(descriptor)) return;
            } else {
                for (Method method : type.getMethods()) if (method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor)) return;
                for (Method method : type.getDeclaredMethods()) if (method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor)) return;
            }
            fail("API 不存在：" + owner + "." + name + descriptor);
        } catch (ClassNotFoundException exception) { fail(owner, exception); }
    }
}
