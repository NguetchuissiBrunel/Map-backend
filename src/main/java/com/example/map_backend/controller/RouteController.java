
package com.example.map_backend.controller;

import com.example.map_backend.model.*;
import com.example.map_backend.service.RouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    @Autowired
    private RouteService routeService;

    @PostMapping
    public ResponseEntity<RouteResponse> calculateRoute(@RequestBody RouteRequestBody body) {
        try {
            if (body.getPoints() == null || body.getPoints().size() != 2) {
                RouteResponse response = new RouteResponse();
                response.setError("Un itinéraire se calcule entre deux points");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            for (Point point : body.getPoints()) {
                if (point.getLat() < -90 || point.getLat() > 90 || point.getLng() < -180 || point.getLng() > 180) {
                    RouteResponse response = new RouteResponse();
                    response.setError("Coordonnées géographiques invalides");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }
            }

            String mode = body.getMode() != null ? body.getMode() : "driving";
            if (!mode.equals("driving") && !mode.equals("walking") && !mode.equals("cycling")) {
                RouteResponse response = new RouteResponse();
                response.setError("Mode de transport invalide");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            String startPlaceName = body.getStartPlaceName() != null ? body.getStartPlaceName() : "Unknown Start";
            String endPlaceName = body.getEndPlaceName() != null ? body.getEndPlaceName() : "Unknown Destination";

            RouteResponse response = routeService.routeWithPgRouting(body.getPoints(), mode, startPlaceName, endPlaceName);
            if (response.getError() != null) {
                return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            RouteResponse response = new RouteResponse();
            response.setError("Erreur lors du calcul d'itinéraire");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/with-detour")
    public ResponseEntity<RouteResponse> calculateRouteWithDetour(@RequestBody RouteWithDetourRequestBody body) {
        try {
            if (body.getStart() == null || body.getDetour() == null || body.getEnd() == null) {
                RouteResponse response = new RouteResponse();
                response.setError("Les points de départ, détour et arrivée sont requis");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            Point[] points = {body.getStart(), body.getDetour(), body.getEnd()};
            for (Point point : points) {
                if (point.getLat() < -90 || point.getLat() > 90 || point.getLng() < -180 || point.getLng() > 180) {
                    RouteResponse response = new RouteResponse();
                    response.setError("Coordonnées géographiques invalides");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }
            }

            String mode = body.getTransportMode() != null ? body.getTransportMode() : "taxi";
            if (!mode.equals("taxi") && !mode.equals("bus") && !mode.equals("moto")) {
                RouteResponse response = new RouteResponse();
                response.setError("Mode de transport invalide");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            RouteResponse response = routeService.routeWithDetour(
                    body.getStart(),
                    body.getDetour(),
                    body.getEnd(),
                    mode,
                    body.getStartPlaceName(),
                    body.getDetourPlaceName(),
                    body.getEndPlaceName()
            );
            if (response.getError() != null) {
                return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            RouteResponse response = new RouteResponse();
            response.setError("Erreur lors du calcul d'itinéraire avec détour");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
