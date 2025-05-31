package com.example.map_backend.model;

public class RouteResponse {
    private Route route;
    private String error;

    // Getters et setters
    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}