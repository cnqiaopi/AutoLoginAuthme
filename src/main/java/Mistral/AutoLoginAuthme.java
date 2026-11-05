package Mistral;

import Mistral.loader.ExternalLibraries;
import Mistral.loader.LibraryDownloader;
import Mistral.loader.LibraryClassLoader;
import Mistral.loader.PluginLifecycle;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

/** 只依赖 Java/Bukkit 的入口；第三方库准备完成后才加载业务实现。 */
public final class AutoLoginAuthme extends JavaPlugin {
    private PluginLifecycle application;
    private LibraryClassLoader libraries;

    @Override public void onEnable() {
        try {
            Path directory = getDataFolder().toPath().resolve("libs");
            new LibraryDownloader().ensure(directory, ExternalLibraries.REQUIRED, getLogger()::info);
            URL ownJar = getClass().getProtectionDomain().getCodeSource().getLocation();
            libraries = new LibraryClassLoader(ExternalLibraries.urls(ownJar, directory), getClassLoader());
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(libraries);
            try {
                application = (PluginLifecycle) libraries.loadClass("Mistral.bootstrap.PluginApplication")
                    .getConstructor(JavaPlugin.class).newInstance(this);
                application.enable();
            } finally { Thread.currentThread().setContextClassLoader(previous); }
        } catch (Exception | LinkageError exception) {
            getLogger().log(Level.SEVERE, "AutoLoginAuthme 加载失败", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override public void onDisable() {
        LibraryClassLoader retiring = libraries;
        libraries = null;
        CompletableFuture<Void> finished = application == null ? CompletableFuture.completedFuture(null) : application.disable();
        application = null;
        finished.whenComplete((ignored, failure) -> {
            if (failure != null) getLogger().log(Level.WARNING, "结束数据库任务时发生错误", failure);
            if (retiring != null) {
                try {
                    java.util.ResourceBundle.clearCache(retiring);
                    retiring.close();
                }
                catch (java.io.IOException exception) { getLogger().log(Level.WARNING, "释放外置库失败", exception); }
            }
        });
    }
}
