package Mistral.loader;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.io.IOException;

/** 不修改服务端类加载器；插件业务和外置库优先从自己明确的 URL 集合加载。 */
public final class LibraryClassLoader extends URLClassLoader {
    static { registerAsParallelCapable(); }
    public LibraryClassLoader(URL[] urls, ClassLoader parent) { super(urls, parent); }

    private boolean privateClass(String name) {
        return (name.startsWith("Mistral.") && !name.startsWith("Mistral.loader.") && !name.equals("Mistral.AutoLoginAuthme"))
            || name.startsWith("com.zaxxer.hikari.") || name.startsWith("org.slf4j.")
            || name.startsWith("com.mysql.") || name.startsWith("com.google.protobuf.");
    }
    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!privateClass(name)) return super.loadClass(name, resolve);
        synchronized (getClassLoadingLock(name)) {
            Class<?> found = findLoadedClass(name);
            if (found == null) found = findClass(name);
            if (resolve) resolveClass(found);
            return found;
        }
    }
    @Override public URL getResource(String name) {
        URL own = findResource(name);
        return own != null ? own : super.getResource(name);
    }
    @Override public Enumeration<URL> getResources(String name) throws IOException {
        if (name.startsWith("org/slf4j/") || name.startsWith("com/mysql/") || name.equals("META-INF/services/java.sql.Driver")) return findResources(name);
        return super.getResources(name);
    }
}
