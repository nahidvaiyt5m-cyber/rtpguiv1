package dev.rtpgui;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One open GUI for one player. */
final class GuiSession {

    /** A visual entity and the transformation it should end up with. */
    record Anim(Display display, Transformation target) {
    }

    /** What a click on an interaction entity does. dim == null means "close". */
    record Action(Dimension dim) {
    }

    final Player player;
    final Location center;
    final List<Anim> anims = new ArrayList<>();
    final List<Entity> interactions = new ArrayList<>();
    final Map<UUID, Action> actions = new HashMap<>();
    volatile boolean interactive = false;
    volatile boolean closed = false;

    GuiSession(Player player, Location center) {
        this.player = player;
        this.center = center;
    }
}
