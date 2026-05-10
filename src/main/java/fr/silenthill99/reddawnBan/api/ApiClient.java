package fr.silenthill99.reddawnBan.api;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client HTTP generique pour parler a l'API Laravel.
 *
 * Role : envelopper java.net.http.HttpClient (built-in Java 11+) en ajoutant
 * automatiquement le header d'authentification, en serialisant les body JSON
 * et en loggant les erreurs.
 *
 * Toutes les methodes sont ASYNCHRONES : elles renvoient un CompletableFuture
 * qui se completera plus tard, sans bloquer le thread appelant. C'est crucial
 * cote Bukkit : le thread principal du serveur ne doit JAMAIS attendre une
 * reponse reseau, sous peine de faire lagger tous les joueurs.
 */
public class ApiClient {

    // HttpClient est concu pour etre reutilise : il gere un pool de connexions
    // TCP en interne. Creer un nouveau HttpClient par requete tuerait la perf.
    private final HttpClient http;

    // Prefixe ajoute devant chaque chemin. Ex: "http://api.test/api"
    private final String baseUrl;

    // Token Bearer, envoye dans le header Authorization de chaque requete.
    private final String token;

    // Delai max d'attente d'une reponse. Au-dela, la requete echoue avec
    // HttpTimeoutException (gere dans send()).
    private final Duration timeout;

    // Logger Bukkit du plugin, recu depuis Main.getLogger().
    private final Logger logger;

    public ApiClient(String baseUrl, String token, Duration timeout, Logger logger) {
        // Si l'utilisateur met "http://api.test/api/" avec un slash final,
        // la concatenation produirait "http://api.test/api//sanctions".
        // On normalise une fois ici pour ne plus y penser ailleurs.
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.token = token;
        this.timeout = timeout;
        this.logger = logger;

        // Construction du HttpClient. Le builder permet de configurer :
        // - connectTimeout : delai pour ETABLIR la connexion (different du
        //   timeout par requete plus bas)
        // - followRedirects : suivre les 301/302 si l'API en envoie un jour
        this.http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // --- API publique : 3 verbes HTTP -------------------------------------

    /**
     * POST /path avec un body JSON. Ex : creer une sanction.
     * Map.of() = aucune query string (les params vont dans le body, pas l'URL).
     */
    public CompletableFuture<HttpResponse<String>> post(String path, String jsonBody) {
        return send(buildJsonRequest("POST", path, Map.of(), jsonBody));
    }

    /**
     * PATCH /path avec un body JSON. Ex : desactiver une sanction (active=false).
     */
    public CompletableFuture<HttpResponse<String>> patch(String path, String jsonBody) {
        return send(buildJsonRequest("PATCH", path, Map.of(), jsonBody));
    }

    /**
     * GET /path?cle=valeur&autre=valeur. Ex : lister les sanctions d'un joueur.
     * Pas de body (null) : un GET ne prend pas de body en HTTP.
     */
    public CompletableFuture<HttpResponse<String>> get(String path, Map<String, String> query) {
        return send(buildJsonRequest("GET", path, query, null));
    }

    // --- Construction de la requete ---------------------------------------

    /**
     * Assemble URL + query string + headers + body en un HttpRequest pret
     * a envoyer. Centralise pour que tous les appels aient les memes
     * headers (Accept, Authorization).
     */
    private HttpRequest buildJsonRequest(String method, String path,
                                         Map<String, String> query, String body) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                // URI.create() parse "http://api.test/api/sanctions?uuid=..."
                .uri(URI.create(baseUrl + path + buildQueryString(query)))
                .timeout(timeout)
                .header("Accept", "application/json")
                // Le format "Bearer <token>" est la convention OAuth2 reprise
                // par Laravel. Cote API, le middleware ApiTokenAuth lit ce
                // header avec request->bearerToken().
                .header("Authorization", "Bearer " + token);

        if (body != null) {
            // Content-Type doit etre present quand on envoie un body, sinon
            // Laravel ne parse pas le JSON et $request->validated() est vide.
            b.header("Content-Type", "application/json");
            // BodyPublishers.ofString : convertit la chaine en flux d'octets
            // UTF-8. Sans le charset explicite, des accents pourraient etre
            // mal encodes selon la JVM par defaut.
            b.method(method, BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            // GET/DELETE n'ont pas de body. noBody() = "0 octet a envoyer".
            b.method(method, BodyPublishers.noBody());
        }
        return b.build();
    }

    // --- Envoi async + logging -------------------------------------------

    /**
     * Envoie la requete et logge automatiquement les echecs.
     *
     * sendAsync() retourne IMMEDIATEMENT un CompletableFuture. La requete
     * part sur un thread interne du HttpClient, le code appelant continue.
     *
     * whenComplete() s'execute quand la reponse arrive (ou quand une erreur
     * survient). C'est equivalent a un "callback" en JavaScript ou une
     * coroutine .then() : le bloc s'execute plus tard, asynchroniquement.
     */
    private CompletableFuture<HttpResponse<String>> send(HttpRequest request) {
        return http.sendAsync(request, BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> {
                    // Cas 1 : exception reseau (timeout, DNS, connexion refusee...)
                    // response est null, error contient l'exception.
                    if (error != null) {
                        logger.log(Level.WARNING, "API call failed: "
                                + request.method() + " " + request.uri(), error);
                        return;
                    }
                    // Cas 2 : reponse recue. On regarde le code HTTP.
                    // 2xx = OK. 4xx = erreur cliente (token, validation).
                    // 5xx = bug cote API. Dans les deux derniers cas on logge
                    // le body pour debug.
                    int code = response.statusCode();
                    if (code >= 400) {
                        logger.warning("API " + request.method() + " " + request.uri()
                                + " returned " + code + ": " + response.body());
                    }
                });
        // Note : whenComplete ne CONSOMME pas le future. Le code appelant
        // recoit le meme CompletableFuture et peut chainer son propre
        // .thenAccept(...) pour reagir a la reponse (ex : prevenir le modo
        // que le ban est confirme).
    }

    // --- Utilitaires prives ----------------------------------------------

    /**
     * Transforme une Map {"uuid": "abc", "active": "true"} en chaine
     * "?uuid=abc&active=true". Encode les caracteres speciaux (espaces,
     * accents) pour qu'ils transitent correctement dans l'URL.
     *
     * Renvoie "" si la map est vide pour ne pas mettre un "?" inutile.
     */
    private static String buildQueryString(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, String> e : query.entrySet()) {
            // On saute les valeurs null pour permettre des filtres optionnels
            // sans avoir a construire une Map differente a chaque appel.
            if (e.getValue() == null) {
                continue;
            }
            if (!first) {
                sb.append('&');
            }
            first = false;
            // URLEncoder transforme " " en "%20", "+" en "%2B", etc.
            // Sans ca, un pseudo "Bob Doe" casserait l'URL.
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
