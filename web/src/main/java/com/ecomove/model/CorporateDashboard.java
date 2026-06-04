package com.ecomove.model;

import java.util.List;

public record CorporateDashboard(
        List<CorporateKpi> kpis,
        List<CorporateMonthlyStat> monthlyStats,
        List<Employee> topEmployees,
        List<DepartmentParticipation> departments
) {}
