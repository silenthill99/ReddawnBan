package fr.silenthill99.reddawnBan;

import fr.silenthill99.reddawnBan.api.ApiClient;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

/**
 * Point d'entree du plugin. Bukkit instancie cette classe au demarrage du serveur,
 * appelle onEnable() une fois, puis onDisable() a l'arret.
 */
public final class Main extends JavaPlugin {

    // Un seul ApiClient pour toute la duree de vie du plugin. Il est cree dans
    // onEnable() et reutilise pour chaque appel HTTP : c'est l'objet qui maintient
    // le pool de connexions reseau, on n'en cree pas un par requete.
    private ApiClient apiClient;

    @Override
    public void onEnable() {
        // saveDefaultConfig() copie le config.yml embarque dans le .jar vers
        // plugins/ReddawnBan/config.yml. Si le fichier existe deja, il n'est PAS
        // ecrase : c'est ce qui permet a l'utilisateur du serveur de modifier
        // l'URL et le token sans qu'on les ecrase a chaque redemarrage.
        saveDefaultConfig();

        // getConfig() lit plugins/ReddawnBan/config.yml.
        // Le 2e argument de getString/getLong est la valeur par defaut si la cle
        // est absente : evite un NullPointerException si l'utilisateur supprime
        // une ligne du config.yml.
        String url = getConfig().getString("api.url", "http://api.test/api");
        String token = getConfig().getString("api.token", "");
        long timeoutSeconds = getConfig().getLong("api.timeout-seconds", 10);

        // Garde-fou : sans token, l'API renvoie 401 sur tout. Mieux vaut
        // prevenir au demarrage que laisser l'admin chercher pourquoi
        // chaque ban echoue silencieusement.
        if (token.isBlank()) {
            getLogger().warning("api.token est vide dans config.yml, les appels seront rejetes en 401.");
        }

        // getLogger() retourne le logger Bukkit du plugin (prefixe [ReddawnBan]).
        // On le passe a ApiClient pour qu'il logge ses erreurs HTTP avec
        // le bon prefixe.
        apiClient = new ApiClient(url, token, Duration.ofSeconds(timeoutSeconds), getLogger());
        getLogger().info("ApiClient initialise sur " + url);
    }

    @Override
    public void onDisable() {
        // HttpClient n'a pas de close() a appeler : Java le ferme tout seul
        // quand le plugin est decharge. Rien a faire ici pour l'instant.
    }

    /**
     * Expose le client HTTP aux autres classes (futurs listeners, commandes).
     * Usage : ((Main) getServer().getPluginManager().getPlugin("ReddawnBan")).getApiClient()
     * ou plus simplement, en gardant une reference au plugin dans le constructeur
     * du listener.
     */
    public ApiClient getApiClient() {
        return apiClient;
    }
}
