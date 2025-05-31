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
        String sql = "SELECT id, name, ST_X(geom) as lng, ST_Y(geom) as lat FROM places WHERE name ILIKE ? LIMIT 10";
        return jdbcTemplate.query(sql, new Object[]{"%" + name + "%"}, (rs, rowNum) -> {
            Place place = new Place(rs.getLong("id"), rs.getString("name"), null);
            place.setCoordinates(new Coordinates(rs.getDouble("lat"), rs.getDouble("lng")));
            return place;
        });
    }

    public Place findPlaceByExactName(String name) {
        String sql = "SELECT id, name, ST_X(geom) as lng, ST_Y(geom) as lat FROM places WHERE name = ?";
        try {
            return jdbcTemplate.queryForObject(sql, new Object[]{name}, (rs, rowNum) -> {
                Place place = new Place(rs.getLong("id"), rs.getString("name"), null);
                place.setCoordinates(new Coordinates(rs.getDouble("lat"), rs.getDouble("lng")));
                return place;
            });
        } catch (Exception e) {
            return null; // Lieu non trouvé
        }
    }
}