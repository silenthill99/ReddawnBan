package fr.silenthill99.reddawnBan;

import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

/**
 * Point d'entree du plugin. Bukkit instancie cette classe au demarrage du serveur,
 * appelle onEnable() une fois, puis onDisable() a l'arret.
 */
public final class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
    }

    @Override
    public void onDisable() {
        // HttpClient n'a pas de close() a appeler : Java le ferme tout seul
        // quand le plugin est decharge. Rien a faire ici pour l'instant.
    }
}
