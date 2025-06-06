package com.example.map_backend.service;

import com.example.map_backend.model.Place;
import com.example.map_backend.repository.PlaceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlaceService {

    private final PlaceRepository placeRepository;

    public PlaceService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    public List<Place> searchPlaces(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Le paramètre name est requis");
        }
        return placeRepository.findByNameContaining(name);
    }

    public Place findClosestPlace(double lat, double lng) {
        if (Double.isNaN(lat) || Double.isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Coordonnées invalides : latitude doit être entre -90 et 90, longitude entre -180 et 180");
        }
        return placeRepository.findClosestPlace(lat, lng);
    }
}