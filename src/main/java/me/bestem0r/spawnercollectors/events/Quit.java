package me.bestem0r.spawnercollectors.events;

import me.bestem0r.spawnercollectors.Collector;
import me.bestem0r.spawnercollectors.SCPlugin;
import me.bestem0r.spawnercollectors.utils.Methods;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class Quit implements Listener {

    private final SCPlugin plugin;

    public Quit(SCPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Collector collector = Methods.getCollector(plugin, event.getPlayer());
        collector.saveAsync();
        plugin.collectors.remove(collector);
    }
}
