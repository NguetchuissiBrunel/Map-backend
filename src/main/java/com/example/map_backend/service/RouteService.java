package com.example.map_backend.service;

import com.example.map_backend.model.*;
import com.example.map_backend.repository.PlaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class RouteService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private WebClient.Builder webClientBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long findNearestNode(Point point) throws Exception {
        String roadNodeQuery = """
            SELECT DISTINCT source as id, ST_Distance(
                ST_Transform(geom, 3857),
                ST_Transform(ST_SetSRID(ST_Point(?, ?), 4326), 3857)
            ) as distance
            FROM routes
            WHERE source IS NOT NULL
            UNION
            SELECT DISTINCT target as id, ST_Distance(
                ST_Transform(geom, 3857),
                ST_Transform(ST_SetSRID(ST_Point(?, ?), 4326), 3857)
            ) as distance
            FROM routes
            WHERE target IS NOT NULL
            ORDER BY distance
            LIMIT 1
        """;

        try {
            List<Long> roadResult = jdbcTemplate.query(roadNodeQuery,
                    (rs, rowNum) -> rs.getLong("id"),
                    point.getLng(), point.getLat(), point.getLng(), point.getLat()
            );
            if (!roadResult.isEmpty()) {
                return roadResult.get(0);
            }
        } catch (Exception e) {
            System.err.println("Road node search failed, trying places: " + e.getMessage());
        }

        String placeQuery = """
            SELECT id
            FROM lieux
            WHERE geom IS NOT NULL
            ORDER BY geom <-> ST_SetSRID(ST_Point(?, ?), 4326)
            LIMIT 1
        """;

        List<Long> result = jdbcTemplate.query(placeQuery,
                (rs, rowNum) -> rs.getLong("id"),
                point.getLng(), point.getLat()
        );
        if (result.isEmpty()) {
            throw new Exception("No node found near the provided coordinates");
        }
        return result.get(0);
    }

    private List<Route> getRouteFromOSRM(List<Point> points, String mode, String startPlaceName, String endPlaceName) {
        try {
            String profile = mode.equals("walking") ? "foot" : mode.equals("cycling") ? "bike" : "car";
            String coordinates = points.stream()
                    .map(p -> p.getLng() + "," + p.getLat())
                    .reduce((a, b) -> a + ";" + b)
                    .orElse("");

            String url = "https://router.project-osrm.org/route/v1/" + profile + "/" + coordinates + "?steps=true&geometries=geojson&alternatives=3"; // Changé à alternatives=3

            Mono<String> response = webClientBuilder.build()
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class);

            String jsonResponse = response.block();
            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                throw new Exception("Empty response from OSRM");
            }

            JsonNode data = objectMapper.readTree(jsonResponse);

            if (data.has("code") && !"Ok".equals(data.get("code").asText())) {
                throw new Exception("OSRM error: " + data.get("message").asText("Unknown error"));
            }

            List<Route> routes = new ArrayList<>();
            if (data.has("routes") && data.get("routes").isArray()) {
                for (JsonNode routeNode : data.get("routes")) {
                    List<RouteStep> steps = new ArrayList<>();

                    if (routeNode.has("legs") && routeNode.get("legs").isArray() && routeNode.get("legs").size() > 0) {
                        JsonNode leg = routeNode.get("legs").get(0);
                        if (leg.has("steps") && leg.get("steps").isArray()) {
                            for (JsonNode step : leg.get("steps")) {
                                String geometry = "";
                                if (step.has("geometry") && step.get("geometry").has("coordinates")) {
                                    JsonNode coordinates_node = step.get("geometry").get("coordinates");
                                    if (coordinates_node.isArray() && coordinates_node.size() > 0) {
                                        StringBuilder geom = new StringBuilder("LINESTRING(");
                                        for (JsonNode coord : coordinates_node) {
                                            if (coord.isArray() && coord.size() >= 2) {
                                                geom.append(coord.get(0).asDouble()).append(" ")
                                                        .append(coord.get(1).asDouble()).append(", ");
                                            }
                                        }
                                        if (geom.length() > 11) {
                                            geom.setLength(geom.length() - 2);
                                            geom.append(")");
                                            geometry = geom.toString();
                                        }
                                    }
                                }

                                RouteStep routeStep = new RouteStep();
                                routeStep.setGeometry(geometry);

                                String instruction = "Step";
                                if (step.has("maneuver") && step.get("maneuver").has("instruction")) {
                                    instruction = step.get("maneuver").get("instruction").asText("Step");
                                }
                                routeStep.setSource(instruction);
                                routeStep.setTarget(instruction);

                                routeStep.setDistance(step.has("distance") ? step.get("distance").asDouble() : 0.0);
                                routeStep.setDuration(step.has("duration") ? step.get("duration").asDouble() : 0.0);
                                steps.add(routeStep);
                            }
                        }
                    }

                    if (steps.isEmpty() && routeNode.has("geometry")) {
                        String geometry = "";
                        JsonNode geom_node = routeNode.get("geometry");
                        if (geom_node.has("coordinates") && geom_node.get("coordinates").isArray()) {
                            JsonNode coordinates_node = geom_node.get("coordinates");
                            if (coordinates_node.size() > 0) {
                                StringBuilder geom = new StringBuilder("LINESTRING(");
                                for (JsonNode coord : coordinates_node) {
                                    if (coord.isArray() && coord.size() >= 2) {
                                        geom.append(coord.get(0).asDouble()).append(" ")
                                                .append(coord.get(1).asDouble()).append(", ");
                                    }
                                }
                                if (geom.length() > 11) {
                                    geom.setLength(geom.length() - 2);
                                    geom.append(")");
                                    geometry = geom.toString();
                                }
                            }
                        }

                        RouteStep defaultStep = new RouteStep();
                        defaultStep.setGeometry(geometry);
                        defaultStep.setSource("Start");
                        defaultStep.setTarget("End");
                        defaultStep.setDistance(routeNode.has("distance") ? routeNode.get("distance").asDouble() : 0.0);
                        defaultStep.setDuration(routeNode.has("duration") ? routeNode.get("duration").asDouble() : 0.0);
                        steps.add(defaultStep);
                    }

                    Route resultRoute = new Route();
                    resultRoute.setDistance(routeNode.has("distance") ? routeNode.get("distance").asDouble() : 0.0);
                    resultRoute.setDuration(routeNode.has("duration") ? routeNode.get("duration").asDouble() : 0.0);
                    resultRoute.setSteps(steps);
                    resultRoute.setStartPlaceName(startPlaceName);
                    resultRoute.setEndPlaceName(endPlaceName);
                    routes.add(resultRoute);
                }
            }

            if (routes.isEmpty()) {
                throw new Exception("No valid routes found in OSRM response");
            }

            return routes;

        } catch (Exception e) {
            System.err.println("Error with OSRM: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public RouteResponse routeWithPgRouting(List<Point> points, String mode, String startPlaceName, String endPlaceName) {
        try {
            if (points.size() != 2) {
                RouteResponse response = new RouteResponse();
                response.setError("Exactly two points are required for routing");
                return response;
            }

            try {
                long source = findNearestNode(points.get(0));
                long target = findNearestNode(points.get(1));

                System.out.println("Found nodes - Source: " + source + ", Target: " + target);

                if (source == target) {
                    throw new Exception("Source and target nodes are the same");
                }

                String nodeValidationQuery = """
                SELECT COUNT(*) as count FROM (
                    SELECT source as node FROM routes WHERE source = ? OR target = ?
                    UNION
                    SELECT target as node FROM routes WHERE source = ? OR target = ?
                ) nodes
            """;

                Integer nodeCount = jdbcTemplate.queryForObject(nodeValidationQuery, Integer.class,
                        source, source, target, target);

                if (nodeCount == null || nodeCount == 0) {
                    throw new Exception("Nodes not found in road network");
                }

                double vitesse = mode.equals("driving") ? 25 : mode.equals("walking") ? 2 : 8;

                String query = """
                WITH chemins AS (
                    SELECT path_id, path_seq, node, edge, cost, agg_cost
                    FROM pgr_ksp(
                        'SELECT id, source, target, cost, reverse_cost FROM routes WHERE cost IS NOT NULL AND cost > 0',
                        ?, ?, 3, false
                    )
                )
                SELECT
                    c.path_id,
                    c.path_seq,
                    ST_AsText(r.geom) as geometry,
                    COALESCE(l1.nom, 'Node ' || r.source) as source,
                    COALESCE(l2.nom, 'Node ' || r.target) as target,
                    c.cost as distance
                FROM chemins c
                JOIN routes r ON c.edge = r.id
                LEFT JOIN lieux l1 ON r.source = l1.id
                LEFT JOIN lieux l2 ON r.target = l2.id
                WHERE c.edge > 0
                ORDER BY c.path_id, c.path_seq
            """;

                List<RouteStep> routeStepsA = new ArrayList<>();
                List<RouteStep> routeStepsB = new ArrayList<>();
                List<RouteStep> routeStepsC = new ArrayList<>();
                final double[] distanceA = {0};
                final double[] distanceB = {0};
                final double[] distanceC = {0};
                final double[] durationA = {0};
                final double[] durationB = {0};
                final double[] durationC = {0};

                jdbcTemplate.query(query, new Object[]{source, target}, (rs) -> {
                    RouteStep step = new RouteStep();
                    step.setGeometry(rs.getString("geometry"));
                    step.setSource(rs.getString("source"));
                    step.setTarget(rs.getString("target"));
                    step.setDistance(rs.getDouble("distance"));
                    step.setDuration(rs.getDouble("distance") / vitesse);

                    int pathId = rs.getInt("path_id");
                    switch (pathId) {
                        case 1:
                            routeStepsA.add(step);
                            distanceA[0] += step.getDistance();
                            durationA[0] += step.getDuration();
                            break;
                        case 2:
                            routeStepsB.add(step);
                            distanceB[0] += step.getDistance();
                            durationB[0] += step.getDuration();
                            break;
                        case 3:
                            routeStepsC.add(step);
                            distanceC[0] += step.getDistance();
                            durationC[0] += step.getDuration();
                            break;
                    }
                    return null;
                });

                List<Route> routes = new ArrayList<>();
                if (!routeStepsA.isEmpty()) {
                    Route routeA = new Route();
                    routeA.setDistance(distanceA[0]);
                    routeA.setDuration(durationA[0]);
                    routeA.setSteps(routeStepsA);
                    routeA.setStartPlaceName(startPlaceName);
                    routeA.setEndPlaceName(endPlaceName);
                    routes.add(routeA);
                }
                if (!routeStepsB.isEmpty()) {
                    Route routeB = new Route();
                    routeB.setDistance(distanceB[0]);
                    routeB.setDuration(durationB[0]);
                    routeB.setSteps(routeStepsB);
                    routeB.setStartPlaceName(startPlaceName);
                    routeB.setEndPlaceName(endPlaceName);
                    routes.add(routeB);
                }
                if (!routeStepsC.isEmpty()) {
                    Route routeC = new Route();
                    routeC.setDistance(distanceC[0]);
                    routeC.setDuration(durationC[0]);
                    routeC.setSteps(routeStepsC);
                    routeC.setStartPlaceName(startPlaceName);
                    routeC.setEndPlaceName(endPlaceName);
                    routes.add(routeC);
                }

                if (!routes.isEmpty()) {
                    System.out.println("Route found with pgRouting: " + routes.size() + " alternatives");
                    RouteResponse response = new RouteResponse();
                    response.setRoutes(routes); // Retourne toutes les routes
                    return response;
                }

                throw new Exception("No routes found by pgRouting");

            } catch (Exception dbError) {
                System.out.println("Switching to external API...");
            }

            List<Route> externalRoutes = getRouteFromOSRM(points, mode, startPlaceName, endPlaceName);
            if (!externalRoutes.isEmpty()) {
                System.out.println("Routes calculated with external API: " + externalRoutes.size() + " alternatives");
                RouteResponse response = new RouteResponse();
                response.setRoutes(externalRoutes);
                return response;
            }

            RouteResponse response = new RouteResponse();
            response.setError("No route found with local and external methods");
            return response;

        } catch (Exception e) {
            System.err.println("General error in RouteWithPgRouting: " + e.getMessage());
            e.printStackTrace();

            List<Route> externalRoutes = getRouteFromOSRM(points, mode, startPlaceName, endPlaceName);
            if (!externalRoutes.isEmpty()) {
                System.out.println("Fallback routes calculated with external API: " + externalRoutes.size() + " alternatives");
                RouteResponse response = new RouteResponse();
                response.setRoutes(externalRoutes);
                return response;
            }

            RouteResponse response = new RouteResponse();
            response.setError("Unable to calculate route");
            return response;
        }
    }
}