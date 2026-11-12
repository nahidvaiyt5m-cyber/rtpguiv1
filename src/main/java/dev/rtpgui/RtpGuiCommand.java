package dev.rtpgui;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RtpGuiCommand implements TabExecutor {

    private final RtpGuiPlugin plugin;
    private final GuiManager gui;

    public RtpGuiCommand(RtpGuiPlugin plugin, GuiManager gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "open" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "open" -> {
                Player target;
                if (args.length > 1) {
                    if (!sender.hasPermission("rtpgui.admin")) {
                        plugin.send(sender, "no-permission");
                        return true;
                    }
                    target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage("Player not found.");
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    if (!p.hasPermission("rtpgui.use")) {
                        plugin.send(sender, "no-permission");
                        return true;
                    }
                    target = p;
                } else {
                    plugin.send(sender, "only-player");
                    return true;
                }
                gui.open(target);
            }
            case "reload" -> {
                if (!sender.hasPermission("rtpgui.admin")) {
                    plugin.send(sender, "no-permission");
                    return true;
                }
                plugin.reload();
                plugin.send(sender, "reloaded");
            }
            case "toggle" -> {
                if (!sender.hasPermission("rtpgui.admin")) {
                    plugin.send(sender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    plugin.send(sender, "usage");
                    return true;
                }
                Dimension d = gui.dimension(args[1]);
                if (d == null) {
                    plugin.send(sender, "unknown-dim", "dim", args[1]);
                    return true;
                }
                boolean state = d.enabled();
                if (args.length > 2) {
                    state = args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true");
                } else {
                    state = !state;
                }
                plugin.getConfig().set("dimensions." + d.id() + ".enabled", state);
                plugin.saveConfig();
                gui.loadDimensions();
                plugin.send(sender, "toggled", "dim", d.id(), "state", state ? "<green>ON" : "<red>OFF");
            }
            case "status" -> {
                if (!sender.hasPermission("rtpgui.admin")) {
                    plugin.send(sender, "no-permission");
                    return true;
                }
                for (Dimension d : gui.dimensions()) {
                    sender.sendMessage(plugin.raw("status-line",
                            "dim", d.id(),
                            "state", d.enabled() ? "<green>ON" : "<red>OFF",
                            "world", d.world()));
                }
            }
            default -> plugin.send(sender, "usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("open");
            if (sender.hasPermission("rtpgui.admin")) out.addAll(List.of("toggle", "reload", "status"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            for (Dimension d : gui.dimensions()) out.add(d.id());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("toggle")) {
            out.addAll(List.of("on", "off"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("open") && sender.hasPermission("rtpgui.admin")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
