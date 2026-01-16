package me.savaduki.extracrates;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for the ExtraCrates plugin.
 * <p>
 * This class focuses on bootstrapping services and providing a clear extension
 * point for the future complex crate ecosystem. It intentionally keeps logic
 * minimal to make the project easy to iterate and upgrade.
 */
public final class ExtraCratesPlugin extends JavaPlugin {

    private CrateRegistry crateRegistry;
    private RewardRegistry rewardRegistry;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        this.crateRegistry = new CrateRegistry();
        this.rewardRegistry = new RewardRegistry();

        getLogger().info(() -> "ExtraCrates has been enabled and is ready for expansion.");
    }

    @Override
    public void onDisable() {
        getLogger().info(() -> "ExtraCrates is shutting down cleanly.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"extracrates".equalsIgnoreCase(command.getName())) {
            return false;
        }

        sender.sendRichMessage("<green>ExtraCrates</green> <gray>|</gray> Plugin base cargado. ¡Listo para futuras funciones complejas!");
        return true;
    }

    public CrateRegistry getCrateRegistry() {
        return crateRegistry;
    }

    public RewardRegistry getRewardRegistry() {
        return rewardRegistry;
    }
}
