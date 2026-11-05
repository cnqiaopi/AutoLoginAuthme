package Mistral.command;

import Mistral.bootstrap.PluginApplication;
import Mistral.bootstrap.PluginRuntime;
import Mistral.model.PlayerSession;
import Mistral.util.IpAddresses;
import Mistral.util.PlayerNames;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class AutoLoginCommand implements CommandExecutor, TabCompleter {
    private final PluginApplication plugin;
    private static final List<String> ACTIONS = Arrays.asList("reload", "status", "lookup", "remove", "cleanup", "debug");
    public AutoLoginCommand(PluginApplication plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("autologin.admin")) { sender.sendMessage("§c你没有 autologin.admin 权限。"); return true; }
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(action)) { plugin.reloadSettings(sender); return true; }
        if ("debug".equals(action)) { plugin.toggleDebug(sender); return true; }
        PluginRuntime runtime = plugin.runtime();
        if (runtime == null) { sender.sendMessage("§e插件正在重载，请稍后重试。"); return true; }
        switch (action) {
            case "status":
                sender.sendMessage(runtime.settings.prefix + "§aAuthMe 已连接；数据库：" + (runtime.store.isHealthy() ? "就绪" : "异常，自动登录暂停"));
                sender.sendMessage("§7缓存：" + runtime.store.cachedPlayers() + "/" + runtime.settings.cacheSize + "；调试：" + runtime.settings.debug);
                break;
            case "cleanup":
                sender.sendMessage(runtime.settings.prefix + "§a已清理 " + runtime.store.clearCache() + " 个缓存会话。");
                break;
            case "lookup":
            case "remove":
                if (args.length != 2 || !PlayerNames.isValid(args[1])) {
                    sender.sendMessage("§c用法：/autologin " + action + " <玩家名，1-16位字母、数字或下划线>");
                    break;
                }
                String target = args[1];
                if ("remove".equals(action)) {
                    runtime.coordinator.revoke(target);
                    runtime.store.delete(target).whenComplete((ignored, failure) -> plugin.scheduler().sync(() ->
                        sender.sendMessage(failure == null ? "§a已删除 " + target + " 的自动登录凭证。" : "§c删除失败，自动登录已暂停，详情见控制台。")));
                } else {
                    runtime.store.find(target).whenComplete((session, failure) -> plugin.scheduler().sync(() -> {
                        if (failure != null) { sender.sendMessage("§c查询失败，详情见控制台。"); return; }
                        showSession(sender, target, session, runtime);
                    }));
                }
                break;
            default:
                sender.sendMessage("§6AutoLoginAuthme 管理命令（/al）");
                sender.sendMessage("§a/autologin reload §7- 重载全部配置与连接池");
                sender.sendMessage("§a/autologin status §7- 查看存储与缓存状态");
                sender.sendMessage("§a/autologin lookup <玩家> §7- 查询凭证");
                sender.sendMessage("§a/autologin remove <玩家> §7- 撤销凭证");
                sender.sendMessage("§a/autologin cleanup §7- 清空缓存");
                sender.sendMessage("§a/autologin debug §7- 切换调试设置并重载");
        }
        return true;
    }

    private void showSession(CommandSender sender, String name, PlayerSession session, PluginRuntime runtime) {
        if (session == null) { sender.sendMessage("§7" + name + " 没有会话记录。"); return; }
        String time;
        try { time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(runtime.settings.zone).format(Instant.ofEpochSecond(session.getAuthenticatedAt())); }
        catch (RuntimeException exception) { time = "无效时间"; }
        sender.sendMessage("§6玩家：" + name + "；IP：" + IpAddresses.mask(session.getIp()));
        sender.sendMessage("§7客户端：" + session.getBrand() + "；认证时间：" + time);
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("autologin.admin") || args.length != 1) return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        for (String action : ACTIONS) if (action.startsWith(args[0].toLowerCase(Locale.ROOT))) result.add(action);
        return result;
    }
}
