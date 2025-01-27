package me.bestem0r.spawnercollectors.collector;

import me.bestem0r.spawnercollectors.CustomEntityType;
import me.bestem0r.spawnercollectors.DataStoreMethod;
import me.bestem0r.spawnercollectors.SCPlugin;
import me.bestem0r.spawnercollectors.menus.EntityMenu;
import me.bestem0r.spawnercollectors.menus.SpawnerMenu;
import me.bestem0r.spawnercollectors.utils.SpawnerUtils;
import net.bestemor.core.config.ConfigManager;
import net.bestemor.core.menu.Menu;
import net.milkbowl.vault.economy.Economy;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

public class Collector {

    private File file;
    private FileConfiguration config;

    private final UUID uuid;
    private Player owner;

    private final List<EntityCollector> collectorEntities = new ArrayList<>();

    private Menu spawnerMenu;
    private Menu entityMenu;
    
    private final SCPlugin plugin;

    private boolean autoSell;

    private boolean loaded = false;

    public Collector(SCPlugin plugin, UUID uuid) {
        this.plugin = plugin;
        this.uuid = uuid;
        this.owner = Bukkit.getPlayer(uuid);

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            switch (plugin.getStoreMethod()) {
                case MYSQL:
                    loadMYSQL();
                    break;
                case YAML:
                    loadYAML();
            }
            loaded = true;

            List<String> entities = new ArrayList<>();
            Iterator<EntityCollector> it = collectorEntities.iterator();
            while (it.hasNext()) {
                EntityCollector collector = it.next();
                if (entities.contains(collector.getEntityType().name())) {
                    it.remove();
                }
                entities.add(collector.getEntityType().name());
            }
        }, plugin.getStoreMethod() == DataStoreMethod.MYSQL ? ConfigManager.getInt("load_delay") : 1L);
    }

    private void loadYAML() {
        this.file = new File(plugin.getDataFolder() + "/collectors/" + uuid + ".yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        this.autoSell = config.getBoolean("auto_sell");

        ConfigurationSection entitySection = config.getConfigurationSection("entities");
        if (entitySection != null) {
            for (String entityKey : entitySection.getKeys(false)) {
                long entityAmount = config.getLong("entities." + entityKey);
                int spawnerAmount = config.getInt("spawners." + entityKey);
                CustomEntityType type = new CustomEntityType(entityKey);
                collectorEntities.add(new EntityCollector(plugin, type, entityAmount, spawnerAmount));
            }
        }
    }
    private void loadMYSQL() {
        plugin.getSqlManager().loadPlayerData(this);
        this.collectorEntities.clear();
        this.collectorEntities.addAll(plugin.getSqlManager().getEntityCollectors(uuid));
    }

    /** Adds spawner */
    public boolean addSpawner(Player player, CustomEntityType type, int amount) {
        Bukkit.getLogger().info("[SpawnerCollectors] Attempting to add type: " + type.name());
        if (!this.plugin.getLootManager().getMaterials().containsKey(type.name()) && player != null) {
            player.sendMessage(ConfigManager.getMessage("messages.not_supported"));
            return false;
        }
        //Check if owner has permission for mob
        if (player != null  && !this.owner.hasPermission("spawnercollectors.bypass_limit")
                && this.plugin.isMorePermissions() && !this.owner.isOp() && this.owner.getEffectivePermissions().stream()
                .noneMatch((s) -> s.getPermission().startsWith("spawnercollectors.spawner." + type.name().toLowerCase()))) {

            player.sendMessage(ConfigManager.getMessage("messages.no_permission_mob"));
            return false;
        }
        //Check if amount is exceeding global max limit
        if (player != null && this.plugin.getMaxSpawners() > 0 && this.plugin.getMaxSpawners() < amount && !this.owner.hasPermission("spawnercollectors.bypass_limit")) {
            player.sendMessage(ConfigManager.getMessage("messages.reached_max_spawners").replace("%max%", String.valueOf(this.plugin.getMaxSpawners())));
            return false;
        }

        int max = 0;
        if (player != null) {
            max = this.owner.getEffectivePermissions().stream()
                    .map(PermissionAttachmentInfo::getPermission)
                    .filter((s) -> s.startsWith("spawnercollectors.spawner." + type.name().toLowerCase() + "."))
                    .filter((s) -> s.length() > 26 + type.name().length() + 1)
                    .map((s) -> s.substring(26 + type.name().length() + 1))
                    .mapToInt(Integer::parseInt)
                    .max()
                    .orElse(0);
        }

        //Check if amount is exceeding per-mob permission limit
        if (player != null && this.plugin.isMorePermissions() && !owner.hasPermission("spawnercollectors.bypass_limit") && max != 0 && max < amount) {
            player.sendMessage(ConfigManager.getMessage("messages.reached_max_spawners").replace("%max%", String.valueOf(max)));
            return false;
        }

        if (!loaded) {
            player.sendMessage(ConfigManager.getMessage("messages.not_loaded"));
            return false;
        }

        Optional<EntityCollector> optionalCollector = this.collectorEntities.stream().filter((c) -> c.getEntityType().name().equals(type.name())).findAny();

        if (optionalCollector.isPresent()) {

            EntityCollector collector = optionalCollector.get();
            //Check if new amount will exceed global max limit
            if (player != null && this.plugin.getMaxSpawners() > 0 && this.plugin.getMaxSpawners() < amount + collector.getSpawnerAmount() && !this.owner.hasPermission("spawnercollectors.bypass_limit")) {
                player.sendMessage(ConfigManager.getMessage("messages.reached_max_spawners").replace("%max%", String.valueOf(this.plugin.getMaxSpawners())));
                return false;
            }
            //Check if new amount will exceed per-mob permission limit
            if (player != null && this.plugin.isMorePermissions() && max != 0 && max < amount + collector.getSpawnerAmount() && !this.owner.hasPermission("spawnercollectors.bypass_limit")) {
                player.sendMessage(ConfigManager.getMessage("messages.reached_max_spawners").replace("%max%", String.valueOf(max)));
                return false;
            }

            collector.addSpawner(amount);
        } else  {
            this.collectorEntities.add(new EntityCollector(this.plugin, type, 0, amount));
        }

        if (player != null) {
            SpawnerUtils.playSound(player, "add_spawner");
        }

        String spawnerName = ChatColor.RESET + WordUtils.capitalizeFully(type.name().replaceAll("_", " ")) + " Spawner";
        String giverName = player == null ? "Console" : player.getName();
        SCPlugin.log.add(ChatColor.stripColor((new Date()) + ": " + giverName + " added " + amount + " " + spawnerName + " to " + this.owner.getName() + "'s collector!"));

        this.updateSpawnerMenuIfView();
        this.updateEntityMenuIfView();
        return true;
    }

    /** Sells every collected entity in every collected */
    public void sellAll(Player player) {

        boolean morePermissions = plugin.isMorePermissions();
        if (morePermissions && !player.hasPermission("spawnercollectors.sell")) {
            player.sendMessage(ConfigManager.getMessage("messages.no_permission_sell"));
            return;
        }

        Economy economy = plugin.getEconomy();
        double total = getTotalWorth();
        collectorEntities.forEach(EntityCollector::clear);
        economy.depositPlayer(player, total);

        if (total > 0) {
            SpawnerUtils.playSound(player, "sell");
            player.sendMessage(ConfigManager.getCurrencyBuilder("messages.sell_all")
                    .replaceCurrency("%worth%", BigDecimal.valueOf(total))
                    .addPrefix().build());

        }
        updateSpawnerMenuIfView();
        updateEntityMenuIfView();
    }

    /** Sells all entities from specified collected */
    public void sell(Player player, EntityCollector collected) {
        boolean morePermissions = plugin.isMorePermissions();
        if (morePermissions && !player.hasPermission("spawnercollectors.sell")) {
            player.sendMessage(ConfigManager.getMessage("messages.no_permission_sell"));
            return;
        }
        Economy economy = plugin.getEconomy();
        economy.depositPlayer(player, collected.getTotalWorth());

        if (collected.getTotalWorth() > 0) {
            SpawnerUtils.playSound(player, "sell");
            player.sendMessage(ConfigManager.getCurrencyBuilder("messages.sell")
                    .replaceCurrency("%worth%", BigDecimal.valueOf(collected.getTotalWorth()))
                    .addPrefix()
                    .build());
        }


        collected.removeEntities(collected.getEntityAmount());
        updateEntityMenuIfView();
        updateSpawnerMenuIfView();

    }

    /** Attempts to spawn virtual mobs */
    public void attemptSpawn() {
        if (owner == null) {
            owner = Bukkit.getPlayer(uuid);
        }
        if (plugin.getAfkChecker().isAfkCheck() && plugin.getAfkChecker().isAFK(owner)) {
            return;
        }
        for (EntityCollector entityCollector : collectorEntities) {
            entityCollector.attemptSpawn(autoSell, owner);
        }
        updateSpawnerMenuIfView();
        updateEntityMenuIfView();
    }

    /** Toggles auto-sell */
    public void toggleAutoSell(Player player) {
        boolean morePermissions = plugin.isMorePermissions();
        if (morePermissions && !player.hasPermission("spawnercollectors.auto_sell")) {
            player.sendMessage(ConfigManager.getMessage("messages.no_permission_auto-sell"));
            return;
        }

        autoSell = !autoSell;

        updateEntityMenuIfView();
        updateSpawnerMenuIfView();
        SpawnerUtils.playSound(player, "toggle_auto_sell");
    }

    public long getTotalMobCount() {
        return collectorEntities.stream().mapToLong(EntityCollector::getEntityAmount).sum();
    }

    /** Returns spawner Inventory */
    public void openSpawnerMenu(Player player) {
        if (this.spawnerMenu == null) {
            this.spawnerMenu = new SpawnerMenu(plugin, this);
        } else {
            updateSpawnerMenu();
        }
        spawnerMenu.open(player);
        SpawnerUtils.playSound(player, "spawners_open");
    }
    /** Returns entity Inventory */
    public void openEntityMenu(Player player) {
        if (this.entityMenu == null) {
            this.entityMenu = new EntityMenu(plugin.getMenuListener(), this, plugin.getLootManager());
        } else {
            entityMenu.update();
        }
        entityMenu.open(player);
        SpawnerUtils.playSound(player, "mobs_open");
    }

    /** Updates content of spawner menu */
    private void updateSpawnerMenu() {
        if (spawnerMenu == null) { return; }
        spawnerMenu.update();
    }

    /** Updates spawner menu if a player is currently viewing it */
    private void updateSpawnerMenuIfView() {
        if (spawnerMenu != null) {
            if (spawnerMenu.getViewers().size() > 0) {
                updateSpawnerMenu();
            }
        }
    }

    /** Updates entity menu if a player is currently viewing it */
    private void updateEntityMenuIfView() {
        if (entityMenu != null) {
            if (entityMenu.getViewers().size() > 0) {
                entityMenu.update();
            }
        }
    }

    public void saveSync() {

        if (!loaded) {
            return;
        }

        switch (plugin.getStoreMethod()) {
            case YAML:
                if (config == null) {
                    this.file = new File(plugin.getDataFolder() + "/collectors/" + uuid + ".yml");
                    this.config = YamlConfiguration.loadConfiguration(file);
                }

                config.set("spawners", null);
                config.set("entities", null);
                for (EntityCollector spawner : collectorEntities) {
                    config.set("spawners." + spawner.getEntityType().name(), spawner.getSpawnerAmount());
                    config.set("entities." + spawner.getEntityType().name(), spawner.getEntityAmount());
                }
                config.set("auto_sell", autoSell);
                try {
                    config.save(file);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case MYSQL:
                if (this.owner == null) {
                    return;
                }
                plugin.getSqlManager().updatePlayerData(this);
                plugin.getSqlManager().deleteEntityData(this);
                for (EntityCollector collector : collectorEntities) {
                    plugin.getSqlManager().insertEntityData(owner.getUniqueId(), collector);
                }
                break;
            default:
                throw new IllegalStateException("Invalid data storage method!");
        }
    }

    public boolean isAutoSell() {
        return autoSell;
    }

    public List<EntityCollector> getCollectorEntities() {
        return collectorEntities;
    }

    public void setAutoSell(boolean autoSell) {
        this.autoSell = autoSell;
    }

    /** Saves collector */
    public void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveSync);
    }

    public void updateEntityMenu() {
        if (entityMenu != null) {
            this.entityMenu.update();
        }
    }

    public double getTotalWorth() {
        double total = 0;
        for (EntityCollector collected : collectorEntities) {
            total += collected.getTotalWorth();
        }
        return total;
    }

    public UUID getUuid() {
        return uuid;
    }

    public BigDecimal getAverageProduction() {
        BigDecimal total = BigDecimal.ZERO;
        for (EntityCollector collected : collectorEntities) {
            total = total.add(collected.getMinutelyProduction());
        }
        return total;
    }
}
