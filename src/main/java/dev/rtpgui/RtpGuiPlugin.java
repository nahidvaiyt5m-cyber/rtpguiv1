package dev.rtpgui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RtpGuiPlugin extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private GuiManager gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        gui = new GuiManager(this);
        gui.loadDimensions();
        gui.cleanupLeftovers();

        getServer().getPluginManager().registerEvents(new GuiListener(this, gui), this);

        RtpGuiCommand cmd = new RtpGuiCommand(this, gui);
        PluginCommand pc = getCommand("rtpgui");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        if (getServer().getPluginManager().getPlugin("BetterRTP") == null) {
            getLogger().warning("BetterRTP not found. The GUI will open, but the RTP commands in config.yml need a working /rtp.");
        }
    }

    @Override
    public void onDisable() {
        if (gui != null) gui.closeAll();
    }

    public void reload() {
        reloadConfig();
        gui.loadDimensions();
    }

    /** Message from config with {placeholders}. Pairs: key1, value1, key2, value2... */
    public Component text(String key, String... pairs) {
        String raw = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return MM.deserialize(getConfig().getString("messages.prefix", "") + raw);
    }

    public Component raw(String key, String... pairs) {
        String raw = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return MM.deserialize(raw);
    }

    public void send(CommandSender to, String key, String... pairs) {
        to.sendMessage(text(key, pairs));
    }
}
