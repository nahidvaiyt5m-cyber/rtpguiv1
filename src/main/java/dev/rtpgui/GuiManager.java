package dev.rtpgui;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class GuiManager {

    static final String TAG = "rtpgui_entity";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final RtpGuiPlugin plugin;
    private final Map<UUID, GuiSession> sessions = new HashMap<>();
    private List<Dimension> dimensions = List.of();

    public GuiManager(RtpGuiPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    public void loadDimensions() {
        List<Dimension> list = new ArrayList<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("dimensions");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection d = sec.getConfigurationSection(id);
                if (d == null) continue;
                list.add(new Dimension(
                        id,
                        d.getBoolean("enabled", true),
                        d.getString("world", "world"),
                        d.getString("label", id),
                        d.getString("model", "rtpgui:" + id),
                        d.getString("label-model", "rtpgui:" + id + "_label"),
                        d.getString("material", "PAPER"),
                        d.getString("command", "rtp world {world}"),
                        d.getString("permission", "")));
            }
        }
        dimensions = List.copyOf(list);
    }

    public List<Dimension> dimensions() {
        return dimensions;
    }

    public Dimension dimension(String id) {
        for (Dimension d : dimensions) {
            if (d.id().equalsIgnoreCase(id)) return d;
        }
        return null;
    }

    private boolean allowed(Player p, Dimension d) {
        if (!plugin.getConfig().getBoolean("require-permissions", false)) return true;
        return d.permission().isEmpty() || p.hasPermission(d.permission());
    }

    // ------------------------------------------------------------------
    // Sessions
    // ------------------------------------------------------------------

    public GuiSession session(Player p) {
        return sessions.get(p.getUniqueId());
    }

    public boolean isGuiEntity(Entity e) {
        return e.getScoreboardTags().contains(TAG);
    }

    public void cleanupLeftovers() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (isGuiEntity(e)) e.remove();
            }
        }
    }

    public void closeAll() {
        for (GuiSession s : new ArrayList<>(sessions.values())) {
            close(s.player, false);
        }
    }

    // ------------------------------------------------------------------
    // Open
    // ------------------------------------------------------------------

    public void open(Player player) {
        close(player, false);

        List<Dimension> avail = new ArrayList<>();
        for (Dimension d : dimensions) {
            if (d.enabled() && allowed(player, d)) avail.add(d);
        }
        if (avail.isEmpty()) {
            plugin.send(player, "no-dimensions");
            return;
        }

        FileConfiguration c = plugin.getConfig();
        double distance = c.getDouble("layout.distance", 3.5);
        float panelScale = (float) c.getDouble("layout.panel-scale", 1.8);
        double spacing = c.getDouble("layout.spacing", 2.1);
        double panelY = c.getDouble("layout.panel-center-y", 1.7);
        float yawOffset = (float) c.getDouble("layout.yaw-offset", 0);
        int pop = Math.max(1, c.getInt("animation.pop-ticks", 12));
        int stagger = Math.max(0, c.getInt("animation.stagger-ticks", 2));
        float closeW = (float) c.getDouble("close-button.width", 2.6);
        float closeH = (float) c.getDouble("close-button.height", 0.7);

        Location origin = player.getLocation();
        double rad = Math.toRadians(origin.getYaw());
        Vector forward = new Vector(-Math.sin(rad), 0, Math.cos(rad));
        Vector right = new Vector(-Math.cos(rad), 0, -Math.sin(rad));
        float faceYaw = origin.getYaw() + 180f + yawOffset;

        Location center = origin.clone().add(forward.clone().multiply(distance));
        center.setPitch(0);
        center.setYaw(faceYaw);

        GuiSession s = new GuiSession(player, center);
        sessions.put(player.getUniqueId(), s);

        int n = avail.size();
        int delay = 2;

        // pending hitboxes, spawned after the pop animation
        record Hit(Location loc, float w, float h, GuiSession.Action action) {
        }
        List<Hit> hits = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Dimension d = avail.get(i);
            double off = (i - (n - 1) / 2.0) * spacing;

            Location panelLoc = center.clone().add(right.clone().multiply(off));
            panelLoc.add(0, panelY, 0);

            // image
            Transformation target = tf(new Vector3f(0, 0, 0), new Vector3f(panelScale, panelScale, 1f));
            ItemDisplay img = spawnItem(player, panelLoc, makeItem(d.material(), d.model()), target);
            s.anims.add(new GuiSession.Anim(img, target));
            schedulePop(s, img, target, delay + i * stagger, pop);

            // name plate above the image
            float labelH = panelScale * 0.16f;
            Location labelLoc = panelLoc.clone().add(0, panelScale / 2.0 + labelH / 2.0 + 0.08, 0);
            if (!d.labelModel().isEmpty()) {
                Transformation labelTarget = tf(new Vector3f(0, 0, 0), new Vector3f(panelScale, labelH, 1f));
                ItemDisplay plate = spawnItem(player, labelLoc, makeItem(d.material(), d.labelModel()), labelTarget);
                s.anims.add(new GuiSession.Anim(plate, labelTarget));
                schedulePop(s, plate, labelTarget, delay + i * stagger, pop);
            } else {
                Transformation labelTarget = tf(new Vector3f(0, 0, 0), new Vector3f(1.0f, 1.0f, 1.0f));
                TextDisplay label = spawnText(player, labelLoc, d.label(), labelTarget);
                s.anims.add(new GuiSession.Anim(label, labelTarget));
                schedulePop(s, label, labelTarget, delay + i * stagger, pop);
            }

            // hitbox (bottom-center of the image)
            Location hitLoc = panelLoc.clone().add(0, -panelScale / 2.0, 0);
            hits.add(new Hit(hitLoc, panelScale, panelScale, new GuiSession.Action(d)));
        }

        // close button, bottom center
        Location closeLoc = center.clone().add(0, panelY - panelScale / 2.0 - 0.1 - closeH / 2.0, 0);
        Transformation closeTarget = tf(new Vector3f(0, 0, 0), new Vector3f(closeW, closeH, 1f));
        ItemDisplay closeImg = spawnItem(player, closeLoc,
                makeItem(c.getString("close-button.material", "PAPER"),
                        c.getString("close-button.model", "rtpgui:close")),
                closeTarget);
        s.anims.add(new GuiSession.Anim(closeImg, closeTarget));
        schedulePop(s, closeImg, closeTarget, delay + n * stagger, pop);
        Location closeHitLoc = closeLoc.clone().add(0, -closeH / 2.0, 0);
        hits.add(new Hit(closeHitLoc, closeW, closeH, new GuiSession.Action(null)));

        player.playSound(center, Sound.BLOCK_PISTON_EXTEND, SoundCategory.MASTER, 0.6f, 1.4f);

        // hitboxes appear when the animation is done
        long hitDelay = delay + (long) n * stagger + pop;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (s.closed || !player.isOnline()) return;
            for (Hit h : hits) {
                Interaction it = spawnHidden(player, h.loc(), Interaction.class, e -> {
                    e.setInteractionWidth(h.w());
                    e.setInteractionHeight(h.h());
                    e.setResponsive(true);
                });
                s.interactions.add(it);
                s.actions.put(it.getUniqueId(), h.action());
            }
            s.interactive = true;
        }, hitDelay);

        // timeout
        long timeout = c.getLong("timeout-seconds", 30) * 20L;
        if (timeout > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (sessions.get(player.getUniqueId()) == s) close(player, true);
            }, timeout);
        }
    }

    // ------------------------------------------------------------------
    // Close
    // ------------------------------------------------------------------

    public void close(Player player, boolean animate) {
        GuiSession s = sessions.remove(player.getUniqueId());
        if (s == null) return;
        s.closed = true;
        s.interactive = false;

        for (Entity e : s.interactions) {
            if (e.isValid()) e.remove();
        }

        if (animate && player.isOnline() && plugin.isEnabled()) {
            for (GuiSession.Anim a : s.anims) {
                if (!a.display().isValid()) continue;
                a.display().setInterpolationDelay(0);
                a.display().setInterpolationDuration(8);
                a.display().setTransformation(hidden(a.target()));
            }
            player.playSound(s.center, Sound.BLOCK_PISTON_CONTRACT, SoundCategory.MASTER, 0.6f, 1.4f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> removeAnims(s), 10L);
        } else {
            removeAnims(s);
        }
    }

    private void removeAnims(GuiSession s) {
        for (GuiSession.Anim a : s.anims) {
            if (a.display().isValid()) a.display().remove();
        }
    }

    // ------------------------------------------------------------------
    // Click
    // ------------------------------------------------------------------

    public void click(Player player, Entity clicked) {
        GuiSession s = sessions.get(player.getUniqueId());
        if (s == null || !s.interactive) return;
        GuiSession.Action a = s.actions.get(clicked.getUniqueId());
        if (a == null) return;

        s.interactive = false; // debounce

        if (a.dim() == null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.8f, 1f);
            close(player, true);
            return;
        }

        Dimension d = a.dim();
        if (!d.enabled() || !allowed(player, d)) {
            plugin.send(player, "no-permission");
            close(player, true);
            return;
        }
        if (Bukkit.getWorld(d.world()) == null) {
            plugin.send(player, "world-missing", "world", d.world());
            close(player, true);
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1f, 1.2f);
        close(player, true);

        String cmd = d.command()
                .replace("{world}", d.world())
                .replace("{player}", player.getName());
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        // Run as the player so BetterRTP applies its own cooldown / delay / economy / permissions.
        player.performCommand(cmd);
    }

    // ------------------------------------------------------------------
    // Spawn helpers
    // ------------------------------------------------------------------

    private <T extends Entity> T spawnHidden(Player viewer, Location loc, Class<T> type, Consumer<T> init) {
        T e = loc.getWorld().spawn(loc, type, ent -> {
            ent.setVisibleByDefault(false);   // nobody sees it...
            ent.setPersistent(false);
            ent.addScoreboardTag(TAG);
            init.accept(ent);
        });
        viewer.showEntity(plugin, e);         // ...except the player who opened the GUI
        return e;
    }

    private ItemDisplay spawnItem(Player viewer, Location loc, ItemStack item, Transformation target) {
        return spawnHidden(viewer, loc, ItemDisplay.class, e -> {
            e.setItemStack(item);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(hidden(target));
        });
    }

    private TextDisplay spawnText(Player viewer, Location loc, String miniMessage, Transformation target) {
        return spawnHidden(viewer, loc, TextDisplay.class, e -> {
            e.text(MM.deserialize(miniMessage));
            e.setBillboard(Display.Billboard.FIXED);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(true);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(hidden(target));
        });
    }

    private ItemStack makeItem(String material, String model) {
        Material m = Material.matchMaterial(material);
        if (m == null || !m.isItem()) m = Material.PAPER;
        ItemStack item = new ItemStack(m);
        try {
            item.setData(DataComponentTypes.ITEM_MODEL, Key.key(model));
        } catch (Exception ex) {
            plugin.getLogger().warning("Invalid item model key: " + model);
        }
        return item;
    }

    private void schedulePop(GuiSession s, Display d, Transformation target, int delay, int pop) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (s.closed || !d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(pop);
            d.setTransformation(target);
        }, delay);
    }

    private static Transformation tf(Vector3f translation, Vector3f scale) {
        return new Transformation(translation, new AxisAngle4f(), scale, new AxisAngle4f());
    }

    /** Underground + tiny: the starting state of the pop-up. */
    private static Transformation hidden(Transformation t) {
        Vector3f tr = new Vector3f(t.getTranslation()).add(0f, -3f, 0f);
        Vector3f sc = new Vector3f(t.getScale()).mul(0.2f);
        return tf(tr, sc);
    }
}
