package com.trustledgersaas.security;

import com.trustledgersaas.entity.Shop;
import com.trustledgersaas.repository.ShopRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDate;

@Component
public class SubscriptionInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ShopRepository shopRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return true; // Let JwtFilter handle unauthorized cases
        }

        String token = authHeader.substring(7);
        Long shopId = jwtUtil.extractShopId(token);
        
        if (shopId == null) {
            return true;
        }

        Shop shop = shopRepository.findById(shopId).orElse(null);
        if (shop == null) {
            return true;
        }

        // Check if fully expired (>24 hrs grace period)
        LocalDate expiryDate = shop.getSubscriptionExpiryDate();
        if (expiryDate != null && LocalDate.now().isAfter(expiryDate.plusDays(1))) {
            // It is fully expired. Block access.
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Subscription expired\", \"message\": \"Your subscription has expired. Please renew your plan from the settings page.\"}");
            return false;
        }

        return true;
    }
}
