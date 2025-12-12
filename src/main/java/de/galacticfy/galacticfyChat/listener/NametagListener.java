package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.rank.SimpleRankService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class NametagListener implements Listener {

    private final SimpleRankService rankService;

    public NametagListener(SimpleRankService rankService) {
        this.rankService = rankService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // alle neu sortieren, damit Tablist direkt stimmt
        for (Player p : Bukkit.getOnlinePlayers()) {
            rankService.updateNametag(p);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            rankService.updateNametag(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        rankService.clearNametag(event.getPlayer());
    }
}

