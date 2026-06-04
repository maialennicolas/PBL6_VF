package com.ecomove.controller;

import com.ecomove.model.*;
import com.ecomove.service.EcoMoveService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EcoMoveController {

    private final EcoMoveService service;

    public EcoMoveController(EcoMoveService service) {
        this.service = service;
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/auth/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return service.register(request);
    }

    @GetMapping("/catalog/companies")
    public List<Empresa> companies() {
        return service.getCompanies();
    }

    @GetMapping("/catalog/car-models")
    public List<CarModel> carModels() {
        return service.getCarModels();
    }

    @GetMapping("/profile")
    public UserProfile profile(@RequestParam long userId) {
        return service.getProfile(userId);
    }

    @PutMapping("/profile")
    public UserProfile updateProfile(@RequestBody ProfileUpdateRequest request) {
        return service.updateProfile(request);
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@RequestParam long userId) {
        return service.getDashboard(userId);
    }

    @GetMapping("/stats")
    public List<MonthlyStat> stats(@RequestParam long userId) {
        return service.getMonthlyStats(userId);
    }

    @GetMapping("/transport-share")
    public List<TransportShare> transportShare(@RequestParam long userId) {
        return service.getTransportShare(userId);
    }

    @GetMapping("/trips")
    public List<Trip> trips(@RequestParam long userId) {
        return service.getRecentTrips(userId);
    }

    @GetMapping("/riders")
    public List<Rider> riders(@RequestParam long userId) {
        return service.getRiders(userId);
    }

    @GetMapping("/transport-lines")
    public List<TransportLine> transportLines() {
        return service.getTransportLines();
    }

    @GetMapping("/transport-stops")
    public List<TransportStop> transportStops(
            @RequestParam(required = false) String proveedor,
            @RequestParam(required = false) Integer limit) {
        return service.getTransportStops(proveedor, limit);
    }

    @GetMapping("/rewards")
    public List<Reward> rewards(@RequestParam(required = false) String category) {
        return service.getRewards(category);
    }

    @PostMapping("/rewards/redeem")
    public Map<String, Object> redeemReward(@RequestParam long userId, @RequestParam long rewardId) {
        boolean ok = service.redeemReward(userId, rewardId);
        return Map.of("ok", ok, "message",
                ok ? "Saria trukatuta" : "Ez dago puntu nahikorik edo saria ez da existitzen");
    }

    @GetMapping("/route/recommended")
    public RouteRecommendation recommendedRoute(@RequestParam long userId) {
        return service.getRecommendedRoute(userId);
    }

    @PostMapping("/tracking/start")
    public TrackingStatus startTracking(@RequestBody LocationTrackRequest request) {
        return service.startTracking(request);
    }

    @PostMapping("/tracking/location")
    public TrackingStatus saveTrackingLocation(@RequestBody LocationTrackRequest request) {
        return service.saveTrackingLocation(request);
    }

    @PostMapping("/tracking/stop")
    public TrackingStatus stopTracking(
            @RequestParam long userId,
            @RequestParam String sessionId,
            @RequestParam(required = false, defaultValue = "0") long durationSeconds,
            @RequestParam(required = false) String endTimestamp) {
        return service.stopTracking(userId, sessionId, durationSeconds, endTimestamp);
    }

    @PostMapping("/carpool/offers")
    public Map<String, Object> offerTrip(@RequestParam long userId, @RequestBody CarpoolOfferRequest request) {
        service.offerTrip(userId, request);
        return Map.of("ok", true, "message", "Bidaia data/carpool_ofertas.csv fitxategian gorde da");
    }

    @PostMapping("/carpool/join")
    public Map<String, Object> joinRide(@RequestParam long userId, @RequestParam String riderName) {
        service.joinRide(userId, riderName);
        return Map.of("ok", true, "message", "Bidaia elkartzea data/carpool_uniones.csv fitxategian gorde da");
    }

    @GetMapping("/corporate")
    public CorporateDashboard corporate(@RequestParam long userId) {
        return service.getCorporateDashboard(userId);
    }

    @GetMapping("/csv/info")
    public Map<String, String> csvInfo() {
        return Map.of("dataDirectory", service.dataDirectory());
    }

    @GetMapping(value = "/csv/users", produces = "text/csv;charset=UTF-8")
    public String usersCsv() {
        return service.exportCsv("usuarios.csv");
    }

    @GetMapping(value = "/csv/trips", produces = "text/csv;charset=UTF-8")
    public String tripsCsv() {
        return service.exportCsv("viajes.csv");
    }

    @GetMapping(value = "/csv/rewards", produces = "text/csv;charset=UTF-8")
    public String rewardsCsv() {
        return service.exportCsv("recompensas.csv");
    }

    @GetMapping(value = "/csv/transport-lines", produces = "text/csv;charset=UTF-8")
    public String transportLinesCsv() {
        return service.exportCsv("lineas_transporte.csv");
    }

    @GetMapping(value = "/csv/transport-stops", produces = "text/csv;charset=UTF-8")
    public String transportStopsCsv() {
        return service.exportCsv("paradas_transporte.csv");
    }

    @GetMapping(value = "/csv/tracking-locations", produces = "text/csv;charset=UTF-8")
    public String trackingLocationsCsv() {
        return service.exportCsv("ubicaciones_bidaia.csv");
    }
}
