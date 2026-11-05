package Mistral.loader;

import java.util.concurrent.CompletableFuture;

/** 入口与隔离运行组件共用的最小接口，由插件原类加载器加载。 */
public interface PluginLifecycle {
    void enable();
    CompletableFuture<Void> disable();
}
