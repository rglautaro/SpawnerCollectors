package me.bestem0r.spawnercollectors.events;

import me.bestem0r.spawnercollectors.SCPlugin;
import me.bestem0r.spawnercollectors.utils.ColorBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.util.*;

public class AFKChecker implements Listener {

    private final SCPlugin plugin;

    private final boolean afkCheck;
    private final int time;

    private final Map<UUID, Instant> lastMove = new HashMap<>();
    private final List<UUID> afkPlayers = new ArrayList<>();

    public AFKChecker(SCPlugin plugin) {
        this.plugin = plugin;

        this.afkCheck = plugin.getConfig().getBoolean("afk.enable");
        this.time = plugin.getConfig().getInt("afk.time");

        if (afkCheck) {
            runChecker();
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {

        if (afkCheck) {
            Player player = event.getPlayer();

            if (afkPlayers.contains(player.getUniqueId())) {
                player.sendMessage(new ColorBuilder(plugin).path("messages.no_longer_afk").addPrefix().build());
                afkPlayers.remove(player.getUniqueId());
            }

            lastMove.put(player.getUniqueId(), Instant.now());

        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (afkCheck) {
            lastMove.remove(event.getPlayer().getUniqueId());
            afkPlayers.remove(event.getPlayer().getUniqueId());
        }
    }

    private void runChecker() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (UUID uuid : lastMove.keySet()) {
                if (!afkPlayers.contains(uuid) && lastMove.get(uuid).plusSeconds(time).isBefore(Instant.now())) {
                    afkPlayers.add(uuid);
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        player.sendMessage(new ColorBuilder(plugin).path("messages.afk").addPrefix().build());
                    }
                }
            }
        }, 20L, 20L);
    }

    public boolean isAFK(Player player) {
        return afkPlayers.contains(player.getUniqueId());
    }

    public boolean isAfkCheck() {
        return afkCheck;
    }
}