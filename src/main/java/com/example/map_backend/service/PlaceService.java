package com.example.map_backend.service;

import com.example.map_backend.model.Coordinates;
import com.example.map_backend.model.Place;
import com.example.map_backend.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.util.List;
import java.util.logging.Logger;

@Service
public class PlaceService {

    private static final Logger LOGGER = Logger.getLogger(PlaceService.class.getName());

    private final PlaceRepository placeRepository;
    private final WebClient webClient;

    public PlaceService(PlaceRepository placeRepository, WebClient.Builder webClientBuilder) {
        this.placeRepository = placeRepository;
        this.webClient = webClientBuilder.baseUrl("https://nominatim.openstreetmap.org").build();
    }

    public List<Place> searchPlaces(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Le paramètre name est requis");
        }
        String normalizedName = normalizeName(name);
        LOGGER.info("Recherche dans la base pour : " + normalizedName + " (original: " + name + ")");
        List<Place> places = placeRepository.findByNameContaining(normalizedName);
        if (places.isEmpty()) {
            LOGGER.info("Aucun lieu trouvé dans la base, recherche dans OSM pour : " + name);
            Place osmPlace = searchPlaceInOSM(name).block(); // Blocking pour simplicité
            if (osmPlace != null) {
                LOGGER.info("Lieu OSM trouvé : " + osmPlace.getName() + " (" + osmPlace.getCoordinates().getLat() + ", " + osmPlace.getCoordinates().getLng() + ")");
                if (isWithinYaounde(osmPlace.getCoordinates().getLat(), osmPlace.getCoordinates().getLng())) {
                    placeRepository.savePlace(osmPlace);
                    LOGGER.info("Lieu inséré dans la base, re-recherche pour : " + normalizedName);
                    places = placeRepository.findByNameContaining(normalizedName);
                } else {
                    LOGGER.warning("Lieu hors de Yaoundé : " + osmPlace.getName());
                }
            } else {
                LOGGER.info("Aucun lieu trouvé dans OSM pour : " + name);
            }
        } else {
            LOGGER.info("Lieux trouvés dans la base : " + places.size());
        }
        return places;
    }

    public Place findClosestPlace(double lat, double lng) {
        if (Double.isNaN(lat) || Double.isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Coordonnées invalides : latitude doit être entre -90 et 90, longitude entre -180 et 180");
        }
        return placeRepository.findClosestPlace(lat, lng);
    }

    private Mono<Place> searchPlaceInOSM(String name) {
        return webClient.get()
                .uri(uriBuilder -> {
                    java.net.URI uri = uriBuilder
                            .path("/search")
                            .queryParam("q", name + ", Yaoundé")
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .queryParam("bounded", 1)
                            .queryParam("viewbox", "11.4,3.95,11.6,3.75")
                            .queryParam("accept-language", "fr")
                            .build();
                    LOGGER.info("URL OSM : " + uri.toString());
                    return uri;
                })
                .header("User-Agent", "map-backend/1.0")
                .retrieve()
                .bodyToFlux(OsmPlace.class)
                .doOnNext(osmPlace -> LOGGER.info("Réponse OSM brute : lat=" + osmPlace.getLat() + ", lon=" + osmPlace.getLon() + ", name=" + osmPlace.getName() + ", display_name=" + osmPlace.getDisplayName()))
                .next()
                .map(osmPlace -> {
                    String formattedName = normalizeName(osmPlace.getName()); // Normaliser avant insertion
                    LOGGER.info("Nom formaté : " + formattedName);
                    return new Place(null, formattedName, new Coordinates(
                            Double.parseDouble(osmPlace.getLat()),
                            Double.parseDouble(osmPlace.getLon())
                    ));
                })
                .onErrorResume(e -> {
                    LOGGER.severe("Erreur lors de la requête OSM : " + e.getMessage());
                    return Mono.empty();
                });
    }

    private boolean isWithinYaounde(double lat, double lng) {
        boolean within = lat >= 3.75 && lat <= 3.95 && lng >= 11.4 && lng <= 11.6;
        LOGGER.info("Validation Yaoundé : lat=" + lat + ", lng=" + lng + ", dans Yaoundé=" + within);
        return within;
    }

    private String normalizeName(String name) {
        // Normaliser : supprimer les accents et convertir en minuscules
        if (name == null) return "";
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD);
        return normalized.replaceAll("[\\p{M}]", "").toLowerCase().trim();
    }

    // Inner class to map OSM API response
    private static class OsmPlace {
        private String lat;
        private String lon;
        private String name;
        private String display_name;

        public String getLat() {
            return lat;
        }

        public void setLat(String lat) {
            this.lat = lat;
        }

        public String getLon() {
            return lon;
        }

        public void setLon(String lon) {
            this.lon = lon;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDisplayName() {
            return display_name;
        }

        public void setDisplayName(String display_name) {
            this.display_name = display_name;
        }
    }
}