package de.galacticfy.galacticfyChat.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

/**
 * Unterdrückt ALLE Advancement-Nachrichten im Chat.
 * (nur Paper – nutzt event.message(null))
 */
public class AdvancementSuppressListener implements Listener {

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        // Wenn eine Message existiert → auf null setzen = keine Chat-Nachricht
        if (event.message() != null) {
            event.message(null);
        }
    }
}
