package com.extracrates.economy;

import com.extracrates.ExtraCratesPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.text.DecimalFormat;

public class EconomyService {
    private static final DecimalFormat FALLBACK_FORMAT = new DecimalFormat("#,##0.00");
    private final Economy economy;

    public EconomyService(ExtraCratesPlugin plugin) {
        RegisteredServiceProvider<Economy> provider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        this.economy = provider != null ? provider.getProvider() : null;
    }

    public boolean isAvailable() {
        return economy != null;
    }

    public boolean hasBalance(Player player, double amount) {
        if (economy == null) {
            return false;
        }
        return economy.has(player, amount);
    }

    public EconomyResponse withdraw(Player player, double amount) {
        if (economy == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No economy provider");
        }
        return economy.withdrawPlayer(player, amount);
    }

    public String format(double amount) {
        if (economy == null) {
            return FALLBACK_FORMAT.format(amount);
        }
        return economy.format(amount);
    }
}
