package me.bestem0r.spawnercollectors.commands.subcommands;

import me.bestem0r.spawnercollectors.Collector;
import me.bestem0r.spawnercollectors.SCPlugin;
import me.bestem0r.spawnercollectors.commands.CommandModule;
import me.bestem0r.spawnercollectors.commands.SubCommand;
import me.bestem0r.spawnercollectors.utils.Methods;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class OpenCommand implements SubCommand {
    private final SCPlugin plugin;

    public OpenCommand(SCPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> getCompletion(int index, String[] args) {
        switch(index) {
            case 0:
                return Bukkit.getOnlinePlayers().stream().map(OfflinePlayer::getName).collect(Collectors.toList());
            case 1:
                return Arrays.asList("mobs", "spawners");
            default:
                return new ArrayList<>();
        }
    }

    public void run(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player)sender;
            if (args.length != 3) {
                player.sendMessage(ChatColor.RED + "Please specify player and which storage you want to open!");
                return;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Could not find player: " + args[1]);
                return;
            }

            if (!args[2].equals("mobs") && !args[2].equals("spawners")) {
                player.sendMessage(ChatColor.RED + "Could not find storage: " + args[2]);
                return;
            }

            Collector collector = Methods.getCollector(this.plugin, target);

            switch(args[2]) {
                case "mobs":
                    collector.openEntityMenu(player);
                    break;
                case "spawners":
                    collector.openSpawnerMenu(player);
            }
        }

    }

    public void setModule(CommandModule module) {
    }

    public String getDescription() {
        return "Open others' spawner/mob storage";
    }
}
