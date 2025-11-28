package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.punish.PunishmentReader;
import de.galacticfy.galacticfyChat.punish.PunishmentReader.Punishment;
import de.galacticfy.galacticfyChat.rank.SimpleRankService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final SimpleRankService rankService;
    private final PunishmentReader punishmentReader;

    public ChatListener(SimpleRankService rankService,
                        PunishmentReader punishmentReader) {
        this.rankService = rankService;
        this.punishmentReader = punishmentReader;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {

        Player player = event.getPlayer();

        // 1) Mute aus DB prüfen
        Punishment mute = punishmentReader.getActiveMute(player.getUniqueId());
        if (mute != null) {
            // Nur blocken, KEINE Nachricht schicken
            event.setCancelled(true);
            return;
        }

        // 2) Kein Mute → dein normales Chat-Format
        event.setCancelled(true);

        Component rawMsg = event.message();

        Component name = rankService.getDisplayName(player);
        Component arrow = Component.text(" » ", NamedTextColor.DARK_GRAY);
        Component msgGray = rawMsg.colorIfAbsent(NamedTextColor.GRAY);

        Component out = name.append(arrow).append(msgGray);

        player.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(out));
    }

}
