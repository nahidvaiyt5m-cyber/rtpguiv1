package dev.rtpgui;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.Locale;

public final class GuiListener implements Listener {

    private final RtpGuiPlugin plugin;
    private final GuiManager gui;

    public GuiListener(RtpGuiPlugin plugin, GuiManager gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    // ---- Clicks -------------------------------------------------------

    /** Right click. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEntityEvent e) {
        if (!gui.isGuiEntity(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        gui.click(e.getPlayer(), e.getRightClicked());
    }

    /** Left click. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLeftClick(PrePlayerAttackEntityEvent e) {
        if (!gui.isGuiEntity(e.getAttacked())) return;
        e.setCancelled(true);
        gui.click(e.getPlayer(), e.getAttacked());
    }

    // ---- /rtp interception -------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("intercept-rtp", true)) return;

        String msg = e.getMessage().trim();
        if (msg.startsWith("/")) msg = msg.substring(1);
        String[] parts = msg.split("\\s+");
        if (parts.length != 1) return; // only a bare "/rtp" opens the GUI; "/rtp world x" passes through

        List<String> cmds = plugin.getConfig().getStringList("intercept-commands");
        String label = parts[0].toLowerCase(Locale.ROOT);
        if (cmds.stream().noneMatch(c -> c.equalsIgnoreCase(label))) return;

        Player p = e.getPlayer();
        if (!p.hasPermission("rtpgui.use") || p.hasPermission("rtpgui.intercept.bypass")) return;

        e.setCancelled(true);
        gui.open(p);
    }

    // ---- Auto close ---------------------------------------------------

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock()) return;
        GuiSession s = gui.session(e.getPlayer());
        if (s == null) return;
        double max = plugin.getConfig().getDouble("close-distance", 7.0);
        Location to = e.getTo();
        if (!to.getWorld().equals(s.center.getWorld()) || to.distanceSquared(s.center) > max * max) {
            gui.close(e.getPlayer(), true);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        gui.close(e.getPlayer(), false);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        gui.close(e.getPlayer(), false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        gui.close(e.getEntity(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        gui.close(e.getPlayer(), false);
    }
}
