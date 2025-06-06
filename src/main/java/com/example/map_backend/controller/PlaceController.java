package com.example.map_backend.controller;

import com.example.map_backend.model.Place;
import com.example.map_backend.service.PlaceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> searchPlaces(@RequestParam String name) {
        try {
            List<Place> places = placeService.searchPlaces(name);
            if (places.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Aucun lieu trouvé");
                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", places);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Erreur serveur");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/closest")
    public ResponseEntity<Map<String, Object>> findClosestPlace(@RequestParam double lat, @RequestParam double lng) {
        try {
            Place place = placeService.findClosestPlace(lat, lng);
            if (place == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Aucun lieu trouvé près des coordonnées fournies");
                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", place);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Erreur serveur");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}