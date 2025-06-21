package com.example.map_backend.repository;

import com.example.map_backend.model.Coordinates;
import com.example.map_backend.model.Place;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PlaceRepository {

    private final JdbcTemplate jdbcTemplate;

    public PlaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Place> findByNameContaining(String name) {
        String sql = "SELECT id, nom, ST_X(geom) as lng, ST_Y(geom) as lat FROM lieux " +
                "WHERE lower(nom) ILIKE ? " +
                "AND ST_Contains(ST_SetSRID(ST_MakeBox2D(ST_Point(11.4, 3.75), ST_Point(11.6, 3.95)), 4326), geom) " +
                "LIMIT 10";
        return jdbcTemplate.query(sql, new Object[]{"%" + name + "%"}, (rs, rowNum) -> {
            Place place = new Place(rs.getLong("id"), rs.getString("nom"), null);
            place.setCoordinates(new Coordinates(rs.getDouble("lat"), rs.getDouble("lng")));
            return place;
        });
    }

    public Place findPlaceByExactName(String name) {
        String sql = "SELECT id, nom, ST_X(geom) as lng, ST_Y(geom) as lat FROM lieux " +
                "WHERE lower(nom) = ? " +
                "AND ST_Contains(ST_SetSRID(ST_MakeBox2D(ST_Point(11.4, 3.75), ST_Point(11.6, 3.95)), 4326), geom)";
        try {
            return jdbcTemplate.queryForObject(sql, new Object[]{name}, (rs, rowNum) -> {
                Place place = new Place(rs.getLong("id"), rs.getString("nom"), null);
                place.setCoordinates(new Coordinates(rs.getDouble("lat"), rs.getDouble("lng")));
                return place;
            });
        } catch (Exception e) {
            return null; // Lieu non trouvé
        }
    }

    public Place findClosestPlace(double lat, double lng) {
        String sql = "SELECT id, nom, ST_X(geom) as lng, ST_Y(geom) as lat " +
                "FROM lieux " +
                "WHERE ST_Contains(ST_SetSRID(ST_MakeBox2D(ST_Point(11.4, 3.75), ST_Point(11.6, 3.95)), 4326), geom) " +
                "ORDER BY geom <-> ST_SetSRID(ST_MakePoint(?, ?), 4326) LIMIT 1";
        try {
            return jdbcTemplate.queryForObject(sql, new Object[]{lng, lat}, (rs, rowNum) -> {
                Place place = new Place(rs.getLong("id"), rs.getString("nom"), null);
                place.setCoordinates(new Coordinates(rs.getDouble("lat"), rs.getDouble("lng")));
                return place;
            });
        } catch (Exception e) {
            return null; // Lieu non trouvé
        }
    }

    public void savePlace(Place place) {
        String sql = "INSERT INTO lieux (nom, geom) VALUES (?, ST_SetSRID(ST_MakePoint(?, ?), 4326))";
        jdbcTemplate.update(sql, place.getName(), place.getCoordinates().getLng(), place.getCoordinates().getLat());
    }
}