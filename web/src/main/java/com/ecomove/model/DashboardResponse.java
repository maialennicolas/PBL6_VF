package com.ecomove.model;

import java.util.List;

public record DashboardResponse(
        UserProfile user,
        List<StatCard> stats,
        List<MonthlyStat> monthlyStats,
        List<TransportShare> transportShare,
        List<Trip> recentTrips,
        RouteRecommendation recommendedRoute
) {}
